package de.mr_pine.borroq.analysis

import com.sun.source.tree.Tree
import de.mr_pine.borroq.BorroQChecker
import de.mr_pine.borroq.Messages
import de.mr_pine.borroq.Strictness
import de.mr_pine.borroq.analysis.exceptions.BorroQException
import de.mr_pine.borroq.analysis.exceptions.BorroQReportedException
import de.mr_pine.borroq.types.*
import de.mr_pine.borroq.types.SignatureType.ArgumentType.Companion.ReleaseMode.*
import org.checkerframework.dataflow.analysis.ForwardTransferFunction
import org.checkerframework.dataflow.analysis.RegularTransferResult
import org.checkerframework.dataflow.analysis.TransferInput
import org.checkerframework.dataflow.analysis.TransferResult
import org.checkerframework.dataflow.cfg.UnderlyingAST
import org.checkerframework.dataflow.cfg.node.*
import org.checkerframework.javacutil.ElementUtils
import kotlin.contracts.ExperimentalContracts

private typealias Result = TransferResult<PermissionValue, BorroQStore>
private typealias Input = TransferInput<PermissionValue, BorroQStore>

class BorroQTransfer(val checker: BorroQChecker, val annotationQuery: AnnotationQuery, val strictness: Strictness) :
    AbstractNodeVisitor<Result, Input>(), ForwardTransferFunction<PermissionValue, BorroQStore> {

    @OptIn(ExperimentalContracts::class)
    inline fun <R> exceptionReportContext(tree: Tree, block: () -> R): R {
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
    ): Result = RegularTransferResult(null, input.regularStore)

    override fun visitNode(
        node: Node, p: Input
    ): Result {
        val source = node.tree
        if (node is MethodInvocationNode) {
            println("Hi from call to ${ElementUtils.getQualifiedName(node.target.method)}")
        }

        if (source == null) {
            System.err.println("Tree of node $node is null")
        } else {
            if (strictness == Strictness.STRICT) {
                checker.reportError(source, Messages.UNKNOWN_TREE_ENCOUNTERED, source.kind)
            } else {
                checker.reportWarning(source, Messages.UNKNOWN_TREE_ENCOUNTERED, source.kind)
            }
        }

        return RegularTransferResult(null, p.regularStore)
    }


    override fun visitMethodInvocation(
        node: MethodInvocationNode, input: Input
    ): Result {
        require(!input.containsTwoStores()) { "Method invocation node $node has two stores" }
        try {
            val methodType = SignatureTypeAnalysis(annotationQuery).getType(node.target.method)
            // TODO: Check super() constructor return mutability is compatible
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

            return super.visitMethodInvocation(node, input)
        } catch (_: BorroQReportedException) {
            return RegularTransferResult(null, input.regularStore)
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
                RegularTransferResult(value, store, true)
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
        return RegularTransferResult(permission, input.regularStore)
    }

    override fun visitObjectCreation(
        n: ObjectCreationNode, p: Input
    ): Result {
        return RegularTransferResult(
            Permission(Permission.Rational.ONE).asValue(), p.regularStore
        )
    }

    //region Noop visit functions
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