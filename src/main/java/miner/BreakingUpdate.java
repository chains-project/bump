package miner;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
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

    public final String url;
    public final String project;
    public final String commit;
    public final Date createdAt;
    public final String dependencyGroupID;
    public final String dependencyArtifactID;
    public final String previousVersion;
    public final String newVersion;
    public final String dependencyScope;
    public final String versionUpdateType;
    public final String prAuthor;
    public final String preCommitAuthor;
    public final String breakingCommitAuthor;
    private String reproductionStatus = "not_attempted";
    public String baseBuildCommand = null;
    public String breakingUpdateReproductionCommand = null;
    private Analysis analysis = null;
    private Metadata metadata = null;

    /**
     * Create a new BreakingUpdate object that stores information about a
     * breaking dependency update.
     *
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
        dependencyGroupID = parsePatch(pr, DEPENDENCY_GROUP_ID, "unknown");
        dependencyArtifactID = parsePatch(pr, DEPENDENCY_ARTIFACT_ID, "unknown");
        previousVersion = parsePatch(pr, PREVIOUS_VERSION, "unknown");
        newVersion = parsePatch(pr, NEW_VERSION, "unknown");
        dependencyScope = parsePatch(pr, SCOPE, "unknown");
        versionUpdateType = parseVersionUpdateType(previousVersion, newVersion);
        prAuthor = parsePRAuthorType(pr);
        preCommitAuthor = parsePreCommitAuthorType(pr.getRepository(), commit);
        breakingCommitAuthor = parseBreakingCommitAuthorType(pr.getRepository(), commit);
    }


    /** Private constructor for loading a BreakingUpdate from a JSON file */
    @JsonCreator
    private BreakingUpdate(@JsonProperty("url") String url,
                           @JsonProperty("project") String project,
                           @JsonProperty("commit") String commit,
                           @JsonProperty("createdAt") Date createdAt,
                           @JsonProperty("dependencyGroupID") String dependencyGroupID,
                           @JsonProperty("dependencyArtifactID") String dependencyArtifactID,
                           @JsonProperty("previousVersion") String previousVersion,
                           @JsonProperty("newVersion") String newVersion,
                           @JsonProperty("dependencyScope") String dependencyScope,
                           @JsonProperty("versionUpdateType") String versionUpdateType,
                           @JsonProperty("prAuthor") String prAuthor,
                           @JsonProperty("preCommitAuthor") String preCommitAuthor,
                           @JsonProperty("breakingCommitAuthor") String breakingCommitAuthor,
                           @JsonProperty("baseBuildCommand") String baseBuildCommand,
                           @JsonProperty("breakingUpdateReproductionCommand") String breakingUpdateReproductionCommand,
                           @JsonProperty("reproductionStatus") String reproductionStatus,
                           @JsonProperty("analysis") Analysis analysis,
                           @JsonProperty("metadata") Metadata metadata){
        this.url = url;
        this.project = project;
        this.commit = commit;
        this.createdAt = createdAt;
        this.dependencyGroupID = dependencyGroupID;
        this.dependencyArtifactID = dependencyArtifactID;
        this.previousVersion = previousVersion;
        this.newVersion = newVersion;
        this.dependencyScope = dependencyScope;
        this.versionUpdateType = versionUpdateType;
        this.prAuthor = prAuthor;
        this.preCommitAuthor = preCommitAuthor;
        this.breakingCommitAuthor = breakingCommitAuthor;
        this.reproductionStatus = reproductionStatus;
        this.baseBuildCommand = baseBuildCommand;
        this.breakingUpdateReproductionCommand = breakingUpdateReproductionCommand;
        this.analysis = analysis;
        this.metadata = metadata;
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
     * Parse the type of user that made the breaking pull request
     *
     * @param pr The pull request to parse
     * @return "bot" if the user is a bot, otherwise return "human".
     */
    private String parsePRAuthorType(GHPullRequest pr) {
        try {
            GHUser user = pr.getUser();
            return user.getType().equals("Bot") ? "bot" : "human";
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Parse the type of user that made the previous commit of the breaking commit
     *
     * @param repository The GitHub repository
     * @param commitSHA  The breaking commit which is used to parse the previous commit
     * @return "bot" if the user is a bot, otherwise return "human".
     */
    private String parsePreCommitAuthorType(GHRepository repository, String commitSHA) {
        try {
            // There is not a proper way to identify the immediate parent of the commit. So we make the assumption
            // that the first parent in the parents list is the immediate parent.
            GHUser user = repository.getCommit(commitSHA).getParents().get(0).getAuthor();
            return user.getType().equals("Bot") ? "bot" : "human";
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Parse the type of user that made the breaking commit
     *
     * @param repository The GitHub repository
     * @param commitSHA  The breaking commit to parse
     * @return "bot" if the user is a bot, otherwise return "human".
     */
    private String parseBreakingCommitAuthorType(GHRepository repository, String commitSHA) {
        try {
            GHUser user = repository.getCommit(commitSHA).getAuthor();
            return user.getType().equals("Bot") ? "bot" : "human";
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toString() {
        return ("BreakingUpdate{url = %s, project = %s, commit = %s, createdAt = %s, dependencyArtifactID = %s, " +
                "dependencyGroupID = %s, previousVersion = %s, newVersion = %s, versionUpdateType = %s, " +
                "prAuthor = %s, preCommitAuthor = %s, breakingCommitAuthor = %s}")
                .formatted(url, project, commit, createdAt, dependencyArtifactID, dependencyGroupID, previousVersion,
                        newVersion, versionUpdateType, prAuthor, preCommitAuthor, breakingCommitAuthor);
    }

    /**
     * Set the reproduction status of this breaking update.
     *
     * @param reproductionStatus the new reproduction status, should be one of "not_attempted", "successful" or
     *                           "unreproducible".
     */
    public void setReproductionStatus(String reproductionStatus) {
        this.reproductionStatus = reproductionStatus;
    }

    /**
     * Update baseBuildCommand of this breaking update.
     *
     * @param baseBuildCommand the new baseBuildCommand to add to this breaking update.
     */
    public void setBaseBuildCommand(String baseBuildCommand) {
        this.baseBuildCommand = baseBuildCommand;
    }

    /**
     * Get baseBuildCommand of this breaking update. Note that if the {@code reproductionStatus} of this breaking
     * update is "not_attempted", baseBuildCommand will be {@code null}.
     *
     * @return baseBuildCommand of this breaking update.
     */
    public String getBaseBuildCommand() {
        return baseBuildCommand;
    }

    /**
     * Update breakingUpdateReproductionCommand of this breaking update.
     *
     * @param breakingUpdateReproductionCommand the new breakingUpdateReproductionCommand to add to this breaking update.
     */
    public void setBreakingUpdateReproductionCommand(String breakingUpdateReproductionCommand) {
        this.breakingUpdateReproductionCommand = breakingUpdateReproductionCommand;
    }

    /**
     * Get breakingUpdateReproductionCommand of this breaking update. Note that if the {@code reproductionStatus} of
     * this breaking update is "not_attempted", breakingUpdateReproductionCommand will be {@code null}.
     *
     * @return breakingUpdateReproductionCommand of this breaking update.
     */
    public String getBreakingUpdateReproductionCommand() {
        return breakingUpdateReproductionCommand;
    }

    /**
     * Update the analysis of this breaking update.
     *
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
     * Update metadata of this breaking update.
     * @param metadata the new metadata to add to this breaking update.
     */
    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }

    /**
     * Get metadata of this breaking update. Note that if the {@code reproductionStatus} of this breaking
     * update is "not_attempted", metadata will be {@code null}.
     * @return metadata of this breaking update.
     */
    public Metadata getMetadata() {
        return metadata;
    }

    /**
     * The Analysis class represents data associated with the reproduction and analysis of a breaking update.
     */
    public static class Analysis {
        private static final String DEFAULT_JAVA_VERSION_FOR_REPRODUCTION = "11";

        public final List<ReproductionLabel> labels;
        public final String javaVersionUsedForReproduction;
        public final String reproductionLogLocation;

        /**
         * Create a new Analysis of this breaking update, where the Java version used for reproduction is set to
         * {@value DEFAULT_JAVA_VERSION_FOR_REPRODUCTION}.
         *
         * @param labels a list of {@link ReproductionLabel}s for the analysis, describing the result of
         *               the attempted reproduction.
         * @param reproductionLogLocation the location where the Maven log of the reproduction is stored.
         */
        public Analysis(List<ReproductionLabel> labels, String reproductionLogLocation) {
            this(labels, DEFAULT_JAVA_VERSION_FOR_REPRODUCTION, reproductionLogLocation);
        }

        /**
         * Create a new Analysis of this breaking update.
         *
         * @param labels a list of {@link ReproductionLabel}s for the analysis, describing the result of
         *               the attempted reproduction.
         * @param javaVersionUsedForReproduction the Java version used in reproducing this breaking update.
         * @param reproductionLogLocation the location where the Maven log of the reproduction is stored.
         */
    	@JsonCreator
        public Analysis(@JsonProperty("labels") List<ReproductionLabel> labels,
			            @JsonProperty("javaVersionUsedForReproduction") String javaVersionUsedForReproduction,
                        @JsonProperty("reproductionLogLocation") String reproductionLogLocation) {
            this.labels = labels;
            this.javaVersionUsedForReproduction = javaVersionUsedForReproduction;
            this.reproductionLogLocation = reproductionLogLocation;
        }

        /**
         * Label indicating the status of the reproduction, i.e. the results of attempted reproduction.
         */
        public enum ReproductionLabel {
            // Note: We order the values so that the failures are first, this allows simple checking with the
            //       isSuccessful method.

            /** There were unknown failures after updating the dependency, but none in the previous commit. */
            UNKNOWN_FAILURE,
            /** There were failures when downloading dependencies after updating the dependency. */
            DEPENDENCY_RESOLUTION_FAILURE,
            /** The compilation failed due to failing maven enforcer rules after updating the dependency,
             * but in the previous commit there were no failures. */
            MAVEN_ENFORCER_FAILURE,
            /** The compilation failed after updating the dependency, but succeeded for the previous commit. */
            COMPILATION_FAILURE,
            /** There were test failures after updating the dependency, but not for the preceding commit. */
            TEST_FAILURE;
        }
    }

    /**
     * Metadata represents metadata associated with the reproduction and analysis of a breaking update.
     */
    public record Metadata(String compareLink, List<String> mavenSourceLinks,
                           BreakingUpdate.Metadata.UpdatedFileType updatedFileType) {
        /**
         * Create metadata of this breaking update.
         *
         * @param compareLink      the comparison link of two GitHub tags where the two tags correspond to the old and
         *                         new versions of the dependency involved in the breaking update.
         * @param mavenSourceLinks the maven source links of the old and new versions of the dependency involved
         *                         in the breaking update.
         * @param updatedFileType  the type of the updated dependency.
         */
        @JsonCreator
        public Metadata(@JsonProperty("compareLink") String compareLink,
                        @JsonProperty("mavenSourceLinks") List<String> mavenSourceLinks,
                        @JsonProperty("updatedFileType") UpdatedFileType updatedFileType) {
            this.compareLink = compareLink;
            this.mavenSourceLinks = mavenSourceLinks;
            this.updatedFileType = updatedFileType;
        }

        /**
         * The type of the updated dependency, indicating whether it is a pom type dependency where a jar file will
         * not be collected, or a jar type dependency where a jar file will be downloaded.
         */
        public enum UpdatedFileType {
            POM,
            JAR,
        }
    }
}
