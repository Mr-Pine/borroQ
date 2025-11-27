package de.mr_pine.borroq.analysis.exceptions

import de.mr_pine.borroq.Messages

class TopPermissionEncounteredException(name: String) : BorroQException(Messages.TOP_PERMISSION_ENCOUNTERED, name) {
}