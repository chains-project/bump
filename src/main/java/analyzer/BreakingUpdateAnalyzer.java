package analyzer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import miner.BreakingUpdate;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * The BreakingUpdateAnalyzer is intended to add additional information about the reproduction of breaking updates
 * to breaking updates stored in JSON format.
 *
 * @author <a href="mailto:gabsko@kth.se">Gabriel Skoglund</a>
 */
public class BreakingUpdateAnalyzer {

    private static final String SUCCESSFUL_DIR = "successful";
    private static final String SUCCESSFUL_JSON = "successful.json";
    private static final String UNREPRODUCIBLE_DIR = "unreproducible";
    private static final String UNREPRODUCIBLE_JSON = "unreproducible.json";
    private static final String LABEL = "label";
    private static final Type REPRODUCTION_JSON_TYPE =
            new TypeToken<Map<String, Map<String, String>>>() {}.getType();
    private final Path reproductionDir;
    private final Path datasetDir;
    private final Gson gson;

    /**
     * Create a new BreakingUpdateAnalyzer
     * @param reproductionDir the directory where reproduction results are stored. This is expected to have a certain
     *                        structure, of the form:
     *                        <pre>{@code
     *                          REPRODUCTION-DIR
     *                              |- successful/          // A directory storing maven logs of successful reproductions.
     *                              |- successful.json      // A file containing information about successful reproductions.
     *                              |- unreproducible/      // A directory storing maven logs of successful reproductions.
     *                              |- unreproducible.json  // A file containing information about successful reproductions.
     *                         }</pre>
     * @param datasetDir the directory where breaking updates are stored in JSON form, as output by the
     *                   BreakingUpdateMiner tool.
     */
    public BreakingUpdateAnalyzer(Path reproductionDir, Path datasetDir) {
        this.reproductionDir = reproductionDir;
        this.datasetDir = datasetDir;
        gson = new GsonBuilder().setPrettyPrinting().create();
    }

    /**
     * Perform the analysis on the directories specified in the constructor.
     */
    public void analyze() {
        System.out.println("Analyzing successful reproductions");
        analyzeReproductions(reproductionDir.resolve(SUCCESSFUL_DIR), reproductionDir.resolve(SUCCESSFUL_JSON), "successful");

        System.out.println("Analyzing unsuccessful reproductions");
        analyzeReproductions(reproductionDir.resolve(UNREPRODUCIBLE_DIR), reproductionDir.resolve(UNREPRODUCIBLE_JSON), "unreproducible");
    }

    /** Parse reproduction data and add this to the individual breaking update JSON files */
    private void analyzeReproductions(Path logDir, Path statusJSONFile, String reproductionStatus) {
        Map<String, Map<String, String>> jsonData = getReproductionJSON(statusJSONFile);

        jsonData.keySet().parallelStream().forEach(key -> {
            System.out.printf("  Processing %s%n", key);

            String label = jsonData.get(key).get(LABEL);
            Path breakingUpdateFilePath = datasetDir.resolve(key + ".json");
            Path logFilePath = logDir.resolve(key + ".log");

            BreakingUpdate breakingUpdate = readBreakingUpdateJSON(breakingUpdateFilePath);
            breakingUpdate.setReproductionStatus(reproductionStatus);
            breakingUpdate.setAnalysis(new BreakingUpdate.Analysis(List.of(label), logFilePath.toString()));

            writeBreakingUpdateJSON(breakingUpdateFilePath, breakingUpdate);
        });
    }

    /**
     * Read the reproduction JSON data from a file.
     */
    private Map<String, Map<String, String>> getReproductionJSON(Path jsonFile) {
        try {
            return gson.fromJson(Files.readString(jsonFile), REPRODUCTION_JSON_TYPE);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Read a BreakingUpdate object from a JSON file.
     */
    private BreakingUpdate readBreakingUpdateJSON(Path datasetFile) {
        try {
            return gson.fromJson(Files.readString(datasetFile), BreakingUpdate.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Write a BreakingUpdate object to a JSON file.
     */
    private void writeBreakingUpdateJSON(Path breakingUpdateFile, BreakingUpdate breakingUpdate) {
        try {
            Files.writeString(breakingUpdateFile, gson.toJson(breakingUpdate));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
