package com.cts.util;

import java.io.InputStream;
import java.util.Properties;

/**
 * Utility class for reading application configuration properties.
 *
 * This class loads the 'application.properties' file from the classpath
 * at application startup and provides a simple method to retrieve
 * property values by their key.
 *
 * Usage:
 *   String value = PropertyUtil.getProperty("db.url");
 *
 * Note:
 *   - This class uses a static block to load properties only once
 *     when the class is first loaded into memory (Eager Loading).
 *   - It follows the Utility Class pattern — no instances needed.
 */
public class PropertyUtil {

    /*
     * A static Properties object to hold all key-value pairs
     * loaded from application.properties.
     *
     * 'static final' ensures:
     *   - It belongs to the class, not any instance.
     *   - It is initialized only once and never replaced.
     */
    private static final Properties properties = new Properties();

    /*
     * Static Initializer Block:
     * This block runs automatically when the JVM first loads the PropertyUtil class.
     * It reads the 'application.properties' file from the classpath and loads
     * all key-value pairs into the 'properties' object.
     *
     * Why static block?
     *   - Properties need to be loaded only once for the entire application lifecycle.
     *   - Static block runs before any method call, so properties are always ready.
     *
     * What happens if the file is missing?
     *   - A RuntimeException is thrown immediately, stopping the application.
     *   - This is intentional — the app should NOT start without valid configuration.
     */
    static {
        try {
            /*
             * Load 'application.properties' from the classpath.
             *
             * Thread.currentThread().getContextClassLoader() gives us
             * the correct ClassLoader that knows where our resources are,
             * especially inside a web application (Tomcat environment).
             *
             * getResourceAsStream() returns the file as a stream —
             * this works whether the file is in a JAR or a folder.
             */
            InputStream inputStream = Thread.currentThread()
                    .getContextClassLoader()
                    .getResourceAsStream("application.properties");

            /*
             * If the file is not found on the classpath, getResourceAsStream()
             * returns null instead of throwing an exception.
             * We handle this case manually by throwing a RuntimeException.
             */
            if (inputStream == null) {
                throw new RuntimeException("application.properties not found in classpath");
            }

            /*
             * Load all key-value pairs from the input stream into
             * the 'properties' object.
             *
             * After this line, we can retrieve any property using:
             *   properties.getProperty("key")
             */
            properties.load(inputStream);

        } catch (Exception e) {
            /*
             * If any error occurs during file loading (file not found,
             * IO error, etc.), we wrap it in a RuntimeException and
             * re-throw it to stop the application from starting silently.
             *
             * Failing fast here prevents harder-to-debug issues later.
             */
            throw new RuntimeException("Failed to load application.properties : " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves the value of a configuration property by its key.
     *
     * Example:
     *   String dbUrl = PropertyUtil.getProperty("db.url");
     *
     * @param key  The property key to look up (e.g., "db.url", "app.name")
     * @return     The trimmed string value associated with the given key
     * @throws RuntimeException  If the key is not found or its value is blank
     */
    public static String getProperty(String key) {

        /*
         * Look up the value for the given key from the loaded properties.
         * Returns null if the key does not exist.
         */
        String value = properties.getProperty(key);

        /*
         * Validate the retrieved value:
         *   - null         → key does not exist in the properties file
         *   - trim().isEmpty() → key exists but has no meaningful value (blank/whitespace)
         *
         * In both cases, we throw a RuntimeException with a clear message
         * so the developer knows exactly which property is missing.
         */
        if (value == null || value.trim().isEmpty()) {
            throw new RuntimeException("Property not found in application.properties : " + key);
        }

        /*
         * Return the trimmed value to avoid issues caused by
         * accidental leading/trailing spaces in the properties file.
         * Example: "  jdbc:postgresql://...  " becomes "jdbc:postgresql://..."
         */
        return value.trim();
    }
}