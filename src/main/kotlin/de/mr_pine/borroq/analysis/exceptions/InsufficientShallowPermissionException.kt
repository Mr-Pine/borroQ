package de.mr_pine.borroq.analysis.exceptions

import de.mr_pine.borroq.Messages
import de.mr_pine.borroq.types.VariablePermission
import de.mr_pine.borroq.types.specifiers.ArgPermission
import org.checkerframework.dataflow.cfg.node.Node

class InsufficientShallowPermissionException(
    name: String,
    requiredPermission: ArgPermission,
    permission: VariablePermission?
) :
    BorroQException(
        Messages.INSUFFICIENT_SHALLOW_PERMISSION,
        name,
        requiredPermission.name,
        permission?.toString() ?: "<primitive/String>"
    ) {

    constructor(node: Node, requiredPermission: ArgPermission, permission: VariablePermission?) : this(
        node.toString(),
        requiredPermission,
        permission
    )
}