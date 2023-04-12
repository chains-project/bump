package miner;

import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHUser;

import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The BreakingUpdate class represents a dependency update that breaks a GitHub Action workflow.
 *
 * @author <a href="mailto:gabsko@kth.se">Gabriel Skoglund</a>
 */
public class BreakingUpdate {

    private static final Pattern DEPENDENCY = Pattern.compile("^\\s*<groupId>(.*)</groupId>\\s*$");
    private static final Pattern ARTIFACTID =
            Pattern.compile("^\\s*<artifactId>(.*)</artifactId>\\s*$");
    private static final Pattern PREVIOUS_VERSION =
            Pattern.compile("^-\\s*<version>(.*)</version>\\s*$");
    private static final Pattern NEW_VERSION = Pattern.compile("^\\+\\s*<version>(.*)</version>\\s*$");
    private static final Pattern SEM_VER = Pattern.compile("^\\d+\\.\\d+\\.\\d+$");

    public final String url;
    public final String project;
    public final String commit;
    public final Date createdAt;
    public final String dependency;
    public final String artifactId;
    public final String previousVersion;
    public final String newVersion;
    public final String versionUpdateType;
    public final String type;
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
        try {
            createdAt = pr.getCreatedAt();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        artifactId = parsePatch(pr, ARTIFACTID,"unknown");
        dependency = parsePatch(pr, DEPENDENCY, "unknown");
        previousVersion = parsePatch(pr, PREVIOUS_VERSION, "unknown");
        newVersion = parsePatch(pr, NEW_VERSION, "unknown");
        versionUpdateType = parseVersionUpdateType(previousVersion, newVersion);
        type = parseType(pr);
    }

    /**
     * Attempt to parse information from the patch associated with a PR.
     * @param pr the pull request for which to parse the patch.
     * @param searchTerm a regex describing the data to extract.
     * @param defaultResult the result to return if no match was found for the regex.
     * @return The result of the first regex capturing group on the first line where the regex matches, if any.
     *         If no match was found, the default result will be returned instead.
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
     * @param previousVersion a string describing the previous version.
     * @param newVersion a string describing the new version.
     * @return "major", "minor" or "patch" if the versions could be parsed according to semver,
     *         "other" otherwise.
     */
    private String parseVersionUpdateType(String previousVersion, String newVersion) {
        if (!SEM_VER.matcher(previousVersion).matches() || !SEM_VER.matcher(newVersion).matches())
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
        return ("BreakingUpdate{url = %s, project = %s, commit = %s, createdAt = %s, dependency = %s," +
                "previousVersion = %s, newVersion = %s, versionUpdateType = %s, type = %s, artifactId = %s}")
                .formatted(url, project, commit, createdAt, dependency, previousVersion, newVersion, versionUpdateType, type, artifactId);
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
     * @return The reproduction status of this breaking update, either
     *         "not_attempted", "successful" or "unreproducible".
     */
    public String getReproductionStatus() {
        return reproductionStatus;
    }

    /**
     * @return The analysis of this breaking update. Note that if the {@code reproductionStatus} of this breaking
     *         update is "not_attempted", the analysis will be {@code null}.
     */
    public Analysis getAnalysis() {
        return analysis;
    }

    /**
     * The Analysis class represents data associated with the reproduction and analysis of a breaking update.
     */
    public static class Analysis {
        private static final String DEFAULT_JAVA_VERSION_FOR_REPRODUCTION = "11";

        public final List<String> labels;
        public final String javaVersionUsedForReproduction;
        public final String reproductionLogLocation;

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
