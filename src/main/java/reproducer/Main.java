package reproducer;

import miner.BreakingUpdate;
import miner.JsonUtils;
import picocli.CommandLine;

import java.io.File;
import java.nio.file.Path;

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

        @Override
        public void run() {
            ResultManager resultManager = new ResultManager(datasetDir, reproductionDir, jarDir);
            BreakingUpdateReproducer reproducer = new BreakingUpdateReproducer(resultManager);
            if (breakingUpdateFile != null) {
                BreakingUpdate bu = JsonUtils.readFromFile(breakingUpdateFile, BreakingUpdate.class);
                reproducer.reproduce(bu);
            } else {
                File[] breakingUpdates = datasetDir.toFile().listFiles();
                if (breakingUpdates.length > 0) {
                    reproducer.reproduceAll(breakingUpdates);
                } else {
                    throw new RuntimeException("The provided directory containing breaking updates is empty.");
                }
            }
        }
    }
}
