package reproducer;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.okhttp.OkDockerHttpClient;
import miner.BreakingUpdate;
import miner.BreakingUpdate.Analysis.ReproductionLabel;
import miner.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final Path tempReproductionDir;
    private final Path successfulReproductionDir;
    private final Path unreproducibleReproductionDir;
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    public static final Map<Pattern, List<BreakingUpdate.Analysis.ReproductionLabel>> FAILURE_PATTERNS = new HashMap<>();

    static {
        FAILURE_PATTERNS.put(Pattern.compile("(?i)(COMPILATION ERROR :)"),
                List.of(BreakingUpdate.Analysis.ReproductionLabel.PRECEDING_COMMIT_COMPILATION_FAILURE,
                        BreakingUpdate.Analysis.ReproductionLabel.COMPILATION_FAILURE));
        FAILURE_PATTERNS.put(Pattern.compile("(?i)(Failed to execute goal org.apache.maven.plugins:maven-enforcer-plugin)"),
                List.of(BreakingUpdate.Analysis.ReproductionLabel.PRECEDING_COMMIT_MAVEN_ENFORCER_ERROR,
                        BreakingUpdate.Analysis.ReproductionLabel.MAVEN_ENFORCER_ERROR));
        FAILURE_PATTERNS.put(Pattern.compile("(?i)(Could not resolve dependencies)"),
                List.of(BreakingUpdate.Analysis.ReproductionLabel.PRECEDING_COMMIT_DEPENDENCY_RESOLUTION_FAILURE,
                        BreakingUpdate.Analysis.ReproductionLabel.DEPENDENCY_RESOLUTION_FAILURE));
        FAILURE_PATTERNS.put(Pattern.compile("(?i)(\\[ERROR] Tests run: | There are test failures)"),
                List.of(BreakingUpdate.Analysis.ReproductionLabel.PRECEDING_COMMIT_TEST_FAILURE,
                        BreakingUpdate.Analysis.ReproductionLabel.TEST_FAILURE));
    }

    /**
     * @param datasetDir the directory where breaking update json files should be written.
     * @param reproductionDir the directory where maven logs should be stored.
     * @param jarDir the directory where jar files corresponding to changed dependencies should be stored.
     */
    public ResultManager(Path datasetDir, Path reproductionDir, Path jarDir) {
        var config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        this.client = DockerClientImpl.getInstance(config,
                new OkDockerHttpClient.Builder().dockerHost(config.getDockerHost()).build());
        this.datasetDir = datasetDir;
        this.jarDir = jarDir;

        tempReproductionDir = reproductionDir;
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

    public void storeResult(BreakingUpdate bu, String containerId, String prevContainerId, Boolean isPrecedingCommit,
                            ReproductionLabel reproductionLabel) {

        // Save log result temporarily.
        Path tempLogOutputLocation = tempReproductionDir.resolve(bu.commit + ".log");
        String logLocation = "/%s/%s.log".formatted(bu.project, bu.commit);
        try (InputStream logStream = client.copyArchiveFromContainerCmd(containerId, logLocation).exec()) {
            Files.write(tempLogOutputLocation, logStream.readAllBytes());
        } catch (IOException e) {
            log.error("Could not store the temporary log file for breaking update {}", bu.commit);
            throw new RuntimeException(e);
        }

        // Get reproduction label.
        ReproductionLabel label = reproductionLabel == null ?
                getReproductionLabel(tempLogOutputLocation, isPrecedingCommit) : reproductionLabel;

        log.info("Storing result {} for breaking update {}", label, bu.commit);
        // Save log result in reproduction dir.
        Path outputDir = label.isSuccessful() ? successfulReproductionDir : unreproducibleReproductionDir;
        Path logOutputLocation = outputDir.resolve(bu.commit + ".log");
        boolean isMovingSuccessful = tempLogOutputLocation.toFile().renameTo(logOutputLocation.toFile());
        if (!isMovingSuccessful) log.error("Could not store the log file for breaking update {}", bu.commit);

        // Update breaking update file.
        bu.setReproductionStatus(label.isSuccessful() ? "successful" : "unreproducible");
        bu.setAnalysis(new BreakingUpdate.Analysis(List.of(label), logOutputLocation.toString()));
        JsonUtils.writeToFile(datasetDir.resolve(bu.commit + JsonUtils.JSON_FILE_ENDING), bu);

        // Create docker images if reproduction was successful.
        if (label.isSuccessful()) {
            copyJars(bu, containerId, prevContainerId);
            log.info("Creating images for breaking update {}", bu.commit);
            createImage(bu, prevContainerId, PRECEDING_COMMIT_CONTAINER_TAG);
            createImage(bu, containerId, BREAKING_UPDATE_COMMIT_CONTAINER_TAG);
            // TODO: Push container to repository
        }
    }

    /** Copy old/new pair of dependency jar/pom files from the corresponding containers */
    private void copyJars(BreakingUpdate bu, String containerId, String prevContainerId) {
        String dependencyLocationBase = "/root/.m2/repository/%s/%s/"
                .formatted(bu.dependencyGroupID.replaceAll("\\.", "/"), bu.dependencyArtifactID);
        for (String type : List.of("jar", "pom")) {
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
            try (InputStream jarStream = client.copyArchiveFromContainerCmd(containerId, newDependencyLocation).exec()) {
                Path dir = Files.createDirectories(jarDir
                        .resolve(bu.dependencyGroupID.replaceAll("\\.", "/"))
                        .resolve(bu.newVersion));
                String fileName = "%s-%s.%s".formatted(bu.dependencyArtifactID, bu.newVersion, type);
                Files.write(dir.resolve(fileName), jarStream.readAllBytes());
                break;
            } catch (NotFoundException e) {
                if (type.equals("jar")) {
                    log.error("Could not find the new jar for breaking update {}, even if the old jar exists.",
                            bu.commit);
                    break;
                } else {
                    log.error("Could not find the new pom for breaking update {}, even if the old pom exists.",
                            bu.commit);
                }
            } catch (IOException e) {
                log.error("Could not store the new {} for breaking update {}.", type, bu.commit, e);
            }
        }
    }

    /**
     * Analyze the log file to identify the reproduction label.
     * @param path the path of the log file.
     * @param isPrecedingCommit whether the commit is the proceeding commit or the new commit.
     */
    private ReproductionLabel getReproductionLabel(Path path, Boolean isPrecedingCommit) {
        try {
            String logContent = readLogFile(path.toString());
            for (Map.Entry<Pattern, List<ReproductionLabel>> entry : FAILURE_PATTERNS.entrySet()) {
                Pattern pattern = entry.getKey();
                Matcher matcher = pattern.matcher(logContent);
                if (matcher.find()) {
                    return isPrecedingCommit ? entry.getValue().get(0) : entry.getValue().get(1);
                }
            }
            return isPrecedingCommit ? ReproductionLabel.PRECEDING_COMMIT_UNKNOWN_FAILURE :
                    ReproductionLabel.UNKNOWN_FAILURE;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** Read a given log file. */
    private static String readLogFile(String filePath) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        return content.toString();
    }

    private void createImage(BreakingUpdate bu, String containerId, String extraTag) {
        client.commitCmd(containerId).withRepository(REPOSITORY).withTag(bu.commit + extraTag).exec();
    }
}
