package com.jacky8399.worstshop.editor;

import java.lang.annotation.*;

/**
 * Denotes an editable class with a default {@link EditableAdaptor}
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Editable {
    /**
     * Returns the translation key of this item. <br>
     * The prefix "worstshop.editor.items." will be automatically attached.
     * @return the translation key of this item
     */
    String value();
}
