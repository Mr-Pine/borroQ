package de.mr_pine.borroq.analysis

import de.mr_pine.borroq.types.BorroQValue
import de.mr_pine.borroq.types.Rational
import de.mr_pine.borroq.types.specifiers.Mutability
import de.mr_pine.borroq.types.specifiers.ReleaseMode

object DefaultInference {
    fun inferVariableMutability(assignedValue: BorroQValue): Mutability {
        return when (assignedValue) {
            is BorroQValue.FieldAccess if assignedValue.fieldPermission.fraction == Rational.ONE -> Mutability.Mutable(
                null
            )

            else -> Mutability.Immutable(null)
        }
    }

    fun inferConstructorReturnMutability(): Mutability = Mutability.Mutable(null)

    fun inferReceiverMutability(): Mutability = Mutability.Immutable(null)
    fun inferReceiverReleaseMode(): ReleaseMode.SingleReleaseMode = ReleaseMode.SingleReleaseMode.Release(null)

    fun inferReturnMutability(): Mutability = Mutability.Immutable(null)
}