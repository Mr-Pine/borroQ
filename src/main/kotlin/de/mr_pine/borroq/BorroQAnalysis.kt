package de.mr_pine.borroq

import org.checkerframework.framework.flow.CFAbstractAnalysis
import org.checkerframework.framework.flow.CFStore
import org.checkerframework.framework.flow.CFValue
import org.checkerframework.javacutil.AnnotationMirrorSet
import javax.lang.model.type.TypeMirror

class BorroQAnalysis(checker: BorroQChecker, factory: BorroQAnnotatedTypeFactory) :
    CFAbstractAnalysis<CFValue?, CFStore?, BorroQTransfer>(checker, factory) {
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