package de.mr_pine.borroq.analysis

import com.sun.source.tree.Tree
import com.sun.source.tree.Tree.Kind
import de.mr_pine.borroq.BorroQChecker
import de.mr_pine.borroq.analysis.Configuration.Companion.getConfig
import org.checkerframework.framework.source.SourceVisitor

class ControlFlowExtensionVisitor(val checker: BorroQChecker) : SourceVisitor<Unit, Unit>(checker) {
    override fun scan(tree: Tree?, p: Unit?) {
        if (tree == null) return
        currentPath
        val isForbidden = when (tree.kind) {
            Kind.MODIFIERS, Kind.BLOCK, Kind.EXPRESSION_STATEMENT, Kind.METHOD_INVOCATION, Kind.IDENTIFIER, Kind.PRIMITIVE_TYPE, Kind.VARIABLE, Kind.ANNOTATION, Kind.ASSIGNMENT, Kind.MEMBER_SELECT, Kind.STRING_LITERAL, Kind.NEW_CLASS, Kind.ARRAY_TYPE, Kind.NEW_ARRAY, Kind.ANNOTATED_TYPE, Kind.TYPE_ANNOTATION, Kind.ARRAY_ACCESS, Kind.INT_LITERAL, Kind.PARENTHESIZED, Kind.BOOLEAN_LITERAL, Kind.RETURN, Kind.PLUS, Kind.LESS_THAN, Kind.LESS_THAN_EQUAL, Kind.GREATER_THAN, Kind.GREATER_THAN_EQUAL, Kind.POSTFIX_DECREMENT, Kind.POSTFIX_INCREMENT, Kind.REMAINDER, Kind.IF, Kind.EQUAL_TO, Kind.TYPE_CAST, Kind.PARAMETERIZED_TYPE, Kind.MINUS -> false

            Kind.TRY, Kind.CATCH, Kind.WHILE_LOOP, Kind.CONDITIONAL_EXPRESSION, Kind.FOR_LOOP -> true
            else -> true
        }
        if (isForbidden) {
            checker.getConfig().borroQExtensions.requireExtension(
                Configuration.BorroQExtensions.Extension.CONTROL_FLOW, tree, checker
            )
        }

        super.scan(tree, p)
    }
}