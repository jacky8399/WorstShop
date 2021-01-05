package com.jacky8399.worstshop.helper;

import com.jacky8399.worstshop.WorstShop;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.UUID;

public class Exceptions {
    public static class ExceptionLog {
        public final Exception exception;
        public final LocalDateTime date;
        public ExceptionLog(Exception e, LocalDateTime date) {
            exception = e;
            this.date = date;
        }

        public ExceptionLog(Exception e) {
            this(e, LocalDateTime.now());
        }
    }

    public static final HashMap<String, ExceptionLog> exceptions = new HashMap<>();
    public static String logException(Exception e) {
        ExceptionLog log = new ExceptionLog(e);
        String id = UUID.randomUUID().toString().substring(0, 8);
        exceptions.put(id, log);
        WorstShop.get().logger.severe("An error just occurred for a player. To inspect, use /worstshop log error show " + id);
        return id;
    }
}
