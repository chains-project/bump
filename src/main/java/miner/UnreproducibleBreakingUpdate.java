package miner;

public class UnreproducibleBreakingUpdate extends BreakingUpdate {

    private static final String DEFAULT_JAVA_VERSION_FOR_REPRODUCTION = "11";
    public String javaVersionUsedForReproduction;

    /**
     * Create a new UnreproducibleBreakingUpdate object that stores information about an
     * unreproducible breaking dependency update.
     */
    public UnreproducibleBreakingUpdate(String url, String project, String projectOrganisation, String breakingCommit,
                                        String prAuthor, String preCommitAuthor, String breakingCommitAuthor,
                                        BreakingUpdate.UpdatedDependency updatedDependency, String licenseInfo) {
        super(url, project, projectOrganisation, breakingCommit, prAuthor, preCommitAuthor, breakingCommitAuthor, updatedDependency, licenseInfo);
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
}
