package miner;

import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHUser;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The BreakingUpdate class represents a dependency update that breaks a GitHub Action workflow.
 *
 * @author <a href="mailto:gabsko@kth.se">Gabriel Skoglund</a>
 */
public class BreakingUpdate {

    private static final Pattern ORIGINAL_VERSION =
            Pattern.compile("^-\\s*<version>(.*)</version>\\s*$");
    private static final Pattern NEW_VERSION = Pattern.compile("^\\+\\s*<version>(.*)</version>\\s*$");
    private static final Pattern SEM_VER = Pattern.compile("^\\d+\\.\\d+\\.\\d+$");

    private final String url;
    private final String project;
    private final String commit;
    private final String versionUpdateType;
    private final String type;
    private String reproductionStatus = "not_attempted";
    private Analysis analysis = null;

    /**
     * Create a new BreakingUpdate object that stores information about a
     * breaking dependency update.
     * @param pr a pull request that corresponds to a breaking dependency update.
     */
    public BreakingUpdate(GHPullRequest pr) {
        url = pr.getHtmlUrl().toString();
        project = pr.getRepository().getName();
        commit = pr.getHead().getSha();
        versionUpdateType = parseVersionUpdateType(pr);
        type = parseType(pr);
    }
    /**
     * @return the GitHub URL of this breaking update.
     */
    public String getUrl() {
        return url;
    }

    /**
     * @return the commit SHA for this breaking update.
     */
    public String getCommit() {
        return commit;
    }

    /**
     * Attempt to parse the version update type, under the assumption that the version
     * number follows the <a href="https://semver.org/">semantic versioning</a> format.
     * @param pr The pull request to check the version update for
     * @return "major", "minor" or "patch" if the versions could be parsed according to semver,
     *         "other" otherwise.
     */
    private String parseVersionUpdateType(GHPullRequest pr) {
        String patch = GitPatchCache.get(pr).orElse("");
        String originalVersion = patch.lines()
                .map(line -> getCapturedVersion(ORIGINAL_VERSION, line))
                .filter(Objects::nonNull)
                .findFirst().orElse("");
        String newVersion = patch.lines()
                .map(line -> getCapturedVersion(NEW_VERSION, line))
                .filter(Objects::nonNull)
                .findFirst().orElse("");

        if (!SEM_VER.matcher(originalVersion).matches() || !SEM_VER.matcher(newVersion).matches())
            return "other";

        List<Integer> originalVersionNumbers = Arrays.stream(originalVersion.split("\\."))
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

    /**
     * Get the version (if any) found by the regex.
     */
    private String getCapturedVersion(Pattern pattern, String input) {
        Matcher matcher = pattern.matcher(input);
        if (!matcher.find())
            return null;
        return matcher.group(1);
    }

    /**
     * Parse the type of user that made this pull request
     * @param pr The pull request to parse
     * @return "dependabot" or "renovate" if the name of the user matches these dependency bot names.
     *         Otherwise, if the user is a bot but not one of the above, return "other", else, return "human".
     */
    private String parseType(GHPullRequest pr) {
        try {
            GHUser user = pr.getUser();
            String userLogin = user.getLogin().toLowerCase();
            // Here we make assumptions as to the name of dependency bots, which may not be foolproof,
            // but probably good enough.
            if (userLogin.contains("dependabot"))
                return "dependabot";
            if (userLogin.contains("renovate"))
                return "renovate";
            return user.getType().equals("Bot") ? "other" : "human";
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toString() {
        return "BreakingUpdate{url = %s, project = %s, commit = %s, versionUpdateType = %s, type = %s}"
                .formatted(url, project, commit, versionUpdateType, type);
    }

    /**
     * Set the reproduction status of this breaking update.
     * @param reproductionStatus the new reproduction status, should be one of "not_attempted", "successful" or
     *                           "unreproducible".
     */
    public void setReproductionStatus(String reproductionStatus) {
        this.reproductionStatus = reproductionStatus;
    }

    /**
     * Update the analysis of this breaking update.
     * @param analysis the new analysis to add to this breaking update.
     */
    public void setAnalysis(Analysis analysis) {
        this.analysis = analysis;
    }

    /**
     * The Analysis class represents data associated with the reproduction and analysis of a breaking update.
     */
    public static class Analysis {
        private static final String DEFAULT_JAVA_VERSION_FOR_REPRODUCTION = "11";

        private final List<String> labels;
        private final String javaVersionUsedForReproduction;
        private final String reproductionLogLocation;

        /**
         * Create a new Analysis of this breaking update, where the Java version used for reproduction is set to
         * {@value DEFAULT_JAVA_VERSION_FOR_REPRODUCTION}.
         *
         * @param labels a list of labels for the analysis, describing relevant properties such as what kind of
         *               reproduction it represents; "BUILD_FAILURE", "TEST_FAILURE" etc.
         * @param reproductionLogLocation the location where the Maven log of the reproduction is stored.
         */
        public Analysis(List<String> labels, String reproductionLogLocation) {
            this(labels, DEFAULT_JAVA_VERSION_FOR_REPRODUCTION, reproductionLogLocation);
        }

        /**
         * Create a new Analysis of this breaking update.
         *
         * @param labels a list of labels for the analysis, describing relevant properties such as what kind of
         *               reproduction it represents; "BUILD_FAILURE", "TEST_FAILURE" etc.
         * @param javaVersionUsedForReproduction the Java version used in reproducing this breaking update.
         * @param reproductionLogLocation the location where the Maven log of the reproduction is stored.
         */
        public Analysis(List<String> labels, String javaVersionUsedForReproduction,
                        String reproductionLogLocation) {
            this.labels = labels;
            this.javaVersionUsedForReproduction = javaVersionUsedForReproduction;
            this.reproductionLogLocation = reproductionLogLocation;
        }
    }
}
