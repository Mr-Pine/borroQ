package de.mr_pine.borroq.types

import de.mr_pine.borroq.qual.mutability.Immutable
import de.mr_pine.borroq.qual.mutability.Mutable
import org.checkerframework.javacutil.AnnotationUtils
import javax.lang.model.element.AnnotationMirror

enum class Mutability(val permissionString: String) {
    MUTABLE("mutable"), IMMUTABLE("readable");

    companion object {
        fun fromAnnotations(annotations: Collection<AnnotationMirror>): Mutability? {
            val mutableAnnotation = AnnotationUtils.getAnnotationByClass(annotations, Mutable::class.java)
            val immutableAnnotation = AnnotationUtils.getAnnotationByClass(annotations, Immutable::class.java)

            if (mutableAnnotation != null && immutableAnnotation != null) {
                throw IllegalStateException("Element is annotated with both @Mutable and @Immutable")
            }

            return if (mutableAnnotation != null) MUTABLE
            else if (immutableAnnotation != null) IMMUTABLE
            else null
        }

        object Defaults {
            val split = IMMUTABLE
            val returnType = IMMUTABLE
            val parameter = IMMUTABLE
        }

    }
}