import java.io.Serializable;

public final class BigDouble implements Comparable<BigDouble>, Serializable
{
    private double mantissa;
    private long exponent;
    private static final BigDouble Zero = new BigDouble(0.0, 0L);
    private static final BigDouble NaN = new BigDouble(Double.NaN, Long.MIN_VALUE);

    private static final double Tolerance = 1e-18;

    //for example: if two exponents are more than 17 apart, consider adding them together pointless, just return the larger one
    private static final int MaxSignificantDigits = 17;

    private static final long ExpLimit = Long.MAX_VALUE;

    //the largest exponent that can appear in a Double, though not all mantissas are valid here.
    private static final long DoubleExpMax = 308;

    //The smallest exponent that can appear in a Double, though not all mantissas are valid here.
    private static final long DoubleExpMin = -324;

    //the largest exponent that can appear in an Integer, though not all mantissas are valid here.
    private static final long IntegerExpMax = 9;

    public double getMantissa() {
        return mantissa;
    }

    public long getExponent() {
        return exponent;
    }

    public BigDouble() {
        mantissa = Zero.mantissa;
        exponent = Zero.exponent;
    }

    public BigDouble(double mantissa, long exponent)
    {
        this(mantissa, exponent, true);
    }

    public BigDouble(double mantissa, long exponent, boolean normalize) {
        this.mantissa = mantissa;
        this.exponent = exponent;
        if (normalize) normalize();
    }

    public BigDouble(BigDouble other)
    {
        this(other, true);
    }

    public BigDouble(BigDouble other, boolean normalize) {
        mantissa = other.mantissa;
        exponent = other.exponent;
        if (normalize) normalize();
    }

    public BigDouble(double value) {
        //SAFETY: Handle Infinity and NaN in a somewhat meaningful way.
        if (Double.isNaN(value))
        {
            mantissa = NaN.mantissa;
            exponent = NaN.exponent;
        }
        else if (Double.isInfinite(value))
        {
            mantissa = value;
            exponent = 0L;
        }
        else if (IsZero(value))
        {
            mantissa = 0.0;
            exponent = 0L;
        }
        else
        {
            mantissa = value;
            exponent = 0L;
            normalize();
        }
    }

    public BigDouble(String value)
    {
        this(Parse(value));
    }

    public static BigDouble valueOf(double value) {
        return new BigDouble(value);
    }

    private boolean isNaN() {
        return Double.isNaN(mantissa);
    }

    public static boolean isNaN(BigDouble value) {
        return value.isNaN();
    }

    private boolean isPositiveInfinity() {
        return mantissa == Double.POSITIVE_INFINITY;
    }

    private static boolean IsPositiveInfinity(BigDouble value) {
        return value.isPositiveInfinity();
    }

    private boolean isNegativeInfinity() {
        return mantissa == Double.NEGATIVE_INFINITY;
    }

    private static boolean IsNegativeInfinity(BigDouble value) {
        return value.isNegativeInfinity();
    }

    private boolean isInfinity() {
        return Double.isInfinite(mantissa);
    }

    private static boolean IsInfinity(BigDouble value) {
        return value.isInfinity();
    }

    private boolean isInfinityOrNaN() {
        return isInfinity() || isNaN();
    }

    public static boolean isBroken(BigDouble value) {
        return value.isInfinityOrNaN() || value.lt(Zero);
    }

    private boolean isZero() {
        return Math.abs(mantissa) < Double.MIN_VALUE;
    }

    public static boolean IsZero(double value) {
        return Math.abs(value) < Double.MIN_VALUE;
    }

    private static boolean AreEqual(double left, double right) {
        return Math.abs(left - right) < Tolerance;
    }

    private static boolean IsInteger(double value) {
        return IsZero(Math.abs(value % 1));
    }

    private boolean isFinite() {
        return !Double.isNaN(mantissa) && !Double.isInfinite(mantissa);
    }

    public static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    public static BigDouble Parse(String value) {
        int indexOfE = value.indexOf('e');
        if (indexOfE == 0) {
            if (value.charAt(1) == 'e') return Pow10(Pow10(Double.parseDouble(value.substring(2))).toDouble());
            else return Pow10(Double.parseDouble(value.substring(1)));
        } else if (indexOfE != -1) {
            double mantissa = Double.parseDouble(value.substring(0, indexOfE));
            long exponent = Long.parseLong(value.substring(indexOfE + 1).replace(",","").replace("+", ""));
            return Normalize(mantissa, exponent);
        }

        BigDouble result = new BigDouble(Double.parseDouble(value));
        if (result.isNaN() || result.isInfinity())
        {
            throw new IllegalArgumentException("Invalid argument: " + value);
        }

        return result;
    }

    public static BigDouble Parse(String value, BigDouble defaultIfNotParsable){
        try {
            return Parse(value);
        } catch(Exception e){
            return defaultIfNotParsable;
        }
    }

    public double toDouble() {
        if (this.isNaN())
        {
            return Double.NaN;
        }

        if (exponent > DoubleExpMax)
        {
            return mantissa > 0 ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
        }

        if (exponent < DoubleExpMin)
        {
            return 0.0;
        }

        //SAFETY: again, handle 5e-324, -5e-324 separately
        if (exponent == DoubleExpMin)
        {
            return mantissa > 0 ? 5e-324 : -5e-324;
        }

        double result = mantissa * Lookup(exponent);
        if (!isFinite(result) || exponent < 0)
        {
            return result;
        }

        double resultRounded = round(result);
        if (Math.abs(resultRounded - result) < 1e-10) return resultRounded;
        return result;
    }

    public int toInteger() {
        if (this.isNaN())
        {
            return Integer.MIN_VALUE;
        }

        if (exponent > IntegerExpMax) {
            return mantissa > 0 ? Integer.MAX_VALUE : Integer.MIN_VALUE;
        } else if (exponent == IntegerExpMax) {
            if (mantissa >= 2.147483647) return Integer.MAX_VALUE;
            else if (mantissa <= -2.147483648) return Integer.MIN_VALUE;
        } else if (exponent < -1) {
            return 0;
        }

        return (int)Math.round(mantissa * Lookup(exponent));
    }

    public BigDouble abs() {
        mantissa = Math.abs(mantissa);
        return this;
    }

    public BigDouble Abs() {
        BigDouble result = new BigDouble(this, false);
        return result.abs();
    }

    public static BigDouble Abs(BigDouble value) {
        return value.Abs();
    }

    public BigDouble negate() {
        mantissa = -mantissa;
        return this;
    }

    public BigDouble Negate() {
        BigDouble result = new BigDouble(this, false);
        return result.negate();
    }

    public double Sign() {
        return Math.signum(mantissa);
    }

    public BigDouble round(long precision) {
        if (!isNaN())
        {
            if (exponent < -1)
            {
                mantissa = 0.0;
                exponent = 0L;
            }
            else if (exponent + precision < MaxSignificantDigits)
            {
                mantissa = round(mantissa * Lookup(exponent + precision)) / Lookup(exponent + precision);
            }
        }
        return this;
    }

    public BigDouble round() {
        return round(0);
    }

    public BigDouble Round(long precision) {
        BigDouble result = new BigDouble(this, false);
        return result.round(precision);
    }

    public BigDouble Round() {
        return Round(0);
    }

    public BigDouble floor(long precision) {
        if (!isNaN())
        {
            if (exponent < -1)
            {
                mantissa = Math.signum(mantissa) >= 0 ? 0.0 : -1.0;
                exponent = 0L;
            }
            else if (exponent + precision < MaxSignificantDigits)
            {
                mantissa = Math.floor(mantissa * Lookup(exponent + precision)) / Lookup(exponent + precision);
            }
        }
        return this;
    }

    public BigDouble floor() {
        return floor(0L);
    }

    public BigDouble Floor(long precision) {
        BigDouble result = new BigDouble(this, false);
        return result.floor(precision);
    }

    public BigDouble Floor() {
        return Floor(0L);
    }

    public BigDouble ceiling(long precision) {
        if (!isNaN())
        {
            if (exponent < -1)
            {
                 mantissa = Math.signum(mantissa) > 0 ? 1.0 : 0.0;
                 exponent = 0L;
            }
            else if (exponent + precision < MaxSignificantDigits)
            {
                mantissa = Math.ceil(mantissa * Lookup(exponent + precision)) / Lookup(exponent + precision);
            }
        }
        return this;
    }

    public BigDouble ceiling() {
        return ceiling(0L);
    }

    public BigDouble Ceiling(long precision) {
        BigDouble result = new BigDouble(this, false);
        return result.ceiling(precision);
    }

    public BigDouble Ceiling() {
        return Ceiling(0L);
    }

    public static double truncate(double value) {
        return value >= 0 ? Math.floor(value) : Math.ceil(value);
    }

    public BigDouble truncate(long precision) {
        if (!isNaN())
        {
            if (exponent < 0)
            {
                mantissa = 0.0;
                exponent = 0L;
            }
            else if (exponent + precision < MaxSignificantDigits)
            {
                mantissa = truncate(mantissa * Lookup(exponent + precision)) / Lookup(exponent + precision);
            }
        }
        return this;
    }

    public BigDouble truncate() {
        return truncate(0L);
    }

    public BigDouble Truncate(long precision) {
        BigDouble result = new BigDouble(this, false);
        return result.truncate(precision);
    }

    public BigDouble Truncate() {
        return Truncate(0L);
    }

    public BigDouble add(BigDouble augend) {
        //figure out which is bigger, shrink the mantissa of the smaller by the difference in exponents, add mantissas, normalize and return

        //TODO: Optimizations and simplification may be possible, see https://github.com/Patashu/break_infinity.js/issues/8

        if (IsZero(mantissa)) {
            mantissa = augend.mantissa;
            exponent = augend.exponent;
        } else if (!IsZero(augend.mantissa)) {
            if (isNaN() || augend.isNaN() || isInfinity() || IsInfinity(augend)) {
                // Let Double handle these cases.
                mantissa += augend.mantissa;
            } else {
                BigDouble bigger, smaller;
                if (exponent >= augend.exponent) {
                    bigger = this;
                    smaller = augend;
                } else {
                    bigger = augend;
                    smaller = this;
                }

                if (bigger.exponent - smaller.exponent > MaxSignificantDigits) {
                    mantissa = bigger.mantissa;
                    exponent = bigger.exponent;
                } else {
                    //have to do this because adding numbers that were once integers but scaled down is imprecise.
                    //Example: 299 + 18
                    mantissa = round(1e14 * bigger.mantissa + 1e14 * smaller.mantissa *
                            Lookup(smaller.exponent - bigger.exponent));
                    exponent = bigger.exponent - 14;
                    normalize();
                }
            }
        }

        return this;
    }

    public BigDouble Add(BigDouble augend) {
        BigDouble result = new BigDouble(this, false);
        return result.add(augend);
    }

    public BigDouble subtract(BigDouble subtrahend) {
        add(subtrahend.Negate());
        return this;
    }

    public BigDouble Subtract(BigDouble subtrahend) {
        BigDouble result = new BigDouble(this, false);
        return result.subtract(subtrahend);
    }

    public BigDouble multiply(int multiplicand) {
        mantissa *= multiplicand;
        return normalize();
    }

    public BigDouble Multiply(int multiplicand) {
        BigDouble result = new BigDouble(this, false);
        return result.multiply(multiplicand);
    }

    public BigDouble multiply(double multiplicand) {
        mantissa *= multiplicand;
        return normalize();
    }

    public BigDouble Multiply(double multiplicand) {
        BigDouble result = new BigDouble(this, false);
        return result.multiply(multiplicand);
    }

    public BigDouble multiply(BigDouble multiplicand) {
        // 2e3 * 4e5 = (2 * 4)e(3 + 5)
        mantissa *= multiplicand.mantissa;
        exponent += multiplicand.exponent;
        return normalize();
    }

    public BigDouble Multiply(BigDouble multiplicand) {
        BigDouble result = new BigDouble(this, false);
        return result.multiply(multiplicand);
    }

    public BigDouble divide(double divisor) {
        return divide(valueOf(divisor));
    }

    public BigDouble divide(BigDouble divisor) {
        multiply(divisor.Reciprocate());
        normalize();
        return this;
    }

    public BigDouble Divide(double divisor) {
        return Divide(valueOf(divisor));
    }

    public BigDouble Divide(BigDouble divisor) {
        BigDouble result = new BigDouble(this, false);
        return result.divide(divisor);
    }

    public BigDouble reciprocate() {
        mantissa = 1.0 / mantissa;
        exponent = -exponent;
        normalize();
        return this;
    }

    public BigDouble Reciprocate() {
        BigDouble result = new BigDouble(this, false);
        return result.reciprocate();
    }

    private BigDouble normalize() {
        if (mantissa >= 1 && mantissa < 10 || !isFinite(mantissa)) {
            return this;
        }

        if (IsZero(mantissa)) {
            mantissa = 0.0;
            exponent = 0L;
            return this;
        }

        long tempExponent = (long)Math.floor(Math.log10(Math.abs(mantissa)));
        //SAFETY: handle 5e-324, -5e-324 separately
        if (tempExponent == DoubleExpMin) {
            mantissa = mantissa * 10 / 1e-323;
        } else {
            mantissa = mantissa / Lookup(tempExponent);
        }

        exponent = exponent + tempExponent;
        return this;
    }

    private static BigDouble Normalize(double mantissa, long exponent) {
        BigDouble result = new BigDouble(mantissa, exponent);
        return result.normalize();
    }

    public String toString() {
        return Double.toString(mantissa) + 'e' + exponent;
    }

    public String toStringPlusMinus() {
        return Double.toString(mantissa) + 'e' + (exponent > 0 ? '+' : "") + exponent;
    }

    private int CompareTo(Object other) {
        if (other == null) {
            return 1;
        }

        if (!(other instanceof BigDouble)) {
            throw new IllegalArgumentException("The parameter must be a BigDouble.");
        }
        return CompareTo(other);
    }

    @Override
    public int compareTo(BigDouble other) {
        if (IsZero(mantissa) || IsZero(other.mantissa)
            || isNaN() || other.isNaN()
            || IsInfinity(this) || IsInfinity(other))
        {
            // Let Double handle these cases.
            return Double.compare(mantissa, other.mantissa);
        }
        if (mantissa > 0 && other.mantissa < 0) {
            return 1;
        }
        if (mantissa < 0 && other.mantissa > 0) {
            return -1;
        }

        int exponentComparison = Long.compare(exponent, other.exponent);
        return exponentComparison != 0
            ? (mantissa > 0 ? exponentComparison : -exponentComparison)
            : Double.compare(mantissa, other.mantissa);
    }

    public boolean Equals(BigDouble other) {
        return !isNaN() && !other.isNaN() &&
            (exponent == other.exponent && AreEqual(mantissa, other.mantissa) || AreSameInfinity(this, other));
    }

    /// <summary>
    /// Relative comparison with tolerance being adjusted with greatest exponent.
    /// <para>
    /// For example, if you put in 1e-9, then any number closer to the larger number
    /// than (larger number) * 1e-9 will be considered equal.
    /// </para>
    /// </summary>
    public boolean Equals(BigDouble other, double tolerance) {
        return !isNaN() && !other.isNaN() && (AreSameInfinity(this, other)
                || Abs(this.Subtract(other)).lte(Max(Abs(), Abs(other)).Multiply(new BigDouble(tolerance))));
    }

    private static boolean AreSameInfinity(BigDouble first, BigDouble second) {
        return IsPositiveInfinity(first) && IsPositiveInfinity(second)
                || IsNegativeInfinity(first) && IsNegativeInfinity(second);
    }

    public boolean eq(BigDouble other) {
        return Equals(other);
    }

    public static boolean eq(BigDouble left, BigDouble right) {
        return left.Equals(right);
    }

    public boolean lt(BigDouble other) {
        if (isNaN() || other.isNaN()) return false;
        if (IsZero(mantissa)) return other.mantissa > 0;
        if (IsZero(other.mantissa)) return mantissa < 0;
        if (exponent == other.exponent) return mantissa < other.mantissa;
        if (mantissa > 0) return other.mantissa > 0 && exponent < other.exponent;
        return other.mantissa > 0 || exponent > other.exponent;
    }

    public static boolean lt(BigDouble left, BigDouble right) {
        return left.lt(right);
    }

    public boolean lte(BigDouble other) {
        if (isNaN() || other.isNaN()) return false;
        return !gt(other);
    }

    public static boolean lte(BigDouble left, BigDouble right) {
        return left.lte(right);
    }

    public boolean gt(BigDouble other) {
        if (isNaN() || other.isNaN()) return false;
        if (IsZero(mantissa)) return other.mantissa < 0;
        if (IsZero(other.mantissa)) return mantissa > 0;
        if (exponent == other.exponent) return mantissa > other.mantissa;
        if (mantissa > 0) return other.mantissa < 0 || exponent > other.exponent;
        return other.mantissa < 0 && exponent < other.exponent;
    }

    public static boolean gt(BigDouble left, BigDouble right) {
        return left.gt(right);
    }

    public boolean gte(BigDouble other) {
        if (isNaN() || other.isNaN()) return false;
        return !lt(other);
    }

    public static boolean gte(BigDouble left, BigDouble right) {
        return left.gte(right);
    }

    private BigDouble maxInPlace(BigDouble other) {
        if (lt(other) || isNaN()) {
            mantissa = other.getMantissa();
            exponent = other.getExponent();
        }
        return this;
    }

    public BigDouble max(BigDouble other) {
        if (isNaN() || other.isNaN()) return NaN;
        return gt(other) ? this.copy() : other.copy();
    }

    public static BigDouble Max(BigDouble left, BigDouble right) {
        return left.max(right);
    }

    private BigDouble minInPlace(BigDouble other) {
        if (gt(other) || isNaN()) {
            mantissa = other.getMantissa();
            exponent = other.getExponent();
        }
        return this;
    }

    public BigDouble min(BigDouble other) {
        if (isNaN() || other.isNaN()) return NaN;
        return this.gt(other) ? other.copy() : this.copy();
    }

    public static BigDouble Min(BigDouble left, BigDouble right) {
        return left.min(right);
    }

    public double AbsLog10() {
        return exponent + Math.log10(Math.abs(mantissa));
    }

    public double log10() {
        return exponent + Math.log10(mantissa);
    }

    public static double log10(BigDouble value) {
        return value.log10();
    }

    public double pLog10() {
        return (mantissa <= 0 || exponent < 0) ? 0 : log10();
    }

    public static double pLog10(BigDouble value) {
        return value.pLog10();
    }

    public double log(BigDouble base) {
        return log(base.toDouble());
    }

    public static double log(BigDouble value, BigDouble base) {
        return value.log(base.toDouble());
    }

    public double log(double base) {
        if (IsZero(base)) {
            return Double.NaN;
        }

        //UN-SAFETY: Most incremental game cases are Log(number := 1 or greater, base := 2 or greater). We assume this to be true and thus only need to return a number, not a BigDouble, and don't do any other kind of error checking.
        return 2.30258509299404568402 / Math.log(base) * log10();
    }

    public static double log(BigDouble value, double base) {
        return value.log(base);
    }

    public double log2() {
        return 3.32192809488736234787 * log10();
    }

    public static double Log2(BigDouble value) {
        return value.log2();
    }

    public double ln() {
        return 2.30258509299404568402 * log10();
    }

    public static double Ln(BigDouble value) {
        return value.ln();
    }

    public static BigDouble Pow10(double power) {
        return IsInteger(power)
            ? Pow10((long) power)
            : Normalize(Math.pow(10, power % 1), (long) (power > 0 ? Math.floor(power) : Math.ceil(power)));
    }

    public static BigDouble Pow10(long power) {
        return new BigDouble(1.0, power);
    }

    public BigDouble pow(BigDouble power) {
        pow(power.toDouble());
        return this;
    }

    public BigDouble Pow(BigDouble power) {
        BigDouble result = new BigDouble(this, false);
        return result.pow(power);
    }

    public static BigDouble Pow(BigDouble value, BigDouble power) {
        return value.Pow(power);
    }

    public BigDouble pow(double power) {
        // TODO: power can be greater than long.MaxValue, which can bring troubles in fast track
        boolean powerIsInteger = IsInteger(power);
        if (lt(Zero) && !powerIsInteger) {
            mantissa = NaN.mantissa;
            exponent = NaN.exponent;
        } else {
            if(is10() && powerIsInteger) {
                BigDouble result = Pow10(power);
                mantissa = result.mantissa;
                exponent = result.exponent;
            } else {
                powInternal(power);
            }
        }
        return this;
    }

    public BigDouble Pow(double power) {
        BigDouble result = new BigDouble(this, false);
        return result.pow(power);
    }

    private boolean is10() {
        return exponent == 1 && IsZero(mantissa - 1);
    }

    private boolean is10(BigDouble value) {
        return value.is10();
    }

    private void powInternal(double power) {
        //UN-SAFETY: Accuracy not guaranteed beyond ~9~11 decimal places.

        //TODO: Fast track seems about neutral for performance. It might become faster if an integer pow is implemented, or it might not be worth doing (see https://github.com/Patashu/break_infinity.js/issues/4 )

        //Fast track: If (this.exponent*power) is an integer and mantissa^power fits in a Number, we can do a very fast method.
        double temp = exponent * power;
        double newMantissa;
        if (IsInteger(temp) && isFinite(temp) && Math.abs(temp) < ExpLimit) {
            newMantissa = Math.pow(mantissa, power);
            if (isFinite(newMantissa)) {
                mantissa = newMantissa;
                exponent = (long) temp;
                normalize();
                return;
            }
        }
        //Same speed and usually more accurate. (An arbitrary-precision version of this calculation is used in break_break_infinity.js, sacrificing performance for utter accuracy.)

        double newExponent = temp >= 0 ? Math.floor(temp) : Math.ceil(temp);
        double residue = temp - newExponent;
        newMantissa = Math.pow(10.0, power * Math.log10(mantissa) + residue);
        if (isFinite(newMantissa)) {
            mantissa = newMantissa;
            exponent = (long) newExponent;
            normalize();
        } else {
            //UN-SAFETY: This should return NaN when mantissa is negative and value is noninteger.
            BigDouble result = Pow10(power * AbsLog10()); //this is 2x faster and gives same values AFAIK
            if (Sign() == -1 && AreEqual(power % 2, 1)) {
                mantissa = -result.mantissa;
            } else {
                mantissa = result.mantissa;
            }
            exponent = result.exponent;
        }
    }

    public BigDouble exp() {
        double x = toDouble(); // Fast track: if -706 < this < 709, we can use regular exp.

        BigDouble result;

        if (-706 < x && x < 709) {
            result = new BigDouble(Math.exp(x));
        } else {
            result = valueOf(Math.E).Pow(x);
        }

        mantissa = result.mantissa;
        exponent = result.exponent;

        return this;
    }

    public BigDouble Exp() {
        return copy().exp();
    }

    public static BigDouble exp(double value) {
        return valueOf(value).exp();
    }

    public BigDouble sqrt() {
        if (mantissa < 0) {
            mantissa = NaN.mantissa;
            exponent = NaN.exponent;
        } else {
            if (exponent % 2 != 0) {
                // mod of a negative number is negative, so != means '1 or -1'
                mantissa = Math.sqrt(mantissa) * 3.16227766016838;
                exponent = (long) Math.floor(exponent / 2.0);
            } else {
                mantissa = Math.sqrt(mantissa);
                exponent = (long) Math.floor(exponent / 2.0);
            }
            normalize();
        }
        return this;
    }

    public BigDouble Sqrt() {
        return copy().sqrt();
    }

    public BigDouble copy() {
        return new BigDouble(this, false);
    }

    public BigDouble clamp(BigDouble min, BigDouble max) {
        return this.max(min).min(max);
    }

    public BigDouble clampMin(BigDouble min) {
        return maxInPlace(min);
    }

    public BigDouble ClampMin(BigDouble min) {
        return this.max(min);
    }

    public BigDouble clampMax(BigDouble max) {
        return minInPlace(max);
    }

    public BigDouble ClampMax(BigDouble max) {
        return this.min(max);
    }

    public BigDouble clampMaxExponent(long maxExp) {
        if (exponent >= maxExp) {
            exponent = maxExp;
        }
        return this;
    }

    public BigDouble ClampMaxExponent(long maxExp) {
        BigDouble result = new BigDouble(this, false);
        return result.clampMaxExponent(maxExp);
    }

    public boolean isExtremelySmall() {
        return exponent < -9000000000000000000L;
    }

    public static double round(double value) {
        if (value > -9.223372036854776e18 && value < 9.223372036854776e18) {
            return Math.round(value);
        } else {
            return value;
        }
    }

    /// <summary>
    /// We need this lookup table because Math.pow(10, exponent) when exponent's absolute value
    /// is large is slightly inaccurate. you can fix it with the power of math... or just make
    /// a lookup table. Faster AND simpler.
    /// </summary>
    private static double[] Powers;

    private static final int IndexOf0 = (int)(-DoubleExpMin - 1);

    static {
        Powers = new double[(int)(DoubleExpMax - DoubleExpMin)];
        int index = 0;
        for (int i = 0; i < Powers.length; i++) {
            Powers[index++] = Double.parseDouble("1e" + (i - IndexOf0));
        }
    }

    private static double Lookup(int power) {
        return Powers[IndexOf0 + power];
    }

    private static double Lookup(long power) {
        return Powers[IndexOf0 + (int)power];
    }
}