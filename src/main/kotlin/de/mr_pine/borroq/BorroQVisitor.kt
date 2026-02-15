@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package de.mr_pine.borroq

import com.sun.source.tree.ClassTree
import com.sun.source.tree.MethodTree
import com.sun.tools.javac.tree.JCTree
import de.mr_pine.borroq.analysis.*
import de.mr_pine.borroq.analysis.exceptions.BorroQException
import de.mr_pine.borroq.analysis.livevariable.LiveVarTransfer
import de.mr_pine.borroq.analysis.transfer.BorroQTransfer
import de.mr_pine.borroq.types.BorroQValue
import org.checkerframework.dataflow.analysis.BackwardAnalysisImpl
import org.checkerframework.dataflow.cfg.ControlFlowGraph
import org.checkerframework.dataflow.cfg.builder.CFGBuilder
import org.checkerframework.dataflow.cfg.visualize.CFGVisualizer
import org.checkerframework.dataflow.cfg.visualize.DOTCFGVisualizer
import org.checkerframework.framework.source.SourceVisitor
import org.checkerframework.javacutil.UserError

class BorroQVisitor(
    val checker: BorroQChecker, val configuration: Configuration, val annotationQuery: AnnotationQuery = AnnotationQuery(
        checker
    )
) : SourceVisitor<Unit, Unit>(checker) {

    var currentClass: ClassTree? = null
    val cfgVisualizer = createCFGVisualizer()

    override fun visitClass(classTree: ClassTree?, p: Unit?) {
        currentClass = classTree
        return super.visitClass(classTree, p)
    }

    override fun visitMethod(tree: MethodTree, p: Unit?) {
        if (tree.body == null) return // Abstract/Interface method

        val cfg = CFGBuilder.build(
            root ?: throw IllegalStateException("No root tree present"),
            tree,
            currentClass ?: throw IllegalStateException("No current class available"),
            checker.processingEnvironment
        )

        val livenessAnalysis = BackwardAnalysisImpl(LiveVarTransfer())
        livenessAnalysis.performAnalysis(cfg)
        val livenessResult = livenessAnalysis.result

        val methodElement = (tree as JCTree.JCMethodDecl).sym
        val memberTypeAnalysis = MemberTypeAnalysis(checker)
        val signatureType = memberTypeAnalysis.getType(methodElement, exceptionReportingContext = {
            try {
                it()
            } catch (e: BorroQException) {
                context(checker, tree) {
                    e.report()
                }
            }
        })

        val transfer = BorroQTransfer(
            signatureType,
            memberTypeAnalysis,
            livenessResult,
            checker,
            annotationQuery,
            configuration
        )

        val analysis = MethodAnalysis(
            -1,
            transfer
        )
        analysis.performAnalysis(cfg)

        visualizeCFG(cfg, analysis)
    }


    fun visualizeCFG(cfg: ControlFlowGraph, analysis: MethodAnalysis) {
        cfgVisualizer?.visualizeWithAction(cfg, cfg.entryBlock, analysis)
    }

    fun createCFGVisualizer(): CFGVisualizer<BorroQValue, BorroQStore, BorroQTransfer>? {
        if (checker.hasOption("flowdotdir")) {
            val flowdotdir = checker.getOption("flowdotdir")
            if (flowdotdir!!.isEmpty()) {
                throw UserError("Empty string provided for -Aflowdotdir command-line argument")
            }
            val verbose = checker.hasOption("verbosecfg")

            val args = buildMap {
                this["outdir"] = flowdotdir
                this["verbose"] = verbose
                this["checkerName"] = checker::class.simpleName
            }

            val res = DOTCFGVisualizer<BorroQValue, BorroQStore, BorroQTransfer>()
            res.init(args)
            return res
        } else if (checker.hasOption("cfgviz")) {
            throw UserError("The -Acfgviz command-line argument is not supported.")
        }
        // Nobody expected to use cfgVisualizer if neither option given.
        return null
    }
}