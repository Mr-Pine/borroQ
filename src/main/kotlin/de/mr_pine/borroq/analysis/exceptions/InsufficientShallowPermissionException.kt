package de.mr_pine.borroq.analysis.exceptions

import de.mr_pine.borroq.Messages
import de.mr_pine.borroq.types.specifiers.ArgPermission
import org.checkerframework.dataflow.cfg.node.Node

class InsufficientShallowPermissionException(
    name: String,
    requiredPermission: ArgPermission
) :
    BorroQException(
        Messages.INSUFFICIENT_SHALLOW_PERMISSION,
        name,
        requiredPermission.name
    ) {

    constructor(node: Node, requiredPermission: ArgPermission) : this(
        node.toString(),
        requiredPermission
    )
}