package de.mr_pine.borroq.qual.mutability;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.TYPE_PARAMETER, ElementType.TYPE_USE})
public @interface Mutable {
    String[] value() default {};
}
