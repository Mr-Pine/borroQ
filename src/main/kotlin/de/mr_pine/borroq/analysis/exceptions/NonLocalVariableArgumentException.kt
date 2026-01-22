package de.mr_pine.borroq.analysis.exceptions

import de.mr_pine.borroq.Messages

class NonLocalVariableArgumentException :
    BorroQException(
        Messages.NON_VARIABLE_ARGUMENT
    )