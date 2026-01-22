package de.mr_pine.borroq.analysis.exceptions

import de.mr_pine.borroq.Messages
import javax.lang.model.type.TypeMirror

class IncompatibleTypeParameterMutabilities(valueType: TypeMirror) :
    BorroQException(
        Messages.TYPE_PARAMETER_MUTABILITY_INCOMPATIBLE, valueType.toString()
    )