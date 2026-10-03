package com.mascill.healthtrack.domain.model

import com.mascill.healthtrack.util.CsBiasV235
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CsBiasV235Test {
    @Test
    fun valuesMatchTheLibrary() {
        golden.forEach { (case, expected) ->
            val (code, values) = values(case)
            assertEquals(case.toString(), 0, code)
            assertEquals(expected.size, values.size)
            expected.indices.forEach { index ->
                assertEquals("$case[$index]", expected[index], values[index], 0.0)
            }
        }
    }

    @Test
    fun errorCodesStayInOrder() {
        assertEquals(-2, CsBiasV235.csBiasV235(0, 30, 170, 1999, 5000).code)
        assertEquals(-2, CsBiasV235.csBiasV235(0, 30, 89, 1999, 5000).code)
        assertEquals(-3, CsBiasV235.csBiasV235(0, 30, 89, 7000, 5000).code)
        assertEquals(-4, CsBiasV235.csBiasV235(0, 17, 170, 7000, 5000).code)
        assertEquals(-5, CsBiasV235.csBiasV235(2, 30, 170, 7000, 5000).code)
        assertEquals(-6, CsBiasV235.csBiasV235(0, 30, 170, 7000, 1999).code)
        assertNull(CsBiasV235.csBiasV235(0, 30, 170, 1999, 5000).data)
    }

    @Test
    fun javaReportNarrowsFloat() {
        val report = CsBiasV235.parseDefaultZ(1, 30, 170.9, 70.56, 500.4)
        assertEquals(0, report.code)
        val data = report.report!!
        assertEquals(21.062211990356445, data.bfp, 0.0)
        assertEquals(75.17662048339844, data.slp, 0.0)
        assertEquals(1587, data.bmr)
        assertEquals(8.5, data.vfl, 0.0)
        assertEquals(64, data.sbc)
    }
}

private fun values(case: List<Int>): Pair<Int, List<Double>> {
    val result = CsBiasV235.csBiasV235(case[0], case[1], case[2], case[3], case[4])
    val data = result.data ?: return result.code to emptyList()
    return result.code to listOf(
        data.bfp,
        data.slm,
        data.bwp,
        data.bmc,
        data.vfr,
        data.pp,
        data.smm,
        data.bmi,
        data.sbw,
        data.mc,
        data.wc,
        data.fc,
        data.bmr,
        data.ma.toDouble(),
        data.sbc.toDouble(),
    )
}

private val golden = mapOf(
    listOf(1, 30, 170, 7055, 5000) to listOf(
        21.05156626506024,
        53.045669999999994,
        55.30005669737775,
        2.6024500000000046,
        8.5,
        19.88870218716509,
        29.279374999999995,
        24.39446366782007,
        63.0,
        -3.408122409314993,
        -7.5500030517578125,
        -4.141880642442819,
        1587.320665,
        30.0,
        64.0,
    ),
    listOf(0, 30, 170, 7055, 5000) to listOf(
        32.940177179305444,
        44.13277,
        47.86678242381289,
        3.127935000000008,
        5.5,
        14.688525232801961,
        26.852474999999995,
        24.39446366782007,
        60.000003814697266,
        -2.3107041854804393,
        -10.549999237060547,
        -8.239295051580108,
        1438.20497,
        39.0,
        64.0,
    ),
    listOf(1, 18, 90, 2000, 2000) to listOf(
        35.901999999999994,
        14.620099999999999,
        73.107,
        1.0,
        3.0,
        -0.006500000000002615,
        20.0,
        24.691358024691358,
        17.212499618530273,
        1.4667746833801258,
        -2.7875003814697266,
        -4.254275064849852,
        478.8071,
        23.0,
        58.0,
    ),
    listOf(0, 99, 220, 25000, 15000) to listOf(
        45.0,
        80.147,
        34.54348,
        7.0,
        51.5,
        -2.484680000000003,
        52.92059999999999,
        51.65289256198347,
        102.8499984741211,
        -60.36250114440918,
        -147.1500015258789,
        -86.78750038146973,
        3054.1901,
        80.0,
        45.0,
    ),
    listOf(1, 45, 149, 15000, 8500) to listOf(
        45.0,
        73.0283,
        42.951899999999995,
        4.0,
        54.0,
        5.733633333333339,
        31.5486,
        67.56452412053511,
        47.1771240234375,
        -43.342987060546875,
        -102.8228759765625,
        -59.479888916015625,
        2287.6192,
        55.0,
        45.0,
    ),
    listOf(0, 45, 150, 15001, 8500) to listOf(
        45.0,
        48.19933400000001,
        35.8115951431948,
        7.0,
        31.5,
        -3.680847411635878,
        29.380744999999987,
        66.66666666666667,
        48.0,
        -46.505496978759766,
        -102.00999450683594,
        -55.50449752807617,
        1918.0383740000002,
        55.0,
        45.0,
    ),
    listOf(1, 60, 190, 5550, 3333) to listOf(
        5.0,
        53.753945,
        65.39603153153153,
        1.0,
        1.0,
        31.457923423423424,
        34.15004999999999,
        15.373961218836564,
        77.0,
        11.185,
        21.5,
        10.315,
        1467.43617,
        50.0,
        64.0,
    ),
    listOf(0, 25, 191, 8233, 6421) to listOf(
        31.59579422530165,
        51.32890200000001,
        46.39019381305302,
        4.0,
        5.5,
        15.95512657336683,
        31.97618499999999,
        22.559688605027276,
        77.52212524414062,
        1.8244100662779026,
        -4.8078765869140625,
        -6.632286653191965,
        1687.0746420000003,
        35.0,
        79.0,
    ),
)
