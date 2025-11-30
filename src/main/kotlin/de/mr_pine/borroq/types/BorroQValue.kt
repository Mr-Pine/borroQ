package de.mr_pine.borroq.types

import org.checkerframework.dataflow.analysis.AbstractValue

sealed interface BorroQValue : AbstractValue<BorroQValue> {
    override fun leastUpperBound(other: BorroQValue?): BorroQValue {
        TODO("Not yet implemented")
    }

    val hasShallowMutability: Boolean
        get() = false
    val hasShallowReadability: Boolean
        get() = false

    data class FreePermission(val permission: Permission) : BorroQValue {
        override val hasShallowMutability: Boolean
            get() = permission.fraction == Rational.ONE
        override val hasShallowReadability: Boolean
            get() = permission.fraction > Rational.ZERO
    }

    data class FieldAccess(val access: Path, val fieldPermission: Permission) : BorroQValue

    data object Primitive : BorroQValue {
        override val hasShallowMutability: Boolean get() = false
        override val hasShallowReadability: Boolean get() = true
    }

    companion object {
        fun Permission.asValue() = BorroQValue.FreePermission(this)
    }
}