package io.bimmergestalt.idriveconnectaddons.screenmirror

/** Main-thread startup wait; disconnection cancels both queued and already dequeued attempts. */
internal class CarAppStartRetry(
    private val schedule: (Runnable, Long) -> Unit,
    private val unschedule: (Runnable) -> Unit,
    private val attempt: () -> Boolean
) {
    private var generation = 0L
    private var pending: Runnable? = null

    fun start() {
        cancel()
        val owner = generation
        var retries = 40
        val task = object : Runnable {
            override fun run() {
                if (owner != generation) return
                pending = null
                if (attempt() || owner != generation || retries-- == 0) return
                pending = this
                schedule(this, 250)
            }
        }
        task.run()
    }

    fun cancel() {
        generation++
        pending?.let(unschedule)
        pending = null
    }
}
