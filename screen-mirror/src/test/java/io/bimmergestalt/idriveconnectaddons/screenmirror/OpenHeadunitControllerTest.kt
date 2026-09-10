package io.bimmergestalt.idriveconnectaddons.screenmirror

import org.junit.Assert.*
import org.junit.Test

class OpenHeadunitControllerTest {
    private class Clock {
        var time = 1_000L
        val scheduled = mutableListOf<Pair<Long, Runnable>>()
        val sent = mutableListOf<Pair<Long, Int>>()
        val keys = PacedNavigationKeys(
            { time }, { task, delay -> scheduled.add(time + delay to task) },
            { task -> scheduled.removeAll { it.second === task } },
            { code -> sent.add(time to code) }
        )
        fun advance(milliseconds: Long) {
            val target = time + milliseconds
            while (true) {
                val next = scheduled.minByOrNull { it.first } ?: break
                if (next.first > target) break
                scheduled.remove(next)
                time = next.first
                next.second.run()
            }
            time = target
        }
    }

    @Test fun rotaryBurstIsBoundedAndKeepsSelectionAfterAcceptedSteps() {
        val clock = Clock()
        clock.keys.setActive(true)
        assertTrue(clock.keys.press(20))
        repeat(3) { assertTrue(clock.keys.press(20)) }
        assertTrue(clock.keys.press(23))
        assertFalse(clock.keys.press(20))
        assertEquals(listOf(1_000L to 20), clock.sent)
        clock.advance(309)
        assertEquals(1, clock.sent.size)
        clock.advance(931)
        assertEquals(listOf(20, 20, 20, 20, 23), clock.sent.map { it.second })
        assertTrue(clock.sent.zipWithNext().all { (a, b) -> b.first - a.first >= 310 })
    }

    @Test fun pauseDropsPendingInputEvenIfCancelledCallbackStillRuns() {
        val clock = Clock()
        clock.keys.setActive(true)
        clock.keys.press(20)
        clock.keys.press(23)
        val stale = clock.scheduled.single().second
        clock.keys.setActive(false)
        assertFalse(clock.keys.press(4))
        clock.advance(500)
        clock.keys.setActive(true)
        clock.keys.press(21)
        stale.run()
        clock.advance(1_000)
        assertEquals(listOf(20, 21), clock.sent.map { it.second })
        assertTrue(clock.scheduled.isEmpty())
    }

    @Test fun quickResumeKeepsDebounceSpacing() {
        val clock = Clock()
        clock.keys.setActive(true)
        clock.keys.press(20)
        clock.keys.setActive(false)
        clock.advance(10)
        clock.keys.setActive(true)
        clock.keys.press(20)
        clock.advance(299)
        assertEquals(1, clock.sent.size)
        clock.advance(1)
        assertEquals(listOf(1_000L to 20, 1_310L to 20), clock.sent)
    }

    @Test fun onlyNavigationKeysAreAcceptedWhileActive() {
        val clock = Clock()
        assertFalse(clock.keys.press(23))
        clock.keys.setActive(true)
        for (invalid in listOf(-1, 3, 24, 66, 85, Int.MAX_VALUE)) {
            assertFalse(clock.keys.press(invalid))
        }
        for (code in listOf(19, 20, 21, 22, 23, 4)) {
            assertTrue(clock.keys.press(code))
            clock.advance(310)
        }
        assertEquals(listOf(19, 20, 21, 22, 23, 4), clock.sent.map { it.second })
    }

    @Test fun lateQueryCannotEnablePausedOrNewSession() {
        val session = ProjectionControlSession()
        session.resume()
        val oldQuery = session.generation
        session.pause()
        assertFalse(session.accept(oldQuery, true))
        assertFalse(session.projecting)
        session.resume()
        assertFalse(session.accept(oldQuery, true))
        assertTrue(session.accept(session.generation, true))
        assertTrue(session.projecting)
        val readyQuery = session.generation
        session.invalidate()
        assertFalse(session.accept(readyQuery, true))
        assertFalse(session.projecting)
        assertTrue(session.accept(session.generation, false))
        assertFalse(session.projecting)
    }
}
