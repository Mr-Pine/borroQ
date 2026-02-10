package de.mr_pine.borroq.analysis.exceptions

import de.mr_pine.borroq.Messages
import de.mr_pine.borroq.types.Path
import de.mr_pine.borroq.types.specifiers.IMutability

class InsufficientDeepPermissionException(variableAccess: Path, mutability: IMutability): BorroQException(Messages.INSUFFICIENT_DEEP_PERMISSION, variableAccess.display(), mutability.permissionString) {
}