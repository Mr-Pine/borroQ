@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package de.mr_pine.borroq.analysis

import com.sun.tools.javac.code.Symbol
import com.sun.tools.javac.code.TargetType
import com.sun.tools.javac.code.Type
import de.mr_pine.borroq.BorroQChecker
import de.mr_pine.borroq.analysis.stub.StubElementPrinter
import de.mr_pine.borroq.analysis.stub.StubManager
import de.mr_pine.borroq.analysis.stub.StubManager.StubOptions.Companion.stubOptions
import de.mr_pine.borroq.isConstructor
import de.mr_pine.borroq.isStatic
import de.mr_pine.borroq.types.SignatureType
import de.mr_pine.borroq.types.specifiers.Mutability
import de.mr_pine.borroq.types.specifiers.ReleaseMode
import org.checkerframework.com.github.javaparser.ast.body.*
import org.checkerframework.com.github.javaparser.ast.expr.AnnotationExpr
import org.checkerframework.com.github.javaparser.ast.expr.MarkerAnnotationExpr
import org.checkerframework.dataflow.cfg.node.MethodAccessNode
import org.checkerframework.javacutil.AnnotationBuilder
import org.checkerframework.javacutil.ElementUtils
import org.checkerframework.javacutil.TypesUtils
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.TypeKind
import kotlin.jvm.optionals.getOrNull

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

    fun getType(methodAccess: MethodAccessNode, exceptionReportingContext: (() -> Unit) -> Unit): SignatureType {
        val unmappedMethodType = getType(methodAccess.method, exceptionReportingContext)
        val methodTypeHasArguments = methodAccess.type != methodAccess.method.asType()
        val receiverHasArguments =
            !methodAccess.isStatic && !methodAccess.method.isConstructor && (methodAccess.receiver.type as Type.ClassType).baseType() != methodAccess.method.enclosingElement.asType()
        return if (methodTypeHasArguments || receiverHasArguments) {
            // TODO: This checks only the return type, input types could only be relaxed by type params, so they aren't that important
            if (unmappedMethodType.returnMutability == null || methodAccess.method.isConstructor || methodAccess.method.returnType.kind.isPrimitive || methodAccess.method.returnType.kind == TypeKind.VOID) {
                unmappedMethodType
            } else {
                val annotations = (methodAccess.type as Type.MethodType).restype.annotationMirrors
                val mappedReturnMutability = Mutability.fromAnnotationsOnType(annotations, null)
                    ?.also { exceptionReportingContext { it.checkForConflicts() } }
                    ?: throw IllegalStateException("No mutability specified")
                val returnMutability = Mutability.lower(unmappedMethodType.returnMutability, mappedReturnMutability)
                unmappedMethodType.copy(returnMutability = returnMutability)
            }
        } else {
            unmappedMethodType
        }
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

        val returnAnnotations = callable.annotations.annotationElements(annotations)
        val returnMutability = Mutability.fromAnnotationsOnType(returnAnnotations, parentElement)
        require(returnMutability?.onPaths == null) { "Return mutability annotation cannot be restricted on paths" }

        val receiverType = if (!callable.isStatic && !callable.isConstructorDeclaration) {
            val receiver =
                callable.receiverParameter.getOrNull()
                    ?: throw IllegalStateException("No receiver parameter specified")
            val annotations = receiver.annotations.annotationElements(annotations)
            val mutability =
                Mutability.fromAnnotationsOnType(annotations, parentElement)?.also { it.checkForConflicts() }
                    ?: throw IllegalStateException("No mutability specified for receiver of ${callable.name.asString()}")
            val releaseMode =
                ReleaseMode.fromAnnotationsOnType(annotations, parentElement)?.also { it.checkForConflicts() }
                    ?: throw IllegalStateException("No release mode specified for receiver of ${callable.name.asString()}")
            SignatureType.ParameterType(mutability, releaseMode)
        } else {
            null
        }

        val parameterTypes = callable.parameters.map {
            if (it.type.isPrimitiveType) return@map null

            val parameterAnnotations = it.annotations.annotationElements(annotations)
            val mutability =
                Mutability.fromAnnotationsOnType(parameterAnnotations, parentElement)
                    ?.also { it.checkForConflicts() }
                    ?: throw IllegalStateException("No mutability specified")
            val releaseMode =
                ReleaseMode.fromAnnotationsOnType(parameterAnnotations, parentElement)
                    ?.also { it.checkForConflicts() }
                    ?: throw IllegalStateException("No release mode specified")
            SignatureType.ParameterType(mutability, releaseMode)
        }
        val signatureType = SignatureType(returnMutability, receiverType, parameterTypes)
        signatureCache[element] = signatureType
        return signatureType
    }

    private fun calculateType(
        methodType: Type.MethodType,
        methodName: String,
        isConstructor: Boolean,
        constructorTypeAnnotations: Collection<AnnotationMirror>?,
        isStatic: Boolean,
        exceptionReportingContext: (() -> Unit) -> Unit
    ): SignatureType {
        val returnMutability = if (isConstructor) {
            Mutability.fromAnnotationsOnType(constructorTypeAnnotations!!, null)
                ?.also { exceptionReportingContext { it.checkForConflicts() } }
                ?: throw IllegalStateException("No mutability specified for type of constructor $methodName$methodType") // Mutability.Companion.Defaults.returnType
        } else if (methodType.restype.kind.isPrimitive || methodType.restype.kind == TypeKind.VOID) {
            null
        } else {
            val annotations = methodType.restype.annotationMirrors
            Mutability.fromAnnotationsOnType(annotations, null)
                ?.also { exceptionReportingContext { it.checkForConflicts() } }
                ?: throw IllegalStateException("No mutability specified for return value of $methodName") // Mutability.Companion.Defaults.returnType
        }

        val parameterTypes = methodType.argtypes.toList().map { argType ->
            if (argType.kind.isPrimitive) {
                return@map null
            }
            val typeAnnotations = argType.annotationMirrors

            val baseTypeElement = TypesUtils.getTypeElement(argType)

            val mutability = Mutability.fromAnnotationsOnType(typeAnnotations, baseTypeElement)
                ?.also { exceptionReportingContext { it.checkForConflicts() } }
                ?: throw IllegalStateException("No mutability specified for parameter of $methodName of type $argType")
            val releaseMode = ReleaseMode.fromAnnotationsOnType(typeAnnotations, baseTypeElement)
                ?.also { exceptionReportingContext { it.checkForConflicts() } }
                ?: throw IllegalStateException("No release mode specified")

            SignatureType.ParameterType(mutability, releaseMode)
        }

        val receiverType = if (isStatic || isConstructor) {
            null
        } else {
            val baseTypeElement = TypesUtils.getTypeElement(
                methodType.recvtype
                    ?: throw IllegalStateException("No explicit receiver type specified for $methodName")
            )
            val receiverMutability = Mutability.fromAnnotationsOnType(
                methodType.recvtype.annotationMirrors, baseTypeElement
            )?.also { exceptionReportingContext { it.checkForConflicts() } }
                ?: throw IllegalStateException("No receiver mutability specified specified for $methodName")
            val receiverReleaseMode =
                ReleaseMode.fromAnnotationsOnType(methodType.recvtype.annotationMirrors, baseTypeElement)
                    ?.also { exceptionReportingContext { it.checkForConflicts() } }
                    ?: throw IllegalStateException("No receiver release mode specified specified for $methodName")
            SignatureType.ParameterType(receiverMutability, receiverReleaseMode)
        }

        return SignatureType(returnMutability, receiverType, parameterTypes)
    }

    private fun calculateType(
        executable: ExecutableElement, exceptionReportingContext: (() -> Unit) -> Unit
    ): SignatureType {
        return calculateType(
            executable.asType() as Type.MethodType,
            executable.simpleName.toString(),
            executable.isConstructor,
            if (executable.isConstructor) {
                (executable as Symbol).rawTypeAttributes.filter { it.position.type == TargetType.METHOD_RETURN }
            } else null,
            executable.isStatic,
            exceptionReportingContext
        )
    }

    fun getFieldMutability(field: VariableElement): Mutability? {
        if (field.asType().kind.isPrimitive) return null // TODO: Ensure no annotation
        val fieldMutability = fieldCache.getOrPut(field) {
            val annotations = field.asType().annotationMirrors
            Mutability.fromAnnotationsOnType(annotations, TypesUtils.getTypeElement(field.asType()))
                ?: throw IllegalStateException("No mutability for field ${field.simpleName} specified")
        }
        require(fieldMutability.onPaths == null) { "Field mutability annotation cannot be restricted on paths" }
        return fieldMutability
    }

    fun getFieldMutability(field: FieldDeclaration, importMap: StubManager.ImportMap) {
        val parent = field.parentNode.get() as TypeDeclaration<*>
        val fqnParentName = parent.fullyQualifiedName.get()
        val parentElement = elements.getTypeElement(fqnParentName)
            ?: throw IllegalStateException("Could not find parent element $fqnParentName of field $field")

        val fieldElement = ElementUtils.findFieldInType(parentElement, field.variables.single().nameAsString)
            ?: throw IllegalStateException("Could not find field ${field.variables.single().nameAsString} in parent element $fqnParentName")
        val fieldMutability = fieldCache.getOrPut(fieldElement) {
            val annotations = field.annotations.annotationElements(importMap)
            val fqTypeName = field.commonType.toClassOrInterfaceType().get().nameWithScope
            val fieldTypeElement = elements.getTypeElement(fqTypeName)
            Mutability.fromAnnotationsOnType(annotations, fieldTypeElement)
                ?: throw IllegalStateException("No mutability for field $field specified")
        }
        require(fieldMutability.onPaths == null) { "Field mutability annotation cannot be restricted on paths" }
    }

    private fun List<AnnotationExpr>.annotationElements(importMap: StubManager.ImportMap) = mapNotNull { annot ->
        val simpleName = annot.nameAsString
        val typeElement =
            importMap[simpleName] ?: elements.getTypeElement(simpleName) ?: elements.getTypeElement(
                "java.lang.$simpleName"
            )
        val annotation = when (annot) {
            is MarkerAnnotationExpr -> AnnotationBuilder.fromName(elements, typeElement.qualifiedName)!!
            else -> TODO("Not a marker expr") // TODO: Currently there are no values, but there will be
        }
        annotation
    }
}