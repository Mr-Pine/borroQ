package de.mr_pine.borroq.types

import de.mr_pine.borroq.types.specifiers.Mutability

class IdentifiedPermission(fraction: Rational, val id: Id) : Permission(fraction), VariablePermission {

    override val hasShallowMutability: Boolean = fraction == Rational.ONE
    override val hasShallowReadability: Boolean = !fraction.isZero()

    override fun split(hint: Mutability): Pair<IdentifiedPermission, IdentifiedPermission> {
        val (a, b) = super.split(hint)
        return a.withId(id) to b.withId(id)
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
        return super.equals(other) && return other.id == id
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + id.hashCode()
        return result
    }

    companion object {
        fun Permission.withId(id: Id) = IdentifiedPermission(this.fraction, id)
    }
}


