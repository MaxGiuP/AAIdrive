package io.bimmergestalt.idriveconnectaddons.screenmirror

import org.junit.Assert.*
import org.junit.Test

class CarAppStartRetryTest {
    private class Fixture {
        var connected = true
        var securityReady = false
        var starts = 0
        var checks = 0
        val tasks = mutableListOf<Runnable>()
        val retry = CarAppStartRetry(
            { task, delay -> assertEquals(250L, delay); tasks.add(task) },
            { task -> tasks.removeAll { it === task } },
            {
                checks++
                if (connected && securityReady) starts++
                !connected || securityReady
            }
        )
        fun tick() = tasks.removeAt(0).run()
    }

    @Test fun lateSecurityConnectionStartsOnceWithoutAnotherBind() {
        val fixture = Fixture()
        fixture.retry.start()
        fixture.tick()
        assertEquals(0, fixture.starts)
        fixture.securityReady = true
        fixture.tick()
        assertEquals(1, fixture.starts)
        assertTrue(fixture.tasks.isEmpty())
    }

    @Test fun disconnectAndStopCancelStartupIncludingStaleCallbacks() {
        val fixture = Fixture()
        fixture.retry.start()
        fixture.connected = false
        fixture.tick()
        assertTrue(fixture.tasks.isEmpty())

        fixture.connected = true
        fixture.retry.start()
        val stale = fixture.tasks.single()
        fixture.retry.cancel()
        fixture.securityReady = true
        stale.run()
        assertEquals(0, fixture.starts)
        assertTrue(fixture.tasks.isEmpty())

        fixture.securityReady = false
        fixture.retry.start()
        stale.run()
        assertEquals(1, fixture.tasks.size)
        fixture.securityReady = true
        fixture.tick()
        assertEquals(1, fixture.starts)
    }

    @Test fun unavailableSecurityStopsWaitingAfterTenSeconds() {
        val fixture = Fixture()
        fixture.retry.start()
        repeat(40) { fixture.tick() }
        assertEquals(41, fixture.checks)
        assertEquals(0, fixture.starts)
        assertTrue(fixture.tasks.isEmpty())
    }
}
