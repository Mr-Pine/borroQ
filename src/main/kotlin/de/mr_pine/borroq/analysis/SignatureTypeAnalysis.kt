package de.mr_pine.borroq.analysis

import de.mr_pine.borroq.isConstructor
import de.mr_pine.borroq.isStatic
import de.mr_pine.borroq.types.Mutability
import de.mr_pine.borroq.types.SignatureType
import de.mr_pine.borroq.types.SignatureType.ArgumentType.Companion.ReleaseMode
import org.checkerframework.javacutil.ElementUtils
import javax.lang.model.element.ExecutableElement
import javax.lang.model.type.TypeKind

class SignatureTypeAnalysis {

    private val signatureCache: MutableMap<String, SignatureType> = mutableMapOf()

    fun getType(method: ExecutableElement): SignatureType {
        val fqn = ElementUtils.getQualifiedName(method)

        return signatureCache.getOrPut(fqn, defaultValue = { calculateType(method) })
    }

    private fun calculateType(method: ExecutableElement): SignatureType {
        val returnPermission =
            if ((method.returnType.kind.isPrimitive || method.returnType.kind == TypeKind.VOID) && !method.isConstructor) {
                null
            } else {
                val annotations = method.returnType.annotationMirrors
                Mutability.fromAnnotations(annotations) ?: Mutability.Companion.Defaults.returnType
            }

        val parameterTypes = method.parameters.map { arg ->
            val typeAnnotations = arg.asType().annotationMirrors

            val mutability = Mutability.fromAnnotations(typeAnnotations) ?: Mutability.Companion.Defaults.parameter
            val releaseMode =
                ReleaseMode.fromAnnotations(typeAnnotations) ?: throw IllegalStateException("No release mode specified")

            SignatureType.ArgumentType(mutability, releaseMode)
        }

        val receiverType = if (method.isStatic || method.isConstructor) {
            null
        } else {
            val receiverMutability =
                Mutability.fromAnnotations(method.receiverType.annotationMirrors)
                    ?: Mutability.Companion.Defaults.parameter
            val receiverReleaseMode = ReleaseMode.fromAnnotations(method.receiverType.annotationMirrors)
                ?: throw IllegalStateException("No receiver release mode specified specified for ${method.simpleName}")
            SignatureType.ArgumentType(receiverMutability, receiverReleaseMode)
        }

        return SignatureType(returnPermission, receiverType, parameterTypes)
    }
}