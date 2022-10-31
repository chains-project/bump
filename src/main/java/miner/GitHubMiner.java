package miner;

import okhttp3.Cache;
import okhttp3.OkHttpClient;
import org.kohsuke.github.*;
import org.kohsuke.github.connector.GitHubConnectorResponse;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The GitHubMiner class allows for the mining of GitHub repositories for relevant data.
 * It is currently set up to look for PRs that update a dependency and break the automated build process.
 *
 * @author <a href="mailto:gabsko@kth.se">Gabriel Skoglund</a>
 */
public class GitHubMiner {

    /**
     * The CACHE_DIR where the HTTP caches will be stored is set to the default system
     * temporary directory i.e. /tmp/ on most UNIX-like systems.
     */
    private static final File CACHE_DIR = Paths.get(System.getProperty("java.io.tmpdir")).toFile();
    private final OkHttpClient httpConnector;
    private final JSONFileWriter fileWriter;
    private final GitHubAPITokenQueue tokenQueue;

    /**
     * @param apiTokens A collection of GitHub API tokens
     * @throws IOException if there is an issue connecting to the GitHub servers
     */
    public GitHubMiner(Collection<String> apiTokens, JSONFileWriter fileWriter) throws IOException {
        this.fileWriter = fileWriter;
        // We use OkHttp with a 10 MB cache for HTTP requests
        Cache cache = new Cache(CACHE_DIR, 10 * 1024 * 1024);
        httpConnector = new OkHttpClient.Builder().cache(cache).build();
        tokenQueue = new GitHubAPITokenQueue(apiTokens);
        String apiToken = apiTokens.iterator().next();
        GitPatchCache.initialize(httpConnector, apiToken);
    }

    /**
     * Query GitHub for repositories that are Maven projects and has
     * GitHub actions that are run on pull requests. The found repositories
     * will be stored in a file called found_repositories in the specified
     * output directory.
     *
     * @param outputDirectory the directory in which to save the output.
     * @throws IOException if there is an issue when interacting with the file system.
     */
    public void findRepositories(Path outputDirectory) throws IOException {
        Path outputFilePath = createOutputFile(outputDirectory, "found_repositories");

        System.out.println("Finding valid repositories");
        int foundRepoCount = 0;
        PagedIterator<GHRepository> iterator = getDefaultSearch().list().iterator();
        while (iterator.hasNext()) {
            List<String> foundRepos = iterator.nextPage().stream()
                   .peek(repository -> System.out.println("Checking " + repository.getFullName()))
                   .filter(RepositoryFilters.isMavenProject)
                   .filter(RepositoryFilters.hasPullRequestWorkflows)
                   .map(GHRepository::getFullName)
                   .toList();
            foundRepoCount += foundRepos.size();
            if (foundRepos.size() > 0)
                Files.writeString(outputFilePath, "\n" + String.join("\n", foundRepos),
                                  StandardOpenOption.APPEND);
        }

        System.out.printf("Found %d valid repositories:\n", foundRepoCount);
    }


    /**
     * Query the given GitHub repositories for pull requests that changes a
     * single line in a pom.xml file and breaks a GitHub action workflow.
     *
     * @param outputDirectory the directory in which to save the output.
     * @param repositories  the repositories to look in, as strings
     *                      of the form user/project, e.g. apache/maven.
     * @param repositoriesToIgnore a list of repositories to ignore when searching.
     * @throws IOException if there is an issue when interacting with the file system.
     */
    public void mineRepositories(Path outputDirectory , List<String> repositories,
                                List<String> repositoriesToIgnore) throws IOException {
        Set<String> ignoredRepos = new HashSet<>(repositoriesToIgnore);
        Path outputFilePath = createOutputFile(outputDirectory, "checked_repositories");

        repositories.parallelStream().filter(r -> !ignoredRepos.contains(r)).forEach(repo -> {
            GitHub gitHub;
            GHRepository repository;
            PagedIterator<GHPullRequest> pullRequests;
            try {
                gitHub = tokenQueue.getGitHub(httpConnector);
                repository = gitHub.getRepository(repo);
                pullRequests = repository.queryPullRequests().state(GHIssueState.ALL).list().iterator();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            System.out.println("Checking " + repository.getFullName());
            while (pullRequests.hasNext()) {
                pullRequests.nextPage().stream()
                        .filter(PullRequestFilters.changesOnlyDependencyVersionInPomXML)
                        .filter(PullRequestFilters.breaksBuild)
                        .map(BreakingUpdate::new)
                        .forEach(breakingUpdate -> {
                            fileWriter.writeBreakingUpdate(breakingUpdate);
                            System.out.println("    Found " + breakingUpdate.getUrl());
                        });
            }

            System.out.println("Done checking " + repository.getFullName());
            try {
                Files.writeString(outputFilePath, "\n" + repository.getFullName(), StandardOpenOption.APPEND);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /** Create a file where output will be stored, if it does not already exist */
    private static Path createOutputFile(Path outputDirectory, String fileName) throws IOException {
        if (!Files.exists(outputDirectory))
            Files.createDirectory(outputDirectory);
        Path outputFilePath = outputDirectory.resolve(fileName);
        if (!Files.exists(outputFilePath))
            Files.createFile(outputFilePath);
        return outputFilePath;
    }

    /**
     * @return The default search configuration for this miner.
     *         Currently set to: Java projects with more than 1000
     *         stars, sorted by stars (descending) and excluding forks.
     */
    private GHRepositorySearchBuilder getDefaultSearch() throws IOException {
        return tokenQueue.getGitHub(httpConnector).searchRepositories()
                .language("Java")
                .fork(GHFork.PARENT_ONLY)
                .stars(">1000")
                .sort(GHRepositorySearchBuilder.Sort.STARS)
                .order(GHDirection.DESC);
    }

    /**
     * The MinerRateLimitChecker helps ensure that the miner does not exceed the GitHub API
     * rate limit. For more information see
     * <a href="https://docs.github.com/en/rest/guides/best-practices-for-integrators#dealing-with-rate-limits">
     *     the GitHub API documentation.
     * </a>
     */
    public static class MinerRateLimitChecker extends RateLimitChecker {
        private static final int REMAINING_CALLS_CUTOFF = 5;
        private final String apiToken;

        public MinerRateLimitChecker(String apiToken) {
            this.apiToken = apiToken;
        }

        @Override
        protected boolean checkRateLimit(GHRateLimit.Record rateLimitRecord, long count) throws InterruptedException {
            if (rateLimitRecord.getRemaining() < REMAINING_CALLS_CUTOFF) {
                long timeToSleep = rateLimitRecord.getResetDate().getTime() - System.currentTimeMillis();
                System.out.printf("Rate limit exceeded for token %s, sleeping %ds until %s\n",
                                  apiToken, timeToSleep / 1000, rateLimitRecord.getResetDate());
                Thread.sleep(timeToSleep);
                return true;
            }
            return false;
        }
    }

    /**
     * The MinerGitHubAbuseLimitHandler determines what to do in case we exceed the
     * GitHub API abuse limit
     */
    public static class MinerGitHubAbuseLimitHandler extends GitHubAbuseLimitHandler {
        private static final int timeToSleepMillis = 10_000;
        private final String apiToken;

        public MinerGitHubAbuseLimitHandler(String apiToken) {
            this.apiToken = apiToken;
        }

        @Override
        public void onError(GitHubConnectorResponse connectorResponse) throws IOException {
            System.out.println(new String(connectorResponse.bodyStream().readAllBytes()));
            System.out.printf("Abuse limit reached for token %s, sleeping %d seconds\n",
                              apiToken, timeToSleepMillis / 1000);
        }
    }
}