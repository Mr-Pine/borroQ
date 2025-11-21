package de.mr_pine.borroq.analysis

import de.mr_pine.borroq.BorroQChecker
import de.mr_pine.borroq.Messages
import de.mr_pine.borroq.Strictness
import org.checkerframework.dataflow.analysis.ForwardTransferFunction
import org.checkerframework.dataflow.analysis.RegularTransferResult
import org.checkerframework.dataflow.analysis.TransferInput
import org.checkerframework.dataflow.analysis.TransferResult
import org.checkerframework.dataflow.cfg.UnderlyingAST
import org.checkerframework.dataflow.cfg.node.AbstractNodeVisitor
import org.checkerframework.dataflow.cfg.node.LocalVariableNode
import org.checkerframework.dataflow.cfg.node.MethodInvocationNode
import org.checkerframework.dataflow.cfg.node.Node

class BorroQTransfer(val checker: BorroQChecker, val strictness: Strictness) :
    AbstractNodeVisitor<TransferResult<MethodAnalysis.AbstractValue, BorroQStore>, TransferInput<MethodAnalysis.AbstractValue, BorroQStore>>(),
    ForwardTransferFunction<MethodAnalysis.AbstractValue, BorroQStore> {
    override fun initialStore(
        underlyingAST: UnderlyingAST?,
        parameters: List<LocalVariableNode?>?
    ): BorroQStore {
        return BorroQStore()
    }

    override fun visitNode(
        node: Node?,
        p: TransferInput<MethodAnalysis.AbstractValue, BorroQStore>?
    ): TransferResult<MethodAnalysis.AbstractValue, BorroQStore> {
        val source = node!!.tree
        if (node is MethodInvocationNode) {
            println("Hi")
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

        return RegularTransferResult(null, p!!.regularStore)
    }
}