package de.mr_pine.borroq.analysis.transfer

import de.mr_pine.borroq.BorroQChecker
import de.mr_pine.borroq.analysis.BorroQStore
import de.mr_pine.borroq.analysis.Configuration
import de.mr_pine.borroq.analysis.livevariable.LiveVarNode
import de.mr_pine.borroq.analysis.livevariable.LiveVarStore
import de.mr_pine.borroq.types.BorroQValue
import de.mr_pine.borroq.types.Id
import de.mr_pine.borroq.types.IdentifiedPermission
import de.mr_pine.borroq.types.specifiers.Mutability
import org.checkerframework.dataflow.analysis.*
import org.checkerframework.dataflow.cfg.node.*
import javax.lang.model.type.TypeKind

typealias Result = TransferResult<BorroQValue, BorroQStore>
typealias Input = TransferInput<BorroQValue, BorroQStore>

abstract class BorroQBaseTransfer(
    private val liveness: AnalysisResult<UnusedAbstractValue, LiveVarStore>,
    private val checker: BorroQChecker,
    private val configuration: Configuration
) : AbstractNodeVisitor<Result, Input>(), ForwardTransferFunction<BorroQValue, BorroQStore> {

    protected fun Node.regularResult(
        value: BorroQValue?, store: BorroQStore, storeChanged: Boolean = true
    ): RegularTransferResult<BorroQValue, BorroQStore> {

        val isStatementExpression =
            this.block?.successors.orEmpty().any { it.nodes.firstOrNull() is ExpressionStatementNode }
        if (isStatementExpression && value is BorroQValue.FreePermission) {
            val borrows =
                value.attachedBorrows.map { it.toBorrow(Id("SHOULD_BE_DELETED", -1)) }.filter { it.target !is Id }
            borrows.forEach(store::addBorrow)

            if (borrows.isNotEmpty()) {
                return regularResult(value.copy(attachedBorrows = emptyList()), store, storeChanged = true)
            }
        }

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

        val liveIds = liveness.getStoreAfter(this)?.liveVariables.liveIds()

        store.deleteInactiveBorrows(liveIds)

        return RegularTransferResult(value, store, storeChanged)
    }

    protected fun propagateFreeBorrowsImmutable(
        node: Node, input: Input
    ): Result {
        val operandResults = node.operands.map { input.getValueOfSubNode(it) }
        require(
            operandResults.zip(node.operands)
                .all { (res, op) ->
                    res is BorroQValue.FreePermission || res is IdentifiedPermission || op.type.kind in listOf(
                        TypeKind.EXECUTABLE
                    )
                }) {
            "Bad res for one of ${node.operands.map { it.type.kind }}"
        }
        val freeBorrows = operandResults.filterIsInstance<BorroQValue.FreePermission>()
            .flatMap { it.attachedBorrows }
        return node.regularResult(
            BorroQValue.FreePermission(Mutability.IMMUTABLE.fraction, freeBorrows),
            input.regularStore,
            false
        )
    }

    override fun visitNode(
        node: Node, p: Input
    ): Result {
        configuration.unknownSyntaxStrictness.reportUnknownSyntaxStrictness(checker, node)
        return node.regularResult(null, p.regularStore, false)
    }

    override fun visitVariableDeclaration(
        n: VariableDeclarationNode, p: Input
    ): Result {
        return propagateFreeBorrowsImmutable(n, p)
    }

    override fun visitMarker(
        n: MarkerNode, p: Input
    ): Result {
        return propagateFreeBorrowsImmutable(n, p)
    }

    override fun visitTernaryExpression(
        n: TernaryExpressionNode, p: Input
    ): Result {
        return propagateFreeBorrowsImmutable(n, p)
    }

    override fun visitNumericalPlus(
        n: NumericalPlusNode, p: Input
    ): Result {
        return propagateFreeBorrowsImmutable(n, p)
    }

    override fun visitNumericalAddition(
        n: NumericalAdditionNode, p: Input
    ): Result {
        return propagateFreeBorrowsImmutable(n, p)
    }

    override fun visitNumericalSubtraction(
        n: NumericalSubtractionNode, p: Input
    ): Result {
        return propagateFreeBorrowsImmutable(n, p)
    }

    override fun visitLessThan(
        n: LessThanNode, p: Input
    ): Result {
        return propagateFreeBorrowsImmutable(n, p)
    }

    override fun visitIntegerRemainder(
        n: IntegerRemainderNode, p: Input
    ): Result {
        return propagateFreeBorrowsImmutable(n, p)
    }

    override fun visitEqualTo(
        n: EqualToNode, p: Input
    ): Result {
        return propagateFreeBorrowsImmutable(n, p)
    }

    override fun visitExpressionStatement(
        n: ExpressionStatementNode, p: Input
    ): Result {
        return propagateFreeBorrowsImmutable(n, p)
    }

    override fun visitMethodAccess(
        n: MethodAccessNode, p: Input
    ): Result {
        return propagateFreeBorrowsImmutable(n, p)
    }
}