package com.contestproject.app.data.vehicle

import android.car.Car
import android.car.FuelType
import android.car.VehicleGear
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * AAOS 표준 차량 속성. Pleos Vehicle SDK 공개 API에 기어가 없어서
 * Pleos 문서(API 호환성 안내)가 권장하는 CarPropertyManager로 읽는다.
 */
internal class CarPropertySource(private val context: Context) {

    private var car: Car? = null
    private var manager: CarPropertyManager? = null
    private var onChange: ((GearState, Float?) -> Unit)? = null
    private var lastGear = GearState.UNKNOWN
    private var lastOutside: Float? = null

    private val callback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            when (value.propertyId) {
                VehiclePropertyIds.GEAR_SELECTION -> lastGear = gearOf(value.value)
                VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE -> lastOutside = value.value as? Float
                else -> return
            }
            onChange?.invoke(lastGear, lastOutside)
        }

        override fun onErrorEvent(propertyId: Int, areaId: Int) {
            Log.w(TAG, "property error $propertyId")
        }
    }

    /** 성공하면 null, 실패하면 사용할 수 없는 이유. */
    fun connect(onChange: (GearState, Float?) -> Unit): UnavailableReason? {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            return UnavailableReason.NOT_A_CAR
        }
        return try {
            // 백그라운드 스레드에는 Looper가 없어 Handler 없이 만들면 null이 온다. 콜백은 메인 Looper로 받는다.
            val connected = Car.createCar(context, Handler(Looper.getMainLooper()))
                ?: return UnavailableReason.SERVICE_MISSING
            val pm = connected.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
            car = connected
            manager = pm
            this.onChange = onChange
            register(pm, VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE)
            register(pm, VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE, CarPropertyManager.SENSOR_RATE_NORMAL)
            null
        } catch (e: SecurityException) {
            Log.w(TAG, "car permission denied", e)
            UnavailableReason.PERMISSION_DENIED
        } catch (t: Throwable) {
            // android.car 라이브러리가 없는 기기(휴대폰 등)는 NoClassDefFoundError
            Log.w(TAG, "car service unavailable", t)
            UnavailableReason.SERVICE_MISSING
        }
    }

    // subscribePropertyEvents는 API 35부터라 minSdk 28 호환을 위해 기존 API를 쓴다.
    @Suppress("DEPRECATION")
    private fun register(pm: CarPropertyManager, propertyId: Int, rate: Float) {
        runCatching { pm.registerCallback(callback, propertyId, rate) }
            .onFailure { Log.w(TAG, "register $propertyId failed: ${it.message}") }
    }

    fun readGear(): GearState = read<Any>(VehiclePropertyIds.GEAR_SELECTION)?.let(::gearOf)
        ?.also { lastGear = it } ?: lastGear

    fun readOutsideTemp(): Float? = read<Float>(VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE)
        ?.also { lastOutside = it } ?: lastOutside

    fun readModelName(): String? = read<String>(VehiclePropertyIds.INFO_MODEL)?.takeIf { it.isNotBlank() }

    fun readPowertrain(): Powertrain {
        val raw = read<Any>(VehiclePropertyIds.INFO_FUEL_TYPE) ?: return Powertrain.UNKNOWN
        val types = when (raw) {
            is IntArray -> raw.toList()
            is Array<*> -> raw.filterIsInstance<Int>()
            else -> return Powertrain.UNKNOWN
        }.filter { it != FuelType.UNKNOWN }
        return when {
            types.isEmpty() -> Powertrain.UNKNOWN
            types.all { it == FuelType.ELECTRIC } -> Powertrain.ELECTRIC
            FuelType.ELECTRIC in types -> Powertrain.HYBRID
            else -> Powertrain.COMBUSTION
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> read(propertyId: Int): T? = try {
        manager?.getProperty<Any>(propertyId, GLOBAL_AREA)?.value as? T
    } catch (e: Exception) {
        Log.w(TAG, "read $propertyId failed: ${e.message}")
        null
    }

    @Suppress("DEPRECATION")
    fun release() {
        runCatching { manager?.unregisterCallback(callback) }
        runCatching { car?.disconnect() }
        manager = null
        car = null
        onChange = null
    }

    private fun gearOf(value: Any?): GearState = when (value as? Int) {
        null -> GearState.UNKNOWN
        VehicleGear.GEAR_PARK -> GearState.PARK
        VehicleGear.GEAR_UNKNOWN -> GearState.UNKNOWN
        else -> GearState.NOT_PARK
    }

    private companion object {
        const val TAG = "CarProperty"
        const val GLOBAL_AREA = 0
    }
}
