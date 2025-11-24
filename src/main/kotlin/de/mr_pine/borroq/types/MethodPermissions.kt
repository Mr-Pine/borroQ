package de.mr_pine.borroq.types

data class MethodPermissions(val returnPermission: Mutability, val arguments: List<ArgumentPermissions>) {
    data class ArgumentPermissions(val mutability: Mutability)
}