package miner;

import org.kohsuke.github.*;

import java.io.IOException;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * The PullRequestFilters class contains predicates over GitHub repositories
 * that can be used to filter for pull requests having certain properties.
 *
 * @author <a href="mailto:gabsko@kth.se">Gabriel Skoglund</a>
 */
public class PullRequestFilters {

    private PullRequestFilters() { /* Nothing to see here... */ }

    private static final Pattern POM_XML_CHANGE = Pattern.compile("^[+]{3}.*pom.xml$", Pattern.MULTILINE);
    private static final Pattern DEPENDENCY_VERSION_CHANGE =
            Pattern.compile("<dependency>(.*^[+-]\\s*<version>.+</version>.*){2}</dependency>",
                       Pattern.DOTALL | Pattern.MULTILINE);

    /**
     * Check whether a given pull request fulfills all of these properties:
     * <ul>
     *     <li>It changes only one line.</li>
     *     <li>The change is made to a pom.xml file.</li>
     *     <li>The change is to the version number in a version tag.</li>
     * </ul>
     */
    public static final Predicate<GHPullRequest> changesOnlyDependencyVersionInPomXML = pr -> {
        try {
            if (pr.getChangedFiles() != 1)
                return false;
            if (pr.getAdditions() != 1 || pr.getDeletions() != 1)
               return false;

            String patch = GitPatchCache.get(pr).orElse("");
            if (POM_XML_CHANGE.matcher(patch).find() && DEPENDENCY_VERSION_CHANGE.matcher(patch).find()) {
                return true;
            } else {
                // If we don't match the predicate, the pull request will get filtered out,
                // and we can remove it from the cache.
                GitPatchCache.remove(pr);
                return false;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    };

    /**
     * Checks whether this pull request breaks a GitHub Action workflow in the associated repository.
     */
    public static final Predicate<GHPullRequest> breaksBuild = pr -> {
        GHWorkflowRunQueryBuilder query = pr.getRepository().queryWorkflowRuns()
                .branch(pr.getHead().getRef())
                .event(GHEvent.PULL_REQUEST)
                .status(GHWorkflowRun.Status.COMPLETED)
                .conclusion(GHWorkflowRun.Conclusion.FAILURE);
        try {
            // The GitHub REST API allows us to query for the head sha, but this is not currently supported
            // by org.kohsuke.github query builder. To ensure that the run actually failed for this specific
            // PR head, we have to verify it after the search.
            return query.list().toList().stream()
                    .anyMatch(run -> run.getHeadSha().equals(pr.getHead().getSha()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    };
}