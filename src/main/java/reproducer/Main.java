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
                names = {"-b", "--benchmark-dir"},
                paramLabel = "BENCHMARK-DIR",
                description = "The directory where successful breaking update reproduction information should be written.",
                required = true
        )
        Path benchmarkDir;

        @CommandLine.Option(
                names = {"-u", "--unsuccessful-reproductions-dir"},
                paramLabel = "UNSUCCESSFUL-REPRODUCTIONS-DIR",
                description = "The directory where unsuccessful breaking update reproduction information should be written.",
                required = true
        )
        Path unsuccessfulReproductionsDir;

        @CommandLine.Option(
                names = {"-d", "--in-progress-reproductions-dir"},
                paramLabel = "NOT-REPRODUCED-DATA-DIR",
                description = "The directory where in-progress candidate breaking update files are located.",
                required = true
        )
        Path notReproducedDataDir;

        @CommandLine.Option(
                names = {"-l", "--log-dir"},
                paramLabel = "LOG-DIR",
                description = "The directory where maven logs and reproduction information should be written.",
                required = true
        )
        Path logDir;

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

        @CommandLine.Option(
                names = {"-w", "--workflow-log-download-dir"},
                paramLabel = "WORKFLOW-DIR",
                description = "The directory to download workflow logs. If a workflow directory is not defined, " +
                        "workflow log files will not be downloaded."
        )
        String workflowDir;

        @CommandLine.Option(
                names = {"-ud", "--user-data-dir"},
                paramLabel = "USER-DATA-DIR",
                description = "The directory where the user data in Chrome is saved. This is required to keep an active " +
                        "web session with GitHub, when downloading the workflow log files. If it is not necessary to download" + 
                        "workflow logs, this option can be ignored."
        )
        String userDataDir;

        @CommandLine.Option(
                names = {"-ch", "--chrome-driver-path"},
                paramLabel = "CHROME-DRIVER-PATH",
                description = "The chrome driver path. This is required to run the WorkflowLogFinder in Windows systems."
        )
        String chromeDriverPath;

        @Override
        public void run() {
            try {
                List<String> apiTokens = Files.readAllLines(apiTokenFile);
                ResultManager.GitHubPackagesCredentials credentials = ResultManager.GitHubPackagesCredentials
                        .fromJson(credentialsFile);
                ResultManager resultManager = new ResultManager(apiTokens, benchmarkDir, unsuccessfulReproductionsDir,
                        notReproducedDataDir, logDir, jarDir, workflowDir, userDataDir, chromeDriverPath, credentials);
                BreakingUpdateReproducer reproducer = new BreakingUpdateReproducer(resultManager);
                if (breakingUpdateFile != null) {
                    BreakingUpdate bu = JsonUtils.readFromFile(breakingUpdateFile, BreakingUpdate.class);
                    reproducer.reproduce(bu);
                } else {
                    File[] breakingUpdates = notReproducedDataDir.toFile().listFiles();
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
