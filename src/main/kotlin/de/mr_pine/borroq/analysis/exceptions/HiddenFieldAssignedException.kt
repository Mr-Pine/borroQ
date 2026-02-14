package de.mr_pine.borroq.analysis.exceptions

import de.mr_pine.borroq.Messages

class HiddenFieldAssignedException :
    BorroQException(Messages.OUT_OF_SCOPE_FIELD_ASSIGNED)