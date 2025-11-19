package de.mr_pine.optionaldemo;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.framework.flow.CFAbstractAnalysis;
import org.checkerframework.framework.flow.CFStore;
import org.checkerframework.framework.flow.CFValue;
import org.checkerframework.javacutil.AnnotationMirrorSet;

import javax.lang.model.type.TypeMirror;

public class OptionalDemoAnalysis extends CFAbstractAnalysis<CFValue, CFStore, OptionalDemoTransfer> {
    public OptionalDemoAnalysis(BaseTypeChecker checker, OptionalDemoAnnotatedTypeFactory factory, int maxCountBeforeWidening) {
        super(checker, factory, maxCountBeforeWidening);
    }

    public OptionalDemoAnalysis(BaseTypeChecker checker, OptionalDemoAnnotatedTypeFactory factory) {
        super(checker, factory);
    }

    @Override
    public CFStore createEmptyStore(boolean sequentialSemantics) {
        return new CFStore(this, sequentialSemantics);
    }

    @Override
    public CFStore createCopiedStore(CFStore cfStore) {
        return new CFStore(cfStore);
    }

    @Override
    public @Nullable CFValue createAbstractValue(AnnotationMirrorSet annotations, TypeMirror underlyingType) {
        return defaultCreateAbstractValue(this, annotations, underlyingType);
    }
}