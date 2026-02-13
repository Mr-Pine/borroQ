package de.mr_pine.borroq.analysis

import de.mr_pine.borroq.analysis.transfer.BorroQTransfer
import de.mr_pine.borroq.types.BorroQValue
import org.checkerframework.dataflow.analysis.ForwardAnalysisImpl
import org.checkerframework.dataflow.cfg.ControlFlowGraph

class MethodAnalysis(maxCountBeforeWidening: Int, transfer: BorroQTransfer) :
    ForwardAnalysisImpl<BorroQValue, BorroQStore, BorroQTransfer>(maxCountBeforeWidening) {

    init {
        super.transferFunction = transfer
    }

    override fun performAnalysis(cfg: ControlFlowGraph?) {
        super.performAnalysis(cfg)
    }
}