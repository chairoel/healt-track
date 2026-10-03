package com.mascill.healthtrack.util

import com.mascill.healthtrack.domain.model.BiasV235Data
import com.mascill.healthtrack.domain.model.BiasV235Result
import com.mascill.healthtrack.domain.model.V235Report
import com.mascill.healthtrack.domain.model.V235ReportResult

private const val FAT = 0
private const val SLM_GROUP = 1
private const val WATER = 2
private const val BMR_GROUP = 3
private const val VISCERAL = 4
private const val AGE_GROUP = 5

private const val COEFFICIENT_SCALE = 10000.0
private const val WEIGHT_HUNDRED = 100.0
private const val IMPEDANCE_TEN = 10.0
private const val BYTE_MASK = 0xFF
private const val SHORT_MASK = 0xFFFF
private const val SIGNED_SHORT = 0x8000
private const val SHORT_SPAN = 0x10000
private const val WORD_MASK = 0xFFFFFFFFL

private const val HEIGHT_REJECT = 65405
private const val HEIGHT_BIAS = 221
private const val AGE_REJECT = 174
private const val AGE_BIAS = 100
private const val IMPEDANCE_REJECT = 52535
private const val IMPEDANCE_BIAS = 15001

private const val WEIGHT_MIN_KG = 20.0
private const val WEIGHT_MAX_KG = 250.0
private const val HEAVY_KG = 150.0
private const val BFP_MIN = 5.0
private const val BFP_MAX = 45.0
private const val SLM_MIN = 7.0
private const val SLM_HI_LIGHT = 141.5
private const val SLM_HI_HEAVY = 236.5
private const val BONE_MIN = 1.0
private const val BONE_HI_LIGHT = 4.0
private const val BONE_HI_HEAVY = 7.0
private const val WATER_MIN = 20.0
private const val WATER_MAX = 85.0
private const val MUSCLE_MIN = 20.0
private const val MUSCLE_HI_LIGHT = 70.0
private const val MUSCLE_HI_HEAVY = 117.0
private const val VISCERAL_MIN = 1.0
private const val VISCERAL_MAX = 59.0
private const val STANDARD_LOW_CM = 150
private const val STANDARD_SPAN_CM = 40
private const val STANDARD_SCALE = 21.25
private const val FEMALE_HEIGHT_OFFSET = 70
private const val MALE_HEIGHT_OFFSET = 80
private const val FEMALE_HEIGHT_COEF = 0.6
private const val MALE_HEIGHT_COEF = 0.7
private const val AGE_DELTA = 10
private const val AGE_MIN = 18
private const val AGE_MAX = 80
private const val AGE_SPAN = 11
private const val BMI_ADD = -2470.0
private const val BMI_SCORE_MIN = 55.0
private const val BMI_SCORE_MAX = 96.0
private const val VFR_SPLIT = 15.0
private const val VFR_FLOOR = -50.0
private const val BODY_OFFSET = 90.0
private const val BODY_CAP = 100.0
private const val FAT_TERM_MIN = 55.0
private const val SCORE_MIN = 45
private const val SCORE_MAX = 100
private const val FAT_SLOPE_BASE = 0.25
private const val PERCENT = 100.0
private const val THOUSAND = 1000.0
private const val CODE_OK = 0
private const val CODE_WEIGHT = -2
private const val CODE_HEIGHT = -3
private const val CODE_AGE = -4
private const val CODE_SEX = -5
private const val CODE_IMPEDANCE = -6

private val coefficient = arrayOf(
    intArrayOf(-3332, 7509, 196, 72, 227193),
    intArrayOf(-3315, 6216, 183, 85, 225540),
    intArrayOf(31860, 19340, -2060, -1320, -1645560),
    intArrayOf(28670, 38940, -4080, -1235, -1576650),
    intArrayOf(87700, 297300, 12800, -6030, 517500),
    intArrayOf(93900, 375800, -3200, -6925, 97000),
    intArrayOf(75432, 99474, -34382, -3090, -2882821),
    intArrayOf(75037, 131523, -43376, -3486, -3117751),
    intArrayOf(-1651, 2628, 649, 24, 123445),
    intArrayOf(-2675, 4200, 1462, 123, 139871),
    intArrayOf(-11165, 15784, 4615, 415, 832548),
    intArrayOf(-7471, 9161, 4184, 517, 542267),
)

private val kVfr4 = doubleBits(0xBFD2147AE0000000uL)
private val kPolyScale = doubleBits(0x3FD99999A0000000uL)
private val kSmmConst = doubleBits(0xC034376C8B439581uL)
private val kScoreFLin = doubleBits(0xC0240A3D80000000uL)
private val kBodyScale = doubleBits(0x3FB99999A0000000uL)
private val kBmiQuad = doubleBits(0xC016BE76C0000000uL)
private val kScoreMLin = doubleBits(0x4025B33340000000uL)
private val kSmmWt = doubleBits(0x3FC65604189374BCuL)
private val kVfrConst = doubleBits(0x4056566660000000uL)
private val kScoreF4 = doubleBits(0x3F302E4B60000000uL)
private val kSmmAge = doubleBits(0xBF907C84B5DCC63FuL)
private val kVfr3 = doubleBits(0x400F4BC6A0000000uL)
private val kScoreM2 = doubleBits(0xBFD2E48E80000000uL)
private val kScoreF2 = doubleBits(0x3FEEB5DCC0000000uL)
private val kScoreMConst = doubleBits(0x3FDB2FEC60000000uL)
private val kVfr5 = doubleBits(0x3F7D8A5480000000uL)
private val kBmiLin = doubleBits(0x406E366660000000uL)
private val kScoreF3 = doubleBits(0xBF9C8C9320000000uL)
private val kSmmSex = doubleBits(0x40036A4A8C154C98uL)
private val kVfr1 = doubleBits(0x403E6147A0000000uL)
private val kSmmZ = doubleBits(0xBF916872B020C49CuL)
private val kVfr2 = doubleBits(0xC036451EC0000000uL)
private val kVfrWeight = doubleBits(0x3FB47AE140000000uL)
private val kScoreFConst = doubleBits(0x40541AE140000000uL)
private val kSmmH = doubleBits(0x3FD0779A6B50B0F2uL)
private val kScoreM3 = doubleBits(0xBF6A0F0A60000000uL)
private val kScoreM4 = doubleBits(0x3F1C714FC0000000uL)
private val kFcSex = doubleBits(0xBFB47AE147AE147BuL)
private val kBmiCoef = doubleBits(0x3FD99999A0000000uL)
private val kBmiCoefZeroVfr = doubleBits(0x3FDEB851E0000000uL)
private val kAgeOnBfp = floatBits(0x3CF5C28F)
private val kWtMale = floatBits(0x3F451EB8)
private val kWtFemale = floatBits(0x3F3C28F6)

private fun doubleBits(bits: ULong): Double = Double.fromBits(bits.toLong())

private fun floatBits(bits: Int): Double = Float.fromBits(bits).toDouble()

private fun i16(value: Int): Int {
    val masked = value and SHORT_MASK
    return if (masked >= SIGNED_SHORT) masked - SHORT_SPAN else masked
}

private fun linear(
    height: Int,
    weight100: Int,
    impedance10: Int,
    age: Int,
    sex: Int,
    group: Int,
): Double {
    val row = coefficient[sex + (group shl 1)]
    val heightS = i16(height)
    val weightS = i16(weight100)
    val impedanceS = i16(impedance10)
    val fromWeight = row[1].toDouble() * weightS.toDouble() / WEIGHT_HUNDRED
    val fromZ = row[3].toDouble() * impedanceS.toDouble() / IMPEDANCE_TEN
    var total = fma(row[0].toDouble(), heightS.toDouble(), fromWeight)
    total = fma(row[2].toDouble(), (age and BYTE_MASK).toDouble(), total)
    return (total + fromZ + row[4].toDouble()) / COEFFICIENT_SCALE
}

private fun halfStep(value: Double): Double {
    val whole = value.toInt()
    if (value - whole.toDouble() < 0.5) {
        return whole.toDouble()
    }
    return f32(f32(whole.toDouble()) + f32(0.5))
}

private fun standardWeight(height: Int, sex: Int): Double {
    val heightU = height and SHORT_MASK
    if (((heightU - STANDARD_LOW_CM).toLong() and WORD_MASK) > STANDARD_SPAN_CM) {
        val heightF = f32(i16(heightU).toDouble())
        val scaled = f32(f32(heightF * f32(STANDARD_SCALE)) / f32(WEIGHT_HUNDRED))
        return f32(f32(scaled * heightF) / f32(WEIGHT_HUNDRED))
    }
    if (sex != 1) {
        val delta = i16(heightU - FEMALE_HEIGHT_OFFSET)
        return f32(f32(delta.toDouble()) * f32(FEMALE_HEIGHT_COEF))
    }
    val delta = i16(heightU - MALE_HEIGHT_OFFSET)
    return f32(f32(delta.toDouble()) * f32(MALE_HEIGHT_COEF))
}

private fun bodyAge(raw: Double, age: Int): Int {
    val years = age and BYTE_MASK
    var estimate = raw.toInt()
    if (estimate - years >= AGE_SPAN) {
        estimate = years + AGE_DELTA
    } else if (years - estimate > AGE_DELTA) {
        estimate = years - AGE_DELTA
    }
    if (estimate <= AGE_MIN) {
        estimate = AGE_MIN
    }
    if (estimate >= AGE_MAX) {
        estimate = AGE_MAX
    }
    return estimate
}

private fun score(
    bfp: Double,
    slm: Double,
    vfr: Double,
    bmi: Double,
    weight100: Int,
    age: Int,
    sex: Int,
): Int {
    val bmiPoly = fma(bmi * kBmiQuad, bmi, bmi * kBmiLin) + BMI_ADD
    val bmiBase = when {
        bmiPoly < BMI_SCORE_MIN -> BMI_SCORE_MIN
        bmiPoly > BMI_SCORE_MAX -> BMI_SCORE_MAX
        else -> bmiPoly
    }
    val x = bfp + f32((age and BYTE_MASK).toDouble() * kAgeOnBfp)
    val fatTerm: Double
    val weightCoef: Double
    if (sex != 0) {
        val square = x * x
        val cube = x * square
        val fourth = x * cube
        var male = cube * kScoreM3
        male = fma(fourth, kScoreM4, male)
        male = fma(square, kScoreM2, male)
        fatTerm = fma(x, kScoreMLin, male) + kScoreMConst
        weightCoef = kWtMale
    } else {
        var fourth = x * kScoreF4
        var cube = x * kScoreF3
        fourth *= x
        cube *= x
        fourth *= x
        cube *= x
        var female = fma(fourth, x, cube)
        female = fma(x * kScoreF2, x, female)
        fatTerm = fma(x, kScoreFLin, female) + kScoreFConst
        weightCoef = kWtFemale
    }
    var visceral = if (vfr < VFR_SPLIT) {
        var fifth = vfr * kVfr5
        var fourth = vfr * kVfr4
        val cubicBase = vfr * kVfr3
        fifth *= vfr
        fourth *= vfr
        fifth *= vfr
        fourth *= vfr
        fifth *= vfr
        fourth *= vfr
        var next = fma(fifth, vfr, fourth)
        next = fma(vfr * cubicBase, vfr, next)
        next = fma(vfr * kVfr2, vfr, next)
        fma(vfr, kVfr1, next) + kVfrConst
    } else {
        VFR_FLOOR
    }
    if (visceral < VFR_FLOOR) {
        visceral = VFR_FLOOR
    }
    val weightS = f32(i16(weight100).toDouble())
    val weightTerm = f32(f32(weightS / f32(-WEIGHT_HUNDRED)) * weightCoef)
    var body = slm + BODY_OFFSET + weightTerm
    if (body > BODY_CAP) {
        body = BODY_CAP
    }
    var fatScore = fatTerm
    if (fatScore < FAT_TERM_MIN) {
        fatScore = FAT_TERM_MIN
    }
    val bmiCoef = if (vfr == 0.0) kBmiCoefZeroVfr else kBmiCoef
    val visceralScale = if (vfr == 0.0) 0.0 else kVfrWeight
    var total = fma(bmiCoef, bmiBase, fatScore * kPolyScale)
    total = fma(body, kBodyScale, total)
    total = fma(visceralScale, visceral, total)
    var raw = total.toInt()
    if (raw >= SCORE_MAX) {
        raw = SCORE_MAX
    }
    if (raw <= SCORE_MIN) {
        raw = SCORE_MIN
    }
    return raw
}

/**
 * Single-frequency body composition, matching `cs_bias_v235` and `parseDefaultZ`.
 */
object CsBiasV235 {
    /**
     * Same inputs as the native call.
     * [weight100] is kilograms times 100. [impedance10] is ohms times 10.
     * Height, weight, and impedance are cut to 16 bits. Age and sex are cut to 8 bits.
     * Out of range, [BiasV235Result.data] is null and [BiasV235Result.code] is negative:
     * -2 weight, -3 height, -4 age, -5 sex, -6 impedance.
     */
    fun csBiasV235(
        sex: Int,
        age: Int,
        heightCm: Int,
        weight100: Int,
        impedance10: Int,
    ): BiasV235Result {
        val sexByte = sex and BYTE_MASK
        val ageByte = age and BYTE_MASK
        val height = heightCm and SHORT_MASK
        val weight = weight100 and SHORT_MASK
        val impedance = impedance10 and SHORT_MASK
        val weightS = i16(weight)
        val weightTimes100 = f32(weightS.toDouble())
        val weightKgF = f32(weightTimes100 / f32(WEIGHT_HUNDRED))
        if (!(weightKgF >= f32(WEIGHT_MIN_KG) && weightKgF <= f32(WEIGHT_MAX_KG))) {
            return BiasV235Result(CODE_WEIGHT, null)
        }
        if (HEIGHT_REJECT > ((height - HEIGHT_BIAS) and SHORT_MASK)) {
            return BiasV235Result(CODE_HEIGHT, null)
        }
        if (((ageByte - AGE_BIAS) and BYTE_MASK) < AGE_REJECT) {
            return BiasV235Result(CODE_AGE, null)
        }
        if (sexByte > 1) {
            return BiasV235Result(CODE_SEX, null)
        }
        if (IMPEDANCE_REJECT > ((impedance - IMPEDANCE_BIAS) and SHORT_MASK)) {
            return BiasV235Result(CODE_IMPEDANCE, null)
        }
        val weight10 = f32(weightTimes100 / f32(IMPEDANCE_TEN))
        val weightKg = weightKgF
        val heavy = weightKgF > f32(HEAVY_KG)
        var bfp = linear(height, weight, impedance, ageByte, sexByte, FAT) / weight10 * THOUSAND
        if (bfp !in BFP_MIN..BFP_MAX) {
            bfp = if (bfp > BFP_MAX) BFP_MAX else BFP_MIN
        }
        var slm = linear(height, weight, impedance, ageByte, sexByte, SLM_GROUP) / IMPEDANCE_TEN
        val slmHi = if (heavy) SLM_HI_HEAVY else SLM_HI_LIGHT
        if (slm < SLM_MIN) {
            slm = SLM_MIN
        } else if (slm > slmHi) {
            slm = slmHi
        }
        var bone = weight10.toInt().toDouble() / IMPEDANCE_TEN + (bfp * weight10 / -THOUSAND) - slm
        val boneHi = if (heavy) BONE_HI_HEAVY else BONE_HI_LIGHT
        if (bone >= boneHi) {
            bone = boneHi
        } else if (bone <= BONE_MIN) {
            bone = BONE_MIN
        }
        var water = linear(height, weight, impedance, ageByte, sexByte, WATER) / weight10 * IMPEDANCE_TEN
        if (water < WATER_MIN) {
            water = WATER_MIN
        } else if (water > WATER_MAX) {
            water = WATER_MAX
        }
        val heightS = i16(height)
        val impedanceS = i16(impedance)
        val protein = (slm + (water * weight10 / -THOUSAND)) / weightKg * PERCENT
        var muscle = fma(weightS.toDouble(), kSmmWt, 0.0) / WEIGHT_HUNDRED
        val muscleZ = impedanceS.toDouble() * kSmmZ / IMPEDANCE_TEN
        muscle = fma(heightS.toDouble(), kSmmH, muscle)
        muscle = fma(ageByte.toDouble(), kSmmAge, muscle)
        muscle = muscle + muscleZ + kSmmConst
        muscle = fma(sexByte.toDouble(), kSmmSex, muscle)
        val muscleHi = if (heavy) MUSCLE_HI_HEAVY else MUSCLE_HI_LIGHT
        if (muscle > muscleHi) {
            muscle = muscleHi
        } else if (muscle < MUSCLE_MIN) {
            muscle = MUSCLE_MIN
        }
        val bmr = linear(height, weight, impedance, ageByte, sexByte, BMR_GROUP)
        var visceral = halfStep(linear(height, weight, impedance, ageByte, sexByte, VISCERAL))
        if (visceral > VISCERAL_MAX) {
            visceral = VISCERAL_MAX
        } else if (visceral < VISCERAL_MIN) {
            visceral = VISCERAL_MIN
        }
        val heightSq = heightS * heightS
        val bmi = weight10.toInt().toDouble() * THOUSAND / heightSq.toDouble()
        val standard = standardWeight(height, sexByte)
        var fatControl = bfp * weightKg / -PERCENT
        val slope = fma(sexByte.toDouble(), kFcSex, FAT_SLOPE_BASE)
        val weightControl = standard - weightKg
        fatControl = fma(standard, slope, fatControl)
        val muscleControl = weightControl - fatControl
        val metabolicAge = bodyAge(
            linear(height, weight, impedance, ageByte, sexByte, AGE_GROUP),
            ageByte,
        )
        val bodyScore = score(bfp, slm, visceral, bmi, weight, ageByte, sexByte)
        return BiasV235Result(
            code = CODE_OK,
            data = BiasV235Data(
                bfp = bfp,
                slm = slm,
                bwp = water,
                bmc = bone,
                vfr = visceral,
                pp = protein,
                smm = muscle,
                bmi = bmi,
                sbw = standard,
                mc = muscleControl,
                wc = weightControl,
                fc = fatControl,
                bmr = bmr,
                ma = metabolicAge,
                sbc = bodyScore,
            ),
        )
    }

    /**
     * Narrows [csBiasV235] the way `parseDefaultZ` fills a stored report.
     * Visceral fat on the report is [V235Report.vfl].
     */
    fun parseDefaultZ(
        sex: Int,
        age: Int,
        heightCm: Double,
        weightKg: Double,
        impedanceOhm: Double,
    ): V235ReportResult {
        val heightF = f32(heightCm)
        val weightF = f32(weightKg)
        val impedanceF = f32(impedanceOhm)
        val native = csBiasV235(
            sex,
            age,
            heightF.toInt(),
            f32(f32(WEIGHT_HUNDRED) * weightF).toInt(),
            f32(impedanceF * f32(IMPEDANCE_TEN)).toInt(),
        )
        val data = native.data
        if (native.code != CODE_OK || data == null) {
            return V235ReportResult(native.code, null)
        }
        val slp = if (weightF > 0.0) {
            f32((data.slm / weightF) * PERCENT)
        } else {
            0.0
        }
        return V235ReportResult(
            code = CODE_OK,
            report = V235Report(
                bfp = f32(data.bfp),
                slm = f32(data.slm),
                slp = slp,
                bwp = f32(data.bwp),
                bmc = f32(data.bmc),
                vfl = f32(data.vfr),
                pp = f32(data.pp),
                smm = f32(data.smm),
                swt = f32(data.sbw),
                mc = f32(data.mc),
                wc = f32(data.wc),
                fc = f32(data.fc),
                bmr = data.bmr.toInt(),
                ma = data.ma,
                sbc = data.sbc,
            ),
        )
    }
}
