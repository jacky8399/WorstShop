package com.jacky8399.worstshop.helper;

import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.editor.Adaptor;
import com.jacky8399.worstshop.editor.DefaultAdaptors;
import com.jacky8399.worstshop.editor.EditableAdaptor;
import com.jacky8399.worstshop.editor.Format;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;

public class EditorUtils {
    EditableAdaptor<?> findAdaptor(Object parent, Field field) {
        if (field.isAnnotationPresent(Adaptor.class)) {
            Adaptor annotation = field.getAnnotation(Adaptor.class);
            Class<? extends EditableAdaptor<?>> adaptorClazz = annotation.value();
            try {
                return createAdaptor(adaptorClazz, field.get(parent));
            } catch (IllegalAccessException e) {
                throw new Error(e);
            }
        } else {
            Class<?> clazz = field.getType();
            // primitive adaptors
            EditableAdaptor<?> adaptor = null;
            if (clazz == Boolean.class || clazz == boolean.class) {
                adaptor = new DefaultAdaptors.BooleanAdaptor();
            } else if (clazz == Integer.class || clazz == int.class) {
                adaptor = new DefaultAdaptors.IntegerAdaptor();
            } else if (clazz == String.class) {
                adaptor = new DefaultAdaptors.StringAdaptor();
            } else {

            }
            // check for annotations
            if (adaptor instanceof DefaultAdaptors.TextAdaptor<?> && field.isAnnotationPresent(Format.class)) {
                Format format = field.getAnnotation(Format.class);
                ((DefaultAdaptors.TextAdaptor<?>) adaptor).setFormat(format.value());
            }
            return adaptor;
        }
    }

    <T extends EditableAdaptor<?>> T createAdaptor(Class<T> clazz, Object editable) {
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
