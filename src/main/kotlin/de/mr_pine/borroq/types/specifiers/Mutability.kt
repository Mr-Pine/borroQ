package de.mr_pine.borroq.types.specifiers

import org.checkerframework.javacutil.AnnotationUtils
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.TypeElement

enum class Mutability {
    MUTABLE,
    IMMUTABLE;

    companion object {
        fun fromAnnotationsOnType(annotations: Collection<AnnotationMirror>, type: TypeElement?): Mutability? {
            val mutableAnnotation =
                AnnotationUtils.getAnnotationByClass(annotations, de.mr_pine.borroq.qual.mutability.Mutable::class.java)
            val immutableAnnotation = AnnotationUtils.getAnnotationByClass(
                annotations,
                de.mr_pine.borroq.qual.mutability.Immutable::class.java
            )

            if (mutableAnnotation != null && immutableAnnotation != null) {
                throw IllegalStateException("Element is annotated with both @Mutable and @Immutable")
            }

            return if (mutableAnnotation != null) {
                MUTABLE
            } else {
                IMMUTABLE
            }
        }

        fun lower(first: Mutability, second: Mutability): Mutability {
            return when {
                first == MUTABLE && second == MUTABLE -> MUTABLE
                else -> IMMUTABLE
            }
        }
    }
}