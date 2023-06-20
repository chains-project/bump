package reproducer;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.okhttp.OkDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import miner.BreakingUpdate;
import miner.BreakingUpdate.Analysis.ReproductionLabel;
import miner.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

/**
 * The BreakingUpdateReproducer class attempts to reproduce breaking updates in a container.
 * In case of a successful reproduction, the resulting container is stored.
 *
 * @author <a href="mailto:gabsko@kth.se">Gabriel Skoglund</a>
 */
public class BreakingUpdateReproducer {

    public static final String BASE_MAVEN_IMAGE = "maven:3.8.6-eclipse-temurin-11";
    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private static final Short EXIT_CODE_OK = 0;

    private final ResultManager resultManager;
    private final DockerClient client;

    /**
     * Set up a new BreakingUpdateReproducer creating new Docker images based on {@value BASE_MAVEN_IMAGE}
     *
     * @param resultManager the ResultManager that will store information about reproduction results.
     */
    public BreakingUpdateReproducer(ResultManager resultManager) {
        this.resultManager = resultManager;
        DockerClientConfig clientConfig = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withRegistryUrl("https://hub.docker.com")
                .build();
        DockerHttpClient httpClient = new OkDockerHttpClient.Builder()
                .dockerHost(clientConfig.getDockerHost())
                .sslConfig(clientConfig.getSSLConfig())
                .connectTimeout(30)
                .build();
        client = DockerClientImpl.getInstance(clientConfig, httpClient);
        log.info("Docker client created");

        try {
            ensureBaseMavenImageExists();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Iterate through a list of breaking updates and attempt to reproduce if not already attempted.
     * @param breakingUpdates the list of breaking updates to reproduce.
     */
    public void reproduceAll(File[] breakingUpdates) {
        for (File breakingUpdate : breakingUpdates) {
            try {
                BreakingUpdate bu = JsonUtils.readFromFile(breakingUpdate.toPath(), BreakingUpdate.class);
                if (bu.getReproductionStatus().equals("not_attempted")) {
                    reproduce(bu);
                }
            } catch (RuntimeException | InterruptedException e) {
                log.error("An exception occurred while reproducing the breaking update in " +
                        breakingUpdate.getName(), e);
            }
        }
    }

    /**
     * Attempt to reproduce the given breaking update.
     * @param bu the breaking update to reproduce.
     */
    public void reproduce(BreakingUpdate bu) throws InterruptedException {
        createImageForBreakingUpdate(bu);
        Map<String, String> startedContainers = new HashMap<>();

        boolean isPrevBuildSuccessful = false;
        int prevAttemptCount;
        // Try running tests 3 times for the previous commit to ensure that the build failure is not due to flaky tests.
        for (prevAttemptCount = 1; prevAttemptCount < 4; prevAttemptCount++) {
            log.info("Attempting for the {} time to compile and test the previous commit of breaking update {}",
                    prevAttemptCount, bu.commit);
            startedContainers.put("prevContainer%s".formatted(prevAttemptCount), startContainer(bu, getPrevCmd(bu)));
            WaitContainerResultCallback result = client.waitContainerCmd(startedContainers.get("prevContainer%s"
                            .formatted(prevAttemptCount)))
                    .exec(new WaitContainerResultCallback());
            if (result.awaitStatusCode().intValue() != EXIT_CODE_OK) {
                log.info("Build failed for the previous commit of {} for the {} time.", bu.commit, prevAttemptCount);
                // If the build failure is not due to test failures, there will be no more attempts.
                if (!resultManager.isTestFailure(bu, startedContainers.get("prevContainer%s".formatted(prevAttemptCount)),
                        false)) {
                    log.info("Build has failed due to a different reason than test failures, and therefore will not " +
                            "be attempted again.");
                    break;
                }
            } else {
                isPrevBuildSuccessful = true;
                // Remove the log file saved in the unreproducible directory in the previous attempts.
                if (prevAttemptCount > 1) resultManager.removeLogFile(bu, "unreproducible");
                break;
            }
        }
        if (!isPrevBuildSuccessful) {
            resultManager.removeResult(bu);
            removeContainers(bu, startedContainers.values());
            return;
        }

        boolean isBuildSuccessful = false;
        int attemptCount;
        // Try running tests 3 times to ensure that the build failure is not due to flaky tests.
        for (attemptCount = 1; attemptCount < 4; attemptCount++) {
            log.info("Attempting for the {} time to compile and test breaking update {}", attemptCount, bu.commit);
            startedContainers.put("newContainer%s".formatted(attemptCount), startContainer(bu, getCmd(bu)));
            WaitContainerResultCallback result = client.waitContainerCmd(startedContainers.get("newContainer%s"
                    .formatted(attemptCount))).exec(new WaitContainerResultCallback());
            if (result.awaitStatusCode().intValue() != EXIT_CODE_OK) {
                if (!resultManager.isTestFailure(bu, startedContainers.get("newContainer%s".formatted(attemptCount)), true)) {
                    log.info("Build has failed due to a different reason than test failures, and therefore will not " +
                            "be attempted again.");
                    attemptCount++;
                    break;
                }
            } else {
                isBuildSuccessful = true;
                // Remove the log file saved in the successful directory in the previous attempts.
                if (attemptCount > 1) resultManager.removeLogFile(bu, "successful");
                break;
            }
        }
        if (!isBuildSuccessful) {
            resultManager.storeResult(bu, startedContainers.get("newContainer%s".formatted(attemptCount - 1)),
                    startedContainers.get("prevContainer%s".formatted(prevAttemptCount)));
            removeContainers(bu, startedContainers.values());
            return;
        }
        resultManager.removeResult(bu);
        removeContainers(bu, startedContainers.values());
    }

    /** Remove the containers created during the reproduction of the breaking update */
    private void removeContainers(BreakingUpdate bu, Collection<String> startedContainers) {
        log.info("Removing containers for breaking update {}", bu.commit);
        for (String containerId : startedContainers)
            client.removeContainerCmd(containerId).exec();
        client.removeImageCmd(bu.commit + ":base").exec();
    }

    /** Start a container for the given breaking update with a specific command */
    private String startContainer(BreakingUpdate bu, String cmd) {
        CreateContainerResponse container = client.createContainerCmd(bu.commit + ":base")
                .withWorkingDir("/" + bu.project)
                .withCmd("bash", "-c", cmd)
                .exec();
        client.startContainerCmd(container.getId()).exec();
        return container.getId();
    }

    /** Command to compile and test the preceding commit of the breaking update */
    private static String getPrevCmd(BreakingUpdate bu) {
        return "set -o pipefail && git checkout %s && git checkout HEAD~1 && mvn clean test -B | tee %s.log"
                .formatted(bu.commit, bu.commit);
    }

    /** Command to compile and test the breaking update */
    private static String getCmd(BreakingUpdate bu) {
        return "set -o pipefail && git checkout %s && mvn clean test -B | tee %s.log"
                .formatted(bu.commit, bu.commit);
    }

    /** Ensure that the maven docker image we use as a base exists */
    public void ensureBaseMavenImageExists() throws InterruptedException {
        try {
            client.inspectImageCmd(BASE_MAVEN_IMAGE).exec();
        } catch (NotFoundException e) {
            log.info("Maven image not present, pulling {}", BASE_MAVEN_IMAGE);
            client.pullImageCmd(BASE_MAVEN_IMAGE)
                    .exec(new PullImageResultCallback())
                    .awaitCompletion();
            log.info("Done pulling Maven image");
        }
    }

    /** Create a new docker container for the given breaking update **/
    private void createImageForBreakingUpdate(BreakingUpdate bu) {
        log.info("Creating docker image for breaking update {}", bu.commit);
        String projectUrl = bu.url.replaceAll("/pull/\\d+", "");
        CreateContainerResponse container = client.createContainerCmd(BASE_MAVEN_IMAGE)
                .withCmd("/bin/bash", "-c", "git clone " + projectUrl +
                                 " && cd " + bu.project + " && git fetch origin " + bu.commit)
                .exec();
        client.startContainerCmd(container.getId()).exec();
        WaitContainerResultCallback waitResult = client.waitContainerCmd(container.getId())
                .exec(new WaitContainerResultCallback());
        if (waitResult.awaitStatusCode().intValue() != EXIT_CODE_OK) {
            log.warn("Could not create docker image for breaking update {}", bu.commit);
            // TODO: Handle this gracefully
            throw new RuntimeException(waitResult.toString());
        }
        client.commitCmd(container.getId())
                .withRepository(bu.commit)
                .withTag("base").exec();
        log.info("Created docker image for breaking update {}", bu.commit);

        client.removeContainerCmd(container.getId()).exec();
    }
}
