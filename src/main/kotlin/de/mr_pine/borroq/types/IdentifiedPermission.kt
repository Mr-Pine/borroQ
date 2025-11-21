package de.mr_pine.borroq.types

class IdentifiedPermission(fraction: Rational, val id: Id) : Permission(fraction), VariablePermission {

    override fun split(): Pair<IdentifiedPermission, IdentifiedPermission> {
        val (a, b) = super.split()
        return a.withId(id) to b.withId(id)
    }

    fun combineFractional(other: IdentifiedPermission) =
        if (id != other.id) {
            VariablePermission.Top
        } else {
            IdentifiedPermission(Rational.max(this.fraction, other.fraction), id)
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
}

fun Permission.withId(id: Id) = IdentifiedPermission(this.fraction, id)

