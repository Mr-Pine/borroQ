package de.mr_pine.borroq.types

import de.mr_pine.borroq.types.Permission.Rational.Companion.minus

open class Permission(val fraction: Rational) {
    data class Rational(val numerator: Int, val denominator: Int) : Comparable<Rational> {
        override fun toString() = "$numerator/$denominator"

        operator fun plus(other: Rational) = Rational(
            numerator * other.denominator + other.numerator * denominator,
            denominator * other.denominator
        ).reduced()

        operator fun minus(other: Rational) = this + (-other)
        operator fun unaryMinus() = Rational(-numerator, denominator)

        fun inverse() = Rational(denominator, numerator)

        operator fun times(other: Rational) = Rational(numerator * other.numerator, denominator * other.denominator)

        operator fun div(other: Rational) = this * other.inverse()
        operator fun div(other: Int) = this / other.asRational()


        fun reduced(): Rational {
            fun calculateGCD(a: Int, b: Int): Int {
                var num1 = a
                var num2 = b
                while (num2 != 0) {
                    val temp = num2
                    num2 = num1 % num2
                    num1 = temp
                }
                return num1
            }

            val gcd = calculateGCD(numerator, denominator)

            return Rational(numerator / gcd, denominator / gcd)
        }

        override fun compareTo(other: Rational) = (this - other).numerator

        companion object {
            fun Int.asRational() = Rational(this, 1)
            operator fun Int.minus(other: Rational) = this.asRational() - other

            val ONE = 1.asRational()
            val ZERO = 0.asRational()

            fun max(a: Rational, b: Rational) = if (a > b) a else b
        }
    }

    open fun split(hint: Mutability?): Pair<Permission, Permission> {
        val splitFraction = when (hint ?: Mutability.Companion.Defaults.split) {
            Mutability.IMMUTABLE -> this.fraction / 2
            Mutability.MUTABLE -> this.fraction
        }
        return Permission(splitFraction) to Permission(1 - splitFraction)
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