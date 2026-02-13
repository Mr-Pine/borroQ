package de.mr_pine.borroq.analysis

import de.mr_pine.borroq.types.BorroQValue
import de.mr_pine.borroq.types.Rational
import de.mr_pine.borroq.types.specifiers.Mutability
import de.mr_pine.borroq.types.specifiers.Mutability.IMMUTABLE
import de.mr_pine.borroq.types.specifiers.Mutability.MUTABLE

object DefaultInference {
    fun inferVariableMutability(assignedValue: BorroQValue): Mutability {
        return when (assignedValue) {
            is BorroQValue.FieldAccess if assignedValue.fieldPermission.fraction == Rational.ONE -> MUTABLE

            else -> IMMUTABLE
        }
    }

    fun inferConstructorReturnMutability() = MUTABLE

    fun inferReceiverMutability() = IMMUTABLE

    fun inferReturnMutability() = IMMUTABLE

    fun inferArrayElementMutability() = IMMUTABLE

    fun inferParameterMutability(isConstructor: Boolean) = IMMUTABLE

    fun inferTypeParameterMutability() = IMMUTABLE

    fun inferFieldAccessMutability() = IMMUTABLE
}