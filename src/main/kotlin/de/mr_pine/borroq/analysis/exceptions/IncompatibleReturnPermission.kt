package de.mr_pine.borroq.analysis.exceptions

import de.mr_pine.borroq.Messages
import de.mr_pine.borroq.types.Mutability
import de.mr_pine.borroq.types.PermissionValue

class IncompatibleReturnPermission(returnValuePermission: PermissionValue, expectedMutability: Mutability) :
    BorroQException(
        Messages.INCOMPATIBLE_RETURN_PERMISSION,
        returnValuePermission.toString(),
        expectedMutability.permissionString
    ) {
}