import androidx.annotation.Keep
import java.io.Serializable
import kotlin.math.*

/*
* This class is implemented to support mutable objects unlike the C#/Javascript implementations.
* Mutability lets us directly modify objects without the need for creating new ones which saves on computational time.
*
* Implementing operator overloading is a possibility, but it forces us to use non-mutable versions of the operations,
* which is why we choose to not use it, even tho it might improve readability a bit.
* */
@Suppress("FunctionName", "RedundantVisibilityModifier")
@Keep
class BigDouble : Comparable<BigDouble>, Serializable {
    public var mantissa = 0.0
    public var exponent: Long = 0

    constructor() {
        mantissa = Zero.mantissa
        exponent = Zero.exponent
    }

    constructor(mantissa: Int, exponent: Long, normalize: Boolean = true) : this(mantissa.toDouble(), exponent, normalize)

    constructor(mantissa: Double, exponent: Long, normalize: Boolean = true) {
        this.mantissa = mantissa
        this.exponent = exponent
        if (normalize) normalize()
    }

    constructor(other: BigDouble, normalize: Boolean = true) {
        mantissa = other.mantissa
        exponent = other.exponent
        if (normalize) normalize()
    }

    constructor(value: Int) : this(value.toDouble())

    constructor(value: Long) : this(value.toDouble())

    constructor(value: Double) {
        //SAFETY: Handle Infinity and NaN in a somewhat meaningful way.
        when {
            value.isNaN() -> {
                mantissa = NaN.mantissa
                exponent = NaN.exponent
            }
            value.isInfinite() -> {
                mantissa = value
                exponent = 0L
            }
            IsZero(value) -> {
                mantissa = 0.0
                exponent = 0L
            }
            else -> {
                mantissa = value
                exponent = 0L
                normalize()
            }
        }
    }

    constructor(value: String) : this(parse(value))

    private val isNaN: Boolean
        get() = mantissa.isNaN()

    private val isPositiveInfinity: Boolean
        get() = mantissa == Double.POSITIVE_INFINITY

    private val isNegativeInfinity: Boolean
        get() = mantissa == Double.NEGATIVE_INFINITY

    private val isInfinity: Boolean
        get() = mantissa.isInfinite()

    private val isInfinityOrNaN: Boolean
        get() = isInfinity || isNaN

    fun toDouble(): Double {
        when {
            isNaN -> return Double.NaN
            exponent > DoubleExpMax -> return if (mantissa > 0) Double.POSITIVE_INFINITY else Double.NEGATIVE_INFINITY
            exponent < DoubleExpMin -> return 0.0
            exponent == DoubleExpMin -> return if (mantissa > 0) 5e-324 else -5e-324
            else -> {
                val result = mantissa * Lookup(exponent)
                if (!isFinite(result) || exponent < 0) {
                    return result
                }

                val resultRounded = roundDouble(result)
                return if (abs(resultRounded - result) < 1e-10) resultRounded else result
            }
        }

    }

    fun toInteger(): Int {
        if (isNaN) {
            return Int.MIN_VALUE
        }

        if (exponent > IntegerExpMax) {
            return if (mantissa > 0) Int.MAX_VALUE else Int.MIN_VALUE
        } else if (exponent == IntegerExpMax) {
            if (mantissa >= 2.147483647) return Int.MAX_VALUE
            else if (mantissa <= -2.147483648) return Int.MIN_VALUE
        } else if (exponent < -1) {
            return 0
        }

        return (mantissa * Lookup(exponent)).roundToInt()
    }

    fun abs(): BigDouble {
        mantissa = abs(mantissa)
        return this
    }

    fun Abs(): BigDouble {
        val result = BigDouble(this, false)
        return result.abs()
    }

    fun negate(): BigDouble {
        mantissa = -mantissa
        return this
    }

    private fun Negate(): BigDouble {
        val result = BigDouble(this, false)
        return result.negate()
    }

    fun Sign(): Double {
        return sign(mantissa)
    }

    fun round(precision: Long = 0): BigDouble {
        if (!isNaN) {
            if (exponent < -1) {
                mantissa = 0.0
                exponent = 0L
            } else if (exponent + precision < MaxSignificantDigits) {
                mantissa = roundDouble(mantissa * Lookup(exponent + precision)) / Lookup(exponent + precision)
            }
        }
        return this
    }

    fun Round(precision: Long = 0): BigDouble {
        val result = BigDouble(this, false)
        return result.round(precision)
    }

    fun floor(precision: Long = 0L): BigDouble {
        if (!isNaN) {
            if (exponent < -1) {
                mantissa = if (sign(mantissa) >= 0) 0.0 else -1.0
                exponent = 0L
            } else if (exponent + precision < MaxSignificantDigits) {
                mantissa = floor(mantissa * Lookup(exponent + precision)) / Lookup(exponent + precision)
            }
        }
        return this
    }

    fun Floor(precision: Long = 0L): BigDouble {
        val result = BigDouble(this, false)
        return result.floor(precision)
    }

    fun ceiling(precision: Long = 0L): BigDouble {
        if (!isNaN) {
            if (exponent < -1) {
                mantissa = if (sign(mantissa) > 0) 1.0 else 0.0
                exponent = 0L
            } else if (exponent + precision < MaxSignificantDigits) {
                mantissa = ceil(mantissa * Lookup(exponent + precision)) / Lookup(exponent + precision)
            }
        }
        return this
    }

    fun Ceiling(precision: Long = 0L): BigDouble {
        val result = BigDouble(this, false)
        return result.ceiling(precision)
    }

    fun add(augend: BigDouble): BigDouble {
        //figure out which is bigger, shrink the mantissa of the smaller by the difference in exponents, add mantissas, normalize and return

        if (IsZero(mantissa)) {
            mantissa = augend.mantissa
            exponent = augend.exponent
        } else if (!IsZero(augend.mantissa)) {
            if (isNaN || augend.isNaN || isInfinity || IsInfinity(augend)) {
                // Let Double handle these cases.
                mantissa += augend.mantissa
            } else {
                val bigger: BigDouble
                val smaller: BigDouble
                if (exponent >= augend.exponent) {
                    bigger = this
                    smaller = augend
                } else {
                    bigger = augend
                    smaller = this
                }
                if (bigger.exponent - smaller.exponent > MaxSignificantDigits) {
                    mantissa = bigger.mantissa
                    exponent = bigger.exponent
                } else {
                    //have to do this because adding numbers that were once integers but scaled down is imprecise.
                    //Example: 299 + 18
                    mantissa = roundDouble(1e14 * bigger.mantissa + 1e14 * smaller.mantissa *
                        Lookup(smaller.exponent - bigger.exponent))
                    exponent = bigger.exponent - 14
                    normalize()
                }
            }
        }
        return this
    }

    fun add(augend: Int): BigDouble {
        add(augend.toBigDouble())
        return this
    }

    fun add(augend: Double): BigDouble {
        add(augend.toBigDouble())
        return this
    }

    fun Add(augend: Int): BigDouble {
        val result = BigDouble(this, false)
        return result.add(augend)
    }

    fun Add(augend: Double): BigDouble {
        val result = BigDouble(this, false)
        return result.add(augend)
    }

    fun Add(augend: BigDouble): BigDouble {
        val result = BigDouble(this, false)
        return result.add(augend)
    }

    fun subtract(subtrahend: Int): BigDouble {
        add(-subtrahend)
        return this
    }

    fun subtract(subtrahend: Double): BigDouble {
        add(-subtrahend)
        return this
    }

    fun subtract(subtrahend: BigDouble): BigDouble {
        add(subtrahend.Negate())
        return this
    }

    fun Subtract(subtrahend: Int): BigDouble {
        val result = BigDouble(this, false)
        return result.subtract(subtrahend)
    }

    fun Subtract(subtrahend: Double): BigDouble {
        val result = BigDouble(this, false)
        return result.subtract(subtrahend)
    }

    fun Subtract(subtrahend: BigDouble): BigDouble {
        val result = BigDouble(this, false)
        return result.subtract(subtrahend)
    }

    fun multiply(multiplicand: Int): BigDouble {
        mantissa *= multiplicand
        return normalize()
    }

    fun Multiply(multiplicand: Int): BigDouble {
        val result = BigDouble(this, false)
        return result.multiply(multiplicand)
    }

    fun multiply(multiplicand: Double): BigDouble {
        mantissa *= multiplicand
        return normalize()
    }

    fun Multiply(multiplicand: Double): BigDouble {
        val result = BigDouble(this, false)
        return result.multiply(multiplicand)
    }

    fun multiply(multiplicand: BigDouble): BigDouble {
        // 2e3 * 4e5 = (2 * 4)e(3 + 5)
        mantissa *= multiplicand.mantissa
        exponent += multiplicand.exponent
        return normalize()
    }

    fun Multiply(multiplicand: BigDouble): BigDouble {
        val result = BigDouble(this, false)
        return result.multiply(multiplicand)
    }

    fun divide(divisor: Int): BigDouble {
        mantissa /= divisor.toDouble()
        return normalize()
    }

    fun divide(divisor: Double): BigDouble {
        mantissa /= divisor
        return normalize()
    }

    fun divide(divisor: BigDouble): BigDouble {
        return multiply(divisor.Reciprocate())
    }

    fun Divide(divisor: Double): BigDouble {
        return Divide(divisor.toBigDouble())
    }

    fun Divide(divisor: BigDouble): BigDouble {
        val result = BigDouble(this, false)
        return result.divide(divisor)
    }

    fun reciprocate(): BigDouble {
        mantissa = 1.0 / mantissa
        exponent = -exponent
        normalize()
        return this
    }

    fun Reciprocate(): BigDouble {
        val result = BigDouble(this, false)
        return result.reciprocate()
    }

    private fun normalize(): BigDouble {
        if (mantissa >= 1 && mantissa < 10 || !isFinite(mantissa)) {
            return this
        }
        if (IsZero(mantissa)) {
            mantissa = 0.0
            exponent = 0L
            return this
        }
        val tempExponent = floor(log10(abs(mantissa))).toLong()
        //SAFETY: handle 5e-324, -5e-324 separately
        mantissa = if (tempExponent == DoubleExpMin) {
            mantissa * 10 / 1e-323
        } else {
            mantissa / Lookup(tempExponent)
        }
        exponent += tempExponent
        return this
    }

    override fun toString(): String {
        return mantissa.toString() + 'e' + exponent
    }

    fun toStringPlusMinus(): String {
        return mantissa.toString() + 'e' + (if (exponent > 0) '+' else "") + exponent
    }

    operator fun compareTo(other: Int): Int {
        return compareTo(other.toBigDouble())
    }

    operator fun compareTo(other: Double): Int {
        return compareTo(other.toBigDouble())
    }

    override fun compareTo(other: BigDouble): Int {
        if (IsZero(mantissa) || IsZero(other.mantissa) || isNaN || other.isNaN || IsInfinity(this) || IsInfinity(other)) {
            // Let Double handle these cases.
            return mantissa.compareTo(other.mantissa)
        }

        if (mantissa > 0 && other.mantissa < 0) {
            return 1
        }

        if (mantissa < 0 && other.mantissa > 0) {
            return -1
        }

        val exponentComparison = exponent.compareTo(other.exponent)
        return if (exponentComparison != 0)
            if (mantissa > 0) exponentComparison else -exponentComparison
        else
            mantissa.compareTo(other.mantissa)
    }

    override fun equals(other: Any?): Boolean {
        return other is BigDouble && !isNaN && !other.isNaN &&
            (exponent == other.exponent && AreEqual(mantissa, other.mantissa) || AreSameInfinity(this, other))
    }

    private fun maxInPlace(other: BigDouble): BigDouble {
        if (this < other || isNaN) {
            mantissa = other.mantissa
            exponent = other.exponent
        }
        return this
    }

    fun max(other: BigDouble): BigDouble {
        if (isNaN || other.isNaN) return NaN
        return if (this > other) copy() else other.copy()
    }

    private fun minInPlace(other: BigDouble): BigDouble {
        if (this > other || isNaN) {
            mantissa = other.mantissa
            exponent = other.exponent
        }
        return this
    }

    fun min(other: BigDouble): BigDouble {
        if (isNaN || other.isNaN) return NaN
        return if (this > other) other.copy() else copy()
    }

    private fun AbsLog10(): Double {
        return exponent + log10(abs(mantissa))
    }

    fun log10(): Double {
        return exponent + log10(mantissa)
    }

    fun pLog10(): Double {
        return if (mantissa <= 0 || exponent < 0) 0.0 else log10()
    }

    fun log(base: BigDouble): Double {
        return log(base.toDouble())
    }

    fun log(base: Double): Double {
        return if (IsZero(base)) Double.NaN
        else 2.30258509299404568402 / ln(base) * log10()

        //UN-SAFETY: Most incremental game cases are Log(number := 1 or greater, base := 2 or greater). We assume this to be true and thus only need to return a number, not a BigDouble, and don't do any other kind of error checking.
    }

    fun log2(): Double {
        return 3.32192809488736234787 * log10()
    }

    fun ln(): Double {
        return 2.30258509299404568402 * log10()
    }

    fun pow(power: Int): BigDouble {
        pow(power.toDouble())
        return this
    }

    fun pow(power: BigDouble): BigDouble {
        pow(power.toDouble())
        return this
    }

    fun Pow(power: BigDouble): BigDouble {
        val result = BigDouble(this, false)
        return result.pow(power)
    }

    fun pow(power: Double): BigDouble {
        val powerIsInteger = IsInteger(power)
        if (this < Zero && !powerIsInteger) {
            mantissa = NaN.mantissa
            exponent = NaN.exponent
        } else {
            if (is10() && powerIsInteger) {
                val result = Pow10(power)
                mantissa = result.mantissa
                exponent = result.exponent
            } else {
                powInternal(power)
            }
        }
        return this
    }

    fun Pow(power: Int): BigDouble {
        val result = BigDouble(this, false)
        return result.pow(power)
    }

    fun Pow(power: Double): BigDouble {
        val result = BigDouble(this, false)
        return result.pow(power)
    }

    private fun is10(): Boolean {
        return exponent == 1L && IsZero(mantissa - 1)
    }

    private fun powInternal(power: Double) {
        //UN-SAFETY: Accuracy not guaranteed beyond ~9~11 decimal places.

        //Fast track: If (this.exponent*power) is an integer and mantissa^power fits in a Number, we can do a very fast method.
        val temp = exponent * power
        var newMantissa: Double
        if (IsInteger(temp) && isFinite(temp) && abs(temp) < ExpLimit) {
            newMantissa = mantissa.pow(power)
            if (isFinite(newMantissa)) {
                mantissa = newMantissa
                exponent = temp.toLong()
                normalize()
                return
            }
        }
        //Same speed and usually more accurate. (An arbitrary-precision version of this calculation is used in break_break_infinity.js, sacrificing performance for utter accuracy.)
        val newExponent = if (temp >= 0) floor(temp) else ceil(temp)
        val residue = temp - newExponent
        newMantissa = 10.0.pow(power * log10(mantissa) + residue)
        if (isFinite(newMantissa)) {
            mantissa = newMantissa
            exponent = newExponent.toLong()
            normalize()
        } else {
            //UN-SAFETY: This should return NaN when mantissa is negative and value is noninteger.
            val result = Pow10(power * AbsLog10()) //this is 2x faster and gives same values AFAIK
            mantissa = if (Sign() == -1.0 && AreEqual(power % 2, 1.0)) {
                -result.mantissa
            } else {
                result.mantissa
            }
            exponent = result.exponent
        }
    }

    fun exp(): BigDouble {
        val x = toDouble() // Fast track: if -706 < this < 709, we can use regular exp.
        val result: BigDouble = if (-706 < x && x < 709) {
            BigDouble(kotlin.math.exp(x))
        } else {
            MATH_E.Pow(x)
        }
        mantissa = result.mantissa
        exponent = result.exponent
        return this
    }

    fun Sqrt(): BigDouble {
        return when {
            mantissa < 0 -> NaN.copy()
            // mod of a negative number is negative, so != means '1 or -1'
            exponent % 2L != 0L -> BigDouble(sqrt(mantissa) * 3.16227766016838, floor(exponent / 2.0).toLong(), true)
            else -> BigDouble(sqrt(mantissa), floor(exponent / 2.0).toLong(), true)
        }
    }

    fun copy(): BigDouble {
        return BigDouble(this, false)
    }

    fun clamp(min: BigDouble, max: BigDouble): BigDouble {
        return this.max(min).min(max)
    }

    fun clampMin(min: BigDouble): BigDouble {
        return maxInPlace(min)
    }

    fun ClampMin(min: BigDouble): BigDouble {
        return this.max(min)
    }

    fun clampMax(max: BigDouble): BigDouble {
        return minInPlace(max)
    }

    fun ClampMax(max: BigDouble): BigDouble {
        return this.min(max)
    }

    fun clampMaxExponent(maxExp: Long): BigDouble {
        if (exponent >= maxExp) {
            exponent = maxExp
        }
        return this
    }

    fun isBroken(): Boolean {
        return isInfinityOrNaN || this < Zero
    }

    override fun hashCode(): Int {
        var result = mantissa.hashCode()
        result = 31 * result + exponent.hashCode()
        return result
    }

    companion object {
        private val Zero = BigDouble(0.0, 0L)
        private val NaN = BigDouble(Double.NaN, Long.MIN_VALUE)
        private val MATH_E = BigDouble(Math.E)
        private const val Tolerance = 1e-18

        //for example: if two exponents are more than 17 apart, consider adding them together pointless, just return the larger one
        private const val MaxSignificantDigits = 17
        private const val ExpLimit = Long.MAX_VALUE

        //the largest exponent that can appear in a Double, though not all mantissas are valid here.
        private const val DoubleExpMax: Long = 308

        //The smallest exponent that can appear in a Double, though not all mantissas are valid here.
        private const val DoubleExpMin: Long = -324

        //the largest exponent that can appear in an Integer, though not all mantissas are valid here.
        private const val IntegerExpMax: Long = 9

        val MAX_VALUE: BigDouble = BigDouble(1.0, ExpLimit)

        fun Int.toBigDouble(): BigDouble {
            return BigDouble(this)
        }

        fun Long.toBigDouble(): BigDouble {
            return BigDouble(this)
        }

        fun Double.toBigDouble(): BigDouble {
            return BigDouble(this)
        }

        fun Double.powB(power: Int): BigDouble {
            return BigDouble(this).pow(power)
        }

        fun Double.powB(power: Double): BigDouble {
            return BigDouble(this).pow(power)
        }

        private fun IsPositiveInfinity(value: BigDouble): Boolean {
            return value.isPositiveInfinity
        }

        private fun IsNegativeInfinity(value: BigDouble): Boolean {
            return value.isNegativeInfinity
        }

        private fun IsInfinity(value: BigDouble): Boolean {
            return value.isInfinity
        }

        fun IsZero(value: Double): Boolean {
            return abs(value) < Double.MIN_VALUE
        }

        private fun AreEqual(left: Double, right: Double): Boolean {
            return abs(left - right) < Tolerance
        }

        private fun IsInteger(value: Double): Boolean {
            return IsZero(abs(value % 1))
        }

        fun isFinite(value: Double): Boolean {
            return !java.lang.Double.isNaN(value) && !java.lang.Double.isInfinite(value)
        }

        fun roundDouble(value: Double): Double {
            return if (value > -9.223372036854776e18 && value < 9.223372036854776e18) {
                value.roundToLong().toDouble()
            } else {
                value
            }
        }

        fun parse(valueInput: String): BigDouble {
            // we want to remove all space in case the string uses space as a thousands separator
            var value = valueInput
            value = value.replace("\\s+".toRegex(), "")
            val indexOfE = value.indexOf('e')
            // if the 'e' is at the start we know it's the Logarithm notation
            if (indexOfE == 0) {
                return if (value[1] == 'e') Pow10(Pow10(value.substring(2).replace(",", ".").toDouble()).toDouble()) else Pow10(value.substring(1).replace(",", ".").toDouble())
            } else if (indexOfE != -1) {
                val mantissa = value.substring(0, indexOfE).replace(",", ".").toDouble()
                val exponent = value.substring(indexOfE + 1).replace(".", "").replace(",", "").replace("+", "").toLong()
                return Normalize(mantissa, exponent)
            }
            val result = BigDouble(value.replace(",", ".").toDouble())
            require(!(result.isNaN || result.isInfinity)) { "Invalid argument: $value" }
            return result
        }

        private fun Normalize(mantissa: Double, exponent: Long): BigDouble {
            val result = BigDouble(mantissa, exponent)
            return result.normalize()
        }

        private fun AreSameInfinity(first: BigDouble, second: BigDouble): Boolean {
            return (IsPositiveInfinity(first) && IsPositiveInfinity(second)
                || IsNegativeInfinity(first) && IsNegativeInfinity(second))
        }

        fun Max(left: BigDouble, right: BigDouble): BigDouble {
            return left.max(right)
        }

        fun Min(left: BigDouble, right: BigDouble): BigDouble {
            return left.min(right)
        }

        fun log10(value: BigDouble): Double {
            return value.log10()
        }

        fun log(value: BigDouble, base: BigDouble): Double {
            return value.log(base.toDouble())
        }

        fun log(value: BigDouble, base: Double): Double {
            return value.log(base)
        }

        fun Pow10(power: Int): BigDouble {
            return BigDouble(1.0, power.toLong())
        }

        private fun Pow10(power: Long): BigDouble {
            return BigDouble(1.0, power)
        }

        fun Pow10(power: Double): BigDouble {
            return if (IsInteger(power)) Pow10(power.toLong()) else Normalize(10.0.pow(power % 1), (if (power > 0) floor(power) else ceil(power)).toLong())
        }

        fun Pow(value: BigDouble, power: BigDouble): BigDouble {
            return value.Pow(power)
        }

        fun exp(value: Double): BigDouble {
            return value.toBigDouble().exp()
        }

        fun sumReducer(accumulator: BigDouble, value: BigDouble): BigDouble {
            return accumulator.add(value)
        }

        fun productReducer(accumulator: BigDouble, value: BigDouble): BigDouble {
            return accumulator.multiply(value)
        }

        /// <summary>
        /// We need this lookup table because Math.pow(10, exponent) when exponent's absolute value
        /// is large is slightly inaccurate. you can fix it with the power of math... or just make
        /// a lookup table. Faster AND simpler.
        /// </summary>
        private var Powers: DoubleArray = DoubleArray((DoubleExpMax - DoubleExpMin).toInt())
        private const val IndexOf0 = (-DoubleExpMin - 1).toInt()

        private fun Lookup(power: Long): Double {
            return Powers[IndexOf0 + power.toInt()]
        }

        init {
            var index = 0
            for (i in Powers.indices) {
                Powers[index++] = ("1e" + (i - IndexOf0)).toDouble()
            }
        }
    }
}