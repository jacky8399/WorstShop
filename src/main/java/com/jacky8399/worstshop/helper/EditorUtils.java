package com.jacky8399.worstshop.helper;

import com.jacky8399.worstshop.i18n.I18n;
import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.editor.*;
import com.jacky8399.worstshop.editor.adaptors.*;
import com.jacky8399.worstshop.i18n.Translatable;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Collection;

public class EditorUtils {
    public static final String I18N_KEY = I18n.Keys.MESSAGES_KEY + "editor.property.";
    public static final Translatable VALUE_FORMAT = I18n.createTranslatable(I18N_KEY + "value-format");
    public static final Translatable NAME_FORMAT = I18n.createTranslatable(I18N_KEY + "name-format");

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> EditableAdaptor<T> findAdaptorForField(Object parent, Field field) {
        EditableAdaptor<?> adaptor;
        if (field.isAnnotationPresent(Adaptor.class)) {
            Adaptor annotation = field.getAnnotation(Adaptor.class);
            Class<? extends EditableAdaptor<?>> adaptorClazz = annotation.value();
            try {
                adaptor = createAdaptor((Class) adaptorClazz, field.get(parent));
            } catch (IllegalAccessException e) {
                throw new Error(e);
            }
        } else {
            Class<?> clazz = field.getType();
            try {
                adaptor = findAdaptorForClass((Class) clazz, field.get(parent));
            } catch (IllegalAccessException e) {
                throw new Error(e);
            }
        }
        // check for annotations
        if (field.isAnnotationPresent(Format.class)) {
            Format format = field.getAnnotation(Format.class);
            if (adaptor instanceof TextAdaptor) {
                ((TextAdaptor<?>) adaptor).setFormat(format.value());
            } else if (adaptor instanceof CustomRepresentationAdaptor &&
                    ((CustomRepresentationAdaptor<?>) adaptor).internal instanceof TextAdaptor) {
                // wow that's a lot of casts
                ((TextAdaptor<?>) ((CustomRepresentationAdaptor<?>) adaptor).internal).setFormat(format.value());
            }
        }
        if (adaptor instanceof ListAdaptor && field.isAnnotationPresent(Factory.class)) {
            Factory factory = field.getAnnotation(Factory.class);
            ((ListAdaptor<?>) adaptor).setFactory(factory.value());
        }
        if (field.isAnnotationPresent(Representation.class)) {
            Representation repr = field.getAnnotation(Representation.class);
            adaptor = new CustomRepresentationAdaptor<>(adaptor, repr);
        }
        return (EditableAdaptor<T>) adaptor;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Nullable
    public static <T> EditableAdaptor<T> findAdaptorForClass(Class<T> clazz, T instance) {
        EditableAdaptor<T> adaptor = null;
        if (clazz.isAnnotationPresent(Editable.class)) {
            // class is editable
            if (clazz.isAnnotationPresent(Adaptor.class)) {
                Adaptor annotation = clazz.getAnnotation(Adaptor.class);
                Class<? extends EditableAdaptor<T>> adaptorClazz = (Class<? extends EditableAdaptor<T>>) annotation.value();
                return createAdaptor(adaptorClazz, instance);
            } else {
                // embed
                adaptor = new EditableObjectAdaptor<>(clazz);
            }
        } else {
            // default adaptors
            if (clazz == Boolean.class || clazz == boolean.class) {
                adaptor = (EditableAdaptor<T>) new BooleanAdaptor();
            } else if (clazz == Integer.class || clazz == int.class) {
                adaptor = (EditableAdaptor<T>) new IntegerAdaptor();
            } else if (clazz == String.class) {
                adaptor = (EditableAdaptor<T>) new StringAdaptor();
            } else if (clazz.isEnum()) {
                adaptor = (EditableAdaptor<T>) new EnumAdaptor<>((Class)clazz);
            } else if (Collection.class.isAssignableFrom(clazz)) {
                adaptor = new ListAdaptor();
            } else if (clazz == ItemStack.class) {
                adaptor = (EditableAdaptor<T>) new ItemStackAdaptor();
            }
        }
        // check for annotations
        if (adaptor instanceof TextAdaptor<?> && clazz.isAnnotationPresent(Format.class)) {
            Format format = clazz.getAnnotation(Format.class);
            ((TextAdaptor<?>) adaptor).setFormat(format.value());
        }
        if (adaptor != null && clazz.isAnnotationPresent(Representation.class)) {
            Representation repr = clazz.getAnnotation(Representation.class);
            adaptor = new CustomRepresentationAdaptor<>(adaptor, repr);
        }
        return adaptor;
    }

    public static <V, T extends EditableAdaptor<V>> T createAdaptor(Class<T> clazz, V editable) {
        if (clazz.isMemberClass() && !Modifier.isStatic(clazz.getModifiers())) {
            // instantiate inner class
            try {
                Constructor<T> ctor = clazz.getDeclaredConstructor(clazz.getDeclaringClass());
                ctor.setAccessible(true);
                return ctor.newInstance(editable);
            } catch (InvocationTargetException e) {
                WorstShop.get().logger.severe("Failed to initialize adaptor " + clazz.getName() + " (inner class of " + clazz.getDeclaringClass().getName() + ")");
                throw new RuntimeException(e);
            } catch (ReflectiveOperationException e) {
                WorstShop.get().logger.severe("Empty constructor for adaptor " + clazz.getName() + " (inner class of " + clazz.getDeclaringClass().getName() + " does not exist?");
                throw new Error(e);
            }
        } else {
            try {
                Constructor<T> ctor = clazz.getConstructor();
                ctor.setAccessible(true);
                return ctor.newInstance();
            } catch (InvocationTargetException e) {
                WorstShop.get().logger.severe("Failed to initialize adaptor " + clazz.getName());
                throw new RuntimeException(e);
            } catch (ReflectiveOperationException e) {
                WorstShop.get().logger.severe("Valid constructor for adaptor " + clazz.getName() + " does not exist?");
                throw new Error(e);
            }
        }
    }

    public static String[] getDesc(@Nullable String parentName, @Nullable String fieldName) {
        return parentName != null && fieldName != null ? I18n.translate("worstshop.editor.property." + parentName + "." + fieldName + ".desc").split("\n") : null;
    }

    public static String translate(String key, Object... args) {
        return I18n.translate(I18N_KEY + key, args);
    }
}
