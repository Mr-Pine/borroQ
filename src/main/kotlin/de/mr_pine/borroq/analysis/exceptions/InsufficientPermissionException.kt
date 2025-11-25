package de.mr_pine.borroq.analysis.exceptions

import de.mr_pine.borroq.Messages
import de.mr_pine.borroq.types.Mutability
import de.mr_pine.borroq.types.VariablePermission

class InsufficientPermissionException(name: String, mutability: Mutability, permission: VariablePermission): BorroQException(Messages.INSUFFICIENT_PERMISSION, name, mutability.permissionString, permission.toString()) {
}