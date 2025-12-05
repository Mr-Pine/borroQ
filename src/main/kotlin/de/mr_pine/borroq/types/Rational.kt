package de.mr_pine.borroq.types

import kotlin.math.abs
import kotlin.math.sign

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

    fun isZero() = numerator == 0


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
        val sign = numerator.sign * denominator.sign

        return Rational(sign * abs(numerator / gcd), abs(denominator / gcd))
    }

    override fun compareTo(other: Rational) = (this - other).numerator

    companion object {
        fun Int.asRational() = Rational(this, 1)
        operator fun Int.minus(other: Rational) = this.asRational() - other

        val ONE = 1.asRational()
        val HALF = ONE / 2
        val ZERO = 0.asRational()

        fun max(a: Rational, b: Rational) = if (a > b) a else b
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Rational) return false
        val reducedSelf = reduced()
        val reducedOther = other.reduced()
        return reducedSelf.numerator == reducedOther.numerator && reducedSelf.denominator == reducedOther.denominator
    }

    override fun hashCode(): Int {
        var result = numerator
        result = 31 * result + denominator
        return result
    }
}