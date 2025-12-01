package de.mr_pine.borroq.analysis.exceptions

import de.mr_pine.borroq.Messages
import de.mr_pine.borroq.types.Path
import de.mr_pine.borroq.types.specifiers.Mutability

class InsufficientDeepPermissionException(variableAccess: Path, mutability: Mutability): BorroQException(Messages.INSUFFICIENT_DEEP_PERMISSION, variableAccess.display(), mutability.permissionString) {
}