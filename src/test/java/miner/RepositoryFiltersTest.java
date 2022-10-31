package miner;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The RepositoryFiltersTest class contains a few basic tests to make sure that we correctly
 * identify repositories as having certain properties.
 */
class RepositoryFiltersTest extends GitHubMinerTestBase {

    @Test
    void isMavenProjectCorrectlyIdentifiesRepository() throws IOException {
        var repo = gitHub.getRepository("google/guava");
        assertTrue(RepositoryFilters.isMavenProject.test(repo));
    }

    @Test
    void hasPullRequestWorkflowsCorrectlyIdentifiesRepository() throws IOException {
        var repo = gitHub.getRepository("google/guava");
        assertTrue(RepositoryFilters.isMavenProject.test(repo));
    }
}