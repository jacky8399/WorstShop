package com.jacky8399.worstshop.helper;

import org.bukkit.configuration.MemorySection;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Conceals ugly casts
 */
@ParametersAreNonnullByDefault
public final class Config {
    private Map<String, Object> backingMap;
    public final Config parent;
    public Config(MemorySection section) {
        this(section.getValues(false), null);
    }

    public Config(Map<String, Object> map, @Nullable Config parent) {
        backingMap = new LinkedHashMap<>(map);
        this.parent = parent;
    }

    public Config(Map<String, Object> map) {
        this(map, null);
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
                return (T) new Config((Map<String, Object>) obj, this);
            }
        } else if (Number.class.isAssignableFrom(clazz) && obj instanceof Number) {
            // numbers huh
            Number num = (Number) obj;
            if (clazz == Integer.class)
                return (T) Integer.valueOf(num.intValue());
            else if (clazz == Double.class)
                return (T) Double.valueOf(num.doubleValue());
            else if (clazz == Long.class)
                return (T) Long.valueOf(num.longValue());
            else if (clazz == Float.class)
                return (T) Float.valueOf(num.floatValue());
            else if (clazz == Byte.class)
                return (T) Byte.valueOf(num.byteValue());
            else if (clazz == Short.class)
                return (T) Short.valueOf(num.shortValue());
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
        return classes.length == 1 ?
                classes[0].getSimpleName() :
                Arrays.stream(classes).map(Class::getSimpleName).collect(Collectors.joining("/"));
    }
    private static String stringifyAcceptedListTypes(Class<?>[] classes) {
        return classes.length == 1 ?
                "list of " + classes[0].getSimpleName() :
                Arrays.stream(classes).map(clazz -> "list of " + clazz.getSimpleName()).collect(Collectors.joining("/"));
    }

    @SafeVarargs
    public final <T> Optional<T> find(String key, Class<? extends T>... classes) throws ConfigException, IllegalArgumentException {
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
        throw new ConfigException("Expected " + stringifyAcceptedTypes(classes) + " at " + key + ", found " + obj.getClass().getSimpleName());
    }

//    public <T> T get(String key, Class<? extends T> clazz) {
//        return find(key, clazz).orElseThrow(throwFor(key));
//    }

    @SafeVarargs
    public final <T> T get(String key, Class<? extends T>... classes) throws ConfigException, IllegalArgumentException {
        if (classes.length == 0)
            throw new IllegalArgumentException("classes cannot be empty");
        return find(key, classes).orElseThrow(throwFor(key, stringifyAcceptedTypes(classes)));
    }

    public <T> Optional<List<T>> findList(String key, Class<T> listType) throws ConfigException, IllegalArgumentException {
        return find(key, List.class)
                .map(list -> ((List<?>) list)
                        .stream()
                        .map(child -> {
                            T newChild = handleObj(child, listType);
                            if (newChild != null)
                                return newChild;
                            throw new ConfigException("Expected list of " + listType.getSimpleName() + " at " + key + ", found at least one conflicting element of " + child.getClass().getSimpleName());
                        })
                        .collect(Collectors.toList())
                );
    }

    @SafeVarargs
    public final <T> Optional<List<? extends T>> findList(String key, Class<? extends T>... listTypes) throws ConfigException, IllegalArgumentException {
        if (listTypes.length == 0)
            throw new IllegalArgumentException("listTypes cannot be empty");
        return find(key, List.class)
                .map(list -> ((List<?>) list)
                        .stream()
                        .map(child -> {
                            for (Class<? extends T> clazz : listTypes) {
                                T newChild = handleObj(child, clazz);
                                if (newChild != null)
                                    return newChild;
                            }
                            throw new ConfigException("Expected " + stringifyAcceptedListTypes(listTypes) + " at " + key + ", found at least one conflicting element of " + child.getClass().getSimpleName());
                        })
                        .collect(Collectors.toList())
                );
    }

    public <T> List<T> getList(String key, Class<T> listType) throws ConfigException, IllegalArgumentException {
        return findList(key, listType).orElseThrow(throwFor(key, "list of " + listType.getSimpleName()));
    }

    @SafeVarargs
    public final <T> List<? extends T> getList(String key, Class<? extends T>... listTypes) throws ConfigException, IllegalArgumentException {
        return findList(key, listTypes).orElseThrow(throwFor(key, stringifyAcceptedListTypes(listTypes)));
    }

    private static Supplier<? extends ConfigException> throwFor(String key, String type) {
        return ()->new ConfigException("Expected " + type + " at " + key + ", found nothing");
    }
}
