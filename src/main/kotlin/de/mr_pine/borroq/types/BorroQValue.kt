package de.mr_pine.borroq.types

import de.mr_pine.borroq.analysis.transfer.BorroQTransfer.Pseudoarg.BorrowTarget
import de.mr_pine.borroq.types.specifiers.ArgPermission
import org.checkerframework.dataflow.analysis.AbstractValue

sealed interface BorroQValue : AbstractValue<BorroQValue> {
    override fun leastUpperBound(other: BorroQValue?): BorroQValue {
        TODO("Not yet implemented")
    }

    fun hasShallowPermission(permission: ArgPermission) = when (permission) {
        ArgPermission.MUTABLE -> false
        ArgPermission.READABLE -> false
    }

    data class PseudocallResult(val permission: Permission, val attachedBorrows: List<FreeBorrow>) : BorroQValue {
        init {
            if (attachedBorrows.isNotEmpty()) {
                System.err.println("Warning: FreePermission has attached borrows")
            }
        }

        override fun hasShallowPermission(permission: ArgPermission) = when (permission) {
            ArgPermission.MUTABLE -> this.permission.fraction == Rational.ONE
            ArgPermission.READABLE -> this.permission.fraction > Rational.ZERO
        }

        data class FreeBorrow(
            val path: Path,
            val fraction: Rational,
            val borrowTarget: BorrowTarget
        ) {
            fun toBorrow(id: Borrow.Identifier) = when (borrowTarget) {
                BorrowTarget.RETURN_VALUE -> Borrow(path, fraction, id)
                BorrowTarget.PERSISTENT -> Borrow(path, fraction, Borrow.Identifier.Dummy)
            }
        }
    }

    data class FieldAccess(val access: Path, val fieldPermission: Permission) : BorroQValue

    data object Primitive : BorroQValue {
        override fun hasShallowPermission(permission: ArgPermission) = when (permission) {
            ArgPermission.MUTABLE -> false
            ArgPermission.READABLE -> true
        }
    }
}