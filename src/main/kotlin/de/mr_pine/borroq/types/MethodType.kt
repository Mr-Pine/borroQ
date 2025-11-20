package de.mr_pine.borroq.types

data class MethodType(val returnPermission: Permission, val arguments: List<ArgumentType>) {
    data class ArgumentType(val placeholder: String)
}