package de.mr_pine.borroq.analysis.exceptions

import de.mr_pine.borroq.Messages
import de.mr_pine.borroq.types.specifiers.IMutability

class IncompatibleReturnPermissionException(expectedMutability: IMutability) :
    BorroQException(
        Messages.INCOMPATIBLE_RETURN_PERMISSION,
        expectedMutability.permissionString
    ) {
}