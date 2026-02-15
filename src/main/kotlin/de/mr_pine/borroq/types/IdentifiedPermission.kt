package de.mr_pine.borroq.types

import de.mr_pine.borroq.types.specifiers.Mutability

class IdentifiedPermission(val fraction: Rational, val id: Id) : VariablePermission {

    fun split(leftShallowMutability: Mutability): Pair<IdentifiedPermission, IdentifiedPermission> {
        return if (leftShallowMutability == Mutability.MUTABLE) {
            IdentifiedPermission(Rational.ONE, id) to IdentifiedPermission(Rational.ZERO, id)
        } else {
            val half = fraction / 2
            IdentifiedPermission(half, id) to IdentifiedPermission(fraction - half, id)
        }
    }

    fun maxFractional(other: IdentifiedPermission) =
        if (id != other.id) {
            VariablePermission.Top
        } else {
            IdentifiedPermission(Rational.max(this.fraction, other.fraction), id)
        }

    fun recombineFractional(other: IdentifiedPermission): IdentifiedPermission {
        require(id == other.id) { "Cannot recombine a non-identified permission with different ids" }
        return IdentifiedPermission(fraction + other.fraction, id)
    }

    override fun toString(): String {
        val perm = super.toString()
        return "${perm}_${id.name}"
    }

    override fun equals(other: Any?): Boolean {
        if (other !is IdentifiedPermission) return false
        return fraction == other.fraction && return other.id == id
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + id.hashCode()
        return result
    }
}


