package com.jacky8399.worstshop.helper;

import org.bukkit.configuration.MemorySection;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Conceals ugly casts
 */
public final class Config {
    private Map<String, Object> backingMap;
    public Config(MemorySection section) {
        backingMap = section.getValues(false);
    }

    public Config(Map<String, Object> map) {
        backingMap = new LinkedHashMap<>(map);
    }

    public Map<String, Object> getPrimitiveMap() {
        return backingMap;
    }

    @SuppressWarnings("unchecked")
    private <T> T handleObj(Object obj, Class<? extends T> clazz) {
        if (clazz.isInstance(obj)) {
            // does it matter? idk
            return (T) obj;
        } else if (clazz.isEnum() && obj instanceof String) {
            @SuppressWarnings("rawtypes")
            T enumObj = (T) Enum.valueOf((Class) clazz, (String) obj);
            return enumObj;
        } else if (Config.class == clazz) {
            if (obj instanceof Map<?, ?>) {
                return (T) new Config((Map<String, Object>) obj);
            }
        }
        // cannot throw an exception
        return null;
    }

//    public <T> Optional<T> find(String key, Class<? extends T> clazz) {
//        Object obj = backingMap.get(key);
//        if (obj == null) {
//            return Optional.empty();
//        }
//        T val = handleObj(obj, clazz);
//        if (val == null) {
//            // incompatible types
//            throw new IllegalArgumentException("Expected " + clazz.getSimpleName() + " at " + key + ", found " + obj.getClass().getSimpleName());
//        }
//        return Optional.of(val);
//    }

    private static String stringifyAcceptedTypes(Class<?>[] classes) {
        return classes.length == 1 ? classes[0].getSimpleName() : Arrays.stream(classes).map(Class::getSimpleName).collect(Collectors.joining("/"));
    }

    @SafeVarargs
    public final <T> Optional<T> find(String key, Class<? extends T>... classes) {
        if (classes.length == 0)
            throw new IllegalArgumentException("classes cannot be empty");
        Object obj = backingMap.get(key);
        if (obj == null) {
            return Optional.empty();
        }
        for (Class<? extends T> clazz : classes) {
            // special classes
            T val = handleObj(obj, clazz);
            if (val != null)
                return Optional.of(val);
        }
        throw new IllegalArgumentException("Expected " + stringifyAcceptedTypes(classes) + " at " + key + ", found " + obj.getClass().getSimpleName());
    }

//    public <T> T get(String key, Class<? extends T> clazz) {
//        return find(key, clazz).orElseThrow(throwFor(key));
//    }

    @SafeVarargs
    public final <T> T get(String key, Class<? extends T>... classes) {
        if (classes.length == 0)
            throw new IllegalArgumentException("classes cannot be empty");
        return find(key, classes).orElseThrow(throwFor(key, stringifyAcceptedTypes(classes)));
    }

    public <T> Optional<List<T>> findList(String key, Class<T> listType) {
        return find(key, List.class).map(list -> ((List<?>) list).stream()
                .map(child -> {
                    T newChild = handleObj(child, listType);
                    if (newChild != null)
                        return newChild;
                    throw new IllegalArgumentException("Expected list of " + listType.getSimpleName() + " at " + key + ", found list of " + child.getClass().getSimpleName());
                })
                .collect(Collectors.toList())
        );
    }

    public <T> List<T> getList(String key, Class<T> listType) {
        return findList(key, listType).orElseThrow(throwFor(key, "list of " + listType.getSimpleName()));
    }


    private static Supplier<? extends IllegalArgumentException> throwFor(String key, String type) {
        return ()->new IllegalArgumentException("Expected " + type + " at " + key + ", found nothing");
    }
}
