package de.mr_pine.borroq.types

sealed class Permission(val fraction: Rational) {
    data class Rational(val numerator: Int, val denominator: Int) : Comparable<Rational> {
        override fun toString() = "$numerator/$denominator"

        operator fun plus(other: Rational) = Rational(
            numerator * other.denominator + other.numerator * denominator,
            denominator * other.denominator
        ).reduced()

        operator fun minus(other: Rational) = Rational(
            numerator * other.denominator - other.numerator * denominator,
            denominator * other.denominator
        ).reduced()

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
            val ONE = Rational(1, 1)
            val ZERO = Rational(0, 1)

            fun max(a: Rational, b: Rational) = if (a > b) a else b
        }
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