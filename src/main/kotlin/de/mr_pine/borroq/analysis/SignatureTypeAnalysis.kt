package de.mr_pine.borroq.analysis

import de.mr_pine.borroq.BorroQChecker
import de.mr_pine.borroq.analysis.stub.StubManager
import de.mr_pine.borroq.analysis.stub.StubManager.StubOptions.Companion.stubOptions
import de.mr_pine.borroq.isConstructor
import de.mr_pine.borroq.isStatic
import de.mr_pine.borroq.types.Mutability
import de.mr_pine.borroq.types.SignatureType
import de.mr_pine.borroq.types.SignatureType.ArgumentType.Companion.ReleaseMode
import org.checkerframework.com.github.javaparser.ast.body.ConstructorDeclaration
import org.checkerframework.com.github.javaparser.ast.body.MethodDeclaration
import org.checkerframework.com.github.javaparser.ast.body.TypeDeclaration
import org.checkerframework.javacutil.ElementUtils
import javax.lang.model.element.ExecutableElement
import javax.lang.model.type.TypeKind

class SignatureTypeAnalysis(checker: BorroQChecker) {

    private val signatureCache: MutableMap<String, SignatureType> = mutableMapOf()

    init {
        val stubManager = StubManager(this, checker.processingEnvironment, checker.elementUtils, checker.stubOptions)
        stubManager.parseStubFiles()
    }

    fun getType(method: ExecutableElement): SignatureType {
        val qualifiedName =
            ElementUtils.getQualifiedName(method) // This isn't really a qualified method name (I'm not even sure such a thing exists)

        return signatureCache.getOrPut(qualifiedName, defaultValue = { calculateType(method) })
    }

    fun getType(constructor: ConstructorDeclaration): SignatureType {
        val parent = constructor.parentNode.get() as TypeDeclaration<*>
        val fqnParentName = parent.fullyQualifiedName.get()
        val fqd = "$fqnParentName.${constructor.toDescriptor()}"

        TODO()
    }

    fun getType(method: MethodDeclaration): SignatureType {
        TODO()
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