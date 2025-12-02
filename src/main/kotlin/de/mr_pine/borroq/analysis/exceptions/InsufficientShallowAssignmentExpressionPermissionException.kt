package de.mr_pine.borroq.analysis.exceptions

import de.mr_pine.borroq.Messages
import de.mr_pine.borroq.types.BorroQValue
import de.mr_pine.borroq.types.Rational
import org.checkerframework.dataflow.cfg.node.Node

class InsufficientShallowAssignmentExpressionPermissionException(
    expression: Node,
    fraction: Rational,
    permission: BorroQValue
) :
    BorroQException(
        Messages.INSUFFICIENT_SHALLOW_PERMISSION_ASSIGNMENT_EXPRESSION,
        expression.toString(),
        fraction.toString(),
        permission.toString()
    )