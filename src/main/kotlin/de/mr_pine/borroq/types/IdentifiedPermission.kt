package de.mr_pine.borroq.types

class IdentifiedPermission(rational: Rational, val id: Id) : Permission(rational), VariablePermission {
    @JvmInline
    value class Id(val name: String)

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

    fun combineFractional(other: IdentifiedPermission) =
        if (id != other.id) {
            VariablePermission.Top
        } else {
            IdentifiedPermission(Rational.max(this.fraction, other.fraction), id)
        }
}