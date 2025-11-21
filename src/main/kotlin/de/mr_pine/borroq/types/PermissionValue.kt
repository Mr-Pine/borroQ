package de.mr_pine.borroq.types

import org.checkerframework.dataflow.analysis.AbstractValue

sealed interface PermissionValue : AbstractValue<PermissionValue> {
    override fun leastUpperBound(other: PermissionValue?): PermissionValue {
        TODO("Not yet implemented")
    }

    data class FreePermission(val permission: Permission) : PermissionValue
    data object Primitive : PermissionValue
}

fun Permission.asValue() = PermissionValue.FreePermission(this)