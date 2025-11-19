package de.mr_pine.optionaldemo

import de.mr_pine.optionaldemo.qual.Present
import org.checkerframework.dataflow.analysis.TransferInput
import org.checkerframework.dataflow.analysis.TransferResult
import org.checkerframework.dataflow.cfg.node.MethodInvocationNode
import org.checkerframework.dataflow.expression.JavaExpression
import org.checkerframework.dataflow.util.NodeUtils
import org.checkerframework.framework.flow.CFAbstractTransfer
import org.checkerframework.framework.flow.CFStore
import org.checkerframework.framework.flow.CFValue
import org.checkerframework.javacutil.AnnotationBuilder
import org.checkerframework.javacutil.TreeUtils
import javax.lang.model.element.ExecutableElement

class OptionalDemoTransfer : CFAbstractTransfer<CFValue?, CFStore?, OptionalDemoTransfer?> {
    constructor(analysis: OptionalDemoAnalysis?) : super(analysis)

    constructor(analysis: OptionalDemoAnalysis?, forceConcurrentSemantics: Boolean) : super(
        analysis,
        forceConcurrentSemantics
    )

    private val isPresentMethod: ExecutableElement? =
        TreeUtils.getMethod("java.util.Optional", "isPresent", 0, analysis.getEnv())

    override fun visitMethodInvocation(
        n: MethodInvocationNode?,
        input: TransferInput<CFValue?, CFStore?>?
    ): TransferResult<CFValue?, CFStore?> {
        val result = super.visitMethodInvocation(n, input)
        if (NodeUtils.isMethodInvocation(n, isPresentMethod, analysis.getEnv())) {
            val recv = n!!.target.receiver
            val presentAnno =
                AnnotationBuilder.fromClass(analysis.typeFactory.elementUtils, Present::class.java)
            result.thenStore!!.insertValue(JavaExpression.fromNode(recv), presentAnno)
        }
        return result
    }
}