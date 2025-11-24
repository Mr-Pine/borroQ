package de.mr_pine.borroq

import javax.lang.model.element.ExecutableElement

val ExecutableElement.isStatic
    get() = modifiers.contains(javax.lang.model.element.Modifier.STATIC)
val ExecutableElement.isConstructor
    get() = kind == javax.lang.model.element.ElementKind.CONSTRUCTOR