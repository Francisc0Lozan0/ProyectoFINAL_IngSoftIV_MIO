package com.sitm.mio.util;

import java.io.*;
import java.util.Properties;

public class ConfigManager {
    private static ConfigManager instance;
    private Properties properties;
    
    private ConfigManager() {
        properties = new Properties();
        try {
            InputStream input = new FileInputStream("config/cluster.properties");
            properties.load(input);
        } catch (IOException e) {
            System.err.println("Error loading configuration: " + e.getMessage());
            setDefaultProperties();
        }
    }
    
    private void setDefaultProperties() {
        properties.setProperty("master.host", "localhost");
        properties.setProperty("master.port", "10000");
        properties.setProperty("cluster.max.workers", "10");
        properties.setProperty("data.path", "./data");
        properties.setProperty("processing.timeout.minutes", "10");
        properties.setProperty("streaming.window.size.seconds", "300");
    }
    
    public static synchronized ConfigManager getInstance() {
        if (instance == null) {
            instance = new ConfigManager();
        }
        return instance;
    }
    
    public String getString(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }
    
    public int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(properties.getProperty(key));
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    public long getLong(String key, long defaultValue) {
        try {
            return Long.parseLong(properties.getProperty(key));
        } catch (Exception e) {
            return defaultValue;
        }
    }
}