package com.jacky8399.worstshop.helper;

public class ConfigException extends RuntimeException {
    public ConfigException(String s, Config config) {
        super(s + " (Path: " + config.path + ")");
    }

    public ConfigException(String s, Config config, String path) {
        super(s + " (Path: " + config.path + " > " + path + ")");
    }

    public ConfigException(String s, Config config, Throwable cause) {
        super(s + " (Path: " + config.path + ")", cause);
    }

    public ConfigException(String s, Config config, String path, Throwable cause) {
        super(s + " (Path: " + config.path + " > " + path + ")", cause);
    }
}
