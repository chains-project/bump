package miner;

import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHPullRequest;

import java.io.IOException;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The PullRequestFiltersTest contains a few basic test cases for the pull request filters
 * // TODO: Add negative test cases as well and extend positive tests
 */
class PullRequestFiltersTest extends GitHubMinerTestBase {

    @Test
    void changesOnlyDependencyVersionInPomXMLCorrectlyIdentifiesPR() throws IOException {
        List<GHPullRequest> prs = List.of(
            gitHub.getRepository("orientechnologies/orientdb").getPullRequest(8118),
            gitHub.getRepository("alibaba/fastjson").getPullRequest(4233)
        );
        assertTrue(prs.stream().allMatch(PullRequestFilters.changesOnlyDependencyVersionInPomXML));
    }

    @Test
    void createdBeforeCorrectlyIdentifiesPR() throws IOException {
        List<GHPullRequest> prs = List.of(
            gitHub.getRepository("orientechnologies/orientdb").getPullRequest(8118),
            gitHub.getRepository("iluwatar/java-design-patterns").getPullRequest(1976),
            gitHub.getRepository("alibaba/fastjson").getPullRequest(4233)
        );
        Date cutoffDate = Date.from(Instant.parse("2022-04-01T00:00:00.00Z"));
        assertEquals(2, prs.stream().filter(PullRequestFilters.createdBefore(cutoffDate)).count());
    }
}
