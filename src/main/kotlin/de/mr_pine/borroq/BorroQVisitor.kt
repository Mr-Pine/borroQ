package de.mr_pine.borroq

import com.sun.source.tree.ClassTree
import com.sun.source.tree.MethodTree
import de.mr_pine.borroq.analysis.BorroQStore
import de.mr_pine.borroq.analysis.BorroQTransfer
import de.mr_pine.borroq.analysis.MethodAnalysis
import de.mr_pine.borroq.types.PermissionValue
import org.checkerframework.dataflow.cfg.ControlFlowGraph
import org.checkerframework.dataflow.cfg.builder.CFGBuilder
import org.checkerframework.dataflow.cfg.visualize.CFGVisualizer
import org.checkerframework.dataflow.cfg.visualize.DOTCFGVisualizer
import org.checkerframework.framework.source.SourceVisitor
import org.checkerframework.javacutil.UserError

class BorroQVisitor(
    val checker: BorroQChecker, val strictness: Strictness, val typeQuery: TypeQuery = TypeQuery(checker)
) : SourceVisitor<Unit, Unit>(checker) {

    var currentClass: ClassTree? = null
    val cfgVisualizer = createCFGVisualizer()

    override fun visitClass(classTree: ClassTree?, p: Unit?) {
        currentClass = classTree
        return super.visitClass(classTree, p)
    }

    override fun visitMethod(tree: MethodTree?, p: Unit?) {
        val cfg = CFGBuilder.build(
            root ?: throw IllegalStateException("No root tree present"),
            tree!!,
            currentClass ?: throw IllegalStateException("No current class available"),
            checker.processingEnvironment
        )
        val analysis = MethodAnalysis(-1, BorroQTransfer(checker, strictness))
        analysis.performAnalysis(cfg)

        visualizeCFG(cfg, analysis)
    }

    fun visualizeCFG(cfg: ControlFlowGraph, analysis: MethodAnalysis) {
        cfgVisualizer?.visualizeWithAction(cfg, cfg.entryBlock, analysis)
    }

    fun createCFGVisualizer(): CFGVisualizer<PermissionValue, BorroQStore, BorroQTransfer>? {
        if (checker.hasOption("flowdotdir")) {
            val flowdotdir = checker.getOption("flowdotdir")
            if (flowdotdir!!.isEmpty()) {
                throw UserError("Empty string provided for -Aflowdotdir command-line argument")
            }
            val verbose = checker.hasOption("verbosecfg")

            val args: MutableMap<String?, Any?> = HashMap<String?, Any?>(4)
            args.put("outdir", flowdotdir)
            args.put("verbose", verbose)
            args.put("checkerName", checker::class.simpleName)

            val res = DOTCFGVisualizer<PermissionValue, BorroQStore, BorroQTransfer>()
            res.init(args)
            return res
        } else if (checker.hasOption("cfgviz")) {
            throw UserError("The -Acfgviz command-line argument is not supported.")
        }
        // Nobody expected to use cfgVisualizer if neither option given.
        return null
    }
}