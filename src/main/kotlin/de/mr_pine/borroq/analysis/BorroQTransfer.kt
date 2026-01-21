@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package de.mr_pine.borroq.analysis

import com.sun.source.tree.Tree
import com.sun.tools.javac.code.Type
import com.sun.tools.javac.tree.JCTree
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
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.ArrayType
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

    private val Mutability.fraction: Rational
        get() = when (this) {
            is Mutability.Mutable -> Rational.ONE
            is Mutability.Immutable -> ImmutableFraction
        }

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
                    ?.let { if (it is Mutability.Mutable) Rational.ONE else ImmutableFraction } ?: ImmutableFraction
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

    context(store: BorroQStore, temporaryBorrows: MutableList<Borrow>)
    private fun processReceiverOrParameter(type: SignatureType.ParameterType, basePath: Path) {
        if (basePath.isStatic) return

        for (pathTail in type.mutability.onPaths ?: listOf(PathTail(emptyList()))) {
            val path = basePath.with(pathTail)
            context(store, store.getBorrows(), memberTypeAnalysis) {
                val allowsRequiredMutability = if (type.mutability is Mutability.Mutable) path.asIdPath()
                    .allowsDeepMutability() else path.asIdPath().allowsDeepReadability()
                if (!allowsRequiredMutability) {
                    throw InsufficientDeepPermissionException(path, type.mutability)
                }
            }
        }

        for ((pathTail, releaseMode) in type.releaseMode.pathsToSingleReleaseMode()
            .filterValues { it is SingleReleaseMode.Borrow || it is SingleReleaseMode.Move }) {
            val path = basePath.with(pathTail)
            val fraction = when (type.mutability) {
                is Mutability.Mutable -> Rational.ONE
                is Mutability.Immutable -> ImmutableFraction
            }
            val tempBorrow = Borrow(path.asIdPath(), fraction, Borrow.Identifier.Dummy)
            store.addBorrow(tempBorrow)

            if (releaseMode is SingleReleaseMode.Borrow) temporaryBorrows.add(tempBorrow)
        }
    }

    private fun VariablePermission?.validateMutability(tree: Tree, mutability: Mutability) = also {
        silentExceptionReportContext(tree) {
            when (mutability) {
                is Mutability.Mutable -> if (!(it?.hasShallowMutability
                        ?: false)
                ) throw InsufficientShallowPermissionException(
                    "this", mutability, it
                )

                is Mutability.Immutable -> if (!(it?.hasShallowReadability
                        ?: true)
                ) throw InsufficientShallowPermissionException(
                    "this", mutability, it
                )
            }
        }
    }

    context(store: BorroQStore, tree: Tree, node: Node)
    private fun processCallLike(
        signature: SignatureType, receiver: Node?, arguments: List<Node>
    ): RegularTransferResult<BorroQValue, BorroQStore> {
        val temporaryBorrows = mutableListOf<Borrow>()
        val receiverTree = receiver?.tree ?: tree
        val returnPermission = signature.returnMutability?.let { Permission(it.fraction) }

        try {
            val receiverPermission = if (signature.receiverType != null) {
                require(receiver != null) { "Receiver is null but non-null return type in signature: ${signature.receiverType}" }

                val permission = exceptionReportContext(receiverTree) {
                    when (receiver) {
                        is ThisNode -> store.chooseAndRemoveThisReceiverPermission(signature.receiverType.mutability)
                        else -> store.chooseAndRemoveArgumentPermission(
                            receiver, signature.receiverType.mutability
                        )
                    }.validateMutability(receiverTree, signature.receiverType.mutability)
                }
                val receiverPath = Path.fromNode(receiver)
                context(temporaryBorrows) {
                    silentExceptionReportContext(receiverTree) {
                        processReceiverOrParameter(
                            signature.receiverType, receiverPath
                        )
                    }
                }

                permission
            } else {
                null
            }

            val argumentPermissions = arguments.zip(signature.parameters).map { (arg, type) ->
                if (type == null) return@map null
                val permission = exceptionReportContext(arg.tree!!) {
                    store.chooseAndRemoveArgumentPermission(
                        arg, type.mutability
                    ).validateMutability(arg.tree!!, type.mutability)
                }

                context(temporaryBorrows) {
                    silentExceptionReportContext(arg.tree!!) { processReceiverOrParameter(type, Path.fromNode(arg)) }
                }

                permission
            }

            /*
             * Imaginary method execution here
             */


            val receiverData = if (signature.receiverType != null) listOf(
                Triple(
                    receiver.takeIf { it !is ThisNode }, signature.receiverType, receiverPermission!!
                )
            ) else emptyList()

            val argumentData = arguments.zip(signature.parameters).zip(argumentPermissions).map { (p, z) ->
                val (x, y) = p
                Triple(x, y, z)
            }

            val freeBorrows = mutableListOf<BorroQValue.FreePermission.FreeBorrow>()
            for ((argument, type, permission) in argumentData + receiverData) {
                when (type?.releaseMode) {
                    null -> {}
                    is ReleaseMode.Mixed -> {
                        store.recombineNodeOrThis(argument, permission!!)
                    }

                    is SingleReleaseMode if type.releaseMode.onPaths.orEmpty().isNotEmpty() -> {
                        store.recombineNodeOrThis(argument, permission!!)
                    }

                    is SingleReleaseMode.Release -> {
                        store.recombineNodeOrThis(argument, permission!!)
                    }

                    is SingleReleaseMode.Borrow, is SingleReleaseMode.Move -> {}
                }
            }

            freeBorrows.run {
                for (borrow in temporaryBorrows) {
                    store.removeBorrow(borrow)
                    add(BorroQValue.FreePermission.FreeBorrow(borrow.path, borrow.fraction))
                }
            }

            val freePermission = returnPermission?.let { BorroQValue.FreePermission(it, freeBorrows) }

            return node.regularResult(freePermission, store)
        } catch (_: BorroQReportedException) {
            return node.regularResult(
                returnPermission?.let { BorroQValue.FreePermission(it, emptyList()) }, store
            )
        }
    }


    override fun visitMethodInvocation(
        node: MethodInvocationNode, input: Input
    ): Result {
        require(!input.containsTwoStores()) { "Method invocation node $node has two stores" }

        val methodType = memberTypeAnalysis.getType(node.target, exceptionReportingContext = { block ->
            try {
                exceptionReportContext(node.tree!!, block)
            } catch (_: BorroQReportedException) {
            }
        })

        if (node.target.method.isConstructor && methodType.returnMutability is Mutability.Immutable && signatureType.returnMutability is Mutability.Mutable) silentExceptionReportContext(
            node.tree!!
        ) {
            throw IncompatibleSuperConstructorMutability()
        }

        return context(input.regularStore, node.target.tree!!, node) {
            processCallLike(
                methodType, node.target.receiver, node.arguments
            )
        }
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
                ?.let { Mutability.fromAnnotationsOnType(it, TypesUtils.getTypeElement(target.type)) }
        }
        require(targetMutabilityAnnotation?.onPaths == null) { "Local variable mutability annotation cannot be restricted on paths" }

        return when (val rhsValue =
            input.getValueOfSubNode(node.expression) ?: return node.regularResult(null, input.regularStore, false)) {
            is BorroQValue.FreePermission -> {
                val targetId = Id.fromNode(target)

                val unfulfilledMutability =
                    targetMutabilityAnnotation is Mutability.Mutable && rhsValue.permission.fraction < Rational.ONE
                val unfulfilledReadability =
                    targetMutabilityAnnotation is Mutability.Mutable && rhsValue.permission.fraction.isZero()
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

                if (mutability is Mutability.Mutable && targetPermission.fraction < Rational.ONE) silentExceptionReportContext(
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

            is ClassNameNode -> Path(PathRoot.StaticPathRoot)
            else -> throw IllegalStateException("Unexpected receiver type ${node.receiver} ${node.receiver.javaClass}")
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
        node: ObjectCreationNode, input: Input
    ): Result {
        val constructorElement: ExecutableElement = TreeUtils.elementFromUse(node.tree!!)
        val signature = memberTypeAnalysis.getType(constructorElement) {
            silentExceptionReportContext(node.tree!!) { it() }
        }

        return context(input.regularStore, node.tree!!, node) { processCallLike(signature, node, node.arguments) }
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
                            is Mutability.Immutable -> ImmutableFraction
                            is Mutability.Mutable -> Rational.ONE
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
        return node.regularResult(
            null, p.regularStore
        )
    }

    // region array stuff
    override fun visitArrayCreation(node: ArrayCreationNode, input: Input): Result {
        val arguments = List(node.initializers.size) {
            val elementType = (node.type as Type.ArrayType).elemtype
            if (elementType.isPrimitive) return@List null

            val elementMutability =
                Mutability.fromAnnotationsOnType(elementType.annotationMirrors, TypesUtils.getTypeElement(elementType))
                    ?: DefaultInference.inferArrayElementMutability()

            SignatureType.ParameterType(elementMutability, SingleReleaseMode.Borrow(null))
        }
        val syntheticSignature = SignatureType(Mutability.Mutable(null), null, arguments)

        return context(input.regularStore, node.tree!!, node) {
            processCallLike(
                syntheticSignature,
                null,
                node.initializers
            )
        }
    }

    override fun visitArrayAccess(
        node: ArrayAccessNode,
        p: Input
    ): Result {
        val componentMutability = node.array.let {
            (it.tree as? JCTree.JCIdent)?.sym?.asType() ?: it.type
        }.let { it as ArrayType }.componentType.let {
            Mutability.fromAnnotationsOnType(
                it.annotationMirrors,
                TypesUtils.getTypeElement(it)
            )
        } ?: DefaultInference.inferTypeParameterMutability()

        val syntheticSignatureType = SignatureType(
            componentMutability,
            SignatureType.ParameterType(
                Mutability.Immutable(null), SingleReleaseMode.Borrow(null)
            ),
            listOf(null)
        )
        return context(p.regularStore, node.tree!!, node) {
            processCallLike(
                syntheticSignatureType,
                node.array,
                listOf(node.index)
            )
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