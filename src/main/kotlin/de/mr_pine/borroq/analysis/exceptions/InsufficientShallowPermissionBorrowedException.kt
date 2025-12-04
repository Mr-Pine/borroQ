package de.mr_pine.borroq.analysis.exceptions

import de.mr_pine.borroq.Messages
import de.mr_pine.borroq.types.Borrow

class InsufficientShallowPermissionBorrowedException(
    conflicting: Borrow
) : BorroQException(
    Messages.INSUFFICIENT_SHALLOW_PERMISSION_BORROWED, conflicting.toString()
)