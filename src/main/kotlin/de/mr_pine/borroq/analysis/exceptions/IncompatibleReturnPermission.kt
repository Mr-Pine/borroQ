package de.mr_pine.borroq.analysis.exceptions

import de.mr_pine.borroq.Messages
import de.mr_pine.borroq.types.specifiers.Mutability
import de.mr_pine.borroq.types.BorroQValue

class IncompatibleReturnPermission(returnValuePermission: BorroQValue, expectedMutability: Mutability) :
    BorroQException(
        Messages.INCOMPATIBLE_RETURN_PERMISSION,
        returnValuePermission.toString(),
        expectedMutability.permissionString
    ) {
}