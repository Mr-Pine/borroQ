package de.mr_pine.borroq.analysis.exceptions

import de.mr_pine.borroq.Messages
import de.mr_pine.borroq.types.Path
import de.mr_pine.borroq.types.VariablePermission

class InsufficientShallowAssignmentTargetReceiverPermissionException(accessPath: Path, permission: VariablePermission) :
    BorroQException(Messages.INSUFFICIENT_SHALLOW_PERMISSION_ASSIGNMENT_TARGET_RECEIVER, accessPath.display(), permission.toString())