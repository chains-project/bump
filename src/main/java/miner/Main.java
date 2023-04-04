package miner;

import org.jetbrains.annotations.NotNull;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

/**
 * This class represents the main entry point to the GitHub breaking updates miner.
 *
 * @author <a href="mailto:gabsko@kth.se">Gabriel Skoglund</a>
 */
public class Main {

    /** The minimum number of stars required when searching for GitHub repositories */
    private static final int MINIMUM_NUMBER_OF_STARS = 10;

    public static void main(String[] args) {
        CommandLineParams params = getCommandLineParams(args);

        try {
            List<String> apiTokens = Files.readAllLines(params.tokens);
            GitHubMiner miner = new GitHubMiner(apiTokens, params.outputDirectory);
            if (params.searchType.reposToMine != null) {
                miner.mineRepositories(params.searchType.reposToMine);
            } else {
                System.out.println("Attempting to find repos");
                Path foundRepos = params.searchType.foundRepos;
                if (foundRepos == null) {
                    Path filePath = params.outputDirectory.resolve(GitHubMiner.FOUND_REPOS_FILE);
                    try {
                        foundRepos = Files.writeString(filePath, JsonUtils.EMPTY_JSON_OBJECT,
                                                       StandardOpenOption.CREATE_NEW);
                    } catch (IOException e) {
                        System.err.println("Could not create a output file in " + filePath +
                            " as the file might already exist. Either specify a different output path or provide" +
                            " an explicit output file for found repositories.");
                        System.exit(1);
                    }
                    System.out.println("Created a new file for found repos as " + filePath);
                }
                miner.findRepositories(new RepositoryList(foundRepos), MINIMUM_NUMBER_OF_STARS);
            }
        } catch (IOException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    @NotNull
    private static CommandLineParams getCommandLineParams(String[] args) {
        CommandLineParams params = new CommandLineParams();
        CommandLine commandLine = new CommandLine(params);
        try {
            commandLine.parseArgs(args);
        } catch (CommandLine.MissingParameterException e) {
            System.out.println(e.getMessage());
            commandLine.usage(System.out);
            System.exit(1);
        }
        if (commandLine.isUsageHelpRequested() || args.length == 0) {
            System.exit(0);
        }
        return params;
    }

    private static class CommandLineParams {
        @CommandLine.Option(
            names = {"-a", "--api-tokens"},
            paramLabel = "TOKEN-FILE",
            description = "A file containing a newline separated list of GitHub API tokens",
            required = true
        )
        Path tokens;

        @CommandLine.Option(
            names = {"-o", "--output-directory"},
            paramLabel = "OUTPUT-DIR",
            description = "A directory where output data will be stored.",
            required = true
        )
        Path outputDirectory;

        @CommandLine.Option(
            names = {"-h", "--help"},
            description = "Print this help message.",
            usageHelp = true
        )
        boolean usageHelpRequested;

        @CommandLine.ArgGroup(multiplicity = "1")
        SearchType searchType;

        static class SearchType {
            @CommandLine.Option(
                names = {"-m", "--mine"},
                arity = "1", // A file is required
                paramLabel = "REPO-FILE",
                description = "Search the given repositories for matching pull requests. The file provided should " +
                              "be a JSON file as given by the --discover-repos operation."
            )
            Path reposToMine;

            @CommandLine.Option(
                names = {"-f", "--find-repos"},
                arity = "0..1",
                paramLabel = "REPO-FILE",
                fallbackValue = CommandLine.Option.NULL_VALUE,
                description = "Search GitHub for repos matching the requirements." +
                              "If a file is provided, found repositories will be added to this file," +
                              "otherwise a file named found_repositories.json will be created in the output directory" +
                              "and used to store the results."
            )
            Path foundRepos;
        }
    }
}
