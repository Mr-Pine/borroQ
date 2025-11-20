package de.mr_pine.borroq

import org.checkerframework.framework.flow.CFStore
import org.checkerframework.framework.flow.CFValue
import org.checkerframework.framework.type.GenericAnnotatedTypeFactory

class BorroQAnnotatedTypeFactory(checker: BorroQChecker): GenericAnnotatedTypeFactory<CFValue?, CFStore?, BorroQTransfer, BorroQAnalysis>(checker) {
}