package de.mr_pine.borroq.analysis.exceptions

import de.mr_pine.borroq.Messages
import de.mr_pine.borroq.types.PathTail
import de.mr_pine.borroq.types.specifiers.ArgPermission

class InsufficientDeepPermissionException(path: PathTail, requiredPermission: ArgPermission) :
    BorroQException(Messages.INSUFFICIENT_DEEP_PERMISSION, path.display(), requiredPermission.name)