package miner;

import okhttp3.OkHttpClient;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

import java.io.IOException;
import java.util.Objects;

/**
 * The GitHubMinerTestBase provides a base on which to build tests for individual classes.
 *
 * @author <a href="mailto:gabsko@kth.se">Gabriel Skoglund</a>
 */
public class GitHubMinerTestBase {

    protected static GitHub gitHub;

    public GitHubMinerTestBase() {
        if (gitHub != null)
            return; // No need to perform set up
        try {
            // We use an environment variable to store a GitHub access token for the tests
            String apiToken = Objects.requireNonNull(System.getenv("TEST_TOKEN"));
            gitHub = new GitHubBuilder().withOAuthToken(apiToken).build();
            OkHttpClient connector = new OkHttpClient.Builder().build();
            GitPatchCache.initialize(connector, apiToken);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
