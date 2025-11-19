package de.mr_pine.optionaldemo;

import de.mr_pine.optionaldemo.qual.Present;
import org.checkerframework.dataflow.analysis.TransferInput;
import org.checkerframework.dataflow.analysis.TransferResult;
import org.checkerframework.dataflow.cfg.node.MethodInvocationNode;
import org.checkerframework.dataflow.cfg.node.Node;
import org.checkerframework.dataflow.expression.JavaExpression;
import org.checkerframework.dataflow.util.NodeUtils;
import org.checkerframework.framework.flow.CFAbstractTransfer;
import org.checkerframework.framework.flow.CFStore;
import org.checkerframework.framework.flow.CFValue;
import org.checkerframework.javacutil.AnnotationBuilder;
import org.checkerframework.javacutil.TreeUtils;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;

public class OptionalDemoTransfer extends CFAbstractTransfer<CFValue, CFStore, OptionalDemoTransfer> {
    public OptionalDemoTransfer(OptionalDemoAnalysis analysis) {
        super(analysis);
    }

    public OptionalDemoTransfer(OptionalDemoAnalysis analysis, boolean forceConcurrentSemantics) {
        super(analysis, forceConcurrentSemantics);
    }

    private final ExecutableElement isPresentMethod = TreeUtils.getMethod("java.util.Optional", "isPresent", 0, analysis.getEnv());

    @Override
    public TransferResult<CFValue, CFStore> visitMethodInvocation(MethodInvocationNode n, TransferInput<CFValue, CFStore> in) {
        TransferResult<CFValue, CFStore> result = super.visitMethodInvocation(n, in);
        if (NodeUtils.isMethodInvocation(n, isPresentMethod, analysis.getEnv())) {
            Node recv = n.getTarget().getReceiver();
            AnnotationMirror presentAnno = AnnotationBuilder.fromClass(analysis.getTypeFactory().getElementUtils(), Present.class);
            result.getThenStore().insertValue(JavaExpression.fromNode(recv), presentAnno);
        }
        return result;
    }
}