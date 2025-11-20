package de.mr_pine.borroq.types

sealed class Permission(val fraction: Rational) {
    data class Rational(val numerator: Int, val denominator: Int) {
        override fun toString() = "$numerator/$denominator"
    }

    override fun toString() = "[$fraction]"

    override fun equals(other: Any?): Boolean {
        return if (other is Permission) {
            other.fraction == fraction
        } else false
    }

    override fun hashCode(): Int {
        return fraction.hashCode()
    }
}