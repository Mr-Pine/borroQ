package de.mr_pine.borroq.analysis

import de.mr_pine.borroq.types.BorroQValue
import de.mr_pine.borroq.types.Rational
import de.mr_pine.borroq.types.specifiers.IMutability
import de.mr_pine.borroq.types.specifiers.IMutability.Immutable
import de.mr_pine.borroq.types.specifiers.IMutability.Mutable
import de.mr_pine.borroq.types.specifiers.Mutability
import de.mr_pine.borroq.types.specifiers.ReleaseMode.SingleReleaseMode.Borrow
import de.mr_pine.borroq.types.specifiers.ReleaseMode.SingleReleaseMode.Release

object DefaultInference {
    fun inferVariableMutability(assignedValue: BorroQValue): IMutability {
        return when (assignedValue) {
            is BorroQValue.FieldAccess if assignedValue.fieldPermission.fraction == Rational.ONE -> Mutable(
                null
            )

            else -> Immutable(null)
        }
    }

    fun inferConstructorReturnMutability() = Mutable(null)

    fun inferReceiverMutability() = Immutable(null)
    fun inferReceiverReleaseMode() = Release(null)

    fun inferReturnMutability() = Immutable(null)

    fun inferArrayElementIMutability() = Immutable(null)
    fun inferArrayElementMutability() = Mutability.IMMUTABLE

    fun inferParameterMutability(isConstructor: Boolean) = Immutable(null)
    fun inferParameterReleaseMode(isConstructor: Boolean) = if (isConstructor) Borrow(null) else Release(null)

    fun inferTypeParameterMutability() = Immutable(null)
}