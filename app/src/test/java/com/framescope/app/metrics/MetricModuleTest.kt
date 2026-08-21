package com.framescope.app.metrics

import com.framescope.app.i18n.AppLanguage
import com.framescope.app.i18n.AppLanguageRuntime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class MetricModuleTest {
    @Before
    fun setUp() {
        AppLanguageRuntime.current = AppLanguage.ENGLISH
    }

    @After
    fun tearDown() {
        AppLanguageRuntime.current = AppLanguage.ENGLISH
    }

    @Test
    fun thermalWithoutUsableReadingDoesNotRenderZeroDegrees() {
        val value = metricValueFor(
            MetricModuleId.THERMAL_MONITOR,
            MetricsState(
                thermalCpuC = 0f,
                thermalStatus = 0,
                thermalReadStatus = MetricReadStatus.EmptyOutput,
                hasThermalCpu = false
            )
        )

        assertEquals("Read unavailable", value)
    }

    @Test
    fun thermalWithUsableReadingRendersTemperatureAndStatus() {
        val value = metricValueFor(
            MetricModuleId.THERMAL_MONITOR,
            MetricsState(
                thermalCpuC = 58f,
                thermalStatus = 0,
                thermalReadStatus = MetricReadStatus.Ok,
                hasThermalCpu = true
            )
        )

        assertEquals("58°C OK", value)
    }
}
