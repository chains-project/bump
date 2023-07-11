package miner;

import miner.GitHubMiner.RepositorySearchConfig;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Date;
import java.util.List;

/**
 * This class represents the main entry point to the GitHub breaking updates miner.
 *
 * @author <a href="mailto:gabsko@kth.se">Gabriel Skoglund</a>
 */
public class Main {

    public static void main(String[] args) {
        int exitCode = new CommandLine(new CLIEntryPoint()).execute(args);
        System.exit(exitCode);
    }

    @CommandLine.Command(subcommands = {Mine.class, Find.class},mixinStandardHelpOptions = true,version = "0.1")
    public static class CLIEntryPoint implements Runnable {
        @Override
        public void run() { CommandLine.usage(this, System.out); }
    }

    @CommandLine.Command(name = "mine", mixinStandardHelpOptions = true, version = "0.1")
    private static class Mine implements Runnable {
        @CommandLine.Option(
                names = {"-a", "--api-tokens"},
                paramLabel = "TOKEN-FILE",
                description = "A file containing a newline separated list of GitHub API tokens",
                required = true
        )
        Path apiTokenFile;

        @CommandLine.Option(
                names = {"-o", "--output-directory"},
                paramLabel = "OUTPUT-DIR",
                description = "A directory where output data will be stored.",
                required = true
        )
        Path outputDirectory;

        @CommandLine.Option(
                names = {"-r", "--repos"},
                paramLabel = "REPO-FILE",
                description = "A JSON file as given by the find-repos operation.",
                required = true
        )
        Path repoFile;

        @Override
        public void run() {
            try {
                List<String> apiTokens = Files.readAllLines(apiTokenFile);
                RepositoryList repoList = new RepositoryList(repoFile);
                new GitHubMiner(apiTokens, outputDirectory).mineRepositories(repoList);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @CommandLine.Command(name = "find", mixinStandardHelpOptions = true, version = "0.1")
    private static class Find implements Runnable {
        @CommandLine.Option(
                names = {"-a", "--api-tokens"},
                paramLabel = "TOKEN-FILE",
                description = "A file containing a newline separated list of GitHub API tokens",
                required = true
        )
        Path apiTokenFile;

        @CommandLine.Option(
                names = {"-o", "--output-directory"},
                paramLabel = "OUTPUT-DIR",
                description = "A directory where output data will be stored.",
                required = true
        )
        Path outputDirectory;
        @CommandLine.Option(
                names = {"-s", "--search-config"},
                paramLabel = "SEARCH-CONFIG",
                description = "A JSON file specifying details about the repositories to search for.",
                required = true
        )
        Path searchConfigFile;

        @CommandLine.Option(
                names = {"-r", "--repos"},
                paramLabel = "REPO-FILE",
                description = "A JSON file containing previously found repositories. If used, this file will be updated," +
                        "otherwise a new file called found_repositories.json will be created in the output directory."
        )
        Path repoFile;
        @CommandLine.Option(
                names = {"-l", "--last"},
                paramLabel = "LAST-DATE",
                description = "Last date of search",
                required = false

        )
        Date lastDate;

        @Override
        public void run() {
            if (repoFile == null) {
                Path filePath = outputDirectory.resolve(GitHubMiner.FOUND_REPOS_FILE);
                try {
                    repoFile = Files.writeString(filePath, JsonUtils.EMPTY_JSON_OBJECT, StandardOpenOption.CREATE_NEW);
                } catch (IOException e) {
                    System.err.println("Could not create a output file in " + filePath +
                            " as the file might already exist. Either specify a different output path or provide" +
                            " an explicit output file for found repositories.");
                    System.exit(1);
                }
                System.out.println("Created a new file for found repos as " + filePath);
            }
            try {
                List<String> apiTokens = Files.readAllLines(apiTokenFile);
                RepositorySearchConfig searchConfig = RepositorySearchConfig.fromJson(searchConfigFile);
                var repoList = new RepositoryList(repoFile);
                new GitHubMiner(apiTokens, outputDirectory).findRepositories(repoList, searchConfig,lastDate);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
