package miner;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * The JsonUtils class provides a simple interface for writing and reading JSON files
 *
 * @author <a href="mailto:gabsko@kth.se">Gabriel Skoglund</a>
 */
public class JsonUtils {

    /** The default JSON file ending ".json" */
    public static final String JSON_FILE_ENDING = ".json";

    /** The default date format of the form yyyy-MM-dd HH:mm:ss" */
    public static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

    /** The string representing an empty JSON object */
    public static final String EMPTY_JSON_OBJECT = "{}";
    private static final Gson gson = new GsonBuilder().setDateFormat(DATE_FORMAT).setPrettyPrinting().create();

    private JsonUtils() { /* Nothing to see here... */ }

    /**
     * Read a JSON object from file
     * @param file the path to the JSON file to read.
     * @param jsonType the type that the data should be considered as.
     * @return an object of the specified type as read from the given file.
     * @throws IOException if there was an issue interacting with the file system.
     * @throws com.google.gson.JsonSyntaxException if there is an error interpreting the JSON data.
     */
    public static <T> T readFromFile(Path file, Type jsonType) throws IOException {
        return gson.fromJson(Files.readString(file), jsonType);
    }

    /**
     * Write an object to a JSON file.
     * @param outputFilePath the file path where the data should be written.
     * @param data the object to be stored as JSON.
     */
    public static void writeToFile(Path outputFilePath, Object data) {
        writeToFile(outputFilePath, data, data.getClass());
    }

    /**
     * Write an object to a JSON file.
     * @param outputFilePath the file path where the data should be written.
     * @param data the object to be stored as JSON.
     * @param typeOfData the type of the object to store.
     */
    public static void writeToFile(Path outputFilePath, Object data, Type typeOfData) {
        String json = gson.toJson(data, typeOfData);
        try {
            Files.writeString(outputFilePath, json, StandardOpenOption.WRITE, StandardOpenOption.CREATE,
                              StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
