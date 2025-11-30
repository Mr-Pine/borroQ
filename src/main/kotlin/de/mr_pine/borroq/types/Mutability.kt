package de.mr_pine.borroq.types

import org.checkerframework.javacutil.AnnotationUtils
import org.checkerframework.javacutil.ElementUtils
import org.checkerframework.javacutil.TypesUtils
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement

sealed interface Mutability {
    val permissionString: String

    data class Mutable(val onPaths: List<PathTail>?) : Mutability {
        override val permissionString = "mutable"
    }

    data class Immutable(val onPaths: List<PathTail>?) : Mutability {
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

            val paths = (annotation.elementValues.values.firstOrNull()?.value as List<*>?).orEmpty()

            if (paths.isNotEmpty() && type == null) {
                throw IllegalStateException("Paths are specified on return type")
            }

            val onPaths = paths.map { (it as AnnotationValue).value as String }.map {
                val fields =
                    it.removePrefix(".").split(".").runningFold(null) { previousField: VariableElement?, fieldName ->
                        val baseType = previousField?.asType()?.let(TypesUtils::getTypeElement) ?: type!!

                        ElementUtils.findFieldInType(baseType, fieldName)
                    }
                PathTail(fields.filterNotNull())
            }.takeIf { it.isNotEmpty() }

            return if (mutableAnnotation != null) Mutable(onPaths)
            else Immutable(onPaths)
        }
    }
}