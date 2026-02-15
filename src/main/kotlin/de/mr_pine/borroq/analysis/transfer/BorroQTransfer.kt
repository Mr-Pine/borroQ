@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package de.mr_pine.borroq.analysis.transfer

import com.sun.source.tree.Tree
import com.sun.tools.javac.code.Type
import com.sun.tools.javac.tree.JCTree
import de.mr_pine.borroq.BorroQChecker
import de.mr_pine.borroq.analysis.BorroQStore
import de.mr_pine.borroq.analysis.Configuration.BorroQExtensions.Extension
import de.mr_pine.borroq.analysis.DefaultInference
import de.mr_pine.borroq.analysis.exceptions.*
import de.mr_pine.borroq.analysis.livevariable.LiveVarStore
import de.mr_pine.borroq.isConstructor
import de.mr_pine.borroq.types.*
import de.mr_pine.borroq.types.IdentifiedPermission.Companion.withId
import de.mr_pine.borroq.types.specifiers.Mutability
import de.mr_pine.borroq.types.specifiers.Scope
import io.github.oshai.kotlinlogging.KotlinLogging
import org.checkerframework.dataflow.analysis.AnalysisResult
import org.checkerframework.dataflow.analysis.RegularTransferResult
import org.checkerframework.dataflow.analysis.UnusedAbstractValue
import org.checkerframework.dataflow.cfg.UnderlyingAST
import org.checkerframework.dataflow.cfg.block.Block
import org.checkerframework.dataflow.cfg.block.ExceptionBlock
import org.checkerframework.dataflow.cfg.node.*
import org.checkerframework.dataflow.expression.JavaExpression
import org.checkerframework.dataflow.expression.LocalVariable
import org.checkerframework.javacutil.TreeUtils
import org.checkerframework.javacutil.TypesUtils
import javax.lang.model.element.ExecutableElement
import javax.lang.model.type.ArrayType
import javax.lang.model.type.ReferenceType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import kotlin.contracts.ExperimentalContracts

private val logger = KotlinLogging.logger { }


class BorroQTransfer(
    private val signatureType: SignatureType,
    private val memberTypeAnalysis: de.mr_pine.borroq.analysis.MemberTypeAnalysis,
    private val liveness: AnalysisResult<UnusedAbstractValue, LiveVarStore>,
    private val checker: BorroQChecker,
    private val annotationQuery: de.mr_pine.borroq.analysis.AnnotationQuery,
    private val configuration: de.mr_pine.borroq.analysis.Configuration
) : BorroQNoopTransfer(liveness, checker, configuration) {

    lateinit var parameterIds: Map<LocalVariableNode, Id>

    private val Node.annotatedType: TypeMirror
        get() = when (val tree = tree) {
            is JCTree.JCIdent -> tree.sym
            is JCTree.JCVariableDecl -> tree.sym
            else -> null
        }?.asType() ?: type

    @OptIn(ExperimentalContracts::class)
    private inline fun silentExceptionReportContext(
        tree: Tree, block: () -> Unit
    ) {
        kotlin.contracts.contract {
            callsInPlace(block, kotlin.contracts.InvocationKind.AT_MOST_ONCE)
        }
        try {
            block()
        } catch (e: BorroQException) {
            context(checker, tree) {
                e.report()
            }
        }
    }

    @OptIn(ExperimentalContracts::class)
    private inline fun <R> exceptionReportContext(tree: Tree, block: () -> R): R {
        kotlin.contracts.contract {
            callsInPlace(block, kotlin.contracts.InvocationKind.EXACTLY_ONCE)
        }
        return try {
            block()
        } catch (e: BorroQException) {
            context(checker, tree) {
                e.report()
            }
            throw BorroQReportedException(e)
        }
    }

    override fun initialStore(
        underlyingAST: UnderlyingAST, parameters: List<LocalVariableNode>
    ): BorroQStore {
        parameterIds = parameters.associateWith { Id.fromNode(it) }

        if (parameters.size != 2) {
            configuration.borroQExtensions.requireExtension(
                Extension.ANY_PARAMETER_COUNT, underlyingAST.code!!, checker
            )
        }

        val parameterPermissions = parameters.zip(signatureType.parameters).mapNotNull { (parameter, parameterType) ->
            val permission = Permission((parameterType ?: return@mapNotNull null).mutability.fraction)
            val localVariable = JavaExpression.fromNode(parameter) as LocalVariable
            val id = parameterIds[parameter]!!

            localVariable to (permission.withId(id) as VariablePermission)
        }.toMap().toMutableMap()

        fun borrowsFromScope(id: Id, scope: Scope, type: TypeMirror, source: Tree): List<Borrow> {
            if (scope.entries.any { it.fields.size > 1 }) {
                configuration.borroQExtensions.requireExtension(Extension.NESTED_FIELD_ACCESS, source, checker)
            }
            if (!scope.includesBase) TODO("Base not in scope")

            val outOfScopePathTails = scope.outOfScopePaths(type, checker.elementUtils)
            val paths = outOfScopePathTails.map { Path(PathRoot.IdPathRoot(id), it) }

            return paths.map { Borrow(it, Rational.ONE, Borrow.Identifier.Dummy) }
        }

        val parameterBorrows = parameters.zip(signatureType.parameters).flatMap { (parameter, parameterType) ->
            if (parameterType == null) return@flatMap emptyList()
            val id =
                parameterPermissions[JavaExpression.fromNode(parameter) as LocalVariable]?.let { (it as IdentifiedPermission).id }
                    ?: return@flatMap emptyList()
            borrowsFromScope(id, parameterType.scope, parameter.type, parameter.tree!!)
        }
        val methodAST = underlyingAST as UnderlyingAST.CFGMethod

        val thisBorrows = if (signatureType.receiverType == null) emptyList() else {
            val returnType = TreeUtils.elementFromDeclaration(methodAST.classTree)
            borrowsFromScope(
                ThisId, signatureType.receiverType.scope, returnType.asType(), underlyingAST.code!!
            )
        }


        val thisFraction = signatureType.receiverType?.mutability?.fraction
            ?: if (TreeUtils.isConstructor(methodAST.method)) Rational.ONE else null
        val thisPermission = thisFraction?.let { IdentifiedPermission(it, ThisId) }

        return BorroQStore(
            checker,
            configuration,
            parameterPermissions,
            thisPermission,
            (parameterBorrows + thisBorrows).toMutableList()
        )
    }

    override fun visitNode(
        node: Node, p: Input
    ): Result {
        configuration.unknownSyntaxStrictness.reportUnknownSyntaxStrictness(checker, node)
        return node.regularResult(null, p.regularStore, false)
    }

    data class Pseudoarg(
        val mutability: Mutability,
        val scope: Scope,
        val borrowTarget: BorrowTarget,
        val argument: BorroQValue,
        val node: Node
    ) {
        enum class BorrowTarget {
            PERSISTENT, RETURN_VALUE
        }
    }

    data class Pseudocall(val returnMutability: Mutability, val arguments: List<Pseudoarg>)

    context(store: BorroQStore, tree: Tree)
    private fun processPseudoarg(pseudoarg: Pseudoarg, input: Input): List<BorroQValue.PseudocallResult.FreeBorrow> {
        if (pseudoarg.node is FieldAccessNode) {
            // We want to handle field access nodes directly, not through a PseudocallResult Value
            val (base, path) = extractFieldPath(pseudoarg.node)

            val scope = Scope(false, pseudoarg.scope.entries.map { PathTail(path.fields + it.fields) } + listOfNotNull(
                PathTail(path.fields).takeIf { pseudoarg.scope.includesBase }
            ))

            val pseudoarg =
                Pseudoarg(pseudoarg.mutability, scope, pseudoarg.borrowTarget, input.getValueOfSubNode(base)!!, base)
            return processPseudoarg(pseudoarg, input)
        }

        if (pseudoarg.node.type.kind.isPrimitive || pseudoarg.node.type.kind == TypeKind.VOID) return emptyList()

        val value = pseudoarg.argument

        silentExceptionReportContext(pseudoarg.node.tree ?: tree) {
            store.ensurePermissionOn(
                pseudoarg.mutability.permission, value, pseudoarg.node, pseudoarg.scope, memberTypeAnalysis
            )
        }

        return buildList {
            val (sourceRoot, baseFraction) = if (value is IdentifiedPermission) {
                PathRoot.IdPathRoot(value.id) to value.fraction
            } else if (pseudoarg.node is ClassNameNode) {
                PathRoot.StaticPathRoot(pseudoarg.node) to Mutability.IMMUTABLE.fraction
            } else {
                TODO("Unsupported pseudo argument: ${pseudoarg.node}")
            }

            if (pseudoarg.scope.includesBase) {
                val source = Path(sourceRoot)
                val fraction = if (pseudoarg.mutability == Mutability.MUTABLE) baseFraction else baseFraction / 2
                add(
                    BorroQValue.PseudocallResult.FreeBorrow(
                        source, fraction, pseudoarg.borrowTarget
                    )
                )
            }

            for (pathTail in pseudoarg.scope.entries) {
                val source = Path(sourceRoot, pathTail)
                val lastFieldMutability = memberTypeAnalysis.getFieldMutability(pathTail.fields.last())!!
                val fraction =
                    if (pseudoarg.mutability == Mutability.MUTABLE && lastFieldMutability == Mutability.MUTABLE) {
                        lastFieldMutability.fraction
                    } else {
                        val borrowedFraction = store.borrowedFraction(sourceRoot, pathTail)
                        Rational.HALF - borrowedFraction
                    }
                add(BorroQValue.PseudocallResult.FreeBorrow(source, fraction, pseudoarg.borrowTarget))
            }
        }
    }

    context(store: BorroQStore, tree: Tree, node: Node)
    private fun processPseudocall(
        pseudocall: Pseudocall,
        input: Input
    ): RegularTransferResult<BorroQValue, BorroQStore> {

        val attachedBorrows = buildList {
            for (pseudoarg in pseudocall.arguments) {
                addAll(processPseudoarg(pseudoarg, input))
            }
        }

        val result = BorroQValue.PseudocallResult(Permission(pseudocall.returnMutability.fraction), attachedBorrows)
        return node.regularResult(result, store, storeChanged = true)
    }

    private fun visitCallable(
        node: Node, receiver: Node?, arguments: List<Node>, executableElement: ExecutableElement, input: Input
    ): Result {
        require(!input.containsTwoStores()) { "Callable invocation node $node has two stores" }

        val methodType = memberTypeAnalysis.getType(executableElement, exceptionReportingContext = { block ->
            try {
                exceptionReportContext(node.tree!!, block)
            } catch (_: BorroQReportedException) {
            }
        })

        if (executableElement.isConstructor && methodType.returnMutability == Mutability.IMMUTABLE && signatureType.returnMutability == Mutability.MUTABLE) silentExceptionReportContext(
            node.tree!!
        ) {
            throw IncompatibleSuperConstructorMutability()
        }

        val borrowTarget =
            if (executableElement.isConstructor) Pseudoarg.BorrowTarget.RETURN_VALUE else Pseudoarg.BorrowTarget.PERSISTENT

        val receiverArg = methodType.receiverType?.let { receiverType ->
            val receiverValue = input.getValueOfSubNode(receiver)!!
            Pseudoarg(
                receiverType.mutability, receiverType.scope, borrowTarget, receiverValue, receiver!!
            )
        }
        val arguments = listOfNotNull(receiverArg) + arguments.zip(methodType.parameters)
            .mapNotNull { (arg, paramType) -> paramType?.let { arg to it } }.map { (arg, paramType) ->
                Pseudoarg(
                    paramType.mutability, paramType.scope, borrowTarget, input.getValueOfSubNode(arg)!!, arg
                )
            }

        val pseudocall = Pseudocall(methodType.returnMutability ?: Mutability.IMMUTABLE, arguments)

        return context(input.regularStore, node.tree!!, node) {
            processPseudocall(pseudocall, input)
        }
    }

    override fun visitMethodInvocation(
        node: MethodInvocationNode, input: Input
    ): Result = visitCallable(node, node.target.receiver, node.arguments, node.target.method, input)


    override fun visitObjectCreation(
        node: ObjectCreationNode, input: Input
    ): Result {
        val constructorElement: ExecutableElement = TreeUtils.elementFromUse(node.tree!!)
        return visitCallable(node, null, node.arguments, constructorElement, input)
    }

    //region assignment
    fun visitLocalVariableAssignment(node: AssignmentNode, target: LocalVariableNode, input: Input): Result {
        require(!input.containsTwoStores()) { "Assignment node $node has two stores" }
        fun result(value: BorroQValue, storeUpdate: BorroQStore.() -> Unit): Result {
            val store = input.getRegularStore()
            store.storeUpdate()
            return node.regularResult(value, store)
        }

        val targetMutabilityAnnotation = target.tree?.let {
            annotationQuery.getAssignmentLeftSideAnnotations(it)?.let { Mutability.fromAnnotations(it) }
        }

        return when (val rhsValue =
            input.getValueOfSubNode(node.expression) ?: return node.regularResult(null, input.regularStore, false)) {
            // The right hand side is a pseudocall
            is BorroQValue.PseudocallResult -> {
                if (targetMutabilityAnnotation != null && rhsValue.permission.fraction < targetMutabilityAnnotation.fraction) silentExceptionReportContext(
                    node.expression.tree!!
                ) {
                    throw InsufficientShallowAssignmentExpressionPermissionException(
                        node.expression, targetMutabilityAnnotation.fraction, rhsValue
                    )
                }

                val newFraction = targetMutabilityAnnotation?.fraction ?: rhsValue.permission.fraction

                val store = input.regularStore
                val targetId = store.createFreshId(target)
                val targetPermission = IdentifiedPermission(newFraction, targetId)
                val borrows = rhsValue.attachedBorrows.map { it.toBorrow(targetId) }

                result(targetPermission) {
                    updatePermission(target, targetPermission)
                    for (borrow in borrows) addBorrow(borrow)
                }
            }

            // The right hand side is a local variable or `this`
            is IdentifiedPermission -> {
                val mutability = targetMutabilityAnnotation ?: DefaultInference.inferVariableMutability(
                    rhsValue
                )

                if (mutability.fraction > rhsValue.fraction) silentExceptionReportContext(
                    node.tree!!
                ) {
                    throw InsufficientShallowPermissionException(target.name, mutability.permission, rhsValue)
                }

                val (targetPermission, remainingPermission) = rhsValue.split(mutability)

                result(targetPermission) {
                    updatePermission(target, targetPermission)
                    updatePermission(node.expression, remainingPermission)
                }
            }

            else -> TODO()
        }
    }

    fun checkAssignableInScope(receiverId: Id, baseType: TypeMirror, path: PathTail) {
        if (!configuration.borroQExtensions.isActive(Extension.NO_OUT_OF_SCOPE_ASSIGNMENT)) {
            return
        }

        val parameterScope = parameterIds.entries.indexOfFirst { it.value == receiverId }.takeIf { it != -1 }?.let {
            signatureType.parameters[it]!!.scope
        } ?: signatureType.receiverType?.takeIf { receiverId == ThisId }?.scope ?: return

        if (!parameterScope.isInScope(path, baseType, checker.elementUtils)) {
            throw HiddenFieldAssignedException()
        }
    }

    fun visitFieldAssignment(node: AssignmentNode, target: FieldAccessNode, input: Input): Result {

        val (base, path) = extractFieldPath(target)
        val receiverId =
            input.getValueOfSubNode(base)?.let { it as? IdentifiedPermission }?.id ?: ThisId.takeIf { base is ThisNode }
        if (receiverId != null) {
            silentExceptionReportContext(target.tree!!) { checkAssignableInScope(receiverId, base.type, path) }
        } else {
            logger.warn { "Could not determine receiver id for field assignment. Cannot check whether $target is in scope" }
        }


        val receiverPseudoarg = Pseudoarg(
            Mutability.MUTABLE,
            Scope(includesBase = true, emptyList()),
            Pseudoarg.BorrowTarget.RETURN_VALUE,
            input.getValueOfSubNode(target.receiver)!!,
            target.receiver
        )

        val fieldMutability = memberTypeAnalysis.getFieldMutability(target.element) ?: return node.regularResult(
            null, input.regularStore, false
        )
        val valuePseudoarg = Pseudoarg(
            fieldMutability,
            Scope.full(node.expression.type, checker.elementUtils),
            Pseudoarg.BorrowTarget.PERSISTENT,
            input.getValueOfSubNode(node.expression)!!,
            node.expression
        )

        val pseudocall = Pseudocall(Mutability.IMMUTABLE, listOf(receiverPseudoarg, valuePseudoarg))

        return context(input.regularStore, node.tree!!, node) {
            processPseudocall(pseudocall, input)
        }
    }

    fun validateTypeParameters(targetType: TypeMirror, valueType: TypeMirror, topLevel: Boolean) {
        val targetMutability =
            Mutability.fromAnnotations(targetType.annotationMirrors) ?: DefaultInference.inferTypeParameterMutability()
        val valueMutability =
            Mutability.fromAnnotations(valueType.annotationMirrors) ?: DefaultInference.inferTypeParameterMutability()

        if (!topLevel && targetMutability == Mutability.MUTABLE && valueMutability == Mutability.IMMUTABLE) {
            throw IncompatibleTypeParameterMutabilities(targetType)
        }

        when (targetType) {
            is ArrayType -> validateTypeParameters(
                targetType.componentType, (valueType as ArrayType).componentType, false
            )

            is ReferenceType -> if (TypesUtils.isParameterizedType(targetType)) {
                TODO()
            }

            is Any if (targetType.kind.isPrimitive || targetType.kind == TypeKind.VOID) -> {}
            else -> TODO("Validate type parameters for $targetType and $valueType")
        }
    }

    override fun visitAssignment(
        node: AssignmentNode, input: Input
    ): Result {
        require(!input.containsTwoStores()) { "Assignment node $node has two stores" }

        silentExceptionReportContext(node.tree!!) {
            validateTypeParameters(
                node.target.annotatedType, node.expression.annotatedType, true
            )
        }

        return when (val target = node.target) {
            is LocalVariableNode -> visitLocalVariableAssignment(node, target, input)
            is FieldAccessNode -> visitFieldAssignment(node, target, input)
            else -> visitNode(node, input)
        }
    }
//endregion

    //region field access
    private fun inferFieldAccessMutabilityInBlock(node: FieldAccessNode, block: Block): Mutability {
        if (block is ExceptionBlock) inferFieldAccessMutabilityInBlock(node, block.successor!!)

        for (usageNode in block.nodes) {
            if (usageNode.operands.contains(node)) when (usageNode) {
                is ReturnNode -> signatureType.returnMutability
                else -> TODO("Infer mutability from ${usageNode}")
            }
        }

        logger.warn { "Could not infer mutability for field access. Falling back to default." }
        return DefaultInference.inferFieldAccessMutability()
    }

    private fun inferFieldAccessMutability(node: FieldAccessNode): Mutability =
        inferFieldAccessMutabilityInBlock(node, node.block!!)

    private fun extractFieldPath(node: FieldAccessNode): Pair<Node, PathTail> {
        fun extractFieldPathRec(node: FieldAccessNode) = when (val receiver = node.receiver) {
            is FieldAccessNode -> {
                val (base, path) = extractFieldPath(node)
                base to path.with(node.element)
            }

            is LocalVariableNode, is ThisNode, is ClassNameNode -> {
                receiver to PathTail(node.element)
            }

            else -> TODO("Extract field path for $receiver")
        }

        val result = extractFieldPathRec(node)
        if (result.second.fields.size != 2) {
            configuration.borroQExtensions.requireExtension(Extension.NESTED_FIELD_ACCESS, node, checker)
        }
        return result
    }

    override fun visitFieldAccess(
        node: FieldAccessNode, input: Input
    ): Result {
        require(!input.containsTwoStores()) { "Field access node $node has two stores" }

        val (base, pathTail) = extractFieldPath(node)

        val targetMutability = inferFieldAccessMutability(node)
        val receiverArg = Pseudoarg(
            targetMutability,
            Scope(false, listOf(pathTail)),
            Pseudoarg.BorrowTarget.PERSISTENT,
            input.getValueOfSubNode(base)!!,
            base
        )
        val pseudocall = Pseudocall(targetMutability, listOf(receiverArg))

        return context(input.regularStore, node.tree!!, node) {
            processPseudocall(pseudocall, input)
        }
    }
//endregion

    override fun visitLocalVariable(
        node: LocalVariableNode, input: Input
    ): Result {
        require(!input.containsTwoStores()) { "Local variable node $node has two stores" }
        val permission = input.regularStore.queryPermission(node)
        return node.regularResult(permission, input.regularStore, false)
    }

    override fun visitThis(
        node: ThisNode, input: Input
    ): Result {
        require(!input.containsTwoStores()) { "Local variable node $node has two stores" }
        val permission = input.regularStore.queryThisPermission()
        return node.regularResult(permission, input.regularStore, false)
    }

    override fun visitReturn(
        node: ReturnNode, input: Input
    ): Result {
        val result = node.result
        if (result == null) {
            configuration.borroQExtensions.requireExtension(Extension.VOID_RETURN, node, checker)
            return node.regularResult(
                null, input.regularStore, false
            )
        } else if (result.type.kind.isPrimitive) {
            return node.regularResult(
                null, input.regularStore, false
            )
        }

        val returnPermission = input.getValueOfSubNode(result)!!

        val store = input.regularStore
        silentExceptionReportContext(result.tree!!) {
            store.ensureDeepPermission(
                signatureType.returnMutability!!.permission,
                returnPermission,
                result,
                checker.elementUtils,
                memberTypeAnalysis
            )
        }

        return node.regularResult(null, store, false)
    }

    override fun visitClassName(
        node: ClassNameNode, input: Input
    ): Result {
        return node.regularResult(
            BorroQValue.PseudocallResult(Permission(Mutability.IMMUTABLE.fraction), emptyList()), input.regularStore
        )
    }

    override fun visitStringLiteral(
        node: StringLiteralNode, input: Input
    ): Result {
        return node.regularResult(
            BorroQValue.PseudocallResult(Permission(Mutability.IMMUTABLE.fraction), emptyList()), input.regularStore
        )
    }

    override fun visitValueLiteral(node: ValueLiteralNode, p: Input): Result {
        if (node.type.kind != TypeKind.BOOLEAN) {
            configuration.borroQExtensions.requireExtension(Extension.ALL_PRIMITIVES, node, checker)
        }
        return node.regularResult(
            null, p.regularStore
        )
    }

    // region array stuff
    override fun visitArrayCreation(node: ArrayCreationNode, input: Input): Result {
        configuration.borroQExtensions.requireExtension(Extension.ARRAYS, node, checker)

        val elementType = (node.type as Type.ArrayType).elemtype

        val pseudoargs = if (elementType.isPrimitive) {
            val elementMutability = Mutability.fromAnnotations(elementType.annotationMirrors)
                ?: DefaultInference.inferArrayElementMutability()
            val scope = TODO("Full scope if mutable")
            node.initializers.map {
                Pseudoarg(
                    elementMutability, scope, Pseudoarg.BorrowTarget.RETURN_VALUE, input.getValueOfSubNode(it)!!, it
                )
            }
        } else emptyList()

        val pseudocall = Pseudocall(Mutability.MUTABLE, pseudoargs)

        return context(input.regularStore, node.tree!!, node) {
            processPseudocall(pseudocall, input)
        }
    }

    override fun visitArrayAccess(
        node: ArrayAccessNode, input: Input
    ): Result {
        configuration.borroQExtensions.requireExtension(Extension.ARRAYS, node, checker)

        val componentMutability = node.array.annotatedType.let { it as ArrayType }.componentType.let {
            Mutability.fromAnnotations(
                it.annotationMirrors
            )
        } ?: DefaultInference.inferTypeParameterMutability()

        val arrayScope = TODO("Array scope")
        val arrayPseudoarg = Pseudoarg(
            Mutability.IMMUTABLE,
            arrayScope,
            Pseudoarg.BorrowTarget.RETURN_VALUE,
            input.getValueOfSubNode(node.array)!!,
            node.array
        )
        val indexPseudoarg = Pseudoarg(
            Mutability.IMMUTABLE,
            Scope(false, emptyList()),
            Pseudoarg.BorrowTarget.RETURN_VALUE,
            input.getValueOfSubNode(node.index)!!,
            node.index
        )
        val pseudocall = Pseudocall(componentMutability, listOf(arrayPseudoarg, indexPseudoarg))

        return context(input.regularStore, node.tree!!, node) {
            processPseudocall(pseudocall, input)
        }
    }
// endregion array stuff

    companion object {
        val ThisId = Id("this")
    }

    override fun visitTypeCast(
        n: TypeCastNode, p: Input
    ): Result {
        if (n.type.kind.isPrimitive) {
            return doNothing(n, p)
        }
        return visitNode(n, p)
    }
}