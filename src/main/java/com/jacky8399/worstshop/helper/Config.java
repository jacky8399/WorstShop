package com.jacky8399.worstshop.helper;

import com.jacky8399.worstshop.WorstShop;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.*;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Conceals ugly casts
 */
@ParametersAreNonnullByDefault
public final class Config {
    private static final Logger logger = WorstShop.get().logger;

    private final Map<String, Object> backingMap;
    public final Config parent;
    public final String path;
    public Config(Map<String, Object> map, @Nullable Config parent, @Nullable String path) {
        backingMap = new LinkedHashMap<>(map);
        this.parent = parent;
        path = path == null ? "?" : path;
        this.path = parent != null ? parent.path + " > " + path : path;
    }

    public Config(Map<String, Object> map) {
        this(map, null, null);
    }

    public Map<String, Object> getPrimitiveMap() {
        return backingMap;
    }

    public final Set<String> getKeys() {
        return backingMap.keySet();
    }

    @SuppressWarnings("unchecked")
    private <T> T handleObj(String path, Object obj, Class<? extends T> clazz) {
        if (clazz.isInstance(obj)) {
            // does it matter? idk
            return (T) obj;
        } else if (clazz.isEnum() && obj instanceof String) {
            @SuppressWarnings("rawtypes")
            T enumObj = (T) ConfigHelper.parseEnum((String) obj, (Class) clazz);
            return enumObj;
        } else if (Config.class == clazz) {
            if (obj instanceof Map<?, ?>) {
                return (T) new Config((Map<String, Object>) obj, this, path);
            }
        } else if (Number.class.isAssignableFrom(clazz) && obj instanceof Number) {
            // numbers huh
            Number num = (Number) obj;
            if (clazz == Integer.class) {
                if (!(obj instanceof Integer))
                    logger.warning("number \"" + num + "\" (@" + this.path + " > " + path + ") was rounded down to an integer.");
                return (T) Integer.valueOf(num.intValue());
            } else if (clazz == Double.class)
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

    public final boolean has(String key) {
        return backingMap.containsKey(key);
    }

    public final boolean has(String key, Class<?> clazz1, Class<?>... clazzOthers) {
        return tryFind(key, clazz1, clazzOthers).isPresent();
    }

    /**
     * Try to find an object of type {@code T} with key {@code key}. <br>
     * Does not throw an exception if object is not found or of an incompatible type
     * @see #find(String, Class, Class[])
     * @see #get(String, Class, Class[])
     */
    @SafeVarargs
    public final <T> Optional<T> tryFind(String key, Class<? extends T> clazz1, Class<? extends T>... clazzOthers) {
        Class<? extends T>[] classes = getClasses(clazz1, clazzOthers);
        Object obj = backingMap.get(key);
        if (obj == null) {
            return Optional.empty();
        }
        for (Class<? extends T> clazz : classes) {
            // special classes
            T val = handleObj(key, obj, clazz);
            if (val != null)
                return Optional.of(val);
        }
        return Optional.empty();
    }

    /**
     * Try to find an object of type {@code T} with key {@code key}. <br>
     * Does not throw an exception if object is not found
     * @exception ConfigException thrown when object is of an incompatible type
     * @see #tryFind(String, Class, Class[])
     * @see #get(String, Class, Class[])
     */
    @SafeVarargs
    public final <T> Optional<T> find(String key, Class<? extends T> clazz1, Class<? extends T>... clazzOthers) throws ConfigException {
        Class<? extends T>[] classes = getClasses(clazz1, clazzOthers);
        Object obj = backingMap.get(key);
        if (obj == null) {
            return Optional.empty();
        }
        for (Class<? extends T> clazz : classes) {
            // special classes
            T val = handleObj(key, obj, clazz);
            if (val != null)
                return Optional.of(val);
        }
        throw new ConfigException("Expected " + stringifyTypes(classes) + " at " + key + ", found " + obj.getClass().getSimpleName(), this);
    }

    /**
     * Find an object of type {@code T} with key {@code key}.
     * @exception ConfigException thrown when object is not found or of an incompatible type
     * @see #tryFind(String, Class, Class[])
     * @see #find(String, Class, Class[])
     */
    @SafeVarargs
    public final <T> T get(String key, Class<? extends T> clazz1, Class<? extends T>... classes) throws ConfigException {
        return find(key, clazz1, classes).orElseThrow(throwFor(key, stringifyTypes(classes)));
    }

    @SafeVarargs
    public final <T> Optional<List<? extends T>> findList(String key, Class<? extends T> listType1, Class<? extends T>... listTypes) throws ConfigException {
        Class<? extends T>[] classes = getClasses(listType1, listTypes);
        return find(key, List.class)
                .map(list -> (List<?>) list)
                .map(list -> {
                    List<T> newList = new ArrayList<>();
                    ListIterator<?> listIterator = list.listIterator();
                    while (listIterator.hasNext()) {
                        int index = listIterator.nextIndex();
                        Object child = listIterator.next();
                        T newChild = null;
                        for (Class<? extends T> clazz : classes) {
                            newChild = handleObj("[" + index + "]", child, clazz);
                            if (newChild != null) {
                                newList.add(newChild);
                                break;
                            }
                        }
                        if (newChild == null)
                            throw new ConfigException("Expected " + stringifyListTypes(classes) + " at " + key +
                                    ", but found " + child.getClass().getSimpleName() + " at " + nameOrdinal(index + 1) + " element", this);
                    }
                    return newList;
                });
    }

    @SafeVarargs
    public final <T> List<? extends T> getList(String key, Class<? extends T> listType1, Class<? extends T>... listTypes) throws ConfigException {
        return findList(key, listType1, listTypes).orElseThrow(throwFor(key, stringifyListTypes(listTypes)));
    }

    private Supplier<? extends ConfigException> throwFor(String key, String type) {
        return ()->new ConfigException("Expected " + type + " at " + key + ", found nothing", this);
    }
}
