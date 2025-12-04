package de.mr_pine.borroq.analysis

import com.sun.source.tree.Tree
import de.mr_pine.borroq.BorroQChecker
import de.mr_pine.borroq.Messages
import de.mr_pine.borroq.Strictness
import de.mr_pine.borroq.analysis.exceptions.*
import de.mr_pine.borroq.analysis.livevariable.LiveVarNode
import de.mr_pine.borroq.analysis.livevariable.LiveVarStore
import de.mr_pine.borroq.isConstructor
import de.mr_pine.borroq.types.*
import de.mr_pine.borroq.types.IdentifiedPermission.Companion.withId
import de.mr_pine.borroq.types.specifiers.Mutability
import de.mr_pine.borroq.types.specifiers.ReleaseMode
import de.mr_pine.borroq.types.specifiers.ReleaseMode.SingleReleaseMode
import org.checkerframework.dataflow.analysis.*
import org.checkerframework.dataflow.cfg.UnderlyingAST
import org.checkerframework.dataflow.cfg.node.*
import org.checkerframework.dataflow.expression.JavaExpression
import org.checkerframework.dataflow.expression.LocalVariable
import org.checkerframework.javacutil.ElementUtils
import org.checkerframework.javacutil.TreeUtils
import org.checkerframework.javacutil.TypesUtils
import javax.lang.model.element.TypeElement
import kotlin.contracts.ExperimentalContracts

private typealias Result = TransferResult<BorroQValue, BorroQStore>
private typealias Input = TransferInput<BorroQValue, BorroQStore>

class BorroQTransfer(
    private val signatureType: SignatureType,
    private val memberTypeAnalysis: MemberTypeAnalysis,
    private val liveness: AnalysisResult<UnusedAbstractValue, LiveVarStore>,
    private val checker: BorroQChecker,
    private val annotationQuery: AnnotationQuery,
    private val strictness: Strictness
) : AbstractNodeVisitor<Result, Input>(), ForwardTransferFunction<BorroQValue, BorroQStore> {

    private var parameters: List<LocalVariableNode>? = null

    fun Node.regularResult(
        value: BorroQValue?, store: BorroQStore, storeChanged: Boolean = true
    ): RegularTransferResult<BorroQValue, BorroQStore> {
        val died =
            liveness.getStoreBefore(this)?.liveVariables.orEmpty() - liveness.getStoreAfter(this)?.liveVariables.orEmpty()
        for (variable in died) {
            store.killVariable(variable.liveVariable)
        }

        fun Set<LiveVarNode>?.liveIds() =
            orEmpty().asSequence().map { it.liveVariable }.filterIsInstance<LocalVariableNode>()
                .mapNotNull { store.queryPermission(it) as IdentifiedPermission? }.map { it.id }.toSet()

        val diedIds =
            liveness.getStoreBefore(this)?.liveVariables.liveIds() - liveness.getStoreAfter(this)?.liveVariables.liveIds()

        for (id in diedIds) {
            val removedBorrows = store.removeBorrowsWithId(id)
            removedBorrows.filter { it.path.tail.fields.isEmpty() }.forEach {
                store.recombineAny(Permission(it.fraction).withId(it.path.id))
            }
        }

        return RegularTransferResult(value, store, storeChanged)
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

        val parameterPermissions = parameters.zip(signatureType.parameters).mapNotNull { (parameter, parameterType) ->
            val permission = when (parameterType?.mutability ?: return@mapNotNull null) {
                is Mutability.Mutable -> Permission(Rational.ONE)
                is Mutability.Immutable -> Permission(ImmutableFraction)
            }
            val localVariable = JavaExpression.fromNode(parameter) as LocalVariable
            localVariable to (permission.withId(Id.fromNode(parameter)) as VariablePermission)
        }.toMap().toMutableMap()

        context(mutability: Mutability, paramSource: Tree)
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
                    .let { if (it is Mutability.Mutable) Rational.ONE else ImmutableFraction }
                Borrow(borrowBase.with(field), fraction, Borrow.Identifier.Dummy)
            }

            val mentionedBorrows = paths.groupBy { it.fields.first() }.flatMap { (field, paths) ->
                if (paths.size == 1 && paths.single().fields.size == 1) {
                    if (memberTypeAnalysis.getFieldMutability(field) !is Mutability.Mutable && mutability is Mutability.Mutable) checker.reportWarning(
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
            if (parameterType?.mutability?.onPaths != null) context(parameterType.mutability, parameter.tree!!) {
                val baseType = TypesUtils.getTypeElement(parameter.type)!!
                calculateBorrows(IdPath(Id.fromNode(parameter)), baseType, parameterType.mutability.onPaths!!)
            } else {
                emptyList()
            }
        }

        val methodAST = underlyingAST as UnderlyingAST.CFGMethod
        val receiverPermission = when (signatureType.receiverType?.mutability) {
            null -> if (TreeUtils.isConstructor(methodAST.method)) Permission(Rational.ONE) else null

            is Mutability.Mutable -> Permission(Rational.ONE)
            is Mutability.Immutable -> Permission(ImmutableFraction)
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
                Mutability.Mutable(emptyList()), methodAST.method.receiverParameter ?: methodAST.method!!
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
        val source = node.tree

        if (source == null) {
            System.err.println("Tree of node $node is null")
        } else {
            when (strictness) {
                Strictness.STRICT -> checker.reportError(source, Messages.UNKNOWN_TREE_ENCOUNTERED, source.kind)
                Strictness.WARN_UNKNOWN -> checker.reportWarning(source, Messages.UNKNOWN_TREE_ENCOUNTERED, source.kind)
                Strictness.ALLOW_UNKNOWN -> {}
            }
        }

        return node.regularResult(null, p.regularStore, false)
    }


    override fun visitMethodInvocation(
        node: MethodInvocationNode, input: Input
    ): Result {
        require(!input.containsTwoStores()) { "Method invocation node $node has two stores" }

        val methodType = memberTypeAnalysis.getType(node.target.method, exceptionReportingContext = { block ->
            try {
                exceptionReportContext(node.tree!!, block)
            } catch (_: BorroQReportedException) {
            }
        })

        val returnPermission = when (methodType.returnMutability) {
            is Mutability.Mutable -> Permission(Rational.ONE)
            is Mutability.Immutable -> Permission(ImmutableFraction)
            null -> null
        }

        try {

            if (node.target.method.isConstructor && methodType.returnMutability is Mutability.Immutable && signatureType.returnMutability is Mutability.Mutable) exceptionReportContext(
                node.tree!!
            ) {
                throw IncompatibleSuperConstructorMutability()
            }

            val outputStore = input.regularStore

            fun receiverTree() = when (node.target.receiver) {
                is ImplicitThisNode -> node.target.tree!!
                else -> node.target.receiver.tree!!
            }

            val temporaryBorrows = mutableListOf<Borrow>()

            fun processReceiverOrParameter(type: SignatureType.ParameterType, basePath: Path) {
                val releasePathMapping = type.releaseMode.pathsToSingleReleaseMode()
                for (pathTail in type.mutability.onPaths ?: listOf(PathTail(emptyList()))) {
                    val path = basePath.with(pathTail)
                    context(outputStore, outputStore.getBorrows(), memberTypeAnalysis) {
                        val allowsRequiredMutability = if (type.mutability is Mutability.Mutable) path.asIdPath()
                            .allowsDeepMutability() else path.asIdPath().allowsDeepReadability()
                        if (!allowsRequiredMutability) {
                            throw InsufficientDeepPermissionException(path, type.mutability)
                        }
                    }
                }

                for ((pathTail, releaseMode) in releasePathMapping.filterValues { it is SingleReleaseMode.Borrow || it is SingleReleaseMode.Move }) {
                    val path = basePath.with(pathTail)
                    val fraction = when (type.mutability) {
                        is Mutability.Mutable -> Rational.ONE
                        is Mutability.Immutable -> ImmutableFraction
                    }
                    val tempBorrow =
                        Borrow(context(input.regularStore) { path.asIdPath() }, fraction, Borrow.Identifier.Dummy)
                    input.regularStore.addBorrow(tempBorrow)

                    if (releaseMode is SingleReleaseMode.Borrow) temporaryBorrows.add(tempBorrow)
                }
            }

            val receiverPermission = if (methodType.receiverType != null) exceptionReportContext(receiverTree()) {
                val permission = when (node.target.receiver) {
                    is ThisNode -> outputStore.chooseAndRemoveThisReceiverPermission(methodType.receiverType.mutability)
                    else -> outputStore.chooseAndRemoveArgumentPermission(
                        node.target.receiver, methodType.receiverType.mutability
                    )
                }
                val receiverPath = Path.fromNode(node.target.receiver)
                processReceiverOrParameter(methodType.receiverType, receiverPath)

                permission
            } else {
                null
            }

            val argumentPermissions = node.arguments.zip(methodType.parameters).map { (arg, type) ->
                if (type == null) return@map null
                exceptionReportContext(arg.tree!!) {
                    val permission = outputStore.chooseAndRemoveArgumentPermission(
                        arg, type.mutability
                    )

                    processReceiverOrParameter(type, Path.fromNode(arg))

                    permission
                }
            }

            /*
             * Imaginary method execution here
             */

            val argumentData = node.arguments.zip(methodType.parameters).zip(argumentPermissions).map { (p, z) ->
                val (x, y) = p
                Triple(x, y, z)
            }
            for ((argument, type, permission) in argumentData) {
                when (type?.releaseMode) {
                    null -> {}
                    is SingleReleaseMode.Release if type.releaseMode.onPaths.orEmpty().isEmpty() -> {
                        outputStore.recombine(argument, permission!!)
                    }

                    else -> TODO()
                }
            }

            if (methodType.receiverType != null) {
                when (node.target.receiver) {
                    is ThisNode -> outputStore.recombineThis(receiverPermission!!)
                    else -> outputStore.recombine(node.target.receiver, receiverPermission!!)
                }
            }

            val freeBorrows = buildList {
                for (borrow in temporaryBorrows) {
                    outputStore.removeBorrow(borrow)
                    add(BorroQValue.FreePermission.FreeBorrow(borrow.path, borrow.fraction))
                }
            }
            val freePermission = returnPermission?.let { BorroQValue.FreePermission(it, freeBorrows) }

            return node.regularResult(freePermission, outputStore)
        } catch (_: BorroQReportedException) {
            return node.regularResult(
                returnPermission?.let { BorroQValue.FreePermission(it, emptyList()) }, input.regularStore
            )
        }
    }

    //region assignment
    fun visitLocalVariableAssignment(node: AssignmentNode, target: LocalVariableNode, input: Input): Result {
        fun result(value: BorroQValue, storeUpdate: BorroQStore.() -> Unit): Result {
            val store = input.getRegularStore()
            store.storeUpdate()
            return node.regularResult(value, store)
        }

        val targetAnnotation = target.tree?.let {
            annotationQuery.getAssignmentLeftSideAnnotations(it)
                ?.let { Mutability.fromAnnotationsOnType(it, TypesUtils.getTypeElement(target.type)) }
        }
        require(targetAnnotation?.onPaths == null) { "Local variable mutability annotation cannot be restricted on paths" }

        return when (val rhsValue =
            input.getValueOfSubNode(node.expression) ?: return node.regularResult(null, input.regularStore, false)) {
            is BorroQValue.FreePermission -> {
                val targetId = Id.fromNode(target)
                val targetPermission = rhsValue.permission.withId(targetId)
                val borrows = rhsValue.attachedBorrows.map { it.toBorrow(targetId) }
                result(targetPermission) {
                    updatePermission(target, targetPermission)
                    for (borrow in borrows) addBorrow(borrow)
                }
            }

            is IdentifiedPermission -> {
                val (targetPermission, remainingPermission) = rhsValue.split(targetAnnotation)
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
                val (usedVariablePermission, _) = rhsValue.fieldPermission.split(targetAnnotation)
                val targetId = Id.fromNode(target)
                val borrow = Borrow(
                    context(input.regularStore) { rhsValue.access.asIdPath() },
                    usedVariablePermission.fraction,
                    targetId
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

    fun visitFieldAssignment(node: AssignmentNode, target: FieldAccessNode, input: Input): Result {
        val fieldMutability = memberTypeAnalysis.getFieldMutability(target.element) ?: return node.regularResult(
            null, input.regularStore, false
        )
        val fieldFraction = when (fieldMutability) {
            is Mutability.Mutable -> Rational.ONE
            is Mutability.Immutable -> ImmutableFraction
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

        val receiverPath = Path.fromNode(target.receiver)
        val pathPermission =
            context(input.regularStore, memberTypeAnalysis) { receiverPath.permission() }!!.withId(Id(""))

        try {
            if (!pathPermission.hasShallowMutability) exceptionReportContext(target.tree!!) {
                throw InsufficientShallowAssignmentTargetReceiverPermissionException(receiverPath, pathPermission)
            }

            if (newValueFraction < fieldFraction) exceptionReportContext(node.tree!!) {
                throw InsufficientShallowAssignmentExpressionPermissionException(
                    node.expression, fieldFraction, newValuePermission
                )
            }

            // Conflicting borrow special case for "hidden" fields
            val hasConflictingBorrow =
                input.regularStore.getBorrows().any { it.path == receiverPath && it.id == Borrow.Identifier.Dummy }
            if (hasConflictingBorrow) exceptionReportContext(node.target.tree!!) {
                throw HiddenFieldAssignedException()
            }
        } catch (_: BorroQReportedException) {
        }

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

    override fun visitAssignment(
        node: AssignmentNode, input: Input
    ): Result {
        require(!input.containsTwoStores()) { "Assignment node $node has two stores" }

        return when (val target = node.target) {
            is LocalVariableNode -> visitLocalVariableAssignment(node, target, input)
            is FieldAccessNode -> visitFieldAssignment(node, target, input)
            else -> visitNode(node, input)
        }
    }
    //endregion

    override fun visitFieldAccess(
        node: FieldAccessNode, input: Input
    ): Result {
        require(!input.containsTwoStores()) { "Field access node $node has two stores" }

        val fieldPermission = memberTypeAnalysis.getFieldMutability(node.element)
            .let { if (it is Mutability.Mutable) Permission(Rational.ONE) else Permission(ImmutableFraction) } // TODO: Correctly handle restrictions

        val receiverPath = when (node.receiver) {
            is FieldAccessNode -> {
                val receiverValue = input.getValueOfSubNode(node.receiver) as BorroQValue.FieldAccess
                receiverValue.access
            }

            is ThisNode -> Path(PathRoot.ThisPathRoot)
            is LocalVariableNode -> Path(PathRoot.LocalVariableRoot(JavaExpression.fromNode(node.receiver) as LocalVariable))
            else -> throw IllegalStateException("Unexpected receiver type ${node.receiver}")
        }

        val path = receiverPath.with(node.element)

        return node.regularResult(BorroQValue.FieldAccess(path, fieldPermission), input.regularStore, false)
    }

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

    override fun visitObjectCreation(
        n: ObjectCreationNode, p: Input
    ): Result {
        return RegularTransferResult(
            BorroQValue.FreePermission(Permission(Rational.ONE), emptyList()), p.regularStore
        )
    }

    override fun visitReturn(
        node: ReturnNode, input: Input
    ): Result = try {
        exceptionReportContext(node.tree!!) {
            require(!input.containsTwoStores()) { "Return node $node with two stores" }
            require(signatureType.returnMutability!!.onPaths == null) { "Return type cannot be restricted on paths" }

            if (node.result == null) return node.regularResult(null, input.regularStore, false)

            input.getValueOfSubNode(node.result)?.let {
                val (basePermission, returnPath) = when (it) {
                    is BorroQValue.FieldAccess -> {
                        val idPath = context(input.regularStore) { it.access.asIdPath() }
                        val base = when (it.access.root) {
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
                        is Mutability.Mutable -> {
                            if (!basePermission.hasShallowMutability) throw IncompatibleReturnPermissionException(
                                signatureType.returnMutability
                            )
                            if (returnPath?.allowsDeepMutability() == false) throw IncompatibleReturnPermissionException(
                                signatureType.returnMutability
                            )
                        }

                        is Mutability.Immutable -> {
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

            fun validateReleaseMode(baseId: Id, releaseMode: ReleaseMode) {
                val pathsReleaseMapping = releaseMode.pathsToSingleReleaseMode()
                for ((pathTail, releaseMode) in pathsReleaseMapping) {
                    val path = IdPath(baseId, pathTail)

                    when (releaseMode) {
                        is SingleReleaseMode.Release, is SingleReleaseMode.Borrow -> {
                            val permissionSum = input.regularStore.localPermissionSum(baseId)
                            val permissionTarget = when (signatureType.receiverType?.mutability) {
                                is Mutability.Immutable -> ImmutableFraction
                                is Mutability.Mutable -> Rational.ONE
                                null -> Rational.ZERO
                            }

                            if (permissionTarget > permissionSum) throw ReleasePermissionMissingException(baseId.name)

                            val borrowIdExclude: Borrow.Identifier? =
                                if (releaseMode is SingleReleaseMode.Borrow) (input.getValueOfSubNode(node.result) as? IdentifiedPermission)?.id else null

                            val conflictingPaths = input.regularStore.getBorrows()
                                .filter { it.path.isPrefixOf(path) || path.isPrefixOf(it.path) }
                                .filter { it.id != borrowIdExclude }
                            if (conflictingPaths.isNotEmpty()) {
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

            node.regularResult(null, input.regularStore, false)
        }
    } catch (_: BorroQReportedException) {
        node.regularResult(null, input.regularStore, false)
    }

    override fun visitStringLiteral(
        node: StringLiteralNode, input: Input
    ): Result {
        return node.regularResult(
            BorroQValue.FreePermission(Permission(ImmutableFraction), emptyList()), input.regularStore
        )
    }

    override fun visitValueLiteral(node: ValueLiteralNode, p: Input): Result {
        return node.regularResult(
            null, p.regularStore
        )
    }

    companion object {
        val ThisId = Id("this")
        val ImmutableFraction = Rational.HALF
    }

    //region Noop visit functions
    override fun visitVariableDeclaration(
        n: VariableDeclarationNode, p: Input
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