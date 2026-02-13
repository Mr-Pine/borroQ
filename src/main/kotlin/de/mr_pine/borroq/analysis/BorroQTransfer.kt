@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package de.mr_pine.borroq.analysis

import com.sun.source.tree.Tree
import com.sun.tools.javac.code.Type
import com.sun.tools.javac.tree.JCTree
import de.mr_pine.borroq.BorroQChecker
import de.mr_pine.borroq.Messages
import de.mr_pine.borroq.analysis.Configuration.BorrowQExtensions.Extension
import de.mr_pine.borroq.analysis.exceptions.*
import de.mr_pine.borroq.analysis.livevariable.LiveVarNode
import de.mr_pine.borroq.analysis.livevariable.LiveVarStore
import de.mr_pine.borroq.isConstructor
import de.mr_pine.borroq.types.*
import de.mr_pine.borroq.types.IdentifiedPermission.Companion.withId
import de.mr_pine.borroq.types.specifiers.IMutability
import de.mr_pine.borroq.types.specifiers.Mutability
import de.mr_pine.borroq.types.specifiers.ReleaseMode
import de.mr_pine.borroq.types.specifiers.ReleaseMode.SingleReleaseMode
import de.mr_pine.borroq.types.specifiers.Scope
import io.github.oshai.kotlinlogging.KotlinLogging
import org.checkerframework.dataflow.analysis.*
import org.checkerframework.dataflow.cfg.UnderlyingAST
import org.checkerframework.dataflow.cfg.block.Block
import org.checkerframework.dataflow.cfg.block.ExceptionBlock
import org.checkerframework.dataflow.cfg.node.*
import org.checkerframework.dataflow.expression.JavaExpression
import org.checkerframework.dataflow.expression.LocalVariable
import org.checkerframework.javacutil.ElementUtils
import org.checkerframework.javacutil.TreeUtils
import org.checkerframework.javacutil.TypesUtils
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.ArrayType
import javax.lang.model.type.ReferenceType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import kotlin.contracts.ExperimentalContracts

private val logger = KotlinLogging.logger { }

private typealias Result = TransferResult<BorroQValue, BorroQStore>
private typealias Input = TransferInput<BorroQValue, BorroQStore>

class BorroQTransfer(
    private val signatureType: SignatureType,
    private val memberTypeAnalysis: MemberTypeAnalysis,
    private val liveness: AnalysisResult<UnusedAbstractValue, LiveVarStore>,
    private val checker: BorroQChecker,
    private val annotationQuery: AnnotationQuery,
    private val configuration: Configuration
) : AbstractNodeVisitor<Result, Input>(), ForwardTransferFunction<BorroQValue, BorroQStore> {

    private var parameters: List<LocalVariableNode>? = null

    private val IMutability.fraction: Rational
        get() = when (this) {
            is IMutability.Mutable -> Rational.ONE
            is IMutability.Immutable -> ImmutableFraction
        }

    private val Mutability.fraction: Rational
        get() = when (this) {
            Mutability.MUTABLE -> Rational.ONE
            Mutability.IMMUTABLE -> ImmutableFraction
        }

    private val Node.annotatedType: TypeMirror
        get() = when (val tree = tree) {
            is JCTree.JCIdent -> tree.sym
            is JCTree.JCVariableDecl -> tree.sym
            else -> null
        }?.asType() ?: type

    fun Node.regularResult(
        value: BorroQValue?, store: BorroQStore, storeChanged: Boolean = true
    ): RegularTransferResult<BorroQValue, BorroQStore> {
        fun toVariable(node: Node): LocalVariableNode? = when (node) {
            is FieldAccessNode -> toVariable(node.receiver)
            is LocalVariableNode -> node
            else -> null
        }

        fun Set<LiveVarNode>?.liveVariables() =
            orEmpty().asSequence().mapNotNull { toVariable(it.liveVariable) }.toSet()

        val diedVariables =
            liveness.getStoreBefore(this)?.liveVariables.liveVariables() - liveness.getStoreAfter(this)?.liveVariables.liveVariables()


        for (variable in diedVariables) {
            store.killVariable(variable)
        }

        fun Set<LiveVarNode>?.liveIds() =
            liveVariables().mapNotNull { store.queryPermission(it) as IdentifiedPermission? }.map { it.id }.toSet()

        val diedIds =
            liveness.getStoreBefore(this)?.liveVariables.liveIds() - liveness.getStoreAfter(this)?.liveVariables.liveIds()

        for (id in diedIds) {
            val removedBorrows = store.removeInactiveBorrowsWithId(id)
            removedBorrows.filter { it.path.tail.fields.isEmpty() }.forEach {
                store.recombineAny(Permission(it.fraction).withId(it.path.id))
            }
        }

        return RegularTransferResult(value, store, storeChanged)
    }

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
        this.parameters = parameters.toList()
        if (parameters.size != 2) {
            configuration.borrowQExtensions.requireExtension(
                Extension.ANY_PARAMETER_COUNT,
                underlyingAST.code!!,
                checker
            )
        }

        val parameterPermissions = parameters.zip(signatureType.parameters).mapNotNull { (parameter, parameterType) ->
            val mutability = if ((parameterType?.mutability
                    ?: return@mapNotNull null) is IMutability.Mutable
            ) Mutability.MUTABLE else Mutability.IMMUTABLE
            val permission = Permission(mutability.fraction)
            val localVariable = JavaExpression.fromNode(parameter) as LocalVariable
            val id = Id.fromNode(parameter)

            localVariable to (permission.withId(Id.fromNode(parameter)) as VariablePermission)
        }.toMap().toMutableMap()

        fun borrowsFromScope(id: Id, scope: Scope, source: Tree): List<Borrow> {
            if (scope.entries.any { it.fields.size > 1 }) {
                configuration.borrowQExtensions.requireExtension(Extension.NESTED_FIELD_ACCESS, source, checker)
            }
            if (scope.includesBase) TODO("Base in scope")

            TODO("Create borrows from scope")
        }


        context(mutability: IMutability, paramSource: Tree)
        fun calculateBorrows(
            borrowBase: IdPath, baseType: TypeElement, paths: List<PathTail>
        ): List<Borrow> {
            if (TypesUtils.isPrimitive(baseType.asType())) {
                return emptyList()
            }
            val typeFields = ElementUtils.getAllFieldsIn(baseType, checker.elementUtils)
            val unmentioned = typeFields.filter { field -> paths.none { it.fields.first() == field } }
            val unmentionedBorrows = unmentioned.map { field ->
                val fraction = memberTypeAnalysis.getFieldMutability(field)
                    ?.let { if (it is IMutability.Mutable) Rational.ONE else ImmutableFraction } ?: ImmutableFraction
                Borrow(borrowBase.with(field), fraction, Borrow.Identifier.Dummy)
            }

            val mentionedBorrows = paths.groupBy { it.fields.first() }.flatMap { (field, paths) ->
                if (paths.size == 1 && paths.single().fields.size == 1) {
                    if (memberTypeAnalysis.getFieldMutability(field) !is IMutability.Mutable && mutability is IMutability.Mutable) checker.reportWarning(
                        paramSource, Messages.IMMUTABLE_FIELD_IN_MUTABLE_ANNOTATION, field.simpleName
                    )
                    return@flatMap emptyList<Borrow>()
                }
                val trimmedPaths = paths.map { PathTail(it.fields.drop(1)) }
                val updatedBase = borrowBase.with(field)
                val fieldType = TypesUtils.getTypeElement(field.asType())!!

                return calculateBorrows(updatedBase, fieldType, trimmedPaths)
            }

            return unmentionedBorrows + mentionedBorrows
        }

        val parameterBorrows = parameters.zip(signatureType.parameters).flatMap { (parameter, parameterType) ->
            val scope = TODO("Get scope from $parameterType")
            val id =
                parameterPermissions.get(JavaExpression.fromNode(parameter) as LocalVariable)
                    ?.let { (it as IdentifiedPermission).id } ?: return@flatMap emptyList()
            borrowsFromScope(id, scope, parameter.tree!!)
        }
        val thisBorrows = borrowsFromScope(ThisId, TODO("Get scope for this"), underlyingAST.code!!)

        val methodAST = underlyingAST as UnderlyingAST.CFGMethod
        val receiverPermission = when (signatureType.receiverType?.mutability) {
            null -> if (TreeUtils.isConstructor(methodAST.method)) Permission(Rational.ONE) else null

            is IMutability.Mutable -> Permission(Rational.ONE)
            is IMutability.Immutable -> Permission(ImmutableFraction)
        }?.withId(ThisId)
        val receiverBorrows = if (signatureType.receiverType != null) run {
            val baseType = TreeUtils.elementFromDeclaration(methodAST.classTree)

            val onPaths = signatureType.receiverType!!.mutability.onPaths ?: return@run emptyList()

            context(
                signatureType.receiverType.mutability, methodAST.method.receiverParameter ?: methodAST.method!!
            ) { calculateBorrows(IdPath(ThisId), baseType, onPaths) }
        } else if (TreeUtils.isConstructor(methodAST.method)) {
            val baseType = TreeUtils.elementFromDeclaration(methodAST.classTree)
            context(
                IMutability.Mutable(emptyList()), methodAST.method.receiverParameter ?: methodAST.method!!
            ) { calculateBorrows(IdPath(ThisId), baseType, emptyList()) }
        } else emptyList()

        return BorroQStore(
            parameterPermissions, receiverPermission, (parameterBorrows + receiverBorrows).toMutableList()
        )
    }

    fun doNothing(
        node: Node, input: Input
    ): Result = node.regularResult(null, input.regularStore, false)

    override fun visitNode(
        node: Node, p: Input
    ): Result {
        configuration.unknownSyntaxStrictness.reportUnknownSyntaxStrictness(checker, node)
        return node.regularResult(null, p.regularStore, false)
    }

    data class Pseudoarg(val mutability: Mutability, val scope: Scope, val argument: BorroQValue, val argTree: Tree)
    data class Pseudocall(val returnMutability: Mutability, val arguments: List<Pseudoarg>)

    context(store: BorroQStore, tree: Tree, node: Node)
    private fun processPseudoarg(pseudoarg: Pseudoarg) {
        if (node.type.kind.isPrimitive || node.type.kind == TypeKind.VOID) return

        val value = pseudoarg.argument

        TODO("Ensure shallow readability")
        if (pseudoarg.mutability == Mutability.MUTABLE) {
            TODO("Ensure shallow mutability")
        }

        if (pseudoarg.scope.includesBase) {
            TODO("Not sure if even necessry")
        }
        for (scopeTail in pseudoarg.scope.entries) {
            TODO("Ensure deep readability on $scopeTail")
            if (pseudoarg.mutability == Mutability.MUTABLE) {
                TODO("Ensure deep mutability on $scopeTail")
            }
        }
    }

    context(store: BorroQStore, tree: Tree, node: Node)
    private fun processPseudocall(
        pseudocall: Pseudocall
    ): RegularTransferResult<BorroQValue, BorroQStore> {

        for (pseudoarg in pseudocall.arguments) {
            context(pseudoarg.argTree) { processPseudoarg(pseudoarg) }
        }

        val result = BorroQValue.FreePermission(Permission(pseudocall.returnMutability.fraction), emptyList())
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

        if (executableElement.isConstructor && methodType.returnMutability is IMutability.Immutable && signatureType.returnMutability is IMutability.Mutable) silentExceptionReportContext(
            node.tree!!
        ) {
            throw IncompatibleSuperConstructorMutability()
        }

        val returnMutability =
            if (methodType.returnMutability is IMutability.Mutable) Mutability.MUTABLE else Mutability.IMMUTABLE

        val receiverArg = methodType.receiverType?.let { receiverType ->
            val argMutability =
                if (receiverType.mutability is IMutability.Mutable) Mutability.MUTABLE else Mutability.IMMUTABLE
            val scope = TODO("Need scope for ${receiverType}")
            val receiverValue = input.getValueOfSubNode(receiver)!!
            Pseudoarg(argMutability, scope, receiverValue, receiver!!.tree ?: node.tree!!)
        }
        val arguments = listOfNotNull(receiverArg) + arguments.zip(methodType.parameters)
            .filterIsInstance<Pair<Node, SignatureType.ParameterType>>().map { (arg, paramType) ->
                val argMutability =
                    if (paramType.mutability is IMutability.Mutable) Mutability.MUTABLE else Mutability.IMMUTABLE
                val scope = TODO("Need scope for ${paramType}")
                Pseudoarg(argMutability, scope, input.getValueOfSubNode(arg)!!, arg.tree!!)
            }

        val pseudocall = Pseudocall(returnMutability, arguments)

        return context(input.regularStore, node.tree!!, node) {
            processPseudocall(pseudocall)
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
            annotationQuery.getAssignmentLeftSideAnnotations(it)
                ?.let { IMutability.fromAnnotationsOnType(it, TypesUtils.getTypeElement(target.type)) }
        }
        require(targetMutabilityAnnotation?.onPaths == null) { "Local variable mutability annotation cannot be restricted on paths" }

        return when (val rhsValue =
            input.getValueOfSubNode(node.expression) ?: return node.regularResult(null, input.regularStore, false)) {
            is BorroQValue.FreePermission -> {
                val targetId = Id.fromNode(target)

                val unfulfilledMutability =
                    targetMutabilityAnnotation is IMutability.Mutable && rhsValue.permission.fraction < Rational.ONE
                val unfulfilledReadability =
                    targetMutabilityAnnotation is IMutability.Mutable && rhsValue.permission.fraction.isZero()
                if (unfulfilledMutability || unfulfilledReadability) silentExceptionReportContext(
                    node.expression.tree!!
                ) {
                    throw InsufficientShallowAssignmentExpressionPermissionException(
                        node.expression, targetMutabilityAnnotation.fraction, rhsValue
                    )
                }

                val targetPermission = IdentifiedPermission(
                    if (unfulfilledMutability) Rational.ONE else if (unfulfilledReadability) ImmutableFraction else rhsValue.permission.fraction * (targetMutabilityAnnotation?.fraction // Avoid mutability issues "downstream"
                        ?: Rational.ONE), targetId
                )
                val borrows = rhsValue.attachedBorrows.map { it.toBorrow(targetId) }
                result(targetPermission) {
                    updatePermission(target, targetPermission)
                    for (borrow in borrows) addBorrow(borrow)
                }
            }

            is IdentifiedPermission -> {
                val mutability = targetMutabilityAnnotation ?: DefaultInference.inferVariableMutability(rhsValue)
                val (targetPermission, remainingPermission) = rhsValue.split(mutability)

                if (mutability is IMutability.Mutable && targetPermission.fraction < Rational.ONE) silentExceptionReportContext(
                    node.tree!!
                ) {
                    throw InsufficientShallowPermissionException(target.name, mutability, targetPermission)
                }

                result(targetPermission) {
                    updatePermission(target, targetPermission)
                    when (node.expression) {
                        is LocalVariableNode -> updatePermission(node.expression, remainingPermission)
                        is ThisNode -> result(rhsValue) { updateThisPermission(remainingPermission) }
                        else -> throw IllegalStateException("Unexpected expression type ${node.expression}")
                    }
                }
            }

            is BorroQValue.FieldAccess -> {
                val idAccessPath = context(input.regularStore) { rhsValue.access.asIdPath() }
                if (input.regularStore.getBorrows()
                        .any { it.path.isPrefixOf(idAccessPath) }
                ) silentExceptionReportContext(
                    node.expression.tree!!
                ) {
                    throw InsufficientShallowPermissionBorrowedException(
                        input.regularStore.getBorrows().first { it.path.isPrefixOf(idAccessPath) })
                }

                val mutability = targetMutabilityAnnotation ?: DefaultInference.inferVariableMutability(rhsValue)
                val (usedVariablePermission, _) = rhsValue.fieldPermission.split(mutability)
                val targetId = Id.fromNode(target)
                val borrow = Borrow(
                    idAccessPath, usedVariablePermission.fraction, targetId
                )
                val variablePermission =
                    context(input.regularStore, memberTypeAnalysis) { rhsValue.access.permission() }?.withId(targetId)
                if (variablePermission == null) {
                    node.regularResult(variablePermission, input.regularStore, false)
                } else {
                    result(variablePermission) {
                        updatePermission(target, variablePermission)
                        addBorrow(borrow)
                    }
                }
            }

            else -> TODO()
        }
    }

    context(store: BorroQStore, memberTypeAnalysis: MemberTypeAnalysis)
    fun checkAssignability(receiver: Node, field: VariableElement) {
        val receiverPath = Path.fromNode(receiver)
        val receiverPathPermission = receiverPath.permission()
            ?: throw IllegalStateException("Could not get permission for assignability check receiver $receiverPath")

        if (receiverPathPermission.fraction < Rational.ONE) { // No shallow mutability
            throw InsufficientShallowAssignmentTargetReceiverPermissionException(receiverPath, receiverPathPermission)
        }

        val receiverRootId = receiverPath.root.toId()
        val accessPath = receiverPath.with(field)

        if (receiverRootId in parameters!!.map { Id.fromNode(it) } + ThisId) {
            // Ensure that the field we assign isn't hidden with scopes
            val parameterIndex = parameters!!.indexOfFirst { Id.fromNode(it) == receiverRootId }.takeIf { it != -1 }
            val parameterType = parameterIndex?.let { parameterIndex -> signatureType.parameters[parameterIndex]!! }
                ?: signatureType.receiverType

            val paths = parameterType?.mutability?.onPaths
            if (paths != null) {
                val hasPrefix = paths.any { it.isPrefixOf(accessPath.tail) }

                if (!hasPrefix) {
                    throw HiddenFieldAssignedException()
                }
            }
        }
    }

    fun visitFieldAssignment(node: AssignmentNode, target: FieldAccessNode, input: Input): Result {
        context(
            input.regularStore, memberTypeAnalysis
        ) { silentExceptionReportContext(target.tree!!) { checkAssignability(target.receiver, target.element) } }

        val fieldMutability = memberTypeAnalysis.getFieldMutability(target.element) ?: return node.regularResult(
            null, input.regularStore, false
        )
        val fieldFraction = when (fieldMutability) {
            is IMutability.Mutable -> Rational.ONE
            is IMutability.Immutable -> ImmutableFraction
        }
        val newValuePermission = input.getValueOfSubNode(node.expression)!!

        val (newValueFraction, newValueId) = try {
            exceptionReportContext(node.expression.tree!!) {
                when (newValuePermission) {
                    is IdentifiedPermission -> newValuePermission.fraction to newValuePermission.id
                    else -> TODO("Fields can currently only be assigned from local variables") // newValuePermission.permission
                }
            }
        } catch (_: BorroQReportedException) {
            Rational.ONE to null
        }

        // TODO: Only enforce mutable -> newValueFraction = 1 ?
        if (newValueFraction < fieldFraction) silentExceptionReportContext(node.tree!!) {
            throw InsufficientShallowAssignmentExpressionPermissionException(
                node.expression, fieldFraction, newValuePermission
            )
        }

        val receiverPath = Path.fromNode(target.receiver)
        input.regularStore.removeBorrowsWithPathPrefix(context(input.regularStore) {
            receiverPath.with(target.element).asIdPath()
        })

        if (newValueId != null) {
            val totalPermissionOfId = input.regularStore.localPermissionSum(newValueId)
            val reassignedPortion = newValueFraction / totalPermissionOfId

            input.regularStore.removeBorrowsWithId(newValueId)
                .map { Borrow(it.path, it.fraction - reassignedPortion, it.id) }.filterNot { it.fraction.isZero() }
                .forEach { input.regularStore.addBorrow(it) }

            input.regularStore.updatePermission(node.expression, Permission(Rational.ZERO).withId(newValueId))
        }

        return node.regularResult(null, input.regularStore)
    }

    fun validateTypeParameters(targetType: TypeMirror, valueType: TypeMirror, topLevel: Boolean) {
        val targetMutability =
            IMutability.fromAnnotationsOnType(targetType.annotationMirrors, TypesUtils.getTypeElement(targetType))
                ?: DefaultInference.inferTypeParameterIMutability()
        val valueMutability =
            IMutability.fromAnnotationsOnType(valueType.annotationMirrors, TypesUtils.getTypeElement(valueType))
                ?: DefaultInference.inferTypeParameterIMutability()

        if (!topLevel && targetMutability is IMutability.Mutable && valueMutability is IMutability.Immutable) {
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
                is ReturnNode -> if (signatureType.returnMutability is IMutability.Mutable) return Mutability.MUTABLE
                else -> TODO("Infer mutability from ${usageNode}")
            }
        }

        logger.warn { "Could not infer mutability for field access. Falling back to default." }
        return DefaultInference.inferFieldAccessMutability()
    }

    private fun inferFieldAccessMutability(node: FieldAccessNode): Mutability =
        inferFieldAccessMutabilityInBlock(node, node.block!!)

    private fun extractFieldPath(node: FieldAccessNode): Pair<Node, PathTail> = when (val receiver = node.receiver) {
        is FieldAccessNode -> {
            val (base, path) = extractFieldPath(node)
            base to path.with(node.element)
        }

        is LocalVariableNode, is ThisNode -> receiver to PathTail(node.element)

        else -> TODO("Extract field path for $receiver")
    }

    override fun visitFieldAccess(
        node: FieldAccessNode, input: Input
    ): Result {
        require(!input.containsTwoStores()) { "Field access node $node has two stores" }

        val (base, pathTail) = extractFieldPath(node)

        val targetMutability = inferFieldAccessMutability(node)
        val receiverArg = Pseudoarg(
            targetMutability, Scope(false, listOf(pathTail)), input.getValueOfSubNode(base)!!, node.tree!!
        )
        val pseudocall = Pseudocall(targetMutability, listOf(receiverArg))

        return context(input.regularStore, node.tree!!, node) {
            processPseudocall(pseudocall)
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

    //region return/exit
    fun validateExit(store: BorroQStore, returnValue: BorroQValue?, reportTree: Tree) {
        fun validateReleaseMode(baseId: Id, releaseMode: ReleaseMode) {
            val pathsReleaseMapping = releaseMode.pathsToSingleReleaseMode()
            for ((pathTail, releaseMode) in pathsReleaseMapping) {
                val path = IdPath(baseId, pathTail)

                when (releaseMode) {
                    is SingleReleaseMode.Release, is SingleReleaseMode.Borrow -> {
                        val permissionSum = store.localPermissionSum(baseId)
                        val permissionTarget = when (signatureType.receiverType?.mutability) {
                            is IMutability.Immutable -> ImmutableFraction
                            is IMutability.Mutable -> Rational.ONE
                            null -> Rational.ZERO
                        }

                        if (permissionTarget > permissionSum) silentExceptionReportContext(reportTree) {
                            throw ReleasePermissionMissingException(baseId.name)
                        }

                        val borrowIdExclude: Borrow.Identifier? =
                            if (releaseMode is SingleReleaseMode.Borrow) (returnValue as? IdentifiedPermission)?.id else null

                        val conflictingPaths =
                            store.getBorrows().filter { it.path.isPrefixOf(path) || path.isPrefixOf(it.path) }
                                .filter { it.id != borrowIdExclude }
                        if (conflictingPaths.isNotEmpty()) silentExceptionReportContext(reportTree) {
                            val borrow = conflictingPaths.first()
                            throw ReleaseBorrowConflictException(path, borrow.id, borrow.path)
                        }
                    }

                    is SingleReleaseMode.Move -> {}
                }
            }
        }

        signatureType.receiverType?.let { validateReleaseMode(ThisId, it.releaseMode) }

        for ((parameter, paramType) in parameters!!.zip(signatureType.parameters)) {
            paramType ?: continue // primitive
            val id = Id.fromNode(parameter)

            validateReleaseMode(id, paramType.releaseMode)
        }
    }

    override fun visitReturn(
        node: ReturnNode, input: Input
    ): Result = try {
        exceptionReportContext(node.tree!!) {
            if (signatureType.returnMutability == null) return node.regularResult(null, input.regularStore, false)
            require(!input.containsTwoStores()) { "Return node $node with two stores" }
            require(signatureType.returnMutability.onPaths == null) { "Return type cannot be restricted on paths" }

            if (node.result == null) return node.regularResult(null, input.regularStore, false)

            input.getValueOfSubNode(node.result)?.let {
                val (basePermission, returnPath) = when (it) {
                    is BorroQValue.FieldAccess -> {
                        val idPath = context(input.regularStore) { it.access.asIdPath() }
                        val base = when (it.access.root) {
                            is PathRoot.StaticPathRoot -> TODO("Static field access in return is not supported")
                            is PathRoot.ThisPathRoot -> input.regularStore.queryThisPermission()!!
                            is PathRoot.LocalVariableRoot -> input.regularStore.queryPermission(it.access.root.variable)!!
                        }
                        (base to idPath)
                    }

                    is IdentifiedPermission -> it to IdPath(it.id)
                    is BorroQValue.FreePermission -> {
                        val dummyIdentifiedPermission = it.permission.withId(Id(""))
                        dummyIdentifiedPermission to null
                    }

                    is BorroQValue.Primitive -> return@let
                    is VariablePermission.Top -> throw TopPermissionEncounteredException("<return value>")
                }
                context(input.regularStore.getBorrows(), memberTypeAnalysis) {
                    when (signatureType.returnMutability) {
                        is IMutability.Mutable -> {
                            if (!basePermission.hasShallowMutability) throw IncompatibleReturnPermissionException(
                                signatureType.returnMutability
                            )
                            if (returnPath?.allowsDeepMutability() == false) throw IncompatibleReturnPermissionException(
                                signatureType.returnMutability
                            )
                        }

                        is IMutability.Immutable -> {
                            if (!basePermission.hasShallowReadability) throw IncompatibleReturnPermissionException(
                                signatureType.returnMutability
                            )
                            if (returnPath?.allowsDeepReadability() == false) throw IncompatibleReturnPermissionException(
                                signatureType.returnMutability
                            )
                        }
                    }
                }
            }

            node.regularResult(input.getValueOfSubNode(node.result), input.regularStore, false)
        }
    } catch (_: BorroQReportedException) {
        node.regularResult(null, input.regularStore, false)
    }
    //endregion

    override fun visitStringLiteral(
        node: StringLiteralNode, input: Input
    ): Result {
        return node.regularResult(
            BorroQValue.FreePermission(Permission(ImmutableFraction), emptyList()), input.regularStore
        )
    }

    override fun visitValueLiteral(node: ValueLiteralNode, p: Input): Result {
        if (node.type.kind != TypeKind.BOOLEAN) {
            configuration.borrowQExtensions.requireExtension(Extension.ALL_PRIMITIVES, node, checker)
        }
        return node.regularResult(
            null, p.regularStore
        )
    }

    // region array stuff
    override fun visitArrayCreation(node: ArrayCreationNode, input: Input): Result {
        configuration.borrowQExtensions.requireExtension(Extension.ARRAYS, node, checker)

        val elementType = (node.type as Type.ArrayType).elemtype

        val pseudoargs = if (elementType.isPrimitive) {
            val elementMutability =
                Mutability.fromAnnotationsOnType(elementType.annotationMirrors, TypesUtils.getTypeElement(elementType))
                    ?: DefaultInference.inferArrayElementMutability()
            val scope = TODO("Full scope if mutable")
            node.initializers.map {
                Pseudoarg(elementMutability, scope, input.getValueOfSubNode(it)!!, it.tree!!)
            }
        } else emptyList()

        val pseudocall = Pseudocall(Mutability.MUTABLE, pseudoargs)

        return context(input.regularStore, node.tree!!, node) {
            processPseudocall(pseudocall)
        }
    }

    override fun visitArrayAccess(
        node: ArrayAccessNode, input: Input
    ): Result {
        configuration.borrowQExtensions.requireExtension(Extension.ARRAYS, node, checker)

        val componentMutability = node.array.annotatedType.let { it as ArrayType }.componentType.let {
            Mutability.fromAnnotationsOnType(
                it.annotationMirrors, TypesUtils.getTypeElement(it)
            )
        } ?: DefaultInference.inferTypeParameterMutability()

        val arrayScope = TODO()
        val arrayPseudoarg =
            Pseudoarg(Mutability.IMMUTABLE, arrayScope, input.getValueOfSubNode(node.array)!!, node.array.tree!!)
        val indexPseudoarg = Pseudoarg(
            Mutability.IMMUTABLE, Scope(false, emptyList()), input.getValueOfSubNode(node.index)!!, node.index.tree!!
        )
        val pseudocall = Pseudocall(componentMutability, listOf(arrayPseudoarg, indexPseudoarg))

        return context(input.regularStore, node.tree!!, node) {
            processPseudocall(pseudocall)
        }
    }

    // endregion array stuff

    companion object {
        val ThisId = Id("this")
        val ImmutableFraction = Rational.HALF
    }

    override fun visitTypeCast(
        n: TypeCastNode, p: Input
    ): Result {
        if (n.type.kind.isPrimitive) {
            return doNothing(n, p)
        }
        return visitNode(n, p)
    }

    //region Noop visit functions
    override fun visitVariableDeclaration(
        n: VariableDeclarationNode, p: Input
    ): Result {
        return doNothing(n, p)
    }

    override fun visitMarker(
        n: MarkerNode, p: Input
    ): Result {
        return doNothing(n, p)
    }

    override fun visitTernaryExpression(
        n: TernaryExpressionNode, p: Input
    ): Result {
        return doNothing(n, p)
    }

    override fun visitNumericalPlus(
        n: NumericalPlusNode, p: Input
    ): Result {
        return doNothing(n, p)
    }

    override fun visitNumericalAddition(
        n: NumericalAdditionNode, p: Input
    ): Result {
        return doNothing(n, p)
    }

    override fun visitNumericalSubtraction(
        n: NumericalSubtractionNode, p: Input
    ): Result {
        return doNothing(n, p)
    }

    override fun visitLessThan(
        n: LessThanNode, p: Input
    ): Result {
        return doNothing(n, p)
    }

    override fun visitIntegerRemainder(
        n: IntegerRemainderNode, p: Input
    ): Result {
        return doNothing(n, p)
    }

    override fun visitEqualTo(
        n: EqualToNode, p: Input
    ): Result {
        return doNothing(n, p)
    }

    override fun visitExpressionStatement(
        n: ExpressionStatementNode, p: Input
    ): Result {
        return doNothing(n, p)
    }

    override fun visitMethodAccess(
        n: MethodAccessNode, p: Input
    ): Result {
        return doNothing(n, p)
    }

    override fun visitClassName(
        n: ClassNameNode, p: Input
    ): Result {
        return doNothing(n, p)
    }
    //endregion
}