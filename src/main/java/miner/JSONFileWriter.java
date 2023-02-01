package miner;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The JSONFileWriter class provides a simple interface for writing JSON files to a specific location.
 *
 * @author <a href="mailto:gabsko@kth.se">Gabriel Skoglund</a>
 */
public class JSONFileWriter {

    public static final String JSON_FILE_ENDING = ".json";
    private final Path outputFilePath;
    private final Gson gson;

    /**
     * @param outputFilePath the file path at which to create the json file(s).
     * @throws IOException if an error occurs when querying the file system.
     */
    public JSONFileWriter(Path outputFilePath) throws IOException {
        this.outputFilePath = outputFilePath;
        if (!Files.exists(outputFilePath))
            Files.createDirectory(outputFilePath);
        gson = new GsonBuilder().setPrettyPrinting().create();
    }

    /**
     * Create a json file containing information about a breaking update.
     * @param breakingUpdate the breaking update to add a json file for.
     */
    public void writeBreakingUpdate(BreakingUpdate breakingUpdate) {
        try {
            Path path = outputFilePath.resolve(breakingUpdate.commit + JSON_FILE_ENDING);
            Files.writeString(path, gson.toJson(breakingUpdate));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
