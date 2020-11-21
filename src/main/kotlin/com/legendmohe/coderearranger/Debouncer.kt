package com.legendmohe.coderearranger

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class Debouncer(private val interval: Int) {

    private val sched = Executors.newScheduledThreadPool(1)
    private val delayedMap = ConcurrentHashMap<Any, TimerTask>()

    fun call(key: Any, runnable: Runnable?) {
        val task = TimerTask(key, runnable)
        var prev: TimerTask?
        do {
            prev = delayedMap.putIfAbsent(key, task)
            if (prev == null) sched.schedule(task, interval.toLong(), TimeUnit.MILLISECONDS)
        } while (prev != null && !prev.extend()) // Exit only if new task was added to map, or existing task was extended successfully
    }

    fun terminate() {
        sched.shutdownNow()
    }

    // The task that wakes up when the wait time elapses
    private inner class TimerTask(private val key: Any, private val runnable: Runnable?) : Runnable {
        private var dueTime: Long = 0
        private val lock = Any()
        fun extend(): Boolean {
            synchronized(lock) {
                if (dueTime < 0) // Task has been shutdown
                    return false
                dueTime = System.currentTimeMillis() + interval
                return true
            }
        }

        override fun run() {
            synchronized(lock) {
                val remaining = dueTime - System.currentTimeMillis()
                if (remaining > 0) { // Re-schedule task
                    sched.schedule(this, remaining, TimeUnit.MILLISECONDS)
                } else { // Mark as terminated and invoke callback
                    dueTime = -1
                    try {
                        runnable?.run()
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    } finally {
                        delayedMap.remove(key)
                    }
                }
            }
        }

        init {
            extend()
        }
    }
}