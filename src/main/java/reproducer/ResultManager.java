package reproducer;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.okhttp.OkDockerHttpClient;
import miner.BreakingUpdate;
import miner.BreakingUpdate.Analysis.ReproductionLabel;
import miner.BreakingUpdate.Metadata.UpdateType;
import miner.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The ResultManager handles storing of reproduction results in the form of logs, jars, Docker images etc.
 *
 * @author <a href="mailto:gabsko@kth.se">Gabriel Skoglund</a>
 */
public class ResultManager {

    /** The repository where the created images will be stored */
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
    private static final String BREAKING_UPDATE_COMMIT_CONTAINER_TAG = "-post";
    private final DockerClient client;
    private final Path datasetDir;
    private final Path jarDir;
    private final Path successfulReproductionDir;
    private final Path unreproducibleReproductionDir;
    private final Collection<String> apiTokens;
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
    public ResultManager(Collection<String> apiTokens, Path datasetDir, Path reproductionDir, Path jarDir)
            throws IOException {
        var config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        this.client = DockerClientImpl.getInstance(config,
                new OkDockerHttpClient.Builder().dockerHost(config.getDockerHost()).build());
        this.datasetDir = datasetDir;
        this.jarDir = jarDir;
        this.apiTokens = apiTokens;
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
    }

    /** Store the log file of the reproduction attempt. */
    public Path storeLogFile(BreakingUpdate bu, String containerId, Boolean isReproducible) {

        log.info("Storing the log file for breaking update {}", bu.commit);
        // Save log result in reproduction dir.
        Path outputDir = isReproducible ? successfulReproductionDir : unreproducibleReproductionDir;
        Path logOutputLocation = outputDir.resolve(bu.commit + ".log");
        String logLocation = "/%s/%s.log".formatted(bu.project, bu.commit);
        try (InputStream logStream = client.copyArchiveFromContainerCmd(containerId, logLocation).exec()) {
            Files.write(logOutputLocation, logStream.readAllBytes());
            return logOutputLocation;
        } catch (IOException e) {
            log.error("Could not store the log file for breaking update {}", bu.commit);
            throw new RuntimeException(e);
        }
    }

    /** Store results when the reproduction is successful. */
    public void storeResult(BreakingUpdate bu, String containerId, String prevContainerId) {

        Path logOutputLocation = storeLogFile(bu, containerId, true);
        // Get reproduction label.
        ReproductionLabel label = getReproductionLabel(logOutputLocation);

        log.info("Storing result {} for breaking update {}", label, bu.commit);
        // Set reproduction status of the breaking update.
        bu.setReproductionStatus("successful");
        // Set analysis of the breaking update.
        bu.setAnalysis(new BreakingUpdate.Analysis(List.of(label), logOutputLocation.toString()));
        // Set metadata of the breaking update.
        UpdateType updateType = extractDependencies(bu, containerId, prevContainerId);
        try {
            MetadataFinder metadataFinder = new MetadataFinder(apiTokens);
            bu.setMetadata(new BreakingUpdate.Metadata(metadataFinder.getCompareLink(bu),
                    metadataFinder.getMavenSourceLinks(bu), updateType));
        } catch (IOException e) {
            log.error("Metadata could not be fetched for the breaking update {}.", bu.commit, e);
        }
        // Update breaking update file.
        JsonUtils.writeToFile(datasetDir.resolve(bu.commit + JsonUtils.JSON_FILE_ENDING), bu);

        // Create docker images if reproduction was successful.
        log.info("Creating images for breaking update {}", bu.commit);
        createImage(bu, prevContainerId, PRECEDING_COMMIT_CONTAINER_TAG);
        createImage(bu, containerId, BREAKING_UPDATE_COMMIT_CONTAINER_TAG);
        // TODO: Push container to repository
    }

    /**
     * Remove JSON data when the reproduction is unsuccessful.
     */
    public void removeResult(BreakingUpdate bu, String containerId) {
        Path logOutputLocation = storeLogFile(bu, containerId, false);
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
    public UpdateType extractDependencies(BreakingUpdate bu, String containerId, String prevContainerId) {
        String dependencyLocationBase = "/root/.m2/repository/%s/%s/"
                .formatted(bu.dependencyGroupID.replaceAll("\\.", "/"), bu.dependencyArtifactID);
        for (String type : List.of("jar", "pom")) {
            UpdateType updateType = UpdateType.valueOf(type.toUpperCase(Locale.ENGLISH));
            String oldDependencyLocation = dependencyLocationBase + "%s/%s-%s.%s"
                    .formatted(bu.previousVersion, bu.dependencyArtifactID, bu.previousVersion, type);
            try (InputStream dependencyStream = client.copyArchiveFromContainerCmd
                    (prevContainerId, oldDependencyLocation).exec()) {
                Path dir = Files.createDirectories(jarDir
                        .resolve(bu.dependencyGroupID.replaceAll("\\.", "/"))
                        .resolve(bu.previousVersion));
                String fileName = "%s-%s.%s".formatted(bu.dependencyArtifactID, bu.previousVersion, type);
                Files.write(dir.resolve(fileName), dependencyStream.readAllBytes());
            } catch (NotFoundException e) {
                if (type.equals("jar")) {
                    log.info("Could not find the old jar for breaking update %s. Searching for a pom instead..."
                            .formatted(bu.commit));
                } else {
                    log.error("Could not find the old jar or pom for breaking update {}", bu.commit);
                }
                continue;
            } catch (IOException e) {
                log.error("Could not store the old %s for breaking update %s.".formatted(type, bu.commit), e);
            }

            String newDependencyLocation = dependencyLocationBase + "%s/%s-%s.%s"
                    .formatted(bu.newVersion, bu.dependencyArtifactID, bu.newVersion, type);
            try (InputStream dependencyStream = client.copyArchiveFromContainerCmd(containerId, newDependencyLocation).exec()) {
                Path dir = Files.createDirectories(jarDir
                        .resolve(bu.dependencyGroupID.replaceAll("\\.", "/"))
                        .resolve(bu.newVersion));
                String fileName = "%s-%s.%s".formatted(bu.dependencyArtifactID, bu.newVersion, type);
                Files.write(dir.resolve(fileName), dependencyStream.readAllBytes());
                return updateType;
            } catch (NotFoundException e) {
                if (type.equals("jar")) {
                    log.error("Could not find the new jar for breaking update %s, even if the old jar exists."
                            .formatted(bu.commit));
                    return updateType;
                } else {
                    log.error("Could not find the new pom for breaking update %s, even if the old pom exists."
                            .formatted(bu.commit));
                }
            } catch (IOException e) {
                log.error("Could not store the new %s for breaking update %s.".formatted(type, bu.commit), e);
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

    private void createImage(BreakingUpdate bu, String containerId, String extraTag) {
        client.commitCmd(containerId).withRepository(REPOSITORY).withTag(bu.commit + extraTag).exec();
    }
}
