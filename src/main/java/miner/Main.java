package miner;

import org.jetbrains.annotations.NotNull;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
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
            GitHubMiner miner = new GitHubMiner(apiTokens, new JSONFileWriter(params.outputDirectory));
            if (params.searchType.repos != null) {
                List<String> repos = Files.readAllLines(params.searchType.repos[0]);
                List<String> reposToIgnore = Collections.emptyList();
                if (params.searchType.repos.length == 2) {
                    reposToIgnore = Files.readAllLines(params.searchType.repos[1]);
                }
                miner.mineRepositories(params.outputDirectory, repos, reposToIgnore);
            } else {
                miner.findRepositories(params.outputDirectory, MINIMUM_NUMBER_OF_STARS);
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
                names = {"-r", "--repos"},
                arity = "1..2", // At least one parameter required
                paramLabel = "REPO-FILE(s)",
                description = "Search the given repositories for matching pull requests. The first parameter " +
                              "should be a file containing a newline separated list of GitHub repositories " +
                              "of the form user/project, i.e. apache/maven. A second file with the same format " +
                              "can also be provided. In this case, these repositories will be excluded from the search. " +
                              "A list of the repositories that have been checked will be saved in a file called " +
                              "checked_repositories in the output folder."
            )
            Path[] repos;

            @CommandLine.Option(
                names = {"-d", "--discover-repos"},
                description = "Search GitHub for repos matching the requirements. " +
                              "The results will be saved to a file named found_repositories " +
                              "in the output directory."
            )
            boolean discoverRepos;
        }
    }
}