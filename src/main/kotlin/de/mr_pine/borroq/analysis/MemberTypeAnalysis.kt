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
import de.mr_pine.borroq.types.SignatureType.ParameterType
import de.mr_pine.borroq.types.specifiers.Mutability
import de.mr_pine.borroq.types.specifiers.Scope
import org.checkerframework.com.github.javaparser.ast.body.*
import org.checkerframework.com.github.javaparser.ast.expr.AnnotationExpr
import org.checkerframework.com.github.javaparser.ast.expr.MarkerAnnotationExpr
import org.checkerframework.dataflow.cfg.node.MethodAccessNode
import org.checkerframework.javacutil.AnnotationBuilder
import org.checkerframework.javacutil.ElementUtils
import javax.lang.model.element.*
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
                val mappedReturnMutability = Mutability.fromAnnotations(annotations)
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
        val returnMutability = Mutability.fromAnnotations(returnAnnotations)

        val receiverType = if (!callable.isStatic && !callable.isConstructorDeclaration) {
            val receiver =
                callable.receiverParameter.getOrNull() ?: throw IllegalStateException("No receiver parameter specified")
            val annotations = receiver.annotations.annotationElements(annotations)
            val mutability = Mutability.fromAnnotations(annotations)
                ?: throw IllegalStateException("No mutability specified for receiver of ${callable.name.asString()}")
            val scope = Scope.fromAnnotationsOnType(annotations, parentElement.asType(), elements)
            SignatureType.ParameterType(mutability, scope)
        } else {
            null
        }

        val parameterTypes = callable.parameters.zip(element.parameters).map { (stubParam, elemParam) ->
            if (stubParam.type.isPrimitiveType) return@map null

            val parameterAnnotations = stubParam.annotations.annotationElements(annotations)
            val mutability = Mutability.fromAnnotations(parameterAnnotations)
                ?: throw IllegalStateException("No mutability specified")
            val scope = Scope.fromAnnotationsOnType(parameterAnnotations, elemParam.asType(), elements)
            SignatureType.ParameterType(mutability, scope)
        }
        val signatureType = SignatureType(returnMutability, receiverType, parameterTypes)
        signatureCache[element] = signatureType
        return signatureType
    }

    private fun calculateType(
        executable: ExecutableElement, exceptionReportingContext: (() -> Unit) -> Unit
    ): SignatureType {
        val methodType = executable.asType() as Type.MethodType
        executable.simpleName.toString()

        val returnMutability = if (executable.isConstructor) {
            Mutability.fromAnnotations(
                (if (executable.isConstructor) {
                    (executable as Symbol).rawTypeAttributes.filter { it.position.type == TargetType.METHOD_RETURN }
                } else null)!!)
                ?: DefaultInference.inferReturnMutability(true)
        } else if (methodType.restype.kind.isPrimitive || methodType.restype.kind == TypeKind.VOID) {
            null
        } else {
            val annotations = methodType.restype.annotationMirrors
            Mutability.fromAnnotations(annotations)
                ?: DefaultInference.inferReturnMutability(false)
        }

        val parameterTypes = methodType.argtypes.toList().map { argType ->
            if (argType.kind.isPrimitive) {
                return@map null
            }
            val typeAnnotations = argType.annotationMirrors

            val mutability = Mutability.fromAnnotations(typeAnnotations)
                ?: DefaultInference.inferParameterMutability(executable.isConstructor)
            val scope = Scope.fromAnnotationsOnType(typeAnnotations, argType, elements)

            ParameterType(mutability, scope)
        }

        val receiverType = if (executable.isStatic || executable.isConstructor) {
            null
        } else {
            val recvType = methodType.recvtype ?: executable.enclosingElement.asType()
            val annotations = recvType?.annotationMirrors
            val mutability =
                annotations?.let { Mutability.fromAnnotations(it) } ?: DefaultInference.inferReceiverMutability()
            val scope = annotations?.let { Scope.fromAnnotationsOnType(it, recvType, elements) }
                ?: DefaultInference.inferDefaultScope(recvType, elements)
            ParameterType(mutability, scope)
        }

        return SignatureType(returnMutability, receiverType, parameterTypes)
    }

    fun getFieldMutability(field: VariableElement): Mutability? {
        if (field.asType().kind.isPrimitive) return null // TODO: Ensure no annotation
        val fieldMutability = fieldCache.getOrPut(field) {
            val annotations = field.asType().annotationMirrors
            Mutability.fromAnnotations(annotations)
                ?: throw IllegalStateException("No mutability for field ${field.simpleName} specified")
        }
        if (field.modifiers.contains(Modifier.STATIC)) {
            require(fieldMutability == Mutability.IMMUTABLE) { "Static field ${field.simpleName} must be immutable" }
        }
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
            Mutability.fromAnnotations(annotations)
                ?: throw IllegalStateException("No mutability for field $field specified")
        }
        if (field.isStatic) {
            require(fieldMutability == Mutability.IMMUTABLE) { "Static field $field must be immutable" }
        }
    }

    private fun List<AnnotationExpr>.annotationElements(importMap: StubManager.ImportMap) = mapNotNull { annot ->
        val simpleName = annot.nameAsString
        val typeElement = importMap[simpleName] ?: elements.getTypeElement(simpleName) ?: elements.getTypeElement(
            "java.lang.$simpleName"
        )
        val annotation = when (annot) {
            is MarkerAnnotationExpr -> AnnotationBuilder.fromName(elements, typeElement.qualifiedName)!!
            else -> TODO("Not a marker expr") // TODO: Currently there are no values, but there will be
        }
        annotation
    }
}