package de.mr_pine.borroq.types

import de.mr_pine.borroq.types.PermissionValue

sealed interface VariablePermission: PermissionValue {
    object Top : VariablePermission {
        override fun toString() = "⊤"
    }

    fun combine(other: VariablePermission) =
        if (this == Top || other == Top) {
            Top
        } else {
            this as IdentifiedPermission
            other as IdentifiedPermission
            combineFractional(other)
        }
}