package com.jacky8399.worstshop.editor;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD})
@Inherited
public @interface Adaptor {
    Class<? extends EditableAdaptor<?>> value();
}
