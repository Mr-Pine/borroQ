package de.mr_pine.borroq.types.specifiers

import de.mr_pine.borroq.types.PathTail
import org.checkerframework.javacutil.AnnotationUtils
import org.checkerframework.javacutil.ElementUtils
import org.checkerframework.javacutil.TypesUtils
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Elements

data class Scope(val includesBase: Boolean, val entries: List<PathTail>) {

    fun isInScope(pathTail: PathTail, type: TypeMirror, elements: Elements): Boolean {
        val outOfScope = outOfScopePaths(type, elements)
        return outOfScope.none { it.isPrefixOf(pathTail) }
    }

    fun outOfScopePaths(type: TypeMirror, elements: Elements): List<PathTail> = outOfScopePaths(
        TypesUtils.getTypeElement(type)
            ?: null!!,
        elements
    )

    fun outOfScopePaths(type: TypeElement, elements: Elements): List<PathTail> {
        val fields = ElementUtils.getAllFieldsIn(type, elements)
        val fullyOutOfScope =
            fields.filter { !it.asType().kind.isPrimitive }
                .filter { field -> entries.none { it.fields.first() == field } }.map { PathTail(listOf(it)) }
        val partialPaths = entries.groupBy({ it.fields.first() }) { it.fields.drop(1) }
            .mapValues { (_, tails) ->
                tails.map { tail ->
                    Scope(
                        false,
                        listOf(PathTail(tail))
                    ).takeIf { tail.isNotEmpty() }
                }
            }
            .mapValues { (field, scopesOnFields) ->
                scopesOnFields.flatMap { scope ->
                    scope?.outOfScopePaths(
                        field.asType(),
                        elements
                    ).orEmpty()
                }
            }
            .flatMap { (field, oosPaths) -> oosPaths.map { oosPath -> PathTail(listOf(field) + oosPath.fields) } }

        return fullyOutOfScope + partialPaths
    }

    companion object {
        fun full(type: TypeMirror, elements: Elements): Scope = Scope(
            true,
            Scope(false, emptyList()).outOfScopePaths(type, elements)
        )

        private fun pathsFromAnnotationValueOnType(annotation: AnnotationMirror, type: TypeMirror): List<PathTail> {
            val paths = (annotation.elementValues.values.firstOrNull()?.value as List<*>?).orEmpty()

            val onPaths = paths.map { (it as AnnotationValue).value as String }.map {
                val fields =
                    it.removePrefix(".").split(".").runningFold(null) { previousField: VariableElement?, fieldName ->
                        val baseType = previousField?.asType()?.let(TypesUtils::getTypeElement)
                            ?: TypesUtils.getTypeElement(type)!!

                        ElementUtils.findFieldInType(baseType, fieldName)
                    }
                PathTail(fields.filterNotNull())
            }

            return onPaths
        }

        fun fromAnnotationsOnType(
            annotations: Collection<AnnotationMirror>,
            type: TypeMirror,
            elements: Elements
        ): Scope {
            val scopeAnnotation =
                AnnotationUtils.getAnnotationByClass(annotations, de.mr_pine.borroq.qual.Scope::class.java)
                    ?: return full(type, elements)

            val pathTails = pathsFromAnnotationValueOnType(scopeAnnotation, type)

            return Scope(false, pathTails)
        }
    }
}
