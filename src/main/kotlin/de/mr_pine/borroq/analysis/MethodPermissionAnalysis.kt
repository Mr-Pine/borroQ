package de.mr_pine.borroq.analysis

import de.mr_pine.borroq.types.MethodPermissions
import de.mr_pine.borroq.types.Mutability
import javax.lang.model.element.ExecutableElement
import javax.lang.model.type.TypeKind

class MethodPermissionAnalysis(val annotationQuery: AnnotationQuery) {
    fun getType(method: ExecutableElement): MethodPermissions {
        val returnPermission = if (method.returnType.kind.isPrimitive) {
            TODO("What if is primitive?")
        } else if (method.returnType.kind == TypeKind.VOID) {
            TODO("What if void?")
        } else {
            val annotations = method.returnType.annotationMirrors
            Mutability.fromAnnotations(annotations) ?: Mutability.Companion.Defaults.returnType
        }

        val parameterTypes = method.parameters.map { arg ->
            val annotations = arg.asType().annotationMirrors
            val mutability = Mutability.fromAnnotations(annotations) ?: Mutability.Companion.Defaults.parameter
            MethodPermissions.ArgumentPermissions(mutability)
        }

        return MethodPermissions(returnPermission, parameterTypes)
    }
}