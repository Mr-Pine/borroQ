package de.mr_pine.borroq.analysis.livevariable;

import org.checkerframework.common.returnsreceiver.qual.This;
import org.checkerframework.dataflow.analysis.*;
import org.checkerframework.dataflow.cfg.UnderlyingAST;
import org.checkerframework.dataflow.cfg.node.*;
import org.checkerframework.dataflow.qual.SideEffectFree;

import java.util.List;

/**
 * A live variable transfer function.
 */
public class LiveVarTransfer
        extends AbstractNodeVisitor<
        TransferResult<UnusedAbstractValue, LiveVarStore>,
        TransferInput<UnusedAbstractValue, LiveVarStore>>
        implements BackwardTransferFunction<UnusedAbstractValue, LiveVarStore> {

    /**
     * Creates a new LiveVarTransfer.
     */
    public LiveVarTransfer() {
    }

    @Override
    @SideEffectFree
    public LiveVarStore initialNormalExitStore(
            UnderlyingAST underlyingAST, List<ReturnNode> returnNodes) {
        return new LiveVarStore();
    }

    @Override
    public LiveVarStore initialExceptionalExitStore(UnderlyingAST underlyingAST) {
        return new LiveVarStore();
    }

    @Override
    public RegularTransferResult<UnusedAbstractValue, LiveVarStore> visitNode(
            Node n, TransferInput<UnusedAbstractValue, LiveVarStore> p) {
        return new RegularTransferResult<>(null, p.getRegularStore());
    }

    @Override
    public RegularTransferResult<UnusedAbstractValue, LiveVarStore> visitAssignment(
            AssignmentNode n, TransferInput<UnusedAbstractValue, LiveVarStore> p) {
        RegularTransferResult<UnusedAbstractValue, LiveVarStore> transferResult =
                (RegularTransferResult<UnusedAbstractValue, LiveVarStore>)
                        super.visitAssignment(n, p);
        processLiveVarInAssignment(
                n.getTarget(), n.getExpression(), transferResult.getRegularStore());
        return transferResult;
    }

    @Override
    public RegularTransferResult<UnusedAbstractValue, LiveVarStore> visitMethodInvocation(
            MethodInvocationNode n, TransferInput<UnusedAbstractValue, LiveVarStore> p) {
        RegularTransferResult<UnusedAbstractValue, LiveVarStore> transferResult =
                (RegularTransferResult<UnusedAbstractValue, LiveVarStore>)
                        super.visitMethodInvocation(n, p);
        LiveVarStore store = transferResult.getRegularStore();
        for (Node arg : n.getArguments()) {
            store.addUseInExpression(arg);
        }
        Node receiver = n.getTarget().getReceiver();
        if (receiver != null) {
            store.addUseInExpression(receiver);
        }
        return transferResult;
    }

    @Override
    public RegularTransferResult<UnusedAbstractValue, LiveVarStore> visitObjectCreation(
            ObjectCreationNode n, TransferInput<UnusedAbstractValue, LiveVarStore> p) {
        RegularTransferResult<UnusedAbstractValue, LiveVarStore> transferResult =
                (RegularTransferResult<UnusedAbstractValue, LiveVarStore>)
                        super.visitObjectCreation(n, p);
        LiveVarStore store = transferResult.getRegularStore();
        for (Node arg : n.getArguments()) {
            store.addUseInExpression(arg);
        }
        return transferResult;
    }

    @Override
    public RegularTransferResult<UnusedAbstractValue, LiveVarStore> visitReturn(
            ReturnNode n, TransferInput<UnusedAbstractValue, LiveVarStore> p) {
        RegularTransferResult<UnusedAbstractValue, LiveVarStore> transferResult =
                (RegularTransferResult<UnusedAbstractValue, LiveVarStore>) super.visitReturn(n, p);
        Node result = n.getResult();
        if (result != null) {
            LiveVarStore store = transferResult.getRegularStore();
            store.addUseInExpression(result);
        }
        return transferResult;
    }

    /**
     * Update the information of live variables from an assignment statement.
     *
     * @param target     the expression that is assigned to
     * @param expression the expression in which the variables should be added
     * @param store      the live variable store
     */
    private void processLiveVarInAssignment(Node target, Node expression, LiveVarStore store) {
        if (target instanceof LocalVariableNode variableNode) {
            store.killLiveVar(new LiveVarNode(variableNode));
        } else {
            store.addUseInExpression(target);
        }
        store.addUseInExpression(expression);
    }
}
