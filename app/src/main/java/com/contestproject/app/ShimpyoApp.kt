package com.contestproject.app

import android.app.Application
import androidx.core.content.ContextCompat
import com.contestproject.app.data.local.PrefsNapStore
import com.contestproject.app.data.vehicle.PleosVehicleGateway
import com.contestproject.app.data.voice.CarWakeVoice
import com.contestproject.app.data.voice.ResourceVoiceLines
import com.contestproject.app.domain.NapState
import com.contestproject.app.service.NapController
import com.contestproject.app.service.NapService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 객체 몇 개뿐이라 Hilt 없이 여기서 직접 만든다.
 * 쪽잠은 화면과 상관없이 이어져야 하므로 컨트롤러는 Activity가 아니라 앱 단위로 둔다.
 */
class ShimpyoApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var napController: NapController
        private set

    override fun onCreate() {
        super.onCreate()
        MainThreadWatchdog.start()
        napController = NapController(
            vehicle = PleosVehicleGateway(this, appScope),
            voice = CarWakeVoice(this),
            store = PrefsNapStore(this),
            lines = ResourceVoiceLines(resources),
            scope = appScope,
        )
        napController.start()

        // 쪽잠이 진행되는 동안만 포그라운드 서비스를 띄워 다른 앱을 쓰는 중에도 시간을 지킨다.
        appScope.launch {
            napController.state
                .map { it is NapState.Active }
                .distinctUntilChanged()
                .collect { active ->
                    if (active) ContextCompat.startForegroundService(this@ShimpyoApp, NapService.intent(this@ShimpyoApp))
                }
        }
    }
}
