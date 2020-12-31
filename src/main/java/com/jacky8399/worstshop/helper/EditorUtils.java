package com.jacky8399.worstshop.helper;

import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.editor.*;
import com.jacky8399.worstshop.editor.DefaultAdaptors.*;
import org.bukkit.craftbukkit.libs.org.apache.commons.lang3.ClassUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;

public class EditorUtils {
    @SuppressWarnings("unchecked")
    public static <T> EditableAdaptor<T> findAdaptorForField(Object parent, Field field) {
        EditableAdaptor<?> adaptor = null;
        if (field.isAnnotationPresent(Adaptor.class)) {
            Adaptor annotation = field.getAnnotation(Adaptor.class);
            Class<? extends EditableAdaptor<?>> adaptorClazz = annotation.value();
            try {
                adaptor = createAdaptor(adaptorClazz, field.get(parent));
            } catch (IllegalAccessException e) {
                throw new Error(e);
            }
        } else {
            Class<?> clazz = field.getType();
            try {
                adaptor = findAdaptorForClass(clazz, field.get(parent));
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
        if (field.isAnnotationPresent(Representation.class)) {
            Representation repr = field.getAnnotation(Representation.class);
            adaptor = new CustomRepresentationAdaptor<>(adaptor, repr);
        }
        return (EditableAdaptor<T>) adaptor;
    }

    @SuppressWarnings("unchecked")
    public static <T> EditableAdaptor<T> findAdaptorForClass(Class<? extends T> clazz, T instance) {
        EditableAdaptor<T> adaptor = null;
        if (clazz.isAnnotationPresent(Editable.class)) {
            // class is editable
            if (clazz.isAnnotationPresent(Adaptor.class)) {
                Adaptor annotation = clazz.getAnnotation(Adaptor.class);
                Class<? extends EditableAdaptor<?>> adaptorClazz = annotation.value();
                return (EditableAdaptor<T>) createAdaptor(adaptorClazz, instance);
            } else {
                // embed
            }
        } else if (ClassUtils.isPrimitiveOrWrapper(clazz) || clazz == String.class) {
            // primitive adaptors
            if (clazz == Boolean.class || clazz == boolean.class) {
                adaptor = (EditableAdaptor<T>) new BooleanAdaptor();
            } else if (clazz == Integer.class || clazz == int.class) {
                adaptor = (EditableAdaptor<T>) new IntegerAdaptor();
            } else if (clazz == String.class) {
                adaptor = (EditableAdaptor<T>) new StringAdaptor();
            } // TODO finish all primitives
        }
        // check for annotations
        if (adaptor instanceof TextAdaptor<?> && clazz.isAnnotationPresent(Format.class)) {
            Format format = clazz.getAnnotation(Format.class);
            ((TextAdaptor<?>) adaptor).setFormat(format.value());
        }
        if (clazz.isAnnotationPresent(Representation.class)) {
            Representation repr = clazz.getAnnotation(Representation.class);
            adaptor = new CustomRepresentationAdaptor<>(adaptor, repr);
        }
        return adaptor;
    }

    public static <T extends EditableAdaptor<?>> T createAdaptor(Class<T> clazz, Object editable) {
        if (clazz.isMemberClass() && !Modifier.isStatic(clazz.getModifiers())) {
            // instantiate inner class
            try {
                Constructor<T> ctor = clazz.getDeclaredConstructor(clazz.getDeclaringClass());
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
                return ctor.newInstance();
            } catch (InvocationTargetException e) {
                WorstShop.get().logger.severe("Failed to initialize adaptor " + clazz.getName());
                throw new RuntimeException(e);
            } catch (ReflectiveOperationException e) {
                WorstShop.get().logger.severe("Valid constructor for adaptor " + clazz.getName() + "does not exist?");
                throw new Error(e);
            }
        }
    }
}
