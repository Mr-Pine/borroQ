package de.mr_pine.borroq.types

data class Borrow(val path: IdPath, val fraction: Rational, val id: Identifier) {
    sealed interface Identifier {
        data object Dummy: Identifier
    }
}
