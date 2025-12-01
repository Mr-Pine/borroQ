package de.mr_pine.borroq.analysis.exceptions

import de.mr_pine.borroq.Messages

class ReleasePermissionMissingException(parameter: String) :
    BorroQException(
        Messages.RELEASE_PERMISSION_MISSING,
        parameter
    )