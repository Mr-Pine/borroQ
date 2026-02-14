package de.mr_pine.borroq.types

open class Permission(val fraction: Rational) {

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

