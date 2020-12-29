package com.jacky8399.worstshop.editor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Format {
    /**
     * Returns the regex used to check for input.
     * @return the regex used to check for input
     */
    String value();
}
