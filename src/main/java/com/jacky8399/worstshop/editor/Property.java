package com.jacky8399.worstshop.editor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Property {
    /**
     * Returns the translation key of the description of this item. <br>
     * Prefix of enclosing class "worstshop.editor.property.&lt;clazz&gt;.&lt;value&gt;.desc" will be prepended. <br>
     * Defaults to the name of the field.
     * @return the translation key of this the description of this item
     */
    String value() default "";
}
