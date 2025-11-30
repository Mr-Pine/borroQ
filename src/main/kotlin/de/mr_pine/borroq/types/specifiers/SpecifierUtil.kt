package de.mr_pine.borroq.types.specifiers

import de.mr_pine.borroq.types.PathTail
import org.checkerframework.javacutil.ElementUtils
import org.checkerframework.javacutil.TypesUtils
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement

fun pathsFromAnnotationValueOnType(annotation: AnnotationMirror, type: TypeElement?): List<PathTail>? {
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

    return onPaths
}