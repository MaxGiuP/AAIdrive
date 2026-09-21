package io.bimmergestalt.idriveconnectaddons.screenmirror

import org.junit.Assert.*
import org.junit.Test
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class LatestFrameSenderTest {
    @Test fun slowTransportKeepsOnlyNewestFrameAndYieldsBetweenSends() {
        val tasks = ArrayDeque<Runnable>()
        val delivered = mutableListOf<Int>()
        lateinit var sender: LatestFrameSender<Int>
        sender = LatestFrameSender({ tasks.add(it) }) {
            delivered.add(it)
            if (it == 1) {
                for (frame in 2..100) sender.offer(frame)
                tasks.add(Runnable { delivered.add(-1) }) // A queued car input callback.
            }
        }
        sender.offer(1)
        tasks.removeFirst().run()
        assertEquals(listOf(1), delivered)
        assertEquals(2, tasks.size)
        while (tasks.isNotEmpty()) tasks.removeFirst().run()
        assertEquals(listOf(1, -1, 100), delivered)
    }

    @Test fun producerDoesNotWaitForBlockedTransport() {
        val tasks = ArrayDeque<Runnable>()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val offered = CountDownLatch(1)
        val sender = LatestFrameSender<Int>({ synchronized(tasks) { tasks.add(it) } }) {
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
        }
        sender.offer(1)
        val sending = Thread { tasks.removeFirst().run() }.apply { start() }
        var producing: Thread? = null
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            producing = Thread {
                repeat(1000) { sender.offer(it) }
                offered.countDown()
            }.apply { start() }
            assertTrue("Capture must continue while a send blocks", offered.await(2, TimeUnit.SECONDS))
        } finally {
            release.countDown()
            sending.join(3000)
            producing?.join(3000)
        }
        assertEquals(1, tasks.size)
    }

    @Test fun invalidationDropsQueuedFrameAndCanResumeWithoutDuplicateTasks() {
        val tasks = ArrayDeque<Runnable>()
        val delivered = mutableListOf<Int>()
        val sender = LatestFrameSender<Int>({ tasks.add(it) }, { delivered.add(it) })
        sender.offer(1)
        sender.invalidate()
        tasks.removeFirst().run()
        assertTrue(delivered.isEmpty())
        sender.offer(2)
        sender.invalidate()
        sender.offer(3)
        assertEquals(1, tasks.size)
        tasks.removeFirst().run()
        assertEquals(listOf(3), delivered)
    }

    @Test fun invalidationWhileSendingDiscardsOldPendingButAllowsNewSession() {
        val tasks = ArrayDeque<Runnable>()
        val delivered = mutableListOf<Int>()
        lateinit var sender: LatestFrameSender<Int>
        sender = LatestFrameSender({ tasks.add(it) }) {
            delivered.add(it)
            if (it == 1) {
                sender.offer(2)
                sender.invalidate()
                sender.offer(3)
            }
        }
        sender.offer(1)
        while (tasks.isNotEmpty()) tasks.removeFirst().run()
        assertEquals(listOf(1, 3), delivered)
    }
}
