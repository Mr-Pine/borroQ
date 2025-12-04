package de.mr_pine.borroq.analysis.exceptions

import de.mr_pine.borroq.Messages
import de.mr_pine.borroq.types.PathTail

class ConflictingPathRestrictionsException(a: PathTail, b: PathTail) :
    BorroQException(
        Messages.CONFLICTING_PATH_RESTRICTIONS, a.display(), b.display()
    )