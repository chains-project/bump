package miner;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.kohsuke.github.GHPullRequest;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The GitPatchCache provides a way to store the result of queries for the patch content of a pull request,
 * in order to reduce the number of API calls.
 * <br>
 * NOTE: This class may not be strictly needed since we use OkHTTP with a cache,
 * and the design is quite inelegant, but it is convenient for the {@link miner.PullRequestFilters}.
 *
 * @author <a href="mailto:gabsko@kth.se">Gabriel Skoglund</a>
 */
public class GitPatchCache {
    private GitPatchCache() { /* Nothing to see here... */ }

    private static final Map<GHPullRequest, String> cache = new ConcurrentHashMap<>();
    private static OkHttpClient httpClient;
    private static String accessToken;

    /**
     * Get the patch contents of a pull request.
     * @param pullRequest the pull request to get the patch of.
     * @return the contents of the patch applied by this pull request.
     */
    public static Optional<String> get(GHPullRequest pullRequest) {
        if (cache.containsKey(pullRequest))
            return Optional.of(cache.get(pullRequest));
        try {
            cache.put(pullRequest, getPullRequestDiffContents(pullRequest));
            return Optional.of(cache.get(pullRequest));
        } catch (IOException e) {
            System.err.println(e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Remove a pull request from the cache.
     * @param pullRequest the pull request to remove.
     */
    public static void remove(GHPullRequest pullRequest) {
        cache.remove(pullRequest);
    }

    /**
     * Initialize the patch cache.
     * @param httpClient the {@link okhttp3.OkHttpClient} to use for the connection to GitHub.
     * @param accessToken the GitHub API token to use for the connection.
     */
    public static void initialize(OkHttpClient httpClient, String accessToken) {
        GitPatchCache.accessToken = accessToken;
        GitPatchCache.httpClient = httpClient;
    }

    private static String getPullRequestDiffContents(GHPullRequest pr) throws IOException {
        if (httpClient == null || accessToken == null)
            throw new IllegalStateException("GitPatchCache has not been initialized");

        Call request = httpClient.newCall(new Request.Builder()
                .get()
                .url(pr.getDiffUrl())
                .header("Authorization", "bearer " + accessToken)
                .build());

        try (var response = request.execute()) {
            if (response.code() != HttpURLConnection.HTTP_OK)
                throw new IOException("Failed to get diff for PR " + pr.getHtmlUrl());
            return Objects.requireNonNull(response.body()).string();
        }
    }
}