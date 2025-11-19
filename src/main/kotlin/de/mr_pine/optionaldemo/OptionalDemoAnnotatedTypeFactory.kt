package de.mr_pine.optionaldemo

import org.checkerframework.common.basetype.BaseTypeChecker
import org.checkerframework.framework.flow.CFStore
import org.checkerframework.framework.flow.CFValue
import org.checkerframework.framework.type.GenericAnnotatedTypeFactory

class OptionalDemoAnnotatedTypeFactory :
    GenericAnnotatedTypeFactory<CFValue?, CFStore?, OptionalDemoTransfer?, OptionalDemoAnalysis?> {
    constructor(checker: BaseTypeChecker, useFlow: Boolean) : super(checker, useFlow) {
        this.postInit()
    }

    constructor(checker: BaseTypeChecker) : super(checker) {
        this.postInit()
    }
}