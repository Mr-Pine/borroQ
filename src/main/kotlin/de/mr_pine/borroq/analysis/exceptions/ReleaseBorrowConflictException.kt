package de.mr_pine.borroq.analysis.exceptions

import de.mr_pine.borroq.Messages
import de.mr_pine.borroq.types.Borrow
import de.mr_pine.borroq.types.Id
import de.mr_pine.borroq.types.IdPath

class ReleaseBorrowConflictException(path: IdPath, borrowTarget: Borrow.Identifier, borrowPath: IdPath) :
    BorroQException(
        Messages.RELEASE_PERMISSION_MISSING, path.display(), borrowTarget.display(), borrowPath.display()
    )

private fun IdPath.display() = "${id.name}.${tail.fields.joinToString(".") { it.simpleName }}"
private fun Borrow.Identifier.display() = when (this) {
    is Borrow.Identifier.Dummy -> "dummy"
    is Id -> name
}