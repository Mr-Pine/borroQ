package de.mr_pine.borroq.types.specifiers

import de.mr_pine.borroq.types.PathTail
import org.checkerframework.javacutil.AnnotationUtils
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.TypeElement

sealed interface Mutability {
    val permissionString: String
    val onPaths: List<PathTail>?

    data class Mutable(override val onPaths: List<PathTail>?) : Mutability {
        override val permissionString = "mutable"
    }

    data class Immutable(override val onPaths: List<PathTail>?) : Mutability {
        override val permissionString = "readable"
    }

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

            val annotation = mutableAnnotation ?: immutableAnnotation ?: return null
            val onPaths = pathsFromAnnotationValueOnType(annotation, type)

            return if (mutableAnnotation != null) Mutable(onPaths)
            else Immutable(onPaths)
        }
    }
}