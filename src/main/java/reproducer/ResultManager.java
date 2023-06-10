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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
    private final Logger log = LoggerFactory.getLogger(this.getClass());

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

    public void storeResult(BreakingUpdate bu, String containerId, String prevContainerId, ReproductionLabel label) {
        log.info("Storing result {} for breaking update {}", label, bu.commit);
        // Log result in reproduction dir
        String logLocation = "/%s/%s.log".formatted(bu.project, bu.commit);
        Path outputDir = label.isSuccessful() ? successfulReproductionDir : unreproducibleReproductionDir;
        Path logOutputLocation = outputDir.resolve(bu.commit + ".log");
        try (InputStream logStream = client.copyArchiveFromContainerCmd(containerId, logLocation).exec()) {
            Files.write(logOutputLocation, logStream.readAllBytes());
        } catch (IOException e) {
            log.error("Could not store log file for breaking update {}", bu.commit);
        }

        // Update breaking update file
        bu.setReproductionStatus(label.isSuccessful() ? "successful" : "unreproducible");
        bu.setAnalysis(new BreakingUpdate.Analysis(List.of(label), logOutputLocation.toString()));
        JsonUtils.writeToFile(datasetDir.resolve(bu.commit + JsonUtils.JSON_FILE_ENDING), bu);

        // Create docker images if reproduction was successful
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
            try (InputStream jarStream = client.copyArchiveFromContainerCmd(containerId, newDependencyLocation).exec()) {
                Path dir = Files.createDirectories(jarDir
                        .resolve(bu.dependencyGroupID.replaceAll("\\.", "/"))
                        .resolve(bu.newVersion));
                String fileName = "%s-%s.%s".formatted(bu.dependencyArtifactID, bu.newVersion, type);
                Files.write(dir.resolve(fileName), jarStream.readAllBytes());
                break;
            } catch (NotFoundException e) {
                if (type.equals("jar")) {
                    log.error("Could not find the new jar for breaking update %s, even if the old jar exists."
                            .formatted(bu.commit));
                    break;
                } else {
                    log.error("Could not find the new pom for breaking update %s, even if the old pom exists."
                            .formatted(bu.commit));
                }
            } catch (IOException e) {
                log.error("Could not store the new %s for breaking update %s.".formatted(type, bu.commit), e);
            }
        }
    }

    private void createImage(BreakingUpdate bu, String containerId, String extraTag) {
        client.commitCmd(containerId).withRepository(REPOSITORY).withTag(bu.commit + extraTag).exec();
    }
}
