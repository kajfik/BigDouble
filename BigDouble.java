import java.io.Serializable;

final class BigDouble implements Comparable<BigDouble>, Serializable
{
    private double Mantissa;
    private long Exponent;

    static final BigDouble Zero = new BigDouble(0.0, 0L);
    static final BigDouble One = new BigDouble(1.0, 0L);
    static final BigDouble NaN = new BigDouble(Double.NaN, Long.MIN_VALUE);
    static final BigDouble PositiveInfinity = new BigDouble(Double.POSITIVE_INFINITY, 0L);
    static final BigDouble NegativeInfinity = new BigDouble(Double.NEGATIVE_INFINITY, 0L);

    static final double Tolerance = 1e-18;

    //for example: if two exponents are more than 17 apart, consider adding them together pointless, just return the larger one
    private static final int MaxSignificantDigits = 17;

    private static final long ExpLimit = Long.MAX_VALUE;

    //the largest exponent that can appear in a Double, though not all mantissas are valid here.
    private static final long DoubleExpMax = 308;

    //The smallest exponent that can appear in a Double, though not all mantissas are valid here.
    private static final long DoubleExpMin = -324;

    double getMantissa(){return Mantissa;}

    long getExponent(){return Exponent;}

    BigDouble()
    {
        Mantissa = Zero.Mantissa;
        Exponent = Zero.Exponent;
    }

    BigDouble(double mantissa, long exponent)
    {
        Mantissa = mantissa;
        Exponent = exponent;
    }

    BigDouble(BigDouble other)
    {
        Mantissa = other.Mantissa;
        Exponent = other.Exponent;
    }

    BigDouble(double value)
    {
        //SAFETY: Handle Infinity and NaN in a somewhat meaningful way.
        if (Double.isNaN(value))
        {
            Mantissa = NaN.Mantissa;
            Exponent = NaN.Exponent;
        }
        else if (Double.isInfinite(value))
        {
            Mantissa = value;
            Exponent = 0L;
        }
        else if (IsZero(value))
        {
            Mantissa = 0.0;
            Exponent = 0L;
        }
        else
        {
            Mantissa = value;
            Exponent = 0L;
            normalize();
        }
    }

    BigDouble(String value)
    {
        this(Parse(value));
    }

    static BigDouble valueOf(double value)
    {
        return new BigDouble(value);
    }

    boolean isNaN()
    {
        return Double.isNaN(Mantissa);
    }

    static boolean IsNaN(BigDouble value)
    {
        return value.isNaN();
    }

    boolean isPositiveInfinity()
    {
        return Mantissa == Double.POSITIVE_INFINITY;
    }

    static boolean IsPositiveInfinity(BigDouble value)
    {
        return value.isPositiveInfinity();
    }

    boolean isNegativeInfinity()
    {
        return Mantissa == Double.NEGATIVE_INFINITY;
    }

    static boolean IsNegativeInfinity(BigDouble value)
    {
        return value.isNegativeInfinity();
    }

    boolean isInfinity()
    {
        return Double.isInfinite(Mantissa);
    }

    static boolean IsInfinity(BigDouble value)
    {
        return value.isInfinity();
    }

    private boolean isZero()
    {
        return Math.abs(Mantissa) < Double.MIN_VALUE;
    }

    private static boolean IsZero(double value)
    {
        return Math.abs(value) < Double.MIN_VALUE;
    }

    private static boolean AreEqual(double left, double right)
    {
        return Math.abs(left - right) < Tolerance;
    }

    private static boolean IsInteger(double value)
    {
        return IsZero(Math.abs(value % 1));
    }

    private boolean isFinite()
    {
        return !Double.isNaN(Mantissa) && !Double.isInfinite(Mantissa);
    }

    private static boolean IsFinite(double value)
    {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    static BigDouble Parse(String value)
    {
        int indexOfE = value.indexOf('e');
        if (indexOfE != -1)
        {
            double mantissa = Double.parseDouble(value.substring(0, indexOfE));
            long exponent = Long.parseLong(value.substring(indexOfE + 1));
            return Normalize(mantissa, exponent);
        }

        if (value.equals("NaN"))
        {
            return NaN;
        }

        BigDouble result = new BigDouble(Double.parseDouble(value));
        if (IsNaN(result))
        {
            throw new IllegalArgumentException("Invalid argument: " + value);
        }

        return result;
    }

    static BigDouble Parse(String value, BigDouble defaultIfNotParsable){
        try {
            return Parse(value);
        } catch(NumberFormatException nfe){
            return defaultIfNotParsable;
        }
    }

    double ToDouble()
    {
        if (IsNaN(this))
        {
            return Double.NaN;
        }

        if (Exponent > DoubleExpMax)
        {
            return Mantissa > 0 ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
        }

        if (Exponent < DoubleExpMin)
        {
            return 0.0;
        }

        //SAFETY: again, handle 5e-324, -5e-324 separately
        if (Exponent == DoubleExpMin)
        {
            return Mantissa > 0 ? 5e-324 : -5e-324;
        }

        double result = Mantissa * Lookup(Exponent);
        if (!IsFinite(result) || Exponent < 0)
        {
            return result;
        }

        double resultRounded = Math.round(result);
        if (Math.abs(resultRounded - result) < 1e-10) return resultRounded;
        return result;
    }

    int ToInteger()
    {
        if (IsNaN(this))
        {
            return Integer.MIN_VALUE;
        }

        if (Exponent > DoubleExpMax)
        {
            return Mantissa > 0 ? Integer.MAX_VALUE : Integer.MIN_VALUE;
        }

        if (Exponent <= DoubleExpMin)
        {
            return 0;
        }

        int result = (int)Math.round(Mantissa * Lookup(Exponent));
        if (!IsFinite(result) || Exponent < 0)
        {
            return result;
        }
        return result;
    }

    BigDouble abs()
    {
        Mantissa = Math.abs(Mantissa);
        return this;
    }

    BigDouble Abs()
    {
        BigDouble result = new BigDouble(this);
        return result.abs();
    }

    static BigDouble Abs(BigDouble value)
    {
        return value.Abs();
    }

    BigDouble negate()
    {
        Mantissa = -Mantissa;
        return this;
    }

    BigDouble Negate()
    {
        BigDouble result = new BigDouble(this);
        return result.negate();
    }

    double Sign()
    {
        return Math.signum(Mantissa);
    }

    static double round(double value, int precision)
    {
        return Math.round(value * Lookup(precision)) / Lookup(precision);
    }

    BigDouble round(long precision)
    {
        if (!isNaN())
        {
            if (Exponent < -1)
            {
                Mantissa = 0.0;
                Exponent = 0L;
            }
            else if (Exponent + precision < MaxSignificantDigits)
            {
                Mantissa = Math.round(Mantissa * Lookup(Exponent + precision)) / Lookup(Exponent + precision);
            }
        }
        return this;
    }

    BigDouble round()
    {
        return round(0);
    }

    BigDouble Round(long precision)
    {
        BigDouble result = new BigDouble(this);
        return result.round(precision);
    }

    BigDouble Round()
    {
        return Round(0);
    }

    BigDouble floor(long precision)
    {
        if (!isNaN())
        {
            if (Exponent < -1)
            {
                Mantissa = Math.signum(Mantissa) >= 0 ? 0.0 : -1.0;
                Exponent = 0L;
            }
            else if (Exponent + precision < MaxSignificantDigits)
            {
                Mantissa = Math.floor(Mantissa * Lookup(Exponent + precision)) / Lookup(Exponent + precision);
            }
        }
        return this;
    }

    BigDouble floor()
    {
        return floor(0L);
    }

    BigDouble Floor(long precision)
    {
        BigDouble result = new BigDouble(this);
        return result.floor(precision);
    }

    BigDouble Floor()
    {
        return Floor(0L);
    }

    BigDouble ceiling(long precision)
    {
        if (!isNaN())
        {
            if (Exponent < -1)
            {
                 Mantissa = Math.signum(Mantissa) > 0 ? 1.0 : 0.0;
                 Exponent = 0L;
            }
            else if (Exponent + precision < MaxSignificantDigits)
            {
                Mantissa = Math.floor(Mantissa * Lookup(Exponent + precision)) / Lookup(Exponent + precision);
            }
        }
        return this;
    }

    BigDouble ceiling()
    {
        return ceiling(0L);
    }

    BigDouble Ceiling(long precision)
    {
        BigDouble result = new BigDouble(this);
        return result.ceiling(precision);
    }

    BigDouble Ceiling()
    {
        return Ceiling(0L);
    }

    static double truncate(double value)
    {
        return value >= 0 ? Math.floor(value) : Math.ceil(value);
    }

    BigDouble truncate(long precision)
    {
        if (!isNaN())
        {
            if (Exponent < 0)
            {
                Mantissa = 0.0;
                Exponent = 0L;
            }
            else if (Exponent + precision < MaxSignificantDigits)
            {
                Mantissa = truncate(Mantissa * Lookup(Exponent + precision)) / Lookup(Exponent + precision);
            }
        }
        return this;
    }

    BigDouble truncate()
    {
        return truncate(0L);
    }

    BigDouble Truncate(long precision)
    {
        BigDouble result = new BigDouble(this);
        return result.truncate(precision);
    }

    BigDouble Truncate()
    {
        return Truncate(0L);
    }

    BigDouble add(BigDouble augend)
    {
        //figure out which is bigger, shrink the mantissa of the smaller by the difference in exponents, add mantissas, normalize and return

        //TODO: Optimizations and simplification may be possible, see https://github.com/Patashu/break_infinity.js/issues/8

        if (IsZero(Mantissa))
        {
            Mantissa = augend.Mantissa;
            Exponent = augend.Exponent;
        }
        else if (!IsZero(augend.Mantissa))
        {
            if (isNaN() || IsNaN(augend) || isInfinity() || IsInfinity(augend))
            {
                // Let Double handle these cases.
                Mantissa += augend.Mantissa;
            }
            else
            {
                BigDouble bigger, smaller;
                if (Exponent >= augend.Exponent)
                {
                    bigger = this;
                    smaller = augend;
                }
                else
                {
                    bigger = augend;
                    smaller = this;
                }

                if (bigger.Exponent - smaller.Exponent > MaxSignificantDigits)
                {
                    Mantissa = bigger.Mantissa;
                    Exponent = bigger.Exponent;
                }
                else
                {
                    //have to do this because adding numbers that were once integers but scaled down is imprecise.
                    //Example: 299 + 18
                    Mantissa = Math.round(1e14 * bigger.Mantissa + 1e14 * smaller.Mantissa *
                            Lookup(smaller.Exponent - bigger.Exponent));
                    Exponent = bigger.Exponent - 14;
                    normalize();
                }
            }
        }

        return this;
    }

    BigDouble Add(BigDouble augend)
    {
        BigDouble result = new BigDouble(this);
        return result.add(augend);
    }

    BigDouble subtract(BigDouble subtrahend)
    {
        add(subtrahend.Negate());
        return this;
    }

    BigDouble Subtract(BigDouble subtrahend)
    {
        BigDouble result = new BigDouble(this);
        return result.subtract(subtrahend);
    }

    BigDouble multiply(int multiplicand)
    {
        Mantissa *= multiplicand;
        return normalize();
    }

    BigDouble Multiply(int multiplicand)
    {
        BigDouble result = new BigDouble(this);
        return result.multiply(multiplicand);
    }

    BigDouble multiply(double multiplicand)
    {
        Mantissa *= multiplicand;
        return normalize();
    }

    BigDouble Multiply(double multiplicand)
    {
        BigDouble result = new BigDouble(this);
        return result.multiply(multiplicand);
    }

    BigDouble multiply(BigDouble multiplicand)
    {
        // 2e3 * 4e5 = (2 * 4)e(3 + 5)
        Mantissa *= multiplicand.Mantissa;
        Exponent += multiplicand.Exponent;
        return normalize();
    }

    BigDouble Multiply(BigDouble multiplicand)
    {
        BigDouble result = new BigDouble(this);
        return result.multiply(multiplicand);
    }

    BigDouble divide(BigDouble divisor)
    {
        multiply(Reciprocate(divisor));
        normalize();
        return this;
    }

    BigDouble Divide(BigDouble divisor)
    {
        BigDouble result = new BigDouble(this);
        return result.divide(divisor);
    }

    BigDouble Reciprocate(BigDouble value)
    {
        return Normalize(1.0 / value.Mantissa, -value.Exponent);
    }

    BigDouble normalize()
    {
        if (Mantissa >= 1 && Mantissa < 10 || !IsFinite(Mantissa))
        {
            return this;
        }

        if (IsZero(Mantissa))
        {
            Mantissa = 0.0;
            Exponent = 0L;
            return this;
        }

        long tempExponent = (long)Math.floor(Math.log10(Math.abs(Mantissa)));
        //SAFETY: handle 5e-324, -5e-324 separately
        if (tempExponent == DoubleExpMin)
        {
            Mantissa = Mantissa * 10 / 1e-323;
        }
        else
        {
            Mantissa = Mantissa / Lookup(tempExponent);
        }

        Exponent = Exponent + tempExponent;
        return this;
    }

    static BigDouble Normalize(double mantissa, long exponent)
    {
        BigDouble result = new BigDouble(mantissa, exponent);
        return result.normalize();
    }

    public String toString() {
        return Double.toString(Mantissa) + 'e' + Long.toString(Exponent);
    }

    int CompareTo(Object other)
    {
        if (other == null)
        {
            return 1;
        }

        if (!(other instanceof BigDouble))
        {
            throw new IllegalArgumentException("The parameter must be a BigDouble.");
        }
        return CompareTo(other);
    }

    @Override
    public int compareTo(BigDouble other) {
        if (IsZero(Mantissa) || IsZero(other.Mantissa)
            || IsNaN(this) || IsNaN(other)
            || IsInfinity(this) || IsInfinity(other))
        {
            // Let Double handle these cases.
            return Double.compare(Mantissa, other.Mantissa);
        }
        if (Mantissa > 0 && other.Mantissa < 0)
        {
            return 1;
        }
        if (Mantissa < 0 && other.Mantissa > 0)
        {
            return -1;
        }

        int exponentComparison = Long.compare(Exponent, other.Exponent);
        return exponentComparison != 0
            ? (Mantissa > 0 ? exponentComparison : -exponentComparison)
            : Double.compare(Mantissa, other.Mantissa);
    }

    boolean Equals(Object other)
    {
        if (!(other instanceof BigDouble))
        {
            return false;
        }
        return Equals(other);
    }

    boolean Equals(BigDouble other)
    {
        return !IsNaN(this) && !IsNaN(other) &&
            (Exponent == other.Exponent && AreEqual(Mantissa, other.Mantissa) || AreSameInfinity(this, other));
    }

    /// <summary>
    /// Relative comparison with tolerance being adjusted with greatest exponent.
    /// <para>
    /// For example, if you put in 1e-9, then any number closer to the larger number
    /// than (larger number) * 1e-9 will be considered equal.
    /// </para>
    /// </summary>
    boolean Equals(BigDouble other, double tolerance)
    {
        return !IsNaN(this) && !IsNaN(other) && (AreSameInfinity(this, other)
                || Abs(this.Subtract(other)).lte(Max(Abs(), Abs(other)).Multiply(new BigDouble(tolerance))));
    }

    private static boolean AreSameInfinity(BigDouble first, BigDouble second)
    {
        return IsPositiveInfinity(first) && IsPositiveInfinity(second)
                || IsNegativeInfinity(first) && IsNegativeInfinity(second);
    }

    boolean eq(BigDouble other)
    {
        return Equals(other);
    }

    static boolean eq(BigDouble left, BigDouble right)
    {
        return left.Equals(right);
    }

    boolean lt(BigDouble other)
    {
        if (isNaN() || other.isNaN()) return false;
        if (IsZero(Mantissa)) return other.Mantissa > 0;
        if (IsZero(other.Mantissa)) return Mantissa < 0;
        if (Exponent == other.Exponent) return Mantissa < other.Mantissa;
        if (Mantissa > 0) return other.Mantissa > 0 && Exponent < other.Exponent;
        return other.Mantissa > 0 || Exponent > other.Exponent;
    }

    static boolean lt(BigDouble left, BigDouble right)
    {
        return left.lt(right);
    }

    boolean lte(BigDouble other)
    {
        if (isNaN() || other.isNaN()) return false;
        return !gt(other);
    }

    static boolean lte(BigDouble left, BigDouble right)
    {
        return left.lte(right);
    }

    boolean gt(BigDouble other)
    {
        if (isNaN() || other.isNaN()) return false;
        if (IsZero(Mantissa)) return other.Mantissa < 0;
        if (IsZero(other.Mantissa)) return Mantissa > 0;
        if (Exponent == other.Exponent) return Mantissa > other.Mantissa;
        if (Mantissa > 0) return other.Mantissa < 0 || Exponent > other.Exponent;
        return other.Mantissa < 0 && Exponent < other.Exponent;
    }

    static boolean gt(BigDouble left, BigDouble right)
    {
        return left.gt(right);
    }

    boolean gte(BigDouble other)
    {
        if (isNaN() || other.isNaN()) return false;
        return !lt(other);
    }

    static boolean gte(BigDouble left, BigDouble right)
    {
        return left.gte(right);
    }

    BigDouble max(BigDouble other)
    {
        if (isNaN() || other.isNaN()) return NaN;
        return gt(other) ? this : other.copy();
    }

    static BigDouble Max(BigDouble left, BigDouble right)
    {
        return left.max(right);
    }

    BigDouble min(BigDouble other)
    {
        if (isNaN() || other.isNaN()) return NaN;
        return this.gt(other) ? other.copy() : this;
    }

    static BigDouble Min(BigDouble left, BigDouble right)
    {
        return left.min(right);
    }

    double AbsLog10()
    {
        return Exponent + Math.log10(Math.abs(Mantissa));
    }

    static double AbsLog10(BigDouble value)
    {
        return value.AbsLog10();
    }

    double Log10()
    {
        return Exponent + Math.log10(Mantissa);
    }

    static double Log10(BigDouble value)
    {
        return value.Log10();
    }

    double Log(BigDouble base)
    {
        return Log(base.ToDouble());
    }

    static double Log(BigDouble value, BigDouble base)
    {
        return value.Log(base.ToDouble());
    }

    double Log(double base)
    {
        if (IsZero(base))
        {
            return Double.NaN;
        }

        //UN-SAFETY: Most incremental game cases are Log(number := 1 or greater, base := 2 or greater). We assume this to be true and thus only need to return a number, not a BigDouble, and don't do any other kind of error checking.
        return 2.30258509299404568402 / Math.log(base) * Log10();
    }

    static double Log(BigDouble value, double base)
    {
        return value.Log(base);
    }

    double log2()
    {
        return 3.32192809488736234787 * Log10();
    }

    static double Log2(BigDouble value)
    {
        return value.log2();
    }

    double ln()
    {
        return 2.30258509299404568402 * Log10();
    }

    static double Ln(BigDouble value)
    {
        return value.ln();
    }

    static BigDouble Pow10(double power)
    {
        return IsInteger(power)
            ? Pow10((long) power)
            : Normalize(Math.pow(10, power % 1), (long) (power > 0 ? Math.floor(power) : Math.ceil(power)));
    }

    static BigDouble Pow10(long power)
    {
        return new BigDouble(1.0, power);
    }
/*
    BigDouble pow(long power)
    {
        if(is10())
        {
            Mantissa = 1.0;
            Exponent = power;
        }
        else
        {
            // TODO: overflows
            double newMantissa = Math.pow(Mantissa, power);
            if(Double.isInfinite(newMantissa))
            {
                pow((double)power);
            }
            else
            {
                Exponent *= power;
                normalize();
            }
        }
        return this;
    }

    BigDouble Pow(long power)
    {
        BigDouble result = new BigDouble(this);
        return result.pow(power);
    }
*/
    BigDouble pow(BigDouble power)
    {
        pow(power.ToDouble());
        return this;
    }

    BigDouble Pow(BigDouble power)
    {
        BigDouble result = new BigDouble(this);
        return result.pow(power);
    }

    static BigDouble Pow(BigDouble value, BigDouble power)
    {
        return value.Pow(power);
    }

    BigDouble pow(double power)
    {
        // TODO: power can be greater that long.MaxValue, which can bring troubles in fast track
        boolean powerIsInteger = IsInteger(power);
        if (lt(Zero) && !powerIsInteger)
        {
            Mantissa = NaN.Mantissa;
            Exponent = NaN.Exponent;
        }
        else
        {
            if(is10() && powerIsInteger)
            {
                BigDouble result = Pow10(power);
                Mantissa = result.Mantissa;
                Exponent = result.Exponent;
            }
            else
            {
                powInternal(power);
            }
        }
        return this;
    }

    BigDouble Pow(double power)
    {
        BigDouble result = new BigDouble(this);
        return result.pow(power);
    }

    private boolean is10()
    {
        return Exponent == 1 && IsZero(Mantissa - 1);
    }

    private boolean is10(BigDouble value)
    {
        return value.is10();
    }

    private void powInternal(double power)
    {
        //UN-SAFETY: Accuracy not guaranteed beyond ~9~11 decimal places.

        //TODO: Fast track seems about neutral for performance. It might become faster if an integer pow is implemented, or it might not be worth doing (see https://github.com/Patashu/break_infinity.js/issues/4 )

        //Fast track: If (this.exponent*power) is an integer and mantissa^power fits in a Number, we can do a very fast method.
        double temp = Exponent * power;
        double newMantissa;
        if (IsInteger(temp) && IsFinite(temp) && Math.abs(temp) < ExpLimit)
        {
            newMantissa = Math.pow(Mantissa, power);
            if (IsFinite(newMantissa))
            {
                Mantissa = newMantissa;
                Exponent = (long) temp;
                normalize();
                return;
            }
        }
        //Same speed and usually more accurate. (An arbitrary-precision version of this calculation is used in break_break_infinity.js, sacrificing performance for utter accuracy.)

        double newExponent = temp >= 0 ? Math.floor(temp) : Math.ceil(temp);
        double residue = temp - newExponent;
        newMantissa = Math.pow(10.0, power * Math.log10(Mantissa) + residue);
        if (IsFinite(newMantissa))
        {
            Mantissa = newMantissa;
            Exponent = (long) newExponent;
            normalize();
        }
        else
        {
            //UN-SAFETY: This should return NaN when mantissa is negative and value is noninteger.
            BigDouble result = Pow10(power * AbsLog10()); //this is 2x faster and gives same values AFAIK
            if (Sign() == -1 && AreEqual(power % 2, 1))
            {
                Mantissa = -result.Mantissa;
            }
            else
            {
                Mantissa = result.Mantissa;
            }
            Exponent = result.Exponent;
        }
    }

    BigDouble sqrt()
    {
        if (Mantissa < 0)
        {
            Mantissa = NaN.Mantissa;
            Exponent = NaN.Exponent;
        }
        else
        {
            if (Exponent % 2 != 0)
            {
                // mod of a negative number is negative, so != means '1 or -1'
                Mantissa = Math.sqrt(Mantissa) * 3.16227766016838;
                Exponent = (long) Math.floor(Exponent / 2.0);
            }
            else
            {
                Mantissa = Math.sqrt(Mantissa);
                Exponent = (long) Math.floor(Exponent / 2.0);
            }
            normalize();
        }
        return this;
    }

    BigDouble Sqrt()
    {
        return this.copy().sqrt();
    }

    BigDouble copy()
    {
        return new BigDouble(this);
    }

    /// <summary>
    /// We need this lookup table because Math.pow(10, exponent) when exponent's absolute value
    /// is large is slightly inaccurate. you can fix it with the power of math... or just make
    /// a lookup table. Faster AND simpler.
    /// </summary>
    private static double[] Powers;

    private static final int IndexOf0 = (int)(-DoubleExpMin - 1);

    static
    {
        Powers = new double[(int)(DoubleExpMax - DoubleExpMin)];
        int index = 0;
        for (int i = 0; i < Powers.length; i++)
        {
            Powers[index++] = Double.parseDouble("1e" + (i - IndexOf0));
        }
    }

    static double Lookup(int power)
    {
        return Powers[IndexOf0 + power];
    }

    static double Lookup(long power)
    {
        return Powers[IndexOf0 + (int)power];
    }
}
