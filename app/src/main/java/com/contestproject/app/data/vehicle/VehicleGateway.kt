package com.contestproject.app.data.vehicle

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 차량 읽기·제어 창구. 앱의 다른 부분은 Pleos SDK를 직접 모르고 이 인터페이스만 쓴다.
 * set 계열은 성공 여부만 돌려주며, 실패해도 예외를 던지지 않는다.
 */
interface VehicleGateway {
    val status: StateFlow<VehicleStatus>
    val events: SharedFlow<VehicleEvent>

    suspend fun connect()
    suspend fun capabilities(): ClimateCapabilities
    suspend fun readClimate(): ClimateSnapshot
    /** 실내 실제 온도(센서). 지원하지 않거나 못 읽으면 null */
    suspend fun readCabinTemperature(): Float?

    suspend fun setHvacPower(on: Boolean): Boolean
    suspend fun setRecirculation(on: Boolean): Boolean
    suspend fun setAc(on: Boolean): Boolean
    suspend fun setTemperature(celsius: Float): Boolean
    suspend fun setFanSpeed(level: Int): Boolean
    suspend fun setDefroster(on: Boolean): Boolean
    suspend fun setSeatHeat(level: Int): Boolean
    suspend fun setSteeringHeat(level: Int): Boolean
    suspend fun setDriverWindow(position: Int): Boolean

    fun release()
}
