package de.mr_pine.borroq.analysis

import de.mr_pine.borroq.types.BorroQValue
import de.mr_pine.borroq.types.Rational
import de.mr_pine.borroq.types.specifiers.Mutability
import de.mr_pine.borroq.types.specifiers.Mutability.Immutable
import de.mr_pine.borroq.types.specifiers.Mutability.Mutable
import de.mr_pine.borroq.types.specifiers.ReleaseMode.SingleReleaseMode.Borrow
import de.mr_pine.borroq.types.specifiers.ReleaseMode.SingleReleaseMode.Release

object DefaultInference {
    fun inferVariableMutability(assignedValue: BorroQValue): Mutability {
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

    fun inferArrayElementMutability() = Immutable(null)

    fun inferParameterMutability(isConstructor: Boolean) = Immutable(null)
    fun inferParameterReleaseMode(isConstructor: Boolean) = if (isConstructor) Borrow(null) else Release(null)

    fun inferTypeParameterMutability() = Immutable(null)
}