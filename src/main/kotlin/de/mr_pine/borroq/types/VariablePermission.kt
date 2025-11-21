package de.mr_pine.borroq.types

sealed interface VariablePermission {
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