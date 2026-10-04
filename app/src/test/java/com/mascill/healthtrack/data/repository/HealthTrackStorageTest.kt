package com.mascill.healthtrack.data.repository

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mascill.healthtrack.data.datasource.local.HealthTrackDatabase
import com.mascill.healthtrack.domain.model.BodyData
import com.mascill.healthtrack.domain.model.MeasurementResult
import com.mascill.healthtrack.domain.model.V235Report
import com.mascill.healthtrack.util.AppDispatcher
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class HealthTrackStorageTest {
    private val dispatcher = AppDispatcher(
        io = Dispatchers.IO,
        main = Dispatchers.IO,
        default = Dispatchers.IO,
    )
    private lateinit var database: HealthTrackDatabase
    private lateinit var bodyRepository: BodyRepositoryImpl
    private lateinit var measurementRepository: MeasurementRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, HealthTrackDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        bodyRepository = BodyRepositoryImpl(database.bodyDao(), dispatcher)
        measurementRepository = MeasurementRepositoryImpl(database.measurementDao(), dispatcher)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun sexTwoIsRejectedBeforeInsert() {
        assertThrows(IllegalArgumentException::class.java) {
            BodyData(heightCm = 166.0, age = 29, sex = 2)
        }
        assertNull(bodyRepository.latest())
    }

    @Test
    fun saveProfileThenLoadLatest() {
        val firstId = bodyRepository.save(BodyData(heightCm = 166.0, age = 29, sex = 1))
        val secondId = bodyRepository.save(BodyData(heightCm = 170.0, age = 40, sex = 0))

        assertTrue(secondId > firstId)
        val latest = bodyRepository.latest()
        assertEquals(170.0, latest!!.heightCm, 0.0)
        assertEquals(40, latest.age)
        assertEquals(0, latest.sex)
        val createdAt = OffsetDateTime.parse(database.bodyDao().latest()!!.createdAt)
        assertEquals(ZoneOffset.UTC, createdAt.offset)
    }

    @Test
    fun saveMeasurementReadsReportJson() {
        val report = V235Report(
            bfp = 1.5,
            slm = 2.5,
            slp = 3.5,
            bwp = 4.5,
            bmc = 5.5,
            vfl = 6.5,
            pp = 7.5,
            smm = 8.5,
            swt = 9.5,
            mc = 10.5,
            wc = 11.5,
            fc = 12.5,
            bmr = 1502,
            ma = 36,
            sbc = 61,
        )
        val first = measurement(
            weightKg = 60.0,
            scaleWeight = "60,0",
            impedanceOhm = listOf(500.0),
            report = report,
        )
        val second = measurement(
            weightKg = 68.7,
            scaleWeight = "68,7",
            impedanceOhm = listOf(600.0),
            report = report.copy(bfp = 23.5, bmr = 1600),
        )
        val firstId = measurementRepository.save(first)
        val secondId = measurementRepository.save(second)

        assertTrue(secondId > firstId)
        val stored = measurementRepository.list()
        assertEquals(2, stored.size)
        assertEquals(secondId, stored[0].id)
        assertEquals(second.scaleWeight, stored[0].result.scaleWeight)
        assertEquals(listOf(600.0), stored[0].result.impedanceOhm)
        assertEquals(second.report, stored[0].result.report)

        val raw = database.measurementDao().listNewestFirst().first()
        assertTrue(raw.report.contains("\"bfp\""))
        assertTrue(raw.report.contains("\"bmr\":1600"))
        assertTrue(raw.impedanceOhm.contains("600"))
        val createdAt = OffsetDateTime.parse(raw.createdAt)
        assertEquals(ZoneOffset.UTC, createdAt.offset)
    }

    private fun measurement(
        weightKg: Double,
        scaleWeight: String,
        impedanceOhm: List<Double>,
        report: V235Report,
    ): MeasurementResult = MeasurementResult(
        mac = "AA:BB:CC:DD:EE:FF",
        productId = 1,
        weightKg = weightKg,
        scaleWeight = scaleWeight,
        impedanceOhm = impedanceOhm,
        formula = "v235",
        code = 0,
        report = report,
    )
}
