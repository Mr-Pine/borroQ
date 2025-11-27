package de.mr_pine.borroq.analysis

import de.mr_pine.borroq.BorroQChecker
import de.mr_pine.borroq.analysis.stub.StubElementPrinter
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
import org.checkerframework.com.github.javaparser.ast.expr.AnnotationExpr
import org.checkerframework.com.github.javaparser.ast.expr.MarkerAnnotationExpr
import org.checkerframework.javacutil.AnnotationBuilder
import org.checkerframework.javacutil.ElementUtils
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.type.TypeKind

class SignatureTypeAnalysis(checker: BorroQChecker) {

    private val signatureCache: MutableMap<ExecutableElement, SignatureType> = mutableMapOf()
    private val elements = checker.elementUtils

    init {
        val stubManager = StubManager(this, checker.processingEnvironment, elements, checker.stubOptions)
        stubManager.parseStubFiles()
    }

    context(annotations: StubManager.ImportMap)
    private fun List<AnnotationExpr>.annotationElements() = mapNotNull { annot ->
        val simpleName = annot.nameAsString
        val typeElement = annotations[simpleName] ?: elements.getTypeElement(simpleName) ?: elements.getTypeElement("java.lang.$simpleName")
        val annotation = when (annot) {
            is MarkerAnnotationExpr -> AnnotationBuilder.fromName(elements, typeElement.qualifiedName)!!
            else -> null // TODO: Currently there are no values, but there will be
        }
        annotation
    }

    fun getType(method: ExecutableElement): SignatureType {
        return signatureCache.getOrPut(method, defaultValue = { calculateType(method) })
    }

    fun getType(constructor: ConstructorDeclaration, annotations: StubManager.ImportMap): SignatureType? {
        val parent = constructor.parentNode.get() as TypeDeclaration<*>
        val fqnParentName = parent.fullyQualifiedName.get()
        val parentElement = elements.getTypeElement(fqnParentName) ?: return null
        val simpleSignature = StubElementPrinter.print(constructor)

        val identicalSimpleConstructors = parentElement.enclosedElements.filter { it.kind == ElementKind.CONSTRUCTOR }
            .filterIsInstance<ExecutableElement>().filter { it.parameters.size == constructor.parameters.size }
            .filter { ElementUtils.getSimpleSignature(it) == simpleSignature }
        require(identicalSimpleConstructors.size <= 1) {
            "Multiple or no constructors with identical signature found: $simpleSignature"
        }
        val element = identicalSimpleConstructors.firstOrNull() ?: return null // No matching constructor found

        val returnAnnotations = context(annotations) { constructor.annotations.annotationElements() }
        val returnMutability = Mutability.fromAnnotations(returnAnnotations)

        val parameterTypes = constructor.parameters.map {
            val parameterAnnotations = context(annotations) { it.annotations.annotationElements() }
            val mutability = Mutability.fromAnnotations(parameterAnnotations) ?: return null // throw IllegalStateException("No mutability specified")
            val releaseMode = ReleaseMode.fromAnnotations(parameterAnnotations) ?: return null // throw IllegalStateException("No release mode specified")
            SignatureType.ArgumentType(mutability, releaseMode)
        }
        val signatureType = SignatureType(returnMutability, null, parameterTypes)
        signatureCache[element] = signatureType
        return signatureType
    }

    fun getType(method: MethodDeclaration): SignatureType? {
        return null
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

            val mutability = Mutability.fromAnnotations(typeAnnotations) ?: throw IllegalStateException("No mutability specified")
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