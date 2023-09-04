package miner;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;
import org.kohsuke.github.GHPullRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
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
    private static final Map<String, String> pomCache = new ConcurrentHashMap<>();
    private static OkHttpClient httpClient;
    private static String accessToken;
    private static final Logger log = LoggerFactory.getLogger(GitPatchCache.class);

    /**
     * Get the patch contents of a pull request.
     *
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
            log.error(e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Get the pom file contents of a pull request.
     *
     * @param projectOrg GitHub org of the project.
     * @param project    GitHub project name.
     * @param buCommit   the breaking commit SHA.
     * @param filePath   the path of the POM file.
     * @return the contents of the patch applied by this pull request.
     */
    public static Optional<String> get(String projectOrg, String project, String buCommit, String filePath) {
        if (pomCache.containsKey(buCommit))
            return Optional.of(pomCache.get(buCommit));
        try {
            pomCache.put(buCommit, getPOMContent(buCommit, projectOrg, project, filePath));
            return Optional.of(pomCache.get(buCommit));
        } catch (IOException e) {
            log.error(e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Remove a pull request from the cache.
     *
     * @param pullRequest the pull request to remove.
     */
    public static void remove(GHPullRequest pullRequest) {
        cache.remove(pullRequest);
    }

    /**
     * Initialize the patch cache.
     *
     * @param httpClient  the {@link okhttp3.OkHttpClient} to use for the connection to GitHub.
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

    private static String getPOMContent(String buCommit, String projectOrg, String project, String filePath) throws IOException {
        if (httpClient == null || accessToken == null)
            throw new IllegalStateException("GitPatchCache has not been initialized");
        String apiUrl = "https://api.github.com/repos/" + projectOrg + "/" + project + "/contents/" + filePath + "?ref="
                + buCommit;
        Call request = httpClient.newCall(new Request.Builder()
                .url(apiUrl)
                .header("Accept", "application/vnd.github.v3+json")
                .build());
        try (var response = request.execute()) {
            if (response.isSuccessful()) {
                ResponseBody responseBody = response.body();
                if (responseBody != null) {
                    JsonObject json = JsonParser.parseString(responseBody.string()).getAsJsonObject();
                    String base64Content = json.get("content").getAsString();
                    String sanitizedBase64Content = base64Content.replaceAll("\\n", "");
                    return new String(Base64.getDecoder().decode(sanitizedBase64Content), StandardCharsets.UTF_8);
                } else {
                    throw new IOException("Failed to retrieve file content from GitHub API. Response body is empty.");
                }
            } else {
                throw new IOException("Failed to retrieve file content from GitHub API. Status code: " + response.code());
            }
        }
    }
}