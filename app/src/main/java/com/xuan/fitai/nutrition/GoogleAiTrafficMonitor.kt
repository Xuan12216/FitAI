package com.xuan.fitai.nutrition

/** Local, process-lifetime diagnostics. Never stores queries, food names, URLs, or answers. */
class GoogleAiTrafficMonitor(private val clock: () -> Long = { System.nanoTime() / 1_000_000 }) {
    enum class Outcome { SUCCESS, INCOMPLETE, TIMEOUT, NETWORK_ERROR, BLOCKED, CANCELLED }
    data class Snapshot(
        val requests: Int = 0, val cacheHits: Int = 0, val suppressed: Int = 0,
        val lastProvider: String? = null, val inFlight: Boolean = false, val lastOutcome: Outcome? = null,
        val lastDurationMs: Long = 0, val lastHttpStatus: Int? = null,
        val requestsInMinute: Int = 0,
    ) {
        // App reminder only, not Google's published limit or a guarantee against blocking.
        val dense: Boolean get() = requestsInMinute >= 6
    }
    private val starts = ArrayDeque<Long>()
    private var current = Snapshot()
    @Synchronized fun snapshot(): Snapshot {
        val now = clock()
        while (starts.isNotEmpty() && now - starts.first() >= 60_000) starts.removeFirst()
        return current.copy(requestsInMinute = starts.size)
    }
    @Synchronized fun started(provider: String = "Google AI") {
        snapshot()
        // A bounded window even if the caller is misused by a future integration.
        if (starts.size == 100) starts.removeFirst()
        starts.addLast(clock())
        current = current.copy(requests = current.requests + 1, inFlight = true, lastProvider = provider)
    }
    @Synchronized fun finished(outcome: Outcome, durationMs: Long, status: Int? = null) {
        current = current.copy(inFlight = false, lastOutcome = outcome,
            lastDurationMs = durationMs.coerceAtLeast(0), lastHttpStatus = status)
    }
    @Synchronized fun cacheHit() { current = current.copy(cacheHits = current.cacheHits + 1) }
    @Synchronized fun suppressed() { current = current.copy(suppressed = current.suppressed + 1) }
}
