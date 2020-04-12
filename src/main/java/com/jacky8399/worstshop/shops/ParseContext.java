package com.jacky8399.worstshop.shops;

import com.google.common.collect.Maps;

import java.util.HashMap;
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
        Stack<Integer> integerStack = CLAZZ_LOCATION.get(clazz);
        if (integerStack != null && !integerStack.empty()) {
            int first = integerStack.firstElement();
            Object obj = STACK.get(first);
            return (T) obj;
        }
        return null;
    }

    public static <T> T findLatest(Class<? extends T> clazz) {
        Stack<Integer> integerStack = CLAZZ_LOCATION.get(clazz);
        if (integerStack != null && !integerStack.empty()) {
            int first = integerStack.firstElement();
            Object obj = STACK.get(first);
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
