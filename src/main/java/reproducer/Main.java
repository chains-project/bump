package reproducer;

import miner.BreakingUpdate;
import miner.JsonUtils;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * This class represents the main entry point to the breaking update reproducer.
 *
 * @author <a href="mailto:gabsko@kth.se">Gabriel Skoglund</a>
 * <p>
 * // TODO: Add option to select a whole directory of files to reproduce
 * // TODO: Add option to redo reproduction (default should be to ignore the breaking update if already reproduced)
 */
public class Main {

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Reproduce()).execute(args);
        System.exit(exitCode);
    }

    @CommandLine.Command(name = "reproduce", mixinStandardHelpOptions = true, version = "0.1")
    private static class Reproduce implements Runnable {

        @CommandLine.Option(
                names = {"-a", "--api-tokens"},
                paramLabel = "TOKEN-FILE",
                description = "A file containing a newline separated list of GitHub API tokens",
                required = true
        )
        Path apiTokenFile;

        @CommandLine.Option(
                names = {"-d", "--dataset-dir"},
                paramLabel = "DATASET-DIR",
                description = "The directory where breaking update information are written.",
                required = true
        )
        Path datasetDir;

        @CommandLine.Option(
                names = {"-r", "--reproduction-dir"},
                paramLabel = "REPRODUCTION-DIR",
                description = "The directory where maven logs and reproduction information should be written.",
                required = true
        )
        Path reproductionDir;

        @CommandLine.Option(
                names = {"-j", "--jar-dir"},
                paramLabel = "JAR-DIR",
                description = "The directory where jar files for the changed dependencies should be stored.",
                required = true
        )
        Path jarDir;

        @CommandLine.Option(
                names = {"-f", "--file"},
                paramLabel = "BREAKING-UPDATE-FILE",
                description = "A JSON file for a specific breaking update to reproduce. If not provided, " +
                        "all breaking updates in the dataset directory which have not already been reproduced " +
                        "will be reproduced instead."
        )
        Path breakingUpdateFile;

        @CommandLine.Option(
                names = {"-c", "--github-packages-credentials"},
                paramLabel = "GITHUB-PACKAGES-CREDENTIALS",
                description = "A JSON file containing the credentials required to push an image to GitHub packages.",
                required = true
        )
        Path credentialsFile;

        @Override
        public void run() {
            try {
                List<String> apiTokens = Files.readAllLines(apiTokenFile);
                ResultManager.GitHubPackagesCredentials credentials = ResultManager.GitHubPackagesCredentials
                        .fromJson(credentialsFile);
                ResultManager resultManager = new ResultManager(apiTokens, datasetDir, reproductionDir, jarDir, credentials);
                BreakingUpdateReproducer reproducer = new BreakingUpdateReproducer(resultManager);
                if (breakingUpdateFile != null) {
                    BreakingUpdate bu = JsonUtils.readFromFile(breakingUpdateFile, BreakingUpdate.class);
                    reproducer.reproduce(bu);
                } else {
                    File[] breakingUpdates = datasetDir.toFile().listFiles();
                    if (breakingUpdates != null && breakingUpdates.length > 0) {
                        reproducer.reproduceAll(breakingUpdates);
                    }
                }
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
