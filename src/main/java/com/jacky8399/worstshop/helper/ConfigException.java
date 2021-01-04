package com.jacky8399.worstshop.helper;

public class ConfigException extends RuntimeException {
    public ConfigException(String s, Config config) {
        super(s + " (Path: " + config.path + ")");
    }
}
