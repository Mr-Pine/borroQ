package de.mr_pine.borroq.analysis.exceptions

import de.mr_pine.borroq.Messages
import de.mr_pine.borroq.types.VariablePermission
import de.mr_pine.borroq.types.specifiers.Mutability

class InsufficientShallowPermissionException(name: String, mutability: Mutability, permission: VariablePermission?): BorroQException(Messages.INSUFFICIENT_SHALLOW_PERMISSION, name, mutability.permissionString, permission?.toString() ?: "<primitive/String>") {
}