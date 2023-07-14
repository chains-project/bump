package reproducer;

import miner.BreakingUpdate;
import miner.GitHubAPITokenQueue;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHTag;
import org.kohsuke.github.PagedIterable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The DependencyRefLinkFinder involves in resolving reference links related to the updated dependency. These references
 * include GitHub comparison links and Maven source links.
 */
public class DependencyRefLinkFinder {

    private final OkHttpClient httpConnector;
    private final GitHubAPITokenQueue tokenQueue;
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    /**
     * @param tokenQueue a GitHubAPITokenQueue of GitHub API tokens.
     * @throws IOException if there is an issue connecting to the GitHub servers.
     */
    public DependencyRefLinkFinder(GitHubAPITokenQueue tokenQueue) throws IOException {
        httpConnector = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS).build();
        this.tokenQueue = tokenQueue;
    }

    /**
     * Get the GitHub comparison links for the old and new tag releases if they exist.
     */
    public String getGithubCompareLink(BreakingUpdate bu) {

        try {
            String repoOwner = bu.updatedDependency.dependencyGroupID.split("\\.").length > 1 ?
                    bu.updatedDependency.dependencyGroupID.split("\\.")[1] : bu.updatedDependency.dependencyGroupID;
            String repoName = repoOwner + "/" + bu.updatedDependency.dependencyArtifactID;
            GHRepository repository = tokenQueue.getGitHub(httpConnector).getRepository(repoName);
            List<String> tags = getTags(repository, bu);
            String notFoundMsg = "Relevant tags were not found in the GitHub repository %s for the updated dependency."
                    .formatted(repository.getName());
            return (tags != null) ? ("https://github.com/%s/compare/%s...%s".formatted(repoName, tags.get(0), tags.get(1)))
                    : notFoundMsg;
        } catch (IOException e) {
            log.error("A GitHub repository could not be found for the updated dependency {}.", bu.breakingCommit);
            return "A GitHub repository could not be found for the updated dependency.";
        }
    }

    /**
     * Get the old and new tag releases if they exist in GitHub.
     */
    private List<String> getTags(GHRepository repository, BreakingUpdate bu) {
        try {
            PagedIterable<GHTag> allTags = repository.listTags();
            List<String> tags = allTags.toList().stream()
                    .filter(tag -> tag.getName().replaceAll("[^0-9.]", "").equals(bu.updatedDependency.previousVersion)
                            || tag.getName().replaceAll("[^0-9.]", "").equals(bu.updatedDependency.newVersion))
                    .map(GHTag::getName)
                    .sorted(Comparator.comparing(found -> !found.contains(bu.updatedDependency.previousVersion)))
                    .toList();
            return tags.size() == 2 ? tags : null;
        } catch (IOException e) {
            log.error("Tags were not found in the GitHub repository {} for the updated dependency {}.",
                    repository.getName(), bu.breakingCommit, e);
        }
        return null;
    }

    /**
     * Get the Maven source jar links for the old and new dependency releases if they exist.
     */
    public List<String> getMavenSourceLinks(BreakingUpdate bu) {
        String mavenSourceLinkBase = "https://repo1.maven.org/maven2/%s/%s/"
                .formatted(bu.updatedDependency.dependencyGroupID.replaceAll("\\.", "/"),
                        bu.updatedDependency.dependencyArtifactID);
        String prevVersionMavenSourceLink = mavenSourceLinkBase + "%s/%s-%s-sources.jar"
                .formatted(bu.updatedDependency.previousVersion, bu.updatedDependency.dependencyArtifactID,
                        bu.updatedDependency.previousVersion);
        String newVersionMavenSourceLink = mavenSourceLinkBase + "%s/%s-%s-sources.jar"
                .formatted(bu.updatedDependency.newVersion, bu.updatedDependency.dependencyArtifactID,
                        bu.updatedDependency.newVersion);
        try (Response prevSourceResponse = httpConnector.newCall(new Request.Builder().url(prevVersionMavenSourceLink)
                .build()).execute();
             Response newSourceResponse = httpConnector.newCall(new Request.Builder().url(newVersionMavenSourceLink)
                     .build()).execute()) {
            if (prevSourceResponse.code() != 404 || newSourceResponse.code() != 404)
                return List.of(prevVersionMavenSourceLink, newVersionMavenSourceLink);
        } catch (IOException e) {
            log.error("Maven source links could not be found for the updated dependency {}.", bu.breakingCommit, e);
        }
        return null;
    }
}
