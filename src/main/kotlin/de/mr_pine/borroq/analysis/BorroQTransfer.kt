package de.mr_pine.borroq.analysis

import de.mr_pine.borroq.BorroQChecker
import de.mr_pine.borroq.BorroQStore
import de.mr_pine.borroq.Messages
import de.mr_pine.borroq.Strictness
import org.checkerframework.dataflow.analysis.ForwardTransferFunction
import org.checkerframework.dataflow.analysis.TransferInput
import org.checkerframework.dataflow.analysis.TransferResult
import org.checkerframework.dataflow.cfg.UnderlyingAST
import org.checkerframework.dataflow.cfg.node.AbstractNodeVisitor
import org.checkerframework.dataflow.cfg.node.LocalVariableNode
import org.checkerframework.dataflow.cfg.node.Node

class BorroQTransfer(val checker: BorroQChecker, val strictness: Strictness) :
    AbstractNodeVisitor<TransferResult<MethodAnalysis.AbstractValue, BorroQStore>, TransferInput<MethodAnalysis.AbstractValue, BorroQStore>>(),
    ForwardTransferFunction<MethodAnalysis.AbstractValue, BorroQStore> {
    override fun initialStore(
        underlyingAST: UnderlyingAST?,
        parameters: List<LocalVariableNode?>?
    ): BorroQStore {
        TODO("Not yet implemented")
    }

    override fun visitNode(
        node: Node?,
        p: TransferInput<MethodAnalysis.AbstractValue, BorroQStore>?
    ): TransferResult<MethodAnalysis.AbstractValue, BorroQStore> {
        if (strictness == Strictness.STRICT) {
            checker.reportError(node!!.tree, Messages.UNKNOWN_TREE_ENCOUNTERED)
        } else {
            checker.reportWarning(node!!.tree, Messages.UNKNOWN_TREE_ENCOUNTERED)
        }
        TODO()
    }
}