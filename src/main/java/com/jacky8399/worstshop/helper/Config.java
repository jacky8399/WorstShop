package com.jacky8399.worstshop.helper;

import org.bukkit.configuration.MemorySection;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.*;
import java.util.function.Supplier;

/**
 * Conceals ugly casts
 */
@ParametersAreNonnullByDefault
public final class Config {
    private final Map<String, Object> backingMap;
    public final Config parent;
    public final String path;
    public Config(MemorySection section) {
        this(section.getValues(false), null, null);
    }

    public Config(Map<String, Object> map, @Nullable Config parent, @Nullable String path) {
        backingMap = new LinkedHashMap<>(map);
        this.parent = parent;
        path = path == null ? "?" : path;
        this.path = parent != null ? parent.path + "->" + path : path;
    }

    public Config(Map<String, Object> map) {
        this(map, null, null);
    }

    public Map<String, Object> getPrimitiveMap() {
        return backingMap;
    }

    @SuppressWarnings("unchecked")
    private <T> T handleObj(String path, Object obj, Class<? extends T> clazz) {
        if (clazz.isInstance(obj)) {
            // does it matter? idk
            return (T) obj;
        } else if (clazz.isEnum() && obj instanceof String) {
            @SuppressWarnings("rawtypes")
            T enumObj = (T) Enum.valueOf((Class) clazz, (String) obj);
            return enumObj;
        } else if (Config.class == clazz) {
            if (obj instanceof Map<?, ?>) {
                return (T) new Config((Map<String, Object>) obj, this, path);
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

    private static String stringifyTypes(Class<?>[] classes) {
        if (classes.length == 1) {
            return classes[0].getSimpleName();
        } else if (classes.length == 2) {
            return "either " + classes[0].getSimpleName() + " or " + classes[1].getSimpleName();
        } else {
            StringJoiner joiner = new StringJoiner("/");
            for (Class<?> clazz : classes) {
                joiner.add(clazz.getSimpleName());
            }
            return joiner.toString();
        }
    }
    private static String stringifyListTypes(Class<?>[] classes) {
        return "list of " + stringifyTypes(classes);
    }
    private static String nameOrdinal(int ordinal) {
        String str = Integer.toString(ordinal);
        if (str.endsWith("1") && ordinal != 11)
            return str + "st";
        else if (str.endsWith("2") && ordinal != 12)
            return str + "nd";
        else if (str.endsWith("3") && ordinal != 13)
            return str + "rd";
        return str + "th";
    }

    private static <T> Class<? extends T>[] getClasses(Class<? extends T> clazz1, Class<? extends T>[] clazzOthers) throws IllegalArgumentException {
        int length = 1 + clazzOthers.length;
        @SuppressWarnings("unchecked")
        Class<? extends T>[] arr = new Class[length];
        arr[0] = clazz1;
        if (length == 1)
            return arr;
        System.arraycopy(clazzOthers, 0, arr, 1, clazzOthers.length);
        return arr;
    }

    @SafeVarargs
    public final <T> Optional<T> find(String key, Class<? extends T> clazz1, Class<? extends T>... clazzOthers) throws ConfigException, IllegalArgumentException {
        Class<? extends T>[] classes = getClasses(clazz1, clazzOthers);
        Object obj = backingMap.get(key);
        if (obj == null) {
            return Optional.empty();
        }
        for (Class<? extends T> clazz : classes) {
            // special classes
            T val = handleObj(path, obj, clazz);
            if (val != null)
                return Optional.of(val);
        }
        throw new ConfigException("Expected " + stringifyTypes(classes) + " at " + key + ", found " + obj.getClass().getSimpleName(), this);
    }

    @SafeVarargs
    public final <T> T get(String key, Class<? extends T> clazz1, Class<? extends T>... classes) throws ConfigException, IllegalArgumentException {
        return find(key, clazz1, classes).orElseThrow(throwFor(key, stringifyTypes(classes)));
    }

    @SafeVarargs
    public final <T> Optional<List<? extends T>> findList(String key, Class<? extends T> listType1, Class<? extends T>... listTypes) throws ConfigException, IllegalArgumentException {
        Class<? extends T>[] classes = getClasses(listType1, listTypes);
        return find(key, List.class)
                .map(list -> (List<?>) list)
                .map(list -> {
                    List<T> newList = new ArrayList<>();
                    ListIterator<?> listIterator = list.listIterator();
                    while (listIterator.hasNext()) {
                        int index = listIterator.nextIndex();
                        Object child = listIterator.next();
                        for (Class<? extends T> clazz : classes) {
                            T newChild = handleObj("[" + index + "]", child, clazz);
                            if (newChild != null)
                                newList.add(newChild);
                            else
                                throw new ConfigException("Expected " + stringifyListTypes(classes) + " at " + key +
                                        ", but found " + child.getClass().getSimpleName() + " at " + nameOrdinal(index + 1) + " element", this);
                        }
                    }
                    return newList;
                });
    }

    @SafeVarargs
    public final <T> List<? extends T> getList(String key, Class<? extends T> listType1, Class<? extends T>... listTypes) throws ConfigException, IllegalArgumentException {
        return findList(key, listType1, listTypes).orElseThrow(throwFor(key, stringifyListTypes(listTypes)));
    }

    private Supplier<? extends ConfigException> throwFor(String key, String type) {
        return ()->new ConfigException("Expected " + type + " at " + key + ", found nothing", this);
    }
}
