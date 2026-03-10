package de.mr_pine.borroq.analysis.exceptions

import de.mr_pine.borroq.Messages
import de.mr_pine.borroq.types.PathTail
import de.mr_pine.borroq.types.specifiers.ArgPermission
import org.checkerframework.dataflow.cfg.node.Node

class InsufficientDeepPermissionException(path: PathTail, receiver: Node, requiredPermission: ArgPermission) :
    BorroQException(Messages.INSUFFICIENT_DEEP_PERMISSION, path.display(), receiver.toString(), requiredPermission.name)