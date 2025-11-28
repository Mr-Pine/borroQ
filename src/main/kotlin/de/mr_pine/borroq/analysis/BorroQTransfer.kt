package de.mr_pine.borroq.analysis

import com.sun.source.tree.Tree
import de.mr_pine.borroq.BorroQChecker
import de.mr_pine.borroq.Messages
import de.mr_pine.borroq.Strictness
import de.mr_pine.borroq.analysis.exceptions.BorroQException
import de.mr_pine.borroq.analysis.exceptions.BorroQReportedException
import de.mr_pine.borroq.analysis.exceptions.IncompatibleReturnPermission
import de.mr_pine.borroq.analysis.exceptions.IncompatibleSuperConstructorMutability
import de.mr_pine.borroq.analysis.livevariable.LiveVarStore
import de.mr_pine.borroq.isConstructor
import de.mr_pine.borroq.types.*
import de.mr_pine.borroq.types.SignatureType.ArgumentType.Companion.ReleaseMode.*
import org.checkerframework.dataflow.analysis.*
import org.checkerframework.dataflow.cfg.UnderlyingAST
import org.checkerframework.dataflow.cfg.node.*
import kotlin.contracts.ExperimentalContracts

private typealias Result = TransferResult<PermissionValue, BorroQStore>
private typealias Input = TransferInput<PermissionValue, BorroQStore>

class BorroQTransfer(
    private val signatureType: SignatureType,
    private val signatureTypeAnalysis: SignatureTypeAnalysis,
    private val liveness: AnalysisResult<UnusedAbstractValue, LiveVarStore>,
    private val checker: BorroQChecker,
    private val annotationQuery: AnnotationQuery,
    private val strictness: Strictness
) :
    AbstractNodeVisitor<Result, Input>(), ForwardTransferFunction<PermissionValue, BorroQStore> {

    fun Node.regularResult(
        value: PermissionValue?,
        store: BorroQStore,
        storeChanged: Boolean = true
    ): RegularTransferResult<PermissionValue, BorroQStore> {
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
        underlyingAST: UnderlyingAST?, parameters: List<LocalVariableNode?>?
    ): BorroQStore {
        return BorroQStore()
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
            val methodType = signatureTypeAnalysis.getType(node.target.method)

            if (node.target.method.isConstructor && methodType.returnMutability == Mutability.IMMUTABLE && signatureType.returnMutability == Mutability.MUTABLE) exceptionReportContext(
                node.tree!!
            ) {
                throw IncompatibleSuperConstructorMutability()
            }

            val outputStore = input.regularStore

            fun receiverTree() = when (node.target.receiver) {
                is ExplicitThisNode -> node.target.receiver.tree!!
                is ImplicitThisNode -> node.target.tree!!
                else -> throw IllegalStateException("Unexpected receiver type ${node.target.receiver}")
            }

            val receiverPermission =
                if (methodType.receiverType != null) exceptionReportContext(receiverTree()) {
                    when (node.target.receiver) {
                        is ThisNode -> outputStore.chooseAndRemoveThisReceiverPermission(methodType.receiverType.mutability)
                        else -> outputStore.chooseAndRemoveArgumentPermission(
                            node.target.receiver,
                            methodType.receiverType.mutability
                        )
                    }
                } else {
                    null
                }

            val argumentPermissions = node.arguments.zip(methodType.arguments).map { (arg, type) ->
                exceptionReportContext(arg.tree!!) {
                    outputStore.chooseAndRemoveArgumentPermission(
                        arg,
                        type.mutability
                    )
                }
            }

            val argumentData = node.arguments.zip(methodType.arguments).zip(argumentPermissions).map { (p, z) ->
                val (x, y) = p
                Triple(x, y, z)
            }
            for ((argument, type, permission) in argumentData) {
                when (type.releaseMode) {
                    RELEASE -> {
                        outputStore.recombine(argument, permission)
                    }

                    BORROW -> TODO("Borrow release mode")
                    MOVE -> TODO("Move release mode")
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
        if (node.target !is LocalVariableNode) {
            return visitNode(node, input)
        }

        fun result(value: PermissionValue, storeUpdate: BorroQStore.() -> Unit): Result {
            return if (node.isSynthetic && input.containsTwoStores()) {
                // This is a synthetic assignment node created for a ternary expression. In this case
                // the `then` and `else` store are not merged.
                val thenStore = input.getThenStore()!!
                val elseStore = input.getElseStore()!!
                visitNode(node, input)/*processCommonAssignment(`in`, lhs, rhs, thenStore, rhsValue)
                processCommonAssignment(`in`, lhs, rhs, elseStore, rhsValue)
                return ConditionalTransferResult<V?, S?>(
                    finishValue(rhsValue, thenStore, elseStore), thenStore, elseStore
                )*/
            } else {
                val store = input.getRegularStore()
                store.storeUpdate()
                node.regularResult(value, store)
            }
        }

        val targetAnnotation = node.target.tree?.let {
            annotationQuery.getAssignmentLeftSideAnnotations(it)?.let(Mutability::fromAnnotations)
        }

        return when (val rhsPermission = input.getValueOfSubNode(node.expression)!!) {
            is PermissionValue.FreePermission -> {
                val targetPermission = rhsPermission.permission.withId(Id.fromNode(node.target))
                result(targetPermission) { updatePermission(node.target, targetPermission) }
            }

            is IdentifiedPermission -> {
                require(node.expression is LocalVariableNode) { "Can only split permissions from variables" }
                val (targetPermission, remainingPermission) = rhsPermission.split(targetAnnotation)
                result(targetPermission) {
                    updatePermission(node.target, targetPermission)
                    updatePermission(node.expression, remainingPermission)
                }
            }

            else -> TODO()
        }

    }

    override fun visitLocalVariable(
        node: LocalVariableNode, input: Input
    ): Result {
        require(!input.containsTwoStores()) { "Local variable node $node is in two stores" }
        val permission = input.regularStore.queryPermission(node)
        return node.regularResult(permission, input.regularStore)
    }

    override fun visitObjectCreation(
        n: ObjectCreationNode, p: Input
    ): Result {
        return RegularTransferResult(
            Permission(Permission.Rational.ONE).asValue(), p.regularStore
        )
    }

    override fun visitReturn(
        node: ReturnNode,
        input: Input
    ): Result = try {
        exceptionReportContext(node.tree!!) {
            require(!input.containsTwoStores()) { "Return node $node with two stores" }
            if (node.result == null) return node.regularResult(null, input.regularStore, false)
            val returnPermission = input.getValueOfSubNode(node.result)!!

            when (signatureType.returnMutability!!) {
                Mutability.MUTABLE -> if (!returnPermission.isMutable) throw IncompatibleReturnPermission(
                    returnPermission,
                    signatureType.returnMutability
                )

                Mutability.IMMUTABLE -> if (!returnPermission.isReadable) throw IncompatibleReturnPermission(
                    returnPermission,
                    signatureType.returnMutability
                )
            }

            node.regularResult(null, input.regularStore, false)
        }
    } catch (_: BorroQReportedException) {
        node.regularResult(null, input.regularStore, false)
    }

    override fun visitStringLiteral(
        node: StringLiteralNode,
        p: Input
    ): Result {
        return node.regularResult(
            PermissionValue.FreePermission(Permission(Permission.Rational.HALF)),
            p.regularStore
        )
    }

    //region Noop visit functions
    override fun visitVariableDeclaration(
        n: VariableDeclarationNode,
        p: Input
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

    override fun visitThis(
        n: ThisNode, p: Input
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