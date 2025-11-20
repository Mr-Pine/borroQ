package de.mr_pine.borroq

import org.checkerframework.framework.flow.CFAbstractTransfer
import org.checkerframework.framework.flow.CFStore
import org.checkerframework.framework.flow.CFValue

class BorroQTransfer(analysis: BorroQAnalysis): CFAbstractTransfer<CFValue?, CFStore?, BorroQTransfer>(analysis) {
}