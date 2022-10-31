package miner;

import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHTreeEntry;

import java.io.IOException;
import java.util.function.Predicate;

/**
 * The RepositoryFilters class contains predicates over GitHub repositories
 * that can be used to filter for repositories having certain properties.
 *
 * @author <a href="mailto:gabsko@kth.se">Gabriel Skoglund</a>
 */
public class RepositoryFilters {

    private RepositoryFilters() { /* Nothing to see here... */ }

    /**
     * Check if a given repository is an Apache Maven project
     * A GitHub repository is considered to be a Maven project if it contains
     * a pom.xml file anywhere in the main branch.
     */
    public static final Predicate<GHRepository> isMavenProject = repository -> {
        try {
            return repository.getTree(repository.getDefaultBranch()).getTree().stream()
                    .map(GHTreeEntry::getPath)
                    .anyMatch(path -> path.contains("pom.xml"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    };

    /**
     * Check whether the given repository contains any workflows that is run on PRs.
     */
    public static final Predicate<GHRepository> hasPullRequestWorkflows = repository -> {
            var workflowIterator = repository.queryWorkflowRuns()
                    .event(GHEvent.PULL_REQUEST)
                    .list().withPageSize(1)
                    .iterator();
            return workflowIterator.hasNext();
    };
}