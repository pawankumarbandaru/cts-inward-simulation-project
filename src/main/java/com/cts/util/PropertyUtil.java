package com.cts.util;

import java.io.InputStream;
import java.util.Properties;

public class PropertyUtil {

    private static final Properties properties = new Properties();

    static {

        try {

            InputStream inputStream = Thread.currentThread()
                    .getContextClassLoader()
                    .getResourceAsStream("application.properties");

            if (inputStream == null) {
                throw new RuntimeException("application.properties not found in classpath");
            }

            properties.load(inputStream);

        } catch (Exception e) {

            throw new RuntimeException("Failed to load application.properties : " + e.getMessage(), e);
        }
    }

    public static String getProperty(String key) {

        String value = properties.getProperty(key);

        if (value == null || value.trim().isEmpty()) {
            throw new RuntimeException("Property not found in application.properties : " + key);
        }

        return value.trim();
    }
}
