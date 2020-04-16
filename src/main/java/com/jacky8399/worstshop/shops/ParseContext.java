package com.jacky8399.worstshop.shops;

import com.google.common.collect.Maps;

import java.util.HashMap;
import java.util.OptionalInt;
import java.util.Stack;
import java.util.stream.Collectors;

public class ParseContext {
    public static final Stack<Object> STACK = new Stack<>();
    public static final HashMap<Class<?>, Stack<Integer>> CLAZZ_LOCATION = Maps.newHashMap();

    public static void pushContext(Object ctx) {
        int nextIdx = STACK.size();
        STACK.add(ctx);
        CLAZZ_LOCATION.computeIfAbsent(ctx.getClass(), k -> new Stack<>()).push(nextIdx);
    }

    public static <T> T popContext() {
        if (STACK.isEmpty())
            throw new IllegalStateException("Stack is empty");
        Object last = STACK.pop();
        // remove clazz location
        Stack<Integer> integerStack = CLAZZ_LOCATION.get(last.getClass());
        if (integerStack != null && !integerStack.empty()) {
            integerStack.pop();
        }
        return (T) last;
    }

    public static <T> T findFirst(Class<? extends T> clazz) {
        // handle superclasses
        OptionalInt clazzLocation = CLAZZ_LOCATION.entrySet().stream()
                .filter(entry -> clazz.isAssignableFrom(entry.getKey()))
                .filter(entry -> !entry.getValue().isEmpty())
                .mapToInt(entry -> entry.getValue().firstElement())
                .min();
        if (clazzLocation.isPresent()) {
            Object obj = STACK.get(clazzLocation.getAsInt());
            return (T) obj;
        }
        return null;
    }

    public static <T> T findLatest(Class<? extends T> clazz) {
        // handle superclasses
        OptionalInt clazzLocation = CLAZZ_LOCATION.entrySet().stream()
                .filter(entry -> clazz.isAssignableFrom(entry.getKey()))
                .filter(entry -> !entry.getValue().isEmpty())
                .mapToInt(entry -> entry.getValue().lastElement())
                .max();
        if (clazzLocation.isPresent()) {
            Object obj = STACK.get(clazzLocation.getAsInt());
            return (T) obj;
        }
        return null;
    }

    public static void clear() {
        STACK.clear();
        CLAZZ_LOCATION.clear();
    }

    public static String getHierarchy() {
        return STACK.stream().map(Object::getClass).map(Class::getSimpleName).collect(Collectors.joining(" > "));
    }
}
