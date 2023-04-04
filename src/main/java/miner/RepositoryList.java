package miner;

import com.google.gson.reflect.TypeToken;
import org.kohsuke.github.GHRepository;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/**
 * The RepositoryList class represents a collection of data regarding GitHub repositories.
 *
 * @author <a href="mailto:gabsko@kth.se">Gabriel Skoglund</a>
 */
public class RepositoryList {

    static final Type JSON_TYPE = new TypeToken<Map<String, RepositoryData>>() {}.getType();
    private final Map<String, RepositoryData> repos;

    /** The file that is used to persist this repository list */
    private final Path backingFile;

    /**
     * Create a new RepositoryList from file.
     * @param jsonFile a path to a JSON file containing a RepositoryList in serialized form.
     */
    public RepositoryList(Path jsonFile) {
        backingFile = jsonFile;
        try {
            repos = JsonUtils.readFromFile(jsonFile, JSON_TYPE);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Add a GitHub repository to this list.
     * @param repo the repository to add.
     */
    public void add(GHRepository repo) {
        repos.put(repo.getFullName(), new RepositoryData(repo, null));
    }

    /**
     * Check if the given GitHub repository is in this list.
     * @param repo the repository to look for.
     * @return true if the repository is in the list, false otherwise.
     */
    public boolean contains(GHRepository repo) {
        return repos.containsKey(repo.getFullName());
    }

    /**
     * @return the number of items in this list.
     */
    public int size() {
        return repos.size();
    }

    /**
     * @return the full names of the repositories in the list, on the form organization/project (eg apache/maven).
     */
    public Set<String> getRepositoryNames() {
        return repos.keySet();
    }

    /**
     * Set the last time the given repository was checked for breaking updates.
     * @param repoName the name of the repository on the form organization/project (eg apache/maven).
     * @param date the time when the repository was last checked.
     */
    public void setCheckedTime(String repoName, Date date) {
        RepositoryData oldInfo = repos.get(repoName);
        repos.put(repoName, new RepositoryData(oldInfo.url, date));
    }

    /**
     * @param repoName the name of the repository on the form organization/project (eg apache/maven).
     * @return the last time the repository was checked for breaking updates, or the start of the UNIX epoch
     *         if this repository is not yet checked.
     */
    public Date getCheckedTime(String repoName) {
        Date lastCheckedTime = repos.get(repoName).lastCheckedAt;
        return lastCheckedTime == null ? Date.from(Instant.EPOCH) : lastCheckedTime;
    }

    /**
     * Store this RepositoryList to a file in JSON format.
     */
    public void writeToFile() {
        JsonUtils.writeToFile(backingFile, repos);
    }

    record RepositoryData(String url, Date lastCheckedAt) {
        public RepositoryData(GHRepository repo, Date date) {
            this(repo.getUrl().toString(), date);
        }
    }
}
