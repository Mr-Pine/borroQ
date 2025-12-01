package de.mr_pine.borroq.analysis.exceptions

import de.mr_pine.borroq.Messages
import de.mr_pine.borroq.types.specifiers.Mutability

class IncompatibleReturnPermissionException(expectedMutability: Mutability) :
    BorroQException(
        Messages.INCOMPATIBLE_RETURN_PERMISSION,
        expectedMutability.permissionString
    ) {
}