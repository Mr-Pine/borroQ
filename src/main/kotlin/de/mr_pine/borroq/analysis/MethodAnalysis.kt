package de.mr_pine.borroq.analysis

import com.sun.source.tree.Tree
import de.mr_pine.borroq.types.BorroQValue
import org.checkerframework.dataflow.analysis.ForwardAnalysisImpl
import org.checkerframework.dataflow.cfg.ControlFlowGraph
import org.checkerframework.dataflow.cfg.UnderlyingAST
import org.checkerframework.dataflow.cfg.block.Block
import org.checkerframework.org.plumelib.util.IPair

class MethodAnalysis(maxCountBeforeWidening: Int, transfer: BorroQTransfer) :
    ForwardAnalysisImpl<BorroQValue, BorroQStore, BorroQTransfer>(maxCountBeforeWidening) {

    init {
        super.transferFunction = transfer
    }

    override fun performAnalysis(cfg: ControlFlowGraph?) {
        super.performAnalysis(cfg)
        validateExitReleaseModes()
    }

    fun validateExitReleaseModes() {
        data class StoreData(val store: BorroQStore, val returnValue: BorroQValue?, val reportTree: Tree)

        fun Block.findTreeBackwards() = generateSequence(this) {
                it.predecessors.firstOrNull()
            }.flatMap { it.nodes }.firstNotNullOfOrNull { it.tree } ?: (cfg.underlyingAST as UnderlyingAST.CFGMethod).method

        val exits = buildList {
            regularExitStore?.let {
                add(
                    StoreData(it, null, cfg.regularExitBlock.findTreeBackwards())
                )
            }
            for ((returnNode, transferResult) in returnStatementStores) {
                add(StoreData(transferResult!!.regularStore, transferResult.resultValue, returnNode.tree!!))
            }
        }

        for (exit in exits) {
            transferFunction.validateExit(exit.store, exit.returnValue, exit.reportTree)
        }
    }

    private operator fun <T, S> IPair<T, S>.component1(): T = this.first
    private operator fun <T, S> IPair<T, S>.component2(): S = this.second

}