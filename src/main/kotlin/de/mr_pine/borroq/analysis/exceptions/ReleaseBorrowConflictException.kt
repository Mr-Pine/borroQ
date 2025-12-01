package de.mr_pine.borroq.analysis.exceptions

import de.mr_pine.borroq.Messages
import de.mr_pine.borroq.types.Borrow
import de.mr_pine.borroq.types.IdPath

class ReleaseBorrowConflictException(path: IdPath, borrowTarget: Borrow.Identifier, borrowPath: IdPath) :
    BorroQException(
        Messages.RELEASE_BORROW_CONFLICT, path.display(), borrowTarget.display(), borrowPath.display()
    )