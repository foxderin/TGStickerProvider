package cc.microblock.TGStickerProvider.utils

import java.util.ArrayDeque
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger

/** Shared conversion capacity and serialization for all app-owned sticker storage operations. */
object BoundedStickerWork {
    const val MAX_WORKERS = 8
    private val storageMonitor = Any()
    private val threadNumber = AtomicInteger()
    private val executor = Executors.newFixedThreadPool(MAX_WORKERS) { task ->
        Thread(task, "sticker-conversion-${threadNumber.incrementAndGet()}").apply { isDaemon = true }
    }

    /** Call from a coordinator/background thread, never the UI or a conversion worker. */
    fun <R> withStorageLock(operation: () -> R): R = synchronized(storageMonitor) { operation() }

    /**
     * Prepare up to [maxInFlight] (at most eight) independent items concurrently, then consume them in input order.
     * The complete operation is serialized with other runs and app-owned deletes/cache clears.
     * Callers must not mutate [items] until this returns. Neither [prepare] nor [discard] may
     * acquire the storage lock or wait on a coordinator that holds it.
     *
     * A successful [consume] takes ownership of its prepared value. If consume throws, [discard]
     * also receives that current value, so discard must only release temporary resources and be
     * safe after partial consumption. Preparation failures are delivered as Result.failure;
     * prepare is responsible for cleaning resources it allocated before throwing.
     *
     * On interruption/failure, already submitted work is drained, not cancelled: a native task
     * may ignore interruption and otherwise leave its eventual temporary output unowned. This
     * deliberately waits for those tasks before releasing the storage lock. It is not a native
     * conversion timeout/cancellation mechanism. The coordinator's interrupt status is restored.
     */
    fun <T, R> run(
        items: List<T>,
        prepare: (T) -> R,
        consume: (index: Int, item: T, result: Result<R>) -> Unit,
        discard: (R) -> Unit,
        maxInFlight: Int = MAX_WORKERS
    ) = withStorageLock {
        require(maxInFlight in 1..MAX_WORKERS) { "maxInFlight must be between 1 and $MAX_WORKERS" }
        val pending = ArrayDeque<Pair<Int, Future<Result<R>>>>()
        var next = 0
        var interrupted = false
        var primaryFailure: Throwable? = null
        var cleanupFailure: Throwable? = null

        fun checkInterrupted() {
            if (Thread.interrupted()) {
                interrupted = true
                throw InterruptedException("Sticker synchronization interrupted")
            }
        }

        fun submitNext() {
            val index = next
            pending.addLast(index to executor.submit(Callable { runCatching { prepare(items[index]) } }))
            next++
        }

        try {
            checkInterrupted()
            repeat(minOf(maxInFlight, items.size)) { submitNext() }
            while (pending.isNotEmpty()) {
                checkInterrupted()
                val (index, future) = pending.first
                val result = future.get()
                checkInterrupted()
                consume(index, items[index], result)
                // Only successful consumption transfers ownership away from this scheduler.
                pending.removeFirst()
                if (next < items.size) submitNext()
            }
        } catch (failure: Throwable) {
            if (failure is InterruptedException) interrupted = true
            primaryFailure = failure
            throw failure
        } finally {
            while (pending.isNotEmpty()) {
                val future = pending.removeFirst().second
                try {
                    var result: Result<R>?
                    while (true) {
                        try {
                            result = future.get()
                            break
                        } catch (_: InterruptedException) {
                            interrupted = true
                        }
                    }
                    result!!.onSuccess(discard)
                } catch (failure: Throwable) {
                    if (failure is InterruptedException) interrupted = true
                    val previous = cleanupFailure
                    if (previous == null) cleanupFailure = failure
                    else if (previous !== failure) previous.addSuppressed(failure)
                }
            }
            if (interrupted) Thread.currentThread().interrupt()
            cleanupFailure?.let { cleanup ->
                val primary = primaryFailure
                if (primary == null) throw cleanup
                if (primary !== cleanup) primary.addSuppressed(cleanup)
            }
        }
    }
}
