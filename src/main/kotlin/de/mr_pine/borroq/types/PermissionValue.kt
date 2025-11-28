package de.mr_pine.borroq.types

import org.checkerframework.dataflow.analysis.AbstractValue

sealed interface PermissionValue : AbstractValue<PermissionValue> {
    override fun leastUpperBound(other: PermissionValue?): PermissionValue {
        TODO("Not yet implemented")
    }

    val isMutable: Boolean
        get() = false
    val isReadable: Boolean
        get() = false

    data class FreePermission(val permission: Permission) : PermissionValue {
        override val isMutable: Boolean
            get() = permission.fraction == Rational.ONE
        override val isReadable: Boolean
            get() = permission.fraction > Rational.ZERO
    }


    data object Primitive : PermissionValue {
        override val isMutable: Boolean get() = false
        override val isReadable: Boolean get() = true
    }
}

fun Permission.asValue() = PermissionValue.FreePermission(this)