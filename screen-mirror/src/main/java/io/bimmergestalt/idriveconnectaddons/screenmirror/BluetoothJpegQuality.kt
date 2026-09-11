package io.bimmergestalt.idriveconnectaddons.screenmirror

/** Small quality adjustment for successful Bluetooth sends; it never changes capture timing. */
internal class BluetoothJpegQuality {
    var quality = MAX_QUALITY
        private set
    private var firstSend = true
    private var slowSends = 0
    private var fastSends = 0

    /** Returns the quality for the next frame. Failed or skipped sends are not samples. */
    fun onFrameSent(sendDurationMs: Long): Int {
        if (sendDurationMs < 0) {
            clearStreaks()
            return quality
        }
        if (firstSend) {
            // Showing the image and initializing its controls adds RPCs to the first send.
            firstSend = false
            return quality
        }
        when {
            sendDurationMs > 375 -> {
                fastSends = 0
                slowSends++
                if (slowSends >= 3) {
                    quality = (quality - 5).coerceAtLeast(MIN_QUALITY)
                    clearStreaks()
                }
            }
            sendDurationMs < 125 -> {
                slowSends = 0
                fastSends++
                if (fastSends >= 12) {
                    quality = (quality + 2).coerceAtMost(MAX_QUALITY)
                    clearStreaks()
                }
            }
            else -> clearStreaks()
        }
        return quality
    }

    fun reset() {
        quality = MAX_QUALITY
        firstSend = true
        clearStreaks()
    }

    private fun clearStreaks() {
        slowSends = 0
        fastSends = 0
    }

    companion object {
        const val MIN_QUALITY = 25
        const val MAX_QUALITY = 30
    }
}
