package de.mr_pine.borroq.types

sealed interface VariablePermission : PermissionValue {
    object Top : VariablePermission {
        override fun toString() = "⊤"
    }

    fun combine(other: VariablePermission) = if (this == Top || other == Top) {
        Top
    } else {
        this as IdentifiedPermission
        other as IdentifiedPermission
        combineFractional(other)
    }

    val isMutable: Boolean
        get() = false
    val isReadable: Boolean
        get() = false
}