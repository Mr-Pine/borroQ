package de.mr_pine.borroq.types

class IdentifiedPermission(rational: Rational, val id: Id) : Permission(rational) {
    data class Id(val name: String)

    override fun toString(): String {
        val perm = super.toString()
        return "${perm}_$id"
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