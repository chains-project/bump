package miner;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The BreakingUpdate class represents a dependency update that breaks a GitHub Action workflow.
 *
 * @author <a href="mailto:gabsko@kth.se">Gabriel Skoglund</a>
 */
public class BreakingUpdate {

    private static final Pattern DEPENDENCY_ARTIFACT_ID =
            Pattern.compile("^\\s*<artifactId>(.*)</artifactId>\\s*$");
    private static final Pattern DEPENDENCY_GROUP_ID =
            Pattern.compile("^\\s*<groupId>(.*)</groupId>\\s*$");
    private static final Pattern PREVIOUS_VERSION =
            Pattern.compile("^-\\s*<version>(.*)</version>\\s*$");
    private static final Pattern NEW_VERSION = Pattern.compile("^\\+\\s*<version>(.*)</version>\\s*$");
    private static final Pattern SCOPE =
            Pattern.compile("^\\s*<scope>(.*)</scope>\\s*$");
    private static final Pattern SEM_VER = Pattern.compile("^\\d+\\.\\d+\\.\\d+$");
    private static final Pattern SEM_VER_WITHOUT_PATCH = Pattern.compile("^\\d+\\.\\d+$");
    public final String url;
    public final String project;
    public final String breakingCommit;
    public final String prAuthor;
    public final String preCommitAuthor;
    public final String breakingCommitAuthor;
    public final UpdatedDependency updatedDependency;
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    /**
     * Create a new BreakingUpdate object that stores information about a
     * breaking dependency update.
     *
     * @param pr a pull request that corresponds to a breaking dependency update.
     */
    public BreakingUpdate(GHPullRequest pr) {
        url = pr.getHtmlUrl().toString();
        project = pr.getRepository().getName();
        breakingCommit = pr.getHead().getSha();
        prAuthor = parsePRAuthorType(pr, "unknown");
        preCommitAuthor = parsePreCommitAuthorType(pr.getRepository(), breakingCommit, "unknown");
        breakingCommitAuthor = parseBreakingCommitAuthorType(pr.getRepository(), breakingCommit, "unknown");
        updatedDependency = new UpdatedDependency(pr);
    }

    /**
     * Constructor for loading a BreakingUpdate from a JSON file
     */
    @JsonCreator
    BreakingUpdate(@JsonProperty("url") String url,
                   @JsonProperty("project") String project,
                   @JsonProperty("breakingCommit") String breakingCommit,
                   @JsonProperty("prAuthor") String prAuthor,
                   @JsonProperty("preCommitAuthor") String preCommitAuthor,
                   @JsonProperty("breakingCommitAuthor") String breakingCommitAuthor,
                   @JsonProperty("updatedDependency") UpdatedDependency updatedDependency) {
        this.url = url;
        this.project = project;
        this.breakingCommit = breakingCommit;
        this.prAuthor = prAuthor;
        this.preCommitAuthor = preCommitAuthor;
        this.breakingCommitAuthor = breakingCommitAuthor;
        this.updatedDependency = updatedDependency;
    }

    /**
     * Parse the type of user that made the breaking pull request
     *
     * @param pr The pull request to parse
     * @return "bot" if the user is a bot, otherwise return "human".
     */
    private String parsePRAuthorType(GHPullRequest pr, String defaultResult) {
        try {
            GHUser user = pr.getUser();
            String userLogin = user.getLogin().toLowerCase();
            // Sometimes, the user type does not get equal to BOT even if the user is actually a bot. Therefore, we add
            // additional checks.
            return user.getType().equals("Bot") || userLogin.contains("dependabot") || userLogin.contains("renovate") ?
                    "bot" : "human";
        } catch (IOException e) {
            log.error("prAuthorType could not be parsed", e);
            return defaultResult;
        }
    }

    /**
     * Parse the type of user that made the previous commit of the breaking commit
     *
     * @param repository The GitHub repository
     * @param commitSHA  The breaking commit which is used to parse the previous commit
     * @return "bot" if the user is a bot, otherwise return "human".
     */
    private String parsePreCommitAuthorType(GHRepository repository, String commitSHA, String defaultResult) {
        try {
            // There is not a proper way to identify the immediate parent of the commit. So we make the assumption
            // that the first parent in the parents list is the immediate parent.
            GHUser user = repository.getCommit(commitSHA).getParents().get(0).getAuthor();
            String userLogin = user.getLogin().toLowerCase();
            // Sometimes, the user type does not get equal to BOT even if the user is actually a bot. Therefore, we add
            // additional checks.
            return user.getType().equals("Bot") || userLogin.contains("dependabot") || userLogin.contains("renovate") ?
                    "bot" : "human";
        } catch (IOException e) {
            log.error("preCommitAuthorType could not be parsed", e);
            return defaultResult;
        }
    }

    /**
     * Parse the type of user that made the breaking commit
     *
     * @param repository The GitHub repository
     * @param commitSHA  The breaking commit to parse
     * @return "bot" if the user is a bot, otherwise return "human".
     */
    private String parseBreakingCommitAuthorType(GHRepository repository, String commitSHA, String defaultResult) {
        try {
            GHUser user = repository.getCommit(commitSHA).getAuthor();
            String userLogin = user.getLogin().toLowerCase();
            // Sometimes, the user type does not get equal to BOT even if the user is actually a bot. Therefore, we add
            // additional checks.
            return user.getType().equals("Bot") || userLogin.contains("dependabot") || userLogin.contains("renovate") ?
                    "bot" : "human";
        } catch (IOException e) {
            log.error("breakingCommitAuthorType could not be parsed", e);
            return defaultResult;
        }
    }

    @Override
    public String toString() {
        return ("BreakingUpdate{url = %s, project = %s, breakingCommit = %s prAuthor = %s, preCommitAuthor = %s, " +
                "breakingCommitAuthor = %s}")
                .formatted(url, project, breakingCommit, prAuthor, preCommitAuthor, breakingCommitAuthor);
    }


    /**
     * UpdatedDependency represents information associated with the updated dependency.
     */
    public static class UpdatedDependency {

        public final String dependencyGroupID;
        public final String dependencyArtifactID;
        public final String previousVersion;
        public final String newVersion;
        public final String dependencyScope;
        public final String versionUpdateType;

        /**
         * Create updated dependency for the breaking update.
         *
         * @param pr the pull request that corresponds to the breaking dependency update.
         */
        public UpdatedDependency(GHPullRequest pr) {
            dependencyGroupID = parsePatch(pr, DEPENDENCY_GROUP_ID, "unknown");
            dependencyArtifactID = parsePatch(pr, DEPENDENCY_ARTIFACT_ID, "unknown");
            previousVersion = parsePatch(pr, PREVIOUS_VERSION, "unknown");
            newVersion = parsePatch(pr, NEW_VERSION, "unknown");
            dependencyScope = parsePatch(pr, SCOPE, "compile");
            versionUpdateType = parseVersionUpdateType(previousVersion, newVersion);
        }

        /**
         * Constructor for loading an UpdatedDependency of a BreakingUpdate from a JSON file
         */
        @JsonCreator
        UpdatedDependency(@JsonProperty("dependencyGroupID") String dependencyGroupID,
                          @JsonProperty("dependencyArtifactID") String dependencyArtifactID,
                          @JsonProperty("previousVersion") String previousVersion,
                          @JsonProperty("newVersion") String newVersion,
                          @JsonProperty("dependencyScope") String dependencyScope,
                          @JsonProperty("versionUpdateType") String versionUpdateType) {
            this.dependencyGroupID = dependencyGroupID;
            this.dependencyArtifactID = dependencyArtifactID;
            this.previousVersion = previousVersion;
            this.newVersion = newVersion;
            this.dependencyScope = dependencyScope;
            this.versionUpdateType = versionUpdateType;
        }

        /**
         * Attempt to parse information from the patch associated with a PR.
         *
         * @param pr            the pull request for which to parse the patch.
         * @param searchTerm    a regex describing the data to extract.
         * @param defaultResult the result to return if no match was found for the regex.
         * @return The result of the first regex capturing group on the first line where the regex matches, if any.
         * If no match was found, the default result will be returned instead.
         */
        private String parsePatch(GHPullRequest pr, Pattern searchTerm, String defaultResult) {
            String patch = GitPatchCache.get(pr).orElse("");
            for (String line : patch.split("\n")) {
                Matcher matcher = searchTerm.matcher(line);
                if (matcher.find())
                    return matcher.group(1);
            }
            return defaultResult;
        }

        /**
         * Attempt to parse the version update type, under the assumption that the version
         * number follows the <a href="https://semver.org/">semantic versioning</a> format.
         *
         * @param previousVersion a string describing the previous version.
         * @param newVersion      a string describing the new version.
         * @return "major", "minor" or "patch" if the versions could be parsed according to semver,
         * "other" otherwise.
         */
        private String parseVersionUpdateType(String previousVersion, String newVersion) {


            if (!(SEM_VER.matcher(previousVersion).matches() || SEM_VER_WITHOUT_PATCH.matcher(previousVersion).matches())
                    || !(SEM_VER.matcher(newVersion).matches() || SEM_VER_WITHOUT_PATCH.matcher(newVersion).matches()))
                return "other";

            List<Integer> originalVersionNumbers = Arrays.stream(previousVersion.split("\\."))
                    .map(Integer::valueOf).toList();
            List<Integer> newVersionNumbers = Arrays.stream(newVersion.split("\\."))
                    .map(Integer::valueOf).toList();
            if (originalVersionNumbers.get(0) < newVersionNumbers.get(0))
                return "major";
            else if (originalVersionNumbers.get(1) < newVersionNumbers.get(1))
                return "minor";
            else
                return "patch";
        }
    }
}
