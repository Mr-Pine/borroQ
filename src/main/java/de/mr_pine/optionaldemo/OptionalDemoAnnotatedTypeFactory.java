package de.mr_pine.optionaldemo;

import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.framework.flow.CFStore;
import org.checkerframework.framework.flow.CFValue;
import org.checkerframework.framework.type.GenericAnnotatedTypeFactory;

public class OptionalDemoAnnotatedTypeFactory extends GenericAnnotatedTypeFactory<CFValue, CFStore, OptionalDemoTransfer, OptionalDemoAnalysis> {
    public OptionalDemoAnnotatedTypeFactory(BaseTypeChecker checker, boolean useFlow) {
        super(checker, useFlow);
        this.postInit();
    }

    public OptionalDemoAnnotatedTypeFactory(BaseTypeChecker checker) {
        super(checker);
        this.postInit();
    }
}