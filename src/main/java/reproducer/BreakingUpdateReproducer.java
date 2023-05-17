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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
     * Attempt to reproduce the given breaking update.
     * @param bu the breaking update to reproduce.
     */
    public void reproduce(BreakingUpdate bu) {
        createImageForBreakingUpdate(bu);
        Map<String, String> startedContainers = new HashMap<>();

        log.info("Attempting to compile previous commit for breaking update {}",  bu.commit);
        startedContainers.put("prevCompileContainer", startContainer(bu, getPrevCompileCmd(bu)));
        WaitContainerResultCallback result = client.waitContainerCmd(startedContainers.get("prevCompileContainer"))
                .exec(new WaitContainerResultCallback());
        if (result.awaitStatusCode().intValue() != EXIT_CODE_OK) {
            log.info("Compile step failed for previous commit of {}", bu.commit);
            resultManager.storeResult(bu, startedContainers.get("prevCompileContainer"), null,
                                      ReproductionLabel.PRECEDING_COMMIT_COMPILATION_FAILURE);
            removeContainers(bu, startedContainers.values());
            return;
        }

        log.info("Attempting to compile breaking update {}", bu.commit);
        startedContainers.put("compileContainer", startContainer(bu, getCompileCmd(bu)));
        result = client.waitContainerCmd(startedContainers.get("compileContainer")).exec(new WaitContainerResultCallback());
        if (result.awaitStatusCode().intValue() != EXIT_CODE_OK) {
            log.info("Compile step failed for breaking update {}", bu.commit);
            resultManager.storeResult(bu, startedContainers.get("compileContainer"),
                                      startedContainers.get("prevCompileContainer"),
                                      ReproductionLabel.COMPILATION_FAILURE);
            removeContainers(bu, startedContainers.values());
            return;
        }

        // TODO: Repeat tests in accordance with some form of best practice to handle flaky tests
        log.info("Attempting to test previous commit for breaking update {}", bu.commit);
        startedContainers.put("prevTestContainer", startContainer(bu, getPrevTestCmd(bu)));
        result = client.waitContainerCmd(startedContainers.get("prevTestContainer")).exec(new WaitContainerResultCallback());
        if (result.awaitStatusCode().intValue() != EXIT_CODE_OK) {
            log.info("Test step failed for previous commit of {}", bu.commit);
            resultManager.storeResult(bu, startedContainers.get("prevTestContainer"), null,
                                      ReproductionLabel.PRECEDING_COMMIT_TEST_FAILURE);
            removeContainers(bu, startedContainers.values());
            return;
        }

        // TODO: Repeat tests in accordance with some form of best practice to handle flaky tests
        log.info("Attempting to test breaking update {}", bu.commit);
        startedContainers.put("testContainer", startContainer(bu, getTestCmd(bu)));
        result = client.waitContainerCmd(startedContainers.get("testContainer")).exec(new WaitContainerResultCallback());
        if (result.awaitStatusCode().intValue() != EXIT_CODE_OK) {
            log.info("Test step failed for breaking update {}", bu.commit);
            resultManager.storeResult(bu, startedContainers.get("testContainer"),
                                      startedContainers.get("prevTestContainer"), ReproductionLabel.TEST_FAILURE);
            removeContainers(bu, startedContainers.values());
            return;
        }

        log.info("Breaking update {} passed all stages without failing", bu.commit);
        resultManager.storeResult(bu, startedContainers.get("testContainer"), null, ReproductionLabel.NO_FAILURE);
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

    /** Command to build the preceding commit of the breaking update */
    private static String getPrevCompileCmd(BreakingUpdate bu) {
        return "set -o pipefail && git checkout %s && git checkout HEAD~1 && mvn compile -B | tee %s.log"
                .formatted(bu.commit, bu.commit);
    }

    /** Command to build the breaking update */
    private static String getCompileCmd(BreakingUpdate bu) {
        // We set the pipefail flag to pass the status of any non-zero exit code in pipes.
        // This is useful for keeping the result of the mvn command when passing it through tee.
        return "set -o pipefail && git checkout %s && mvn compile -B | tee %s.log"
                .formatted(bu.commit, bu.commit);
    }

    /** Command to test the preceding commit of the breaking update */
    private static String getPrevTestCmd(BreakingUpdate bu) {
        return "set -o pipefail && git checkout %s && git checkout HEAD~1 && mvn test -B | tee %s.log"
                .formatted(bu.commit, bu.commit);
    }

    /** Command to test the breaking update */
    private static String getTestCmd(BreakingUpdate bu) {
        return "set -o pipefail && git checkout %s && mvn test -B | tee %s.log"
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
