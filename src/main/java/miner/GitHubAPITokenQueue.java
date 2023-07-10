package miner;

import okhttp3.OkHttpClient;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.extras.okhttp3.OkHttpGitHubConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The GitHubAPITokenQueue provides an interface for creating {@link org.kohsuke.github.GitHub} objects
 * using a pool of API tokens. This makes it possible have several GitHub connections active in parallel,
 * each using a different token. Since tokens are limited to a certain number of requests per hour, this is
 * useful for getting a higher rate of API usage.
 *
 * @author <a href="mailto:gabsko@kth.se">Gabriel Skoglund</a>
 */
public class GitHubAPITokenQueue {

    private final Queue<String> tokenQueue;
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    /**
     * @param apiTokens a collection of GitHub API tokens.
     * @throws IOException if there is an error while communicating with the GitHub servers.
     * @throws RuntimeException if no valid API tokens were provided.
     */
    public GitHubAPITokenQueue(Collection<String> apiTokens) throws IOException {
        verifyTokens(apiTokens);
        if (apiTokens.size() < 1)
            throw new RuntimeException("No valid API tokens provided!");
        tokenQueue = new ConcurrentLinkedQueue<>(apiTokens);
    }

    /**
     * Remove all invalid tokens from the given collection. Since tokens are set to expire in a given time
     * this means that we need to make sure we are not using outdated tokens.
     */
    private void verifyTokens(Collection<String> apiTokens) throws IOException {
        Iterator<String> iterator = apiTokens.iterator();
        while (iterator.hasNext()) {
            String apiToken = iterator.next();
            GitHub gitHub = new GitHubBuilder().withOAuthToken(apiToken).build();
            if (!gitHub.isCredentialValid()) {
                iterator.remove();
                log.error("Found invalid token {}, removing it from use.", apiToken);
            }
        }
    }

    /**
     * Get a new {@link org.kohsuke.github.GitHub} object. The object will be set up using the
     * provided {@link okhttp3.OkHttpClient} and using the {@link miner.GitHubMiner.MinerRateLimitChecker}
     * and {@link miner.GitHubMiner.MinerGitHubAbuseLimitHandler}. The API token used is guaranteed to be
     * the least recently used token from this token queue.
     *
     * @param connector the {@link okhttp3.OkHttpClient} to use for the connection.
     * @return a {@link org.kohsuke.github.GitHub} using an API token from the queue.
     * @throws IOException if there is an error connecting to the GitHub servers.
     */
    public GitHub getGitHub(OkHttpClient connector) throws IOException {
        String apiToken = tokenQueue.remove();
        tokenQueue.add(apiToken);
        return new GitHubBuilder()
            .withConnector(new OkHttpGitHubConnector(connector))
            .withOAuthToken(apiToken)
            .withRateLimitChecker(new GitHubMiner.MinerRateLimitChecker(apiToken))
            .withAbuseLimitHandler(new GitHubMiner.MinerGitHubAbuseLimitHandler(apiToken))
            .build();
    }

    /**
     * @return The number of API tokens available from this token queue.
     */
    public int size() {
        return tokenQueue.size();
    }
}