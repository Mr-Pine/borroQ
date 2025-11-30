package de.mr_pine.borroq.analysis

import com.sun.source.tree.Tree
import de.mr_pine.borroq.BorroQChecker
import de.mr_pine.borroq.Messages
import de.mr_pine.borroq.Strictness
import de.mr_pine.borroq.analysis.exceptions.BorroQException
import de.mr_pine.borroq.analysis.exceptions.BorroQReportedException
import de.mr_pine.borroq.analysis.livevariable.LiveVarStore
import de.mr_pine.borroq.types.*
import de.mr_pine.borroq.types.BorroQValue.Companion.asValue
import de.mr_pine.borroq.types.IdentifiedPermission.Companion.withId
import de.mr_pine.borroq.types.specifiers.Mutability
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

    fun Node.regularResult(
        value: BorroQValue?, store: BorroQStore, storeChanged: Boolean = true
    ): RegularTransferResult<BorroQValue, BorroQStore> {
        val died =
            liveness.getStoreBefore(this)?.liveVariables.orEmpty() - liveness.getStoreAfter(this)?.liveVariables.orEmpty()
        for (variable in died) {
            store.killVariable(variable.liveVariable)
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
        val parameterPermissions = parameters.zip(signatureType.parameters).mapNotNull { (parameter, parameterType) ->
            val permission = when (parameterType?.mutability ?: return@mapNotNull null) {
                is Mutability.Mutable -> Permission(Rational.ONE)
                is Mutability.Immutable -> Permission(Rational.HALF)
            }
            val localVariable = JavaExpression.fromNode(parameter) as LocalVariable
            localVariable to (permission.withId(Id.fromNode(parameter)) as VariablePermission)
        }.toMap().toMutableMap()

        context(mutability: Mutability, paramSource: Tree) fun calculateBorrows(
            borrowBase: IdPath, baseType: TypeElement, paths: List<PathTail>
        ): List<Borrow> {
            val typeFields = ElementUtils.getAllFieldsIn(baseType, checker.elementUtils)
            val unmentioned = typeFields.filter { field -> paths.none { it.fields.first() == field } }
            val unmentionedBorrows = unmentioned.map { field ->
                val fraction = memberTypeAnalysis.getFieldMutability(field)
                    .let { if (it is Mutability.Mutable) Rational.ONE else Rational.HALF }
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
            is Mutability.Immutable -> Permission(Rational.HALF)
        }?.withId(Id("this"))
        val receiverBorrows = if (signatureType.receiverType != null) run {
            val baseType = TreeUtils.elementFromDeclaration(methodAST.classTree)

            val onPaths = signatureType.receiverType!!.mutability.onPaths ?: return@run emptyList()

            context(
                signatureType.receiverType.mutability, methodAST.method.receiverParameter ?: methodAST.method!!
            ) { calculateBorrows(IdPath(Id("this")), baseType, onPaths) }
        } else if (TreeUtils.isConstructor(methodAST.method)) {
            val baseType = TreeUtils.elementFromDeclaration(methodAST.classTree)
            context(
                Mutability.Mutable(emptyList()), methodAST.method.receiverParameter ?: methodAST.method!!
            ) { calculateBorrows(IdPath(Id("this")), baseType, emptyList()) }
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
        try {
            val methodType = memberTypeAnalysis.getType(node.target.method)

            /* TODO: Reintroduce
            if (node.target.method.isConstructor && methodType.returnMutability == Mutability.IMMUTABLE && signatureType.returnMutability == Mutability.MUTABLE) exceptionReportContext(
                node.tree!!
            ) {
                throw IncompatibleSuperConstructorMutability()
            }
             */

            val outputStore = input.regularStore

            fun receiverTree() = when (node.target.receiver) {
                is ImplicitThisNode -> node.target.tree!!
                else -> node.target.receiver.tree!!
            }

            val receiverPermission = if (methodType.receiverType != null) exceptionReportContext(receiverTree()) {
                when (node.target.receiver) {
                    is ThisNode -> outputStore.chooseAndRemoveThisReceiverPermission(methodType.receiverType.mutability)
                    else -> outputStore.chooseAndRemoveArgumentPermission(
                        node.target.receiver, methodType.receiverType.mutability
                    )
                }
            } else {
                null
            }

            val argumentPermissions = node.arguments.zip(methodType.parameters).map { (arg, type) ->
                if (type == null) return@map null
                exceptionReportContext(arg.tree!!) {
                    outputStore.chooseAndRemoveArgumentPermission(
                        arg, type.mutability
                    )
                }
            }

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

            return node.regularResult(null, outputStore)
        } catch (_: BorroQReportedException) {
            return node.regularResult(null, input.regularStore)
        }
    }

    override fun visitAssignment(
        node: AssignmentNode, input: Input
    ): Result {
        require(!input.containsTwoStores()) { "Assignment node $node has two stores" }

        if (node.target !is LocalVariableNode) {
            return visitNode(node, input)
        }

        fun result(value: BorroQValue, storeUpdate: BorroQStore.() -> Unit): Result {
            val store = input.getRegularStore()
            store.storeUpdate()
            return node.regularResult(value, store)
        }

        val targetAnnotation = node.target.tree?.let {
            annotationQuery.getAssignmentLeftSideAnnotations(it)
                ?.let { Mutability.fromAnnotationsOnType(it, TypesUtils.getTypeElement(node.target.type)) }
        }

        return when (val rhsValue = input.getValueOfSubNode(node.expression)!!) {
            is BorroQValue.FreePermission -> {
                val targetPermission = rhsValue.permission.withId(Id.fromNode(node.target))
                result(targetPermission) { updatePermission(node.target, targetPermission) }
            }

            is IdentifiedPermission -> {
                val (targetPermission, remainingPermission) = rhsValue.split(targetAnnotation)
                result(targetPermission) {
                    updatePermission(node.target, targetPermission)
                    when (node.expression) {
                        is LocalVariableNode -> updatePermission(node.expression, remainingPermission)
                        is ThisNode -> result(rhsValue) { updateThisPermission(remainingPermission) }
                        else -> throw IllegalStateException("Unexpected expression type ${node.expression}")
                    }
                }
            }

            is BorroQValue.FieldAccess -> {
                val (usedVariablePermission, _) = rhsValue.fieldPermission.split(targetAnnotation)
                val targetId = Id.fromNode(node.target)
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
                        updatePermission(node.target, variablePermission)
                        addBorrow(borrow)
                    }
                }
            }

            else -> TODO()
        }

    }

    override fun visitFieldAccess(
        node: FieldAccessNode, input: Input
    ): Result {
        require(!input.containsTwoStores()) { "Field access node $node has two stores" }

        val fieldPermission = memberTypeAnalysis.getFieldMutability(node.element)
            .let { if (it is Mutability.Mutable) Permission(Rational.ONE) else Permission(Rational.HALF) } // TODO: Correctly handle restrictions

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
            Permission(Rational.ONE).asValue(), p.regularStore
        )
    }

    override fun visitReturn(
        node: ReturnNode, input: Input
    ): Result = try {
        exceptionReportContext(node.tree!!) {
            require(!input.containsTwoStores()) { "Return node $node with two stores" }
            if (node.result == null) return node.regularResult(null, input.regularStore, false)
            val returnPermission = input.getValueOfSubNode(node.result)!!

            /* TODO: Reintroduce with correct restriction handling
            when (signatureType.returnMutability!!) {
                Mutability.MUTABLE -> if (!returnPermission.hasShallowMutability) throw IncompatibleReturnPermission(
                    returnPermission, signatureType.returnMutability
                )

                Mutability.IMMUTABLE -> if (!returnPermission.hasShallowReadability) throw IncompatibleReturnPermission(
                    returnPermission, signatureType.returnMutability
                )
            }
             */
            node.regularResult(null, input.regularStore, false)
        }
    } catch (_: BorroQReportedException) {
        node.regularResult(null, input.regularStore, false)
    }

    override fun visitValueLiteral(node: ValueLiteralNode, p: Input): Result {
        return node.regularResult(
            BorroQValue.FreePermission(Permission(Rational.HALF)), p.regularStore
        )
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