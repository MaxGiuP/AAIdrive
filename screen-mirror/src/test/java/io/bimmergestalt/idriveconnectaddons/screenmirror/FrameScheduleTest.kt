package io.bimmergestalt.idriveconnectaddons.screenmirror

import org.junit.Assert.*
import org.junit.Test

class FrameScheduleTest {
    @Test fun firstFrameIsImmediate() {
        assertEquals(0L, FrameSchedule().request(0, 100))
    }

    @Test fun burstsKeepOneWakeAtTheRateLimit() {
        val schedule = FrameSchedule()
        schedule.onFrame(1000)
        assertEquals(1100L, schedule.request(1001, 100))
        for (now in 1002L..1099L) assertNull(schedule.request(now, 100))
        assertEquals(1L, schedule.remainingDelay(1099, 100))
        schedule.onWake()
        assertEquals(0L, schedule.remainingDelay(1100, 100))
    }

    @Test fun fallbackPollCannotReplaceEarlierFrameDeadline() {
        val schedule = FrameSchedule()
        schedule.onFrame(1000)
        assertEquals(1100L, schedule.request(1050, 100))
        assertNull(schedule.request(1050, 100, 1000))
    }

    @Test fun newFramePreemptsIdleFallbackPoll() {
        val schedule = FrameSchedule()
        assertEquals(2000L, schedule.request(1000, 100, 1000))
        assertEquals(1050L, schedule.request(1050, 100))
        assertNull(schedule.request(1060, 100))
    }

    @Test fun slowCarSendDoesNotAddAnotherFrameInterval() {
        val schedule = FrameSchedule()
        schedule.onFrame(1000)
        assertEquals(1250L, schedule.request(1250, 100))
    }

    @Test fun stricterDrivingLimitIsRecheckedWhenTimerWakes() {
        val schedule = FrameSchedule()
        schedule.onFrame(1000)
        assertEquals(1100L, schedule.request(1000, 100))
        schedule.onWake()
        assertEquals(900L, schedule.remainingDelay(1100, 1000))
        assertEquals(2000L, schedule.request(1100, 1000))
    }

    @Test fun resumeDoesNotWaitForPreviousCaptureDeadline() {
        val schedule = FrameSchedule()
        schedule.onFrame(1000)
        schedule.request(1000, 1000)
        schedule.reset()
        assertEquals(1050L, schedule.request(1050, 1000))
    }
}
