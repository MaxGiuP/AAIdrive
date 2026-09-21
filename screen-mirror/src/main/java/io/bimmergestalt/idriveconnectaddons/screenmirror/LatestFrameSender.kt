package io.bimmergestalt.idriveconnectaddons.screenmirror

/** One in-flight send and one replaceable pending frame; producers never wait for the transport. */
internal class LatestFrameSender<T>(
    private val post: (Runnable) -> Unit,
    private val send: (T) -> Unit
) {
    private var pending: T? = null
    private var scheduled = false

    @Synchronized
    fun offer(frame: T) {
        pending = frame
        schedule()
    }

    @Synchronized
    fun invalidate() { pending = null }

    private fun schedule() {
        if (scheduled || pending == null) return
        scheduled = true
        post(Runnable {
            val frame = synchronized(this) { pending.also { pending = null } }
            try {
                if (frame != null) send(frame)
            } finally {
                synchronized(this) {
                    scheduled = false
                    // A separate runnable gives car focus and input callbacks a turn between sends.
                    schedule()
                }
            }
        })
    }
}
