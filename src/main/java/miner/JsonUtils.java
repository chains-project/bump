package miner;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;

/**
 * The JsonUtils class provides a simple interface for writing and reading JSON files
 *
 * @author <a href="mailto:gabsko@kth.se">Gabriel Skoglund</a>
 */
public class JsonUtils {

    /** The default JSON file ending ".json" */
    public static final String JSON_FILE_ENDING = ".json";

    /** The default date format of the form yyyy-MM-dd HH:mm:ss" */
    public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    /** The string representing an empty JSON object */
    public static final String EMPTY_JSON_OBJECT = "{}";

    private static final ObjectMapper mapper = new ObjectMapper().setDateFormat(DATE_FORMAT);

    private JsonUtils() { /* Nothing to see here... */ }

    /**
     * Read a JSON object from file
     * @param file the path to the JSON file to read.
     * @param jsonType the type that the data should be considered as.
     * @return an object of the specified type as read from the given file.
     */
    public static <T> T readFromFile(Path file, Class<T> jsonType) {
        try {
            return mapper.readValue(Files.readString(file), jsonType);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Read a JSON object from file
     * @param file the path to the JSON file to read.
     * @param jsonType the type that the data should be considered as.
     * @return an object of the specified type as read from the given file.
     */
    public static <T> T readFromFile(Path file, JavaType jsonType) {
        try {
            return mapper.readValue(Files.readString(file), jsonType);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Write an object to a JSON file.
     * @param outputFilePath the file path where the data should be written.
     * @param data the object to be stored as JSON.
     */
    public static void writeToFile(Path outputFilePath, Object data) {
        try {
            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(data);
            Files.writeString(outputFilePath, json, StandardOpenOption.WRITE, StandardOpenOption.CREATE,
                              StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @return a {@link TypeFactory} from the underlying {@link ObjectMapper}.
     */
    public static TypeFactory getTypeFactory() {
        return mapper.getTypeFactory();
    }
}
