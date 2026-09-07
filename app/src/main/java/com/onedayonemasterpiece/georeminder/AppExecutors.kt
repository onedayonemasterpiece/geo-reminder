package com.onedayonemasterpiece.georeminder

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object AppExecutors {
    val io: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "geo-reminder-io").apply { isDaemon = true }
    }
}
