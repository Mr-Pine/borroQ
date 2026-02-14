package de.mr_pine.borroq.types.specifiers

import de.mr_pine.borroq.types.Rational
import org.checkerframework.javacutil.AnnotationUtils
import javax.lang.model.element.AnnotationMirror

enum class Mutability {
    MUTABLE,
    IMMUTABLE;

    val fraction: Rational
        get() = when (this) {
            MUTABLE -> Rational.ONE
            IMMUTABLE -> Rational.HALF
        }

    val permission: ArgPermission
        get() = when (this) {
            MUTABLE -> ArgPermission.MUTABLE
            IMMUTABLE -> ArgPermission.READABLE
        }

    companion object {
        fun fromAnnotations(annotations: Collection<AnnotationMirror>): Mutability? {
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
            } else if (immutableAnnotation != null) {
                IMMUTABLE
            } else {
                null
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