package miner;

import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Assumptions;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

import java.io.IOException;

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
        String apiToken = System.getenv("TEST_TOKEN");
        Assumptions.assumeTrue(apiToken != null, "TEST_TOKEN environment variable not set, skipping test");
        try {
            gitHub = new GitHubBuilder().withOAuthToken(apiToken).build();
            OkHttpClient connector = new OkHttpClient.Builder().build();
            GitPatchCache.initialize(connector, apiToken);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
