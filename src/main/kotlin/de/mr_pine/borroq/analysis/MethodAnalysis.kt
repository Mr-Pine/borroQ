package de.mr_pine.borroq.analysis

import de.mr_pine.borroq.types.PermissionValue
import org.checkerframework.dataflow.analysis.ForwardAnalysisImpl

class MethodAnalysis(maxCountBeforeWidening: Int, transfer: BorroQTransfer) :
    ForwardAnalysisImpl<PermissionValue, BorroQStore, BorroQTransfer>(maxCountBeforeWidening) {

    init {
        super.transferFunction = transfer
    }

}