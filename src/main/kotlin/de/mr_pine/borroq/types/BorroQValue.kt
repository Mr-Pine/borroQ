package de.mr_pine.borroq.types

import org.checkerframework.dataflow.analysis.AbstractValue

sealed interface BorroQValue : AbstractValue<BorroQValue> {
    override fun leastUpperBound(other: BorroQValue?): BorroQValue {
        TODO("Not yet implemented")
    }

    val isMutable: Boolean
        get() = false
    val isReadable: Boolean
        get() = false

    data class FreePermission(val permission: Permission) : BorroQValue {
        override val isMutable: Boolean
            get() = permission.fraction == Rational.ONE
        override val isReadable: Boolean
            get() = permission.fraction > Rational.ZERO
    }

    data class FieldAccess(val access: Path, val fieldPermission: Permission): BorroQValue

    data object Primitive : BorroQValue {
        override val isMutable: Boolean get() = false
        override val isReadable: Boolean get() = true
    }
}

fun Permission.asValue() = BorroQValue.FreePermission(this)