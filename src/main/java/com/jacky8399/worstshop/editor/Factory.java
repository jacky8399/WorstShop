package com.jacky8399.worstshop.editor;


import org.jetbrains.annotations.NotNull;

import java.lang.annotation.*;
import java.util.function.Supplier;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Documented
@Inherited
public @interface Factory {
    Class<? extends Supplier<@NotNull ?>> value();
}
