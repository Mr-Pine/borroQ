package de.mr_pine.borroq.analysis.exceptions

import de.mr_pine.borroq.Messages
import de.mr_pine.borroq.types.Borrow
import de.mr_pine.borroq.types.Path

class ReleaseBorrowConflictException(path: Path, borrowTarget: Borrow.Identifier, borrowPath: Path) :
    BorroQException(
        Messages.RELEASE_BORROW_CONFLICT, path.display(), borrowTarget.display(), borrowPath.display()
    )