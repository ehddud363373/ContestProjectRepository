package com.contestproject.app.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.contestproject.app.ShimpyoApp
import com.contestproject.app.data.vehicle.ClimateCapabilities
import com.contestproject.app.data.vehicle.VehicleStatus
import com.contestproject.app.domain.NapNotice
import com.contestproject.app.domain.NapState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NapViewModel(application: Application) : AndroidViewModel(application) {

    private val controller = (application as ShimpyoApp).napController

    val napState: StateFlow<NapState> = controller.state
    val vehicle: StateFlow<VehicleStatus> = controller.vehicleStatus
    val capabilities: StateFlow<ClimateCapabilities?> = controller.capabilities
    val busy: StateFlow<Boolean> = controller.busy

    /**
     * 남은 시간 표시용 시계. 화면이 보일 때만 돈다.
     * 표시가 초 단위라 1초마다, 초가 바뀌는 순간에 맞춰 갱신한다(자주 갱신하면 접근성 트리 재계산 비용이 커진다).
     */
    val now: StateFlow<Long> = flow {
        while (true) {
            val t = System.currentTimeMillis()
            emit(t)
            delay(1_000 - t % 1_000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(1_000), System.currentTimeMillis())

    private val _selectedMinutes = MutableStateFlow(controller.lastDurationMin)
    val selectedMinutes: StateFlow<Int> = _selectedMinutes.asStateFlow()

    private val _demoMode = MutableStateFlow(controller.demoMode)
    val demoMode: StateFlow<Boolean> = _demoMode.asStateFlow()

    private val _notice = MutableStateFlow<NapNotice?>(null)
    val notice: StateFlow<NapNotice?> = _notice.asStateFlow()

    init {
        viewModelScope.launch {
            controller.notices.collect { notice ->
                _notice.value = notice
                delay(NOTICE_MS)
                if (_notice.value == notice) _notice.value = null
            }
        }
    }

    fun selectMinutes(minutes: Int) { _selectedMinutes.value = minutes }
    fun startNap() = controller.startNap(_selectedMinutes.value)
    fun wakeNow() = controller.wakeNow()
    fun acknowledgeWake() = controller.acknowledgeWake()
    fun snoozeAlert() = controller.snoozeAlert()
    fun extend() = controller.extend()
    fun depart() = controller.depart()
    fun dismissNotice() { _notice.value = null }

    fun cyclePowertrain() = controller.cyclePowertrainOverride()

    fun toggleDemoMode() {
        val next = !controller.demoMode
        controller.demoMode = next
        _demoMode.value = next
    }

    private companion object {
        const val NOTICE_MS = 5_000L
    }
}
