@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package de.mr_pine.borroq.analysis

import com.sun.tools.javac.code.Symbol
import com.sun.tools.javac.code.TargetType
import de.mr_pine.borroq.BorroQChecker
import de.mr_pine.borroq.analysis.stub.StubElementPrinter
import de.mr_pine.borroq.analysis.stub.StubManager
import de.mr_pine.borroq.analysis.stub.StubManager.StubOptions.Companion.stubOptions
import de.mr_pine.borroq.isConstructor
import de.mr_pine.borroq.isStatic
import de.mr_pine.borroq.types.SignatureType
import de.mr_pine.borroq.types.specifiers.Mutability
import de.mr_pine.borroq.types.specifiers.ReleaseMode
import org.checkerframework.com.github.javaparser.ast.body.CallableDeclaration
import org.checkerframework.com.github.javaparser.ast.body.ConstructorDeclaration
import org.checkerframework.com.github.javaparser.ast.body.MethodDeclaration
import org.checkerframework.com.github.javaparser.ast.body.TypeDeclaration
import org.checkerframework.com.github.javaparser.ast.expr.AnnotationExpr
import org.checkerframework.com.github.javaparser.ast.expr.MarkerAnnotationExpr
import org.checkerframework.javacutil.AnnotationBuilder
import org.checkerframework.javacutil.ElementUtils
import org.checkerframework.javacutil.TypesUtils
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.TypeKind

class MemberTypeAnalysis(checker: BorroQChecker) {

    private val signatureCache: MutableMap<ExecutableElement, SignatureType> = mutableMapOf()
    private val fieldCache: MutableMap<VariableElement, Mutability> = mutableMapOf()

    private val elements = checker.elementUtils

    init {
        val stubManager = StubManager(this, checker.processingEnvironment, elements, checker.stubOptions)
        stubManager.parseStubFiles()
    }

    fun getType(method: ExecutableElement, exceptionReportingContext: (() -> Unit) -> Unit): SignatureType {
        return signatureCache.getOrPut(method, defaultValue = { calculateType(method, exceptionReportingContext) })
    }

    fun getType(callable: CallableDeclaration<*>, annotations: StubManager.ImportMap): SignatureType? {
        val parent = callable.parentNode.get() as TypeDeclaration<*>
        val fqnParentName = parent.fullyQualifiedName.get()
        val parentElement = elements.getTypeElement(fqnParentName) ?: return null
        val simpleSignature = StubElementPrinter.print(callable)

        val identicalSimpleCallable = parentElement.enclosedElements.filter {
            it.kind == when (callable) {
                is MethodDeclaration -> ElementKind.METHOD
                is ConstructorDeclaration -> ElementKind.CONSTRUCTOR
                else -> throw IllegalStateException("Unexpected callable type: ${callable.javaClass}")
            }
        }.filterIsInstance<ExecutableElement>().filter { it.parameters.size == callable.parameters.size }
            .filter { ElementUtils.getSimpleSignature(it) == simpleSignature }
        require(identicalSimpleCallable.size <= 1) {
            "Multiple or no constructors with identical signature found: $simpleSignature"
        }
        val element = identicalSimpleCallable.firstOrNull() ?: return null // No matching constructor found

        fun List<AnnotationExpr>.annotationElements() = mapNotNull { annot ->
            val simpleName = annot.nameAsString
            val typeElement = annotations[simpleName] ?: elements.getTypeElement(simpleName) ?: elements.getTypeElement(
                "java.lang.$simpleName"
            )
            val annotation = when (annot) {
                is MarkerAnnotationExpr -> AnnotationBuilder.fromName(elements, typeElement.qualifiedName)!!
                else -> null // TODO: Currently there are no values, but there will be
            }
            annotation
        }

        val returnAnnotations = callable.annotations.annotationElements()
        val returnMutability = Mutability.fromAnnotationsOnType(returnAnnotations, parentElement)
        require(returnMutability?.onPaths == null) { "Return mutability annotation cannot be restricted on paths" }

        if (!callable.isStatic && !callable.isConstructorDeclaration) {
            TODO("Non-static stubs")
        }

        val parameterTypes = callable.parameters.map {
            if (it.type.isPrimitiveType) return@map null

            val parameterAnnotations = it.annotations.annotationElements()
            val mutability =
                Mutability.fromAnnotationsOnType(parameterAnnotations, parentElement)?.also { it.checkForConflicts() }
                    ?: return null // throw IllegalStateException("No mutability specified")
            val releaseMode =
                ReleaseMode.fromAnnotationsOnType(parameterAnnotations, parentElement)?.also { it.checkForConflicts() }
                    ?: return null // throw IllegalStateException("No release mode specified")
            SignatureType.ParameterType(mutability, releaseMode)
        }
        val signatureType = SignatureType(returnMutability, null, parameterTypes)
        signatureCache[element] = signatureType
        return signatureType
    }

    private fun calculateType(
        executable: ExecutableElement, exceptionReportingContext: (() -> Unit) -> Unit
    ): SignatureType {
        val returnPermission = if (executable.isConstructor) {
            executable as Symbol
            val annotations = executable.rawTypeAttributes.filter { it.position.type == TargetType.METHOD_RETURN }
            Mutability.fromAnnotationsOnType(annotations, null)
                ?.also { exceptionReportingContext { it.checkForConflicts() } }
                ?: throw IllegalStateException("No mutability specified") // Mutability.Companion.Defaults.returnType
        } else if (executable.returnType.kind.isPrimitive || executable.returnType.kind == TypeKind.VOID) {
            null
        } else {
            val annotations = executable.returnType.annotationMirrors
            Mutability.fromAnnotationsOnType(annotations, null)
                ?.also { exceptionReportingContext { it.checkForConflicts() } }
                ?: throw IllegalStateException("No mutability specified") // Mutability.Companion.Defaults.returnType
        }

        val parameterTypes = executable.parameters.map { arg ->
            if (arg.asType().kind.isPrimitive) {
                return@map null
            }
            val typeAnnotations = arg.asType().annotationMirrors

            val baseTypeElement = TypesUtils.getTypeElement(arg.asType())

            val mutability = Mutability.fromAnnotationsOnType(typeAnnotations, baseTypeElement)
                ?.also { exceptionReportingContext { it.checkForConflicts() } }
                ?: throw IllegalStateException("No mutability specified")
            val releaseMode = ReleaseMode.fromAnnotationsOnType(typeAnnotations, baseTypeElement)
                ?.also { exceptionReportingContext { it.checkForConflicts() } }
                ?: throw IllegalStateException("No release mode specified")

            SignatureType.ParameterType(mutability, releaseMode)
        }

        val receiverType = if (executable.isStatic || executable.isConstructor) {
            null
        } else {
            val baseTypeElement = TypesUtils.getTypeElement(executable.receiverType)
            val receiverMutability = Mutability.fromAnnotationsOnType(
                executable.receiverType.annotationMirrors, baseTypeElement
            )?.also { exceptionReportingContext { it.checkForConflicts() } }
                ?: throw IllegalStateException("No receiver mutability specified specified for ${executable.simpleName}")
            val receiverReleaseMode =
                ReleaseMode.fromAnnotationsOnType(executable.receiverType.annotationMirrors, baseTypeElement)
                    ?.also { exceptionReportingContext { it.checkForConflicts() } }
                    ?: throw IllegalStateException("No receiver release mode specified specified for ${executable.simpleName}")
            SignatureType.ParameterType(receiverMutability, receiverReleaseMode)
        }

        return SignatureType(returnPermission, receiverType, parameterTypes)
    }

    fun getFieldMutability(field: VariableElement): Mutability? {
        if (field.asType().kind.isPrimitive) return null
        val fieldMutability = fieldCache.getOrPut(field) {
            val annotations = field.asType().annotationMirrors
            Mutability.fromAnnotationsOnType(annotations, TypesUtils.getTypeElement(field.asType()))
                ?: throw IllegalStateException("No mutability for field specified")
        }
        require(fieldMutability.onPaths == null) { "Field mutability annotation cannot be restricted on paths" }
        return fieldMutability
    }
}