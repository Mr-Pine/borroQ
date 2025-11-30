package de.mr_pine.borroq.types

/**
 * @param returnMutability The mutability of the return value. `null` if the return type is primitive/null
 * @param receiverType The type of the receiver. `null` if the method is static (or a constructor)
 * @param arguments The types of the arguments. `null` if the argument is a primitive
 */
data class SignatureType(
    val returnMutability: Mutability?, val receiverType: ArgumentType?, val arguments: List<ArgumentType?>
) {
    data class ArgumentType(val mutability: Mutability, val releaseMode: ReleaseMode) {
    }
}

