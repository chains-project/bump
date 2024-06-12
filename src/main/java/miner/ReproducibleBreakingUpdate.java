package miner;

public class ReproducibleBreakingUpdate extends BreakingUpdate {

    private static final String DEFAULT_JAVA_VERSION_FOR_REPRODUCTION = "11";
    public String preCommitReproductionCommand = null;
    public String breakingUpdateReproductionCommand = null;
    public String javaVersionUsedForReproduction;
    public final UpdatedDependency updatedDependency;
    private FailureCategory failureCategory;
    public String licenseInfo;

    /**
     * Create a new ReproducibleBreakingUpdate object that stores information about a
     * reproducible breaking dependency update.
     */
    public ReproducibleBreakingUpdate(String url, String project, String projectOrganisation, String breakingCommit,
                                      String prAuthor, String preCommitAuthor, String breakingCommitAuthor,
                                      BreakingUpdate.UpdatedDependency updatedDependency, String githubCompareLink,
                                      String mavenSourceLinkPre, String mavenSourceLinkBreaking,
                                      UpdatedDependency.UpdatedFileType updatedFileType, String licenseInfo, String dependencyLicenseInfo, String githubRepoSlug) {
        super(url, project, projectOrganisation, breakingCommit, prAuthor, preCommitAuthor, breakingCommitAuthor, updatedDependency);
        this.updatedDependency = new UpdatedDependency(updatedDependency.dependencyGroupID, updatedDependency.dependencyArtifactID,
                updatedDependency.previousVersion, updatedDependency.newVersion, updatedDependency.dependencyScope,
                updatedDependency.versionUpdateType, updatedDependency.dependencySection, githubCompareLink, mavenSourceLinkPre,
                mavenSourceLinkBreaking, updatedFileType, dependencyLicenseInfo, githubRepoSlug);
        this.licenseInfo = licenseInfo;
    }

    /**
     * Set the java version used in the reproduction process.
     *
     * @param javaVersionUsedForReproduction the java version used in the reproduction process.
     */
    public void setJavaVersionUsedForReproduction(String javaVersionUsedForReproduction) {
        this.javaVersionUsedForReproduction = javaVersionUsedForReproduction;
    }

    /**
     * Set the default java version used in the reproduction process.
     */
    public void setJavaVersionUsedForReproduction() {
        this.javaVersionUsedForReproduction = DEFAULT_JAVA_VERSION_FOR_REPRODUCTION;
    }

    /**
     * Update preCommitReproductionCommand of this breaking update.
     *
     * @param preCommitReproductionCommand the new preCommitReproductionCommand to add to this breaking update.
     */
    public void setPreCommitReproductionCommand(String preCommitReproductionCommand) {
        this.preCommitReproductionCommand = preCommitReproductionCommand;
    }

    /**
     * Get preCommitReproductionCommand of this breaking update.
     *
     * @return preCommitReproductionCommand of this breaking update.
     */
    public String getPreCommitReproductionCommand() {
        return preCommitReproductionCommand;
    }

    /**
     * Update failureCategory of this breaking update.
     *
     * @param failureCategory the failureCategory to add to this breaking update.
     */
    public void setFailureCategory(FailureCategory failureCategory) {
        this.failureCategory = failureCategory;
    }

    /**
     * Get failureCategory of this breaking update.
     *
     * @return failureCategory of this breaking update.
     */
    public FailureCategory getFailureCategory() {
        return failureCategory;
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
     * Get breakingUpdateReproductionCommand of this breaking update.
     *
     * @return breakingUpdateReproductionCommand of this breaking update.
     */
    public String getBreakingUpdateReproductionCommand() {
        return breakingUpdateReproductionCommand;
    }

    /**
     * Failure category indicating the status of the reproduction, i.e. the results of attempted reproduction.
     */
    public enum FailureCategory {

        /**
         * There were unknown failures after updating the dependency, but none in the previous commit.
         */
        UNKNOWN_FAILURE,
        /**
         * There were failures when downloading dependencies after updating the dependency.
         */
        DEPENDENCY_RESOLUTION_FAILURE,
        /**
         * There were failures when updating the dependency because the dependency version is locked by a
         * dependency-lock plugin.
         */
        DEPENDENCY_LOCK_FAILURE,
        /**
         * The compilation failed due to failing enforcer rules after updating the dependency,
         * but in the previous commit there were no failures.
         */
        ENFORCER_FAILURE,
        /**
         * The compilation failed after updating the dependency, but succeeded for the previous commit.
         */
        COMPILATION_FAILURE,
        /**
         * There were test failures after updating the dependency, but not for the preceding commit.
         */
        TEST_FAILURE;
    }

    /**
     * UpdatedDependency represents information associated with the updated dependency.
     */
    public static class UpdatedDependency extends BreakingUpdate.UpdatedDependency {

        public String githubCompareLink;
        public String mavenSourceLinkPre;
        public String mavenSourceLinkBreaking;
        public UpdatedFileType updatedFileType;
        public final String licenseInfo;
        public final String githubRepoSlug;

        /**
         * Create updated dependency for the breaking update.
         */
        public UpdatedDependency(String dependencyGroupID, String dependencyArtifactID, String previousVersion,
                                 String newVersion, String dependencyScope, String versionUpdateType, String dependencySection,
                                 String githubCompareLink, String mavenSourceLinkPre, String mavenSourceLinkBreaking,
                                 UpdatedFileType updatedFileType, String licenseInfo, String githubRepoSlug) {
            super(dependencyGroupID, dependencyArtifactID, previousVersion, newVersion, dependencyScope, versionUpdateType,
                    dependencySection);
            this.githubCompareLink = githubCompareLink;
            this.mavenSourceLinkPre = mavenSourceLinkPre;
            this.mavenSourceLinkBreaking = mavenSourceLinkBreaking;
            this.updatedFileType = updatedFileType;
            this.licenseInfo = licenseInfo;
            this.githubRepoSlug = githubRepoSlug;
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
