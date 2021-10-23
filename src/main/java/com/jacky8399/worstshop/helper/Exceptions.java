package com.jacky8399.worstshop.helper;

import com.jacky8399.worstshop.WorstShop;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
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

    public static final LinkedHashMap<String, ExceptionLog> exceptions = new LinkedHashMap<String, ExceptionLog>(26) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ExceptionLog> eldest) {
            return size() > 25;
        }
    };
    public static String logException(Exception e) {
        ExceptionLog log = new ExceptionLog(e);
        String id = UUID.randomUUID().toString().substring(0, 8);
        exceptions.put(id, log);
        WorstShop.get().logger.severe("FATAL ERROR. For details run /worstshop log error show " + id);
        return id;
    }
}
