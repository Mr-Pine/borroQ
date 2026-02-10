package de.mr_pine.borroq.types

import de.mr_pine.borroq.types.specifiers.IMutability
import de.mr_pine.borroq.types.specifiers.ReleaseMode

/**
 * @param returnMutability The mutability of the return value. `null` if the return type is primitive/null
 * @param receiverType The type of the receiver. `null` if the method is static (or a constructor)
 * @param parameters The types of the arguments. `null` if the argument is a primitive
 */
data class SignatureType(
    val returnMutability: IMutability?, val receiverType: ParameterType?, val parameters: List<ParameterType?>
) {
    data class ParameterType(val mutability: IMutability, val releaseMode: ReleaseMode) {
    }
}

