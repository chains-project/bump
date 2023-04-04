package miner;

import okhttp3.Cache;
import okhttp3.OkHttpClient;
import org.kohsuke.github.*;
import org.kohsuke.github.connector.GitHubConnectorResponse;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

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

    /**
     * When looking for repositories, we will only consider projects added to GitHub
     * in this year or later.
     */
    public static final int SEARCH_CUTOFF_YEAR = 2010;

    /** Default file name for the file containing found repositories" */
    static final String FOUND_REPOS_FILE = "found_repositories.json";
    private final OkHttpClient httpConnector;
    private final GitHubAPITokenQueue tokenQueue;
    private final Path outputDirectory;

    /**
     * @param apiTokens a collection of GitHub API tokens.
     * @param outputDirectory a path to the directory where found breaking updates will be stored.
     * @throws IOException if there is an issue connecting to the GitHub servers.
     */
    public GitHubMiner(Collection<String> apiTokens, Path outputDirectory) throws IOException {
        this.outputDirectory = outputDirectory;
        // We use OkHttp with a 10 MB cache for HTTP requests
        Cache cache = new Cache(CACHE_DIR, 10 * 1024 * 1024);
        httpConnector = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .cache(cache).build();
        tokenQueue = new GitHubAPITokenQueue(apiTokens);
        String apiToken = apiTokens.iterator().next();
        GitPatchCache.initialize(httpConnector, apiToken);
    }

    /**
     * Query GitHub for repositories that are Maven projects and has
     * GitHub actions that are run on pull requests. The found repositories
     * will be stored in a file called found_repositories in the specified
     * output directory.
     * <p>
     * Since GitHub will only return at most 1000 results per API call,
     * and searching is restricted to a tighter API rate limit,
     * this method will attempt to perform sequential queries using different
     * API tokens until the full search result has been returned.
     *
     * @param repoList a {@link RepositoryList} of previously found repositories.
     * @param minNumberOfStars the minimum number of stars of the GitHub repositories to search for.
     * @throws IOException if there is an issue when interacting with the file system.
     */
    public void findRepositories(RepositoryList repoList, int minNumberOfStars) throws IOException {
        System.out.println("Finding valid repositories");
        int previousSize = repoList.size();
        LocalDate creationDate = LocalDate.now();
        PagedSearchIterable<GHRepository> search = searchForRepos(minNumberOfStars, creationDate);

        while (creationDate.getYear() >= SEARCH_CUTOFF_YEAR) {
            System.out.printf("Checking repos created on %s\n", creationDate);
            PagedIterator<GHRepository> iterator = search.iterator();
            while (iterator.hasNext()) {
                iterator.nextPage().stream()
                        .filter(repository -> !repoList.contains(repository))
                        .peek(repository -> System.out.println("  Checking " + repository.getFullName()))
                        .filter(RepositoryFilters.isMavenProject)
                        .filter(RepositoryFilters.hasPullRequestWorkflows)
                        .forEach(repository -> {
                            repoList.add(repository);
                            System.out.println("  Found " + repository.getUrl());
                        });
            }
            creationDate = creationDate.minusDays(1);
            search = searchForRepos(minNumberOfStars, creationDate);
            repoList.writeToFile();
        }
        System.out.printf("Found %d valid repositories\n", repoList.size() - previousSize);
    }

    /**
     * Search for GitHub repos that use Java as the main language, having the required minimum number
     * of stars and having been created at the given date. Forks will be ignored and the result will
     * be sorted based on the number of stars, descending.
     */
    private PagedSearchIterable<GHRepository> searchForRepos(int minNumberOfStars, LocalDate creationDate)
            throws IOException {
        return tokenQueue.getGitHub(httpConnector).searchRepositories()
                .language("Java")
                .fork(GHFork.PARENT_ONLY)
                .stars(">=" + minNumberOfStars)
                .created(creationDate.toString())
                .sort(GHRepositorySearchBuilder.Sort.STARS)
                .order(GHDirection.DESC)
                .list();
    }

    /**
     * Query the given GitHub repositories for pull requests that changes a
     * single line in a pom.xml file and breaks a GitHub action workflow.
     *
     * @param repoFile a file containing GitHub repositories, as given by the {@link #findRepositories} method.
     * @throws IOException if there is an issue when interacting with the file system.
     */
    public void mineRepositories(Path repoFile) throws IOException {
        RepositoryList repoList = new RepositoryList(repoFile);

        // We want to limit the number of threads we create so that each API token is allocated
        // to one thread. This is in line with the recommendations from
        // https://docs.github.com/en/rest/overview/resources-in-the-rest-api#secondary-rate-limits
        // In order to do this, we create our own ForkJoinPool instead of relying on the default one.
        ForkJoinPool threadPool = new ForkJoinPool(tokenQueue.size());
        try {
            threadPool.submit(() -> repoList.getRepositoryNames().parallelStream().forEach(repo -> {
                try {
                    mineRepo(repo, repoList.getCheckedTime(repo));
                    repoList.setCheckedTime(repo, Date.from(Instant.now()));
                    repoList.writeToFile();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            })).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        } finally {
            threadPool.shutdown();
        }
    }

    /**
     * Iterate over all pull requests of a repo added after a given date and save ones that contain breaking updates
     */
    private void mineRepo(String repo, Date cutoffDate) throws IOException {
        System.out.println("Checking " + repo);
        GHRepository repository = tokenQueue.getGitHub(httpConnector).getRepository(repo);
        PagedIterator<GHPullRequest> pullRequests = repository.queryPullRequests()
                .state(GHIssueState.ALL)
                .sort(GHPullRequestQueryBuilder.Sort.CREATED)
                .direction(GHDirection.DESC)
                .list().iterator();

        while (pullRequests.hasNext()) {
            List<GHPullRequest> nextPage = pullRequests.nextPage();
            if (PullRequestFilters.createdBefore(cutoffDate).test(nextPage.get(0))) {
                System.out.println("Checked all PRs for " + repo + " created after " + cutoffDate);
                break;
            }
            nextPage.stream()
                    .takeWhile(PullRequestFilters.createdBefore(cutoffDate).negate())
                    .filter(PullRequestFilters.changesOnlyDependencyVersionInPomXML)
                    .filter(PullRequestFilters.breaksBuild)
                    .map(BreakingUpdate::new)
                    .forEach(breakingUpdate -> {
                        writeBreakingUpdate(breakingUpdate);
                        System.out.println("    Found " + breakingUpdate.url);
                    });
        }
    }

    /**
     * Create a json file containing information about a breaking update.
     */
    public void writeBreakingUpdate(BreakingUpdate breakingUpdate) {
        Path path = outputDirectory.resolve(breakingUpdate.commit + JsonUtils.JSON_FILE_ENDING);
        JsonUtils.writeToFile(path, breakingUpdate);
    }

    /**
     * The MinerRateLimitChecker helps ensure that the miner does not exceed the GitHub API
     * rate limit. For more information see
     * <a href="https://docs.github.com/en/rest/guides/best-practices-for-integrators#dealing-with-rate-limits">
     * the GitHub API documentation.
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
