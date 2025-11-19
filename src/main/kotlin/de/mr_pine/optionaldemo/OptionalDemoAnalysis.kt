package de.mr_pine.optionaldemo

import org.checkerframework.common.basetype.BaseTypeChecker
import org.checkerframework.framework.flow.CFAbstractAnalysis
import org.checkerframework.framework.flow.CFStore
import org.checkerframework.framework.flow.CFValue
import org.checkerframework.javacutil.AnnotationMirrorSet
import javax.lang.model.type.TypeMirror

class OptionalDemoAnalysis : CFAbstractAnalysis<CFValue?, CFStore?, OptionalDemoTransfer?> {
    constructor(
        checker: BaseTypeChecker,
        factory: OptionalDemoAnnotatedTypeFactory,
        maxCountBeforeWidening: Int
    ) : super(checker, factory, maxCountBeforeWidening)

    constructor(checker: BaseTypeChecker, factory: OptionalDemoAnnotatedTypeFactory) : super(checker, factory)

    override fun createEmptyStore(sequentialSemantics: Boolean): CFStore {
        return CFStore(this, sequentialSemantics)
    }

    override fun createCopiedStore(cfStore: CFStore?): CFStore {
        return CFStore(cfStore)
    }

    override fun createAbstractValue(annotations: AnnotationMirrorSet?, underlyingType: TypeMirror?): CFValue? {
        return defaultCreateAbstractValue(this, annotations, underlyingType)
    }
}