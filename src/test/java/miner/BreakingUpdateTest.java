package miner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The BreakingUpdateTest performs some basic tests to make sure that we parse information about a breaking
 * update correctly.
 *
 * @author <a href="mailto:gabsko@kth.se">Gabriel Skoglund</a>
 */
public class BreakingUpdateTest extends GitHubMinerTestBase {

    static List<BreakingUpdate> breakingUpdates;

    @BeforeEach
    public void setUp() throws IOException {
        if (breakingUpdates != null)
            return;
        breakingUpdates = List.of(
                new BreakingUpdate(gitHub.getRepository("alibaba/fastjson").getPullRequest(4233)),
                new BreakingUpdate(gitHub.getRepository("orientechnologies/orientdb").getPullRequest(8118)),
                new BreakingUpdate(gitHub.getRepository("pholser/junit-quickcheck").getPullRequest(499)),
                new BreakingUpdate(gitHub.getRepository("feedzai/pdb").getPullRequest(342)),
                new BreakingUpdate(gitHub.getRepository("INRIA/spoon").getPullRequest(4620))
        );
    }

    @Test
    public void groupIDIsCorrectlyIdentified() {
        List<String> expected = List.of(
                "org.eclipse.jetty",
                "com.fasterxml.jackson.core",
                "org.mockito",
                "mysql",
                "org.bitbucket.mstrobel"
        );
        for (int i = 0; i < breakingUpdates.size(); i++)
            assertEquals(expected.get(i), breakingUpdates.get(i).updatedDependency.dependencyGroupID);
    }

    @Test
    public void artifactIDIsCorrectlyIdentified() {
        List<String> expected = List.of(
                "jetty-server",
                "jackson-databind",
                "mockito-core",
                "mysql-connector-java",
                "procyon-compilertools"
        );
        for (int i = 0; i < breakingUpdates.size(); i++)
            assertEquals(expected.get(i), breakingUpdates.get(i).updatedDependency.dependencyArtifactID);
    }

    @Test
    public void previousVersionIsCorrectlyIdentified() {
        List<String> expected = List.of(
                "9.4.17.v20190418",
                "2.6.0",
                "4.11.0",
                "5.1.49",
                "0.5.36"
        );
        for (int i = 0; i < breakingUpdates.size(); i++)
            assertEquals(expected.get(i), breakingUpdates.get(i).updatedDependency.previousVersion);
    }

    @Test
    public void newVersionIsCorrectlyIdentified() {
        List<String> expected = List.of(
                "10.0.10",
                "2.9.4",
                "5.3.1",
                "8.0.28",
                "0.6.0"
        );
        for (int i = 0; i < breakingUpdates.size(); i++)
            assertEquals(expected.get(i), breakingUpdates.get(i).updatedDependency.newVersion);
    }

    @Test
    public void versionUpdateTypeIsCorrectlyIdentified() {
        List<String> expected = List.of(
                "other",
                "minor",
                "major",
                "major",
                "minor"
        );
        for (int i = 0; i < breakingUpdates.size(); i++)
            assertEquals(expected.get(i), breakingUpdates.get(i).updatedDependency.versionUpdateType);
    }

    @Test
    public void prAuthorTypeIsCorrectlyIdentified() {
        List<String> expected = List.of(
                "bot",
                "human",
                "bot",
                "bot",
                "bot"
        );
        for (int i = 0; i < breakingUpdates.size(); i++)
            assertEquals(expected.get(i), breakingUpdates.get(i).prAuthor);
    }

    @Test
    public void previousCommitAuthorTypeIsCorrectlyIdentified() {
        List<String> expected = List.of(
                "human",
                "human",
                "bot",
                "bot",
                "human"
        );
        for (int i = 0; i < breakingUpdates.size(); i++)
            assertEquals(expected.get(i), breakingUpdates.get(i).preCommitAuthor);
    }

    @Test
    public void breakingCommitAuthorTypeIsCorrectlyIdentified() {
        List<String> expected = List.of(
                "bot",
                "human",
                "bot",
                "bot",
                "bot"
        );
        for (int i = 0; i < breakingUpdates.size(); i++)
            assertEquals(expected.get(i), breakingUpdates.get(i).breakingCommitAuthor);
    }
}
