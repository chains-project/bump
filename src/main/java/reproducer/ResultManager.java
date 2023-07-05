package reproducer;

import com.fasterxml.jackson.databind.type.MapType;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.AuthConfig;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.github.dockerjava.core.command.PushImageResultCallback;
import com.github.dockerjava.okhttp.OkDockerHttpClient;
import miner.BreakingUpdate;
import miner.BreakingUpdate.Analysis.ReproductionLabel;
import miner.BreakingUpdate.Metadata.UpdatedFileType;
import miner.GitHubAPITokenQueue;
import miner.JsonUtils;
import okhttp3.OkHttpClient;
import org.kohsuke.github.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The ResultManager handles storing of reproduction results in the form of logs, jars, Docker images etc.
 *
 * @author <a href="mailto:gabsko@kth.se">Gabriel Skoglund</a>
 */
public class ResultManager {

    /**
     * The repository where the created images will be stored
     */
    private static final String REPOSITORY = "ghcr.io/chains-project/breaking-updates";

    /**
     * Tag that will be added as a suffix to breaking update containers containing the state of the repo
     * directly preceding the breaking update commit.
     */
    private static final String PRECEDING_COMMIT_CONTAINER_TAG = "-pre";

    /**
     * Tag that will be added as a suffix to breaking update containers containing the repo at the commit that
     * introduced the breaking update.
     */
    private static final String BREAKING_UPDATE_COMMIT_CONTAINER_TAG = "-breaking";
    /**
     * The repository where the log files and jar/pom files will be stored
     */
    private static final String CACHE_REPO = "chains-project/breaking-updates-cache";
    /**
     * The branch in the CACHE_REPO where the log files and jar/pom files will be committed to.
     */
    private static final String BRANCH_NAME = "main";
    private final DockerClient client;
    private final Path datasetDir;
    private final Path jarDir;
    private final Path successfulReproductionDir;
    private final Path unreproducibleReproductionDir;
    private final GitHubAPITokenQueue tokenQueue;
    private final OkHttpClient httpConnector;
    private final GitHubPackagesCredentials registryCredentials;
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    public static final Map<Pattern, BreakingUpdate.Analysis.ReproductionLabel> FAILURE_PATTERNS = new HashMap<>();

    static {
        FAILURE_PATTERNS.put(Pattern.compile("(?i)(COMPILATION ERROR :)"),
                BreakingUpdate.Analysis.ReproductionLabel.COMPILATION_FAILURE);
        FAILURE_PATTERNS.put(Pattern.compile("(?i)(Failed to execute goal org.apache.maven.plugins:maven-enforcer-plugin)"),
                BreakingUpdate.Analysis.ReproductionLabel.MAVEN_ENFORCER_FAILURE);
        FAILURE_PATTERNS.put(Pattern.compile("(?i)(Could not resolve dependencies)"),
                BreakingUpdate.Analysis.ReproductionLabel.DEPENDENCY_RESOLUTION_FAILURE);
        FAILURE_PATTERNS.put(Pattern.compile("(?i)(\\[ERROR] Tests run: | There are test failures)"),
                BreakingUpdate.Analysis.ReproductionLabel.TEST_FAILURE);
    }

    /**
     * @param datasetDir      the directory where breaking update json files should be written.
     * @param reproductionDir the directory where maven logs should be stored.
     * @param jarDir          the directory where jar files corresponding to changed dependencies should be stored.
     */
    public ResultManager(Collection<String> apiTokens, Path datasetDir, Path reproductionDir, Path jarDir,
                         GitHubPackagesCredentials registryCredentials) throws IOException {
        var config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        this.client = DockerClientImpl.getInstance(config,
                new OkDockerHttpClient.Builder().dockerHost(config.getDockerHost()).build());
        this.datasetDir = datasetDir;
        this.jarDir = jarDir;
        this.tokenQueue = new GitHubAPITokenQueue(apiTokens);
        this.registryCredentials = registryCredentials;
        successfulReproductionDir = reproductionDir.resolve("successful");
        unreproducibleReproductionDir = reproductionDir.resolve("unreproducible");
        if (Files.notExists(successfulReproductionDir) || Files.notExists(unreproducibleReproductionDir)) {
            try {
                log.info("Creating subdirectories for reproduction logs in {}", reproductionDir);
                Files.createDirectories(successfulReproductionDir);
                Files.createDirectories(unreproducibleReproductionDir);
            } catch (IOException e) {
                log.error("Could not create subdirectories for reproduction logs");
                throw new RuntimeException(e);
            }
        }
        httpConnector = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS).build();
    }

    /**
     * Store the log file of the reproduction attempt.
     */
    private Path storeLogFile(BreakingUpdate bu, String containerId, Boolean isReproducible) {

        // Save log result in reproduction dir.
        Path outputDir = isReproducible ? successfulReproductionDir : unreproducibleReproductionDir;
        Path logOutputLocation = outputDir.resolve(bu.commit + ".log");
        String logLocation = "/%s/%s.log".formatted(bu.project, bu.commit);
        try (InputStream logStream = client.copyArchiveFromContainerCmd(containerId, logLocation).exec()) {
            byte[] fileContent = logStream.readAllBytes();
            Files.write(logOutputLocation, fileContent);
            return logOutputLocation;
        } catch (IOException e) {
            log.error("Could not store the log file for breaking update {}", bu.commit);
            throw new RuntimeException(e);
        }
    }

    /**
     * Delete the log file of the reproduction attempt from the wrong directory.
     */
    public void removeLogFile(BreakingUpdate bu, String directory) {
        Path outputDir = directory.equals("successful") ? successfulReproductionDir : unreproducibleReproductionDir;
        boolean isRemovingSuccessful = outputDir.resolve(bu.commit + ".log").toFile().delete();
        if (!isRemovingSuccessful) log.error("Could not remove the log file from the {} reproduction directory for the "
                + "breaking update {}", directory, bu.commit);
    }

    /**
     * Store results when the reproduction is successful.
     */
    public void storeResult(BreakingUpdate bu, String postContainerId, String prevContainerId) {

        Path logOutputLocation = successfulReproductionDir.resolve(bu.commit + ".log");
        // Push the saved log file to the cache repo.
        try {
            byte[] fileContent = Files.readAllBytes(logOutputLocation);
            pushFiles(bu, logOutputLocation, fileContent);
        } catch (IOException e) {
            log.error("Failed to push the {} to the {}.", logOutputLocation.toFile().getName(), CACHE_REPO, e);
        }

        // Get reproduction label.
        ReproductionLabel label = getReproductionLabel(logOutputLocation);
        log.info("Storing result {} for breaking update {}", label, bu.commit);
        // Set reproduction status of the breaking update.
        bu.setReproductionStatus("successful");
        // Set analysis of the breaking update.
        bu.setAnalysis(new BreakingUpdate.Analysis(List.of(label), logOutputLocation.toString()));
        // Set metadata of the breaking update.
        UpdatedFileType updateType = extractDependencies(bu, postContainerId, prevContainerId);
        try {
            MetadataFinder metadataFinder = new MetadataFinder(tokenQueue);
            bu.setMetadata(new BreakingUpdate.Metadata(metadataFinder.getCompareLink(bu),
                    metadataFinder.getMavenSourceLinks(bu), updateType));
        } catch (IOException e) {
            log.error("Metadata could not be fetched for the breaking update {}.", bu.commit, e);
        }

        // Create docker images if reproduction was successful.
        log.info("Creating images for breaking update {}", bu.commit);
        createImage(bu, prevContainerId, PRECEDING_COMMIT_CONTAINER_TAG);
        createImage(bu, postContainerId, BREAKING_UPDATE_COMMIT_CONTAINER_TAG);
        log.info("Pushing the created images for breaking update {}", bu.commit);
        pushImage(bu, PRECEDING_COMMIT_CONTAINER_TAG, registryCredentials);
        pushImage(bu, BREAKING_UPDATE_COMMIT_CONTAINER_TAG, registryCredentials);
        storeImageMetadata(bu, List.of(PRECEDING_COMMIT_CONTAINER_TAG, BREAKING_UPDATE_COMMIT_CONTAINER_TAG),
                List.of("/root/.m2", "/" + bu.project));

        bu.setBaseBuildCommand("docker run %s:%s%s".formatted(REPOSITORY, bu.commit, PRECEDING_COMMIT_CONTAINER_TAG));
        bu.setBreakingUpdateReproductionCommand("docker run %s:%s%s".formatted(REPOSITORY, bu.commit,
                BREAKING_UPDATE_COMMIT_CONTAINER_TAG));
        // Update breaking update file.
        JsonUtils.writeToFile(datasetDir.resolve(bu.commit + JsonUtils.JSON_FILE_ENDING), bu);
    }

    /**
     * Remove JSON data when the reproduction is unsuccessful.
     */
    public void removeResult(BreakingUpdate bu) {
        log.info("Removing the JSON file containing an unreproducible breaking update {}", bu.commit);
        boolean isRemovingSuccessful = datasetDir.resolve(bu.commit + JsonUtils.JSON_FILE_ENDING).toFile().delete();
        if (!isRemovingSuccessful) log.error("Could not remove the JSON file of unreproducible breaking update {}"
                , bu.commit);
    }

    /**
     * Copy old/new pair of dependency jar/pom files from the corresponding containers.
     *
     * @return the type of the updated dependency.
     */
    private UpdatedFileType extractDependencies(BreakingUpdate bu, String postContainerId, String prevContainerId) {
        String dependencyLocationBase = "/root/.m2/repository/%s/%s/"
                .formatted(bu.dependencyGroupID.replaceAll("\\.", "/"), bu.dependencyArtifactID);
        for (String type : List.of("jar", "pom")) {
            UpdatedFileType updateType = UpdatedFileType.valueOf(type.toUpperCase(Locale.ENGLISH));
            String oldDependencyLocation = dependencyLocationBase + "%s/%s-%s.%s"
                    .formatted(bu.previousVersion, bu.dependencyArtifactID, bu.previousVersion, type);
            try (InputStream dependencyStream = client.copyArchiveFromContainerCmd
                    (prevContainerId, oldDependencyLocation).exec()) {
                Path dir = Files.createDirectories(jarDir
                        .resolve(bu.dependencyGroupID.replaceAll("\\.", "/"))
                        .resolve(bu.previousVersion));
                String fileName = "%s-%s.%s".formatted(bu.dependencyArtifactID, bu.previousVersion, type);
                byte[] fileContent = dependencyStream.readAllBytes();
                Path filePath = Files.write(dir.resolve(fileName), fileContent);
                // Push the saved old jar/pom file to the cache repo.
                pushFiles(bu, filePath, fileContent);
            } catch (NotFoundException e) {
                if (type.equals("jar")) {
                    log.info("Could not find the old jar for breaking update {}. Searching for a pom instead...",
                            bu.commit);
                } else {
                    log.error("Could not find the old jar or pom for breaking update {}", bu.commit);
                }
                continue;
            } catch (IOException e) {
                log.error("Could not store the old {} for breaking update {}.", type, bu.commit, e);
            }

            String newDependencyLocation = dependencyLocationBase + "%s/%s-%s.%s"
                    .formatted(bu.newVersion, bu.dependencyArtifactID, bu.newVersion, type);
            try (InputStream dependencyStream = client.copyArchiveFromContainerCmd(postContainerId, newDependencyLocation).exec()) {
                Path dir = Files.createDirectories(jarDir
                        .resolve(bu.dependencyGroupID.replaceAll("\\.", "/"))
                        .resolve(bu.newVersion));
                String fileName = "%s-%s.%s".formatted(bu.dependencyArtifactID, bu.newVersion, type);
                byte[] fileContent = dependencyStream.readAllBytes();
                Path filePath = Files.write(dir.resolve(fileName), fileContent);
                // Push the saved new jar/pom file to the cache repo.
                pushFiles(bu, filePath, fileContent);
                return updateType;
            } catch (NotFoundException e) {
                if (type.equals("jar")) {
                    log.error("Could not find the new jar for breaking update {}, even if the old jar exists.",
                            bu.commit);
                    return updateType;
                } else {
                    log.error("Could not find the new pom for breaking update {}, even if the old pom exists.",
                            bu.commit);
                }
            } catch (IOException e) {
                log.error("Could not store the new {} for breaking update {}.", type, bu.commit, e);
            }
            return updateType;
        }
        return null;
    }

    /**
     * Analyze the log file to identify the reproduction label.
     * @param path the path of the log file.
     */
    private ReproductionLabel getReproductionLabel(Path path) {
        try {
            String logContent = Files.readString(path);
            for (Map.Entry<Pattern, ReproductionLabel> entry : FAILURE_PATTERNS.entrySet()) {
                Pattern pattern = entry.getKey();
                Matcher matcher = pattern.matcher(logContent);
                if (matcher.find()) {
                    return entry.getValue();
                }
            }
            return ReproductionLabel.UNKNOWN_FAILURE;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Check whether the build failed due to test failures.
     */
    public Boolean isTestFailure(BreakingUpdate bu, String containerId, Boolean isReproducible) {
        Path logOutputLocation = storeLogFile(bu, containerId, isReproducible);
        return getReproductionLabel(logOutputLocation).equals(BreakingUpdate.Analysis.ReproductionLabel.TEST_FAILURE);
    }

    /**
     * Create a new image with the changes of a breaking update reproduction container.
     */
    private void createImage(BreakingUpdate bu, String containerId, String extraTag) {
        Map<String, String> labels = Map.of(
                "github_repository", bu.project,
                "pr_url", bu.url,
                "updated_dependency", bu.dependencyGroupID + "/" + bu.dependencyArtifactID,
                "new_version", bu.newVersion,
                "previous_version", bu.previousVersion,
                "reproduction_label", bu.getAnalysis().labels.get(0).name()
        );
        client.commitCmd(containerId).withRepository(REPOSITORY).withTag(bu.commit + extraTag)
                .withLabels(labels).exec();
    }

    /**
     * The GitHubPackagesCredentials contains the required credentials to push an image to GitHub packages.
     */
    public record GitHubPackagesCredentials(String userName, String identityToken) {
        public static ResultManager.GitHubPackagesCredentials fromJson(Path jsonFile) {
            return JsonUtils.readFromFile(jsonFile, ResultManager.GitHubPackagesCredentials.class);
        }
    }

    /**
     * Push an image to GitHub packages using the provided credentials.
     */
    public void pushImage(BreakingUpdate bu, String extraTag, GitHubPackagesCredentials registryCredentials) {
        try {
            AuthConfig authConfig = new AuthConfig()
                    .withUsername(registryCredentials.userName)
                    .withPassword(registryCredentials.identityToken)
                    .withRegistryAddress(REPOSITORY);
            client.pushImageCmd(REPOSITORY)
                    .withTag(bu.commit + extraTag)
                    .withAuthConfig(authConfig)
                    .exec(new PushImageResultCallback())
                    .awaitCompletion();
        } catch (Exception e) {
            log.error("Failed to push the image {} to GitHub packages.", bu.commit + extraTag, e);
        }
    }

    /**
     * Push a given log file or a jar/pom file to the GitHub repo breaking-updates-cache.
     */
    public void pushFiles(BreakingUpdate bu, Path filePath, byte[] fileContent) {

        try {
            GitHub github = tokenQueue.getGitHub(httpConnector);
            GHRepository repo = github.getRepository(CACHE_REPO);
            GHRef branchRef = repo.getRef("heads/" + BRANCH_NAME);
            String latestCommitHash = branchRef.getObject().getSha();

            // Create the tree.
            GHTreeBuilder treeBuilder = repo.createTree();
            treeBuilder.baseTree(latestCommitHash);
            treeBuilder.add(bu.commit + "/" + filePath.toFile().getName(), fileContent, false);
            GHTree tree = treeBuilder.create();

            // Create the commit.
            GHCommit commit = repo.createCommit()
                    .message("Push the %s for the breaking update %s.".formatted(filePath.toFile().getName(), bu.commit))
                    .parent(latestCommitHash)
                    .tree(tree.getSha())
                    .create();

            // Update the branch reference.
            branchRef.updateTo(commit.getSHA1());

            log.info("Successfully pushed the {} to the {}.", filePath.toFile().getName(), CACHE_REPO);
        } catch (IOException e) {
            log.error("Failed to push the {} to the {}.", filePath.toFile().getName(), CACHE_REPO, e);
        } catch (GHException e) {
            log.error("The provided GitHub token does not have the permission to push the {} to the {}",
                    filePath.toFile().getName(), CACHE_REPO, e);
        }
    }

    /**
     * Store image metadata for successfully created images. Image metadata includes size of the all downloaded
     * dependencies for the project (.m2 folder) and the size of the project after cloning.
     */
    public void storeImageMetadata(BreakingUpdate bu, List<String> tags, List<String> folderPaths) {
        Map<String, String> reproduction_metadata = new HashMap<>();
        for (int tagCount = 0; tagCount < tags.size(); tagCount++) {
            for (String folderPath : folderPaths) {
                CreateContainerResponse container = client.createContainerCmd(REPOSITORY + ":" + bu.commit + tags.get(tagCount))
                        .withCmd("/bin/sh", "-c", "tail -f /dev/null").exec();
                client.startContainerCmd(container.getId()).exec();
                // Execute the `du` command inside the container to get the folder size.
                String[] command = {"/bin/sh", "-c", "du -s " + folderPath};
                ExecCreateCmdResponse execCreateCmdResponse = client.execCreateCmd(container.getId())
                        .withAttachStdout(true)
                        .withCmd(command)
                        .exec();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                try {
                    client.execStartCmd(execCreateCmdResponse.getId())
                            .exec(new ExecStartResultCallback(outputStream, System.err))
                            .awaitCompletion();
                    // Extract the folder size from the command output.
                    String[] commandOutput = outputStream.toString(StandardCharsets.UTF_8).trim().split("\\s+");
                    if (folderPath.contains("m2")) {
                        reproduction_metadata.put((tagCount < 1) ? "prevImageM2FolderSize" : "postImageM2FolderSize",
                                String.valueOf(commandOutput[0]));
                    } else {
                        reproduction_metadata.put((tagCount < 1) ? "prevImageProjectFolderSize" : "postImageProjectFolderSize",
                                String.valueOf(commandOutput[0]));
                    }
                } catch (InterruptedException e) {
                    log.error("Failed to get the folder size of the folder {} inside the image {} for the " +
                            "breaking update {}.", folderPath, REPOSITORY + ":" + bu.commit + tags.get(tagCount), bu.commit, e);
                }
                client.stopContainerCmd(container.getId()).exec();
                client.removeContainerCmd(container.getId()).exec();
            }
        }
        try {
            MapType jsonType = JsonUtils.getTypeFactory().constructMapType(Map.class, String.class, Object.class);
            Path imageMetadataFilePath = successfulReproductionDir.resolve("image_metadata" + JsonUtils.JSON_FILE_ENDING);
            if (Files.notExists(imageMetadataFilePath)) {
                Files.createFile(imageMetadataFilePath);
            }
            Map<String, Map<String, String>> imageMetadata = JsonUtils.readFromNullableFile(successfulReproductionDir
                    .resolve("image_metadata" + JsonUtils.JSON_FILE_ENDING), jsonType);
            if (imageMetadata == null) {
                imageMetadata = new HashMap<>();
            }
            imageMetadata.put(bu.commit, reproduction_metadata);
            JsonUtils.writeToFile(imageMetadataFilePath, imageMetadata);
            log.info("Successfully stored the image metadata for the breaking update {} in {}\\image_metadata.json file.",
                    bu.commit, successfulReproductionDir);
        } catch (RuntimeException | IOException e) {
            log.error("Failed to store the image metadata for the breaking update {}.", bu.commit, e);
        }
    }
}
