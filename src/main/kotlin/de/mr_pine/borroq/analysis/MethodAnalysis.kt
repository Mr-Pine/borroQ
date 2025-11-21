package de.mr_pine.borroq.analysis

import de.mr_pine.borroq.analysis.BorroQStore
import org.checkerframework.dataflow.analysis.ForwardAnalysisImpl

class MethodAnalysis(maxCountBeforeWidening: Int, transfer: BorroQTransfer) : ForwardAnalysisImpl<MethodAnalysis.AbstractValue, BorroQStore, BorroQTransfer>(maxCountBeforeWidening) {

    init {
        super.transferFunction = transfer
    }

    class AbstractValue: org.checkerframework.dataflow.analysis.AbstractValue<AbstractValue> {
        override fun leastUpperBound(other: AbstractValue?): AbstractValue {
            TODO("Not yet implemented")
        }

    }
}