package de.mr_pine.borroq.types

import de.mr_pine.borroq.analysis.transfer.BorroQTransfer.Pseudoarg.BorrowTarget
import org.checkerframework.dataflow.analysis.AbstractValue

sealed interface BorroQValue : AbstractValue<BorroQValue> {
    override fun leastUpperBound(other: BorroQValue?): BorroQValue {
        TODO("Not yet implemented")
    }

    data class FreePermission(val fraction: Rational, val attachedBorrows: List<FreeBorrow>) : BorroQValue {

        data class FreeBorrow(
            val source: Path,
            val fraction: Rational,
            val targetType: BorrowTarget
        ) {
            fun toBorrow(id: Borrow.Identifier) = when (targetType) {
                BorrowTarget.RETURN_VALUE -> Borrow(source, fraction, id)
                BorrowTarget.PERSISTENT -> Borrow(source, fraction, Borrow.Identifier.Dummy)
            }
        }
    }
}