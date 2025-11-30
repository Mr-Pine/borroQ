package de.mr_pine.borroq.types

open class Permission(val fraction: Rational) {

    /**
     * @param hint A hint to the split of how the permission should be split. If [Mutability.MUTABLE] is specified, the whole permission is split, leaving a `0` permission
     *
     * @return `splitPermission to remainingPermission`
     */
    open fun split(hint: Mutability?): Pair<Permission, Permission> {
        val splitFraction = when (hint!!) {
            is Mutability.Mutable -> this.fraction / 2
            is Mutability.Immutable -> this.fraction
        }
        return Permission(splitFraction) to Permission(fraction - splitFraction)
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

