package de.mr_pine.borroq.types

data class Borrow(val source: Path, val fraction: Rational, val target: Identifier) {
    sealed interface Identifier {
        data object Dummy : Identifier
    }
}
