package com.contestproject.app

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 디버그 빌드 전용. 메인 스레드가 1초 넘게 막히면 그때의 스택을 로그로 남긴다.
 * Pleos 에뮬레이터는 root가 안 돼서 ANR 스택을 직접 볼 수 없어 만들었다.
 */
object MainThreadWatchdog {
    private const val TAG = "MainWatchdog"
    private const val THRESHOLD_MS = 1_000L

    fun start() {
        val main = Handler(Looper.getMainLooper())
        Thread({
            while (true) {
                val answered = AtomicBoolean(false)
                main.post { answered.set(true) }
                Thread.sleep(THRESHOLD_MS)
                if (!answered.get()) {
                    val stack = Looper.getMainLooper().thread.stackTrace
                        .take(25)
                        .joinToString("\n") { "    at $it" }
                    Log.w(TAG, "main thread blocked >${THRESHOLD_MS}ms\n$stack")
                    while (!answered.get()) Thread.sleep(200)
                }
            }
        }, "main-watchdog").apply { isDaemon = true }.start()
    }
}
