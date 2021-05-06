package com.google.android.connecteddevice.experimental.vehiclesettings

import android.car.testapi.FakeCar
import android.car.VehiclePropertyIds
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VehicleSettingsControllerTest {
  private lateinit var vehicleSettingsController: VehicleSettingsController
  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val fakeCar = FakeCar.createFakeCar(context)
  private val car = fakeCar.getCar()

  @Before
  fun setUp() {
    vehicleSettingsController = VehicleSettingsController(car)
  }

  @Test
  fun carPropertyEventCallback_updatesStateAccurately() {
    val fanSpeed = VehiclePropertyIds.HVAC_FAN_SPEED
    val fanDirection = VehiclePropertyIds.HVAC_FAN_DIRECTION
    val temperatureSet = VehiclePropertyIds.HVAC_TEMPERATURE_SET
    val defroster = VehiclePropertyIds.HVAC_DEFROSTER
    val acOn = VehiclePropertyIds.HVAC_AC_ON
    val recircOn = VehiclePropertyIds.HVAC_RECIRC_ON
    val autoOn = VehiclePropertyIds.HVAC_AUTO_ON
    val globalAreaId = 117
    val frontAreaId = 2
    val rearAreaId = 2
    val driverAreaId = 49
    val passengerAreaId = 68
    val newValueFanSpeed: Int = 25
    val newValueFanDirection: Int = 0
    val newValueRecircOn: Boolean = true
    val newValueDefrosterRear: Boolean = true
    val newValueDefrosterFront: Boolean = true
    val newValueAcOn: Boolean = true
    val newValueAutoOn: Boolean = true
    val newValueTemperatureSetDriver: Float = 29.toFloat()
    val newValueTemperatureSetPassenger: Float = 30.toFloat()
    val valueFanSpeed = PropertyValue(fanSpeed, newValueFanSpeed, globalAreaId)
    val valueFanDirection = PropertyValue(fanDirection, newValueFanDirection, globalAreaId)
    val valueRecircOn = PropertyValue(recircOn, newValueRecircOn, globalAreaId)
    val valueDefrosterRear = PropertyValue(defroster, newValueDefrosterRear, rearAreaId)
    val valueAcOn = PropertyValue(acOn, newValueAcOn, globalAreaId)
    val valueAutoOn = PropertyValue(autoOn, newValueAutoOn, globalAreaId)
    val valueTemperatureSetDriver = PropertyValue(
      temperatureSet,
      newValueTemperatureSetDriver,
      driverAreaId
    )
    val valueTemperatureSetPassenger = PropertyValue(
      temperatureSet,
      newValueTemperatureSetPassenger,
      passengerAreaId
    )
    val valueDefrosterFront = PropertyValue(
      defroster,
      newValueDefrosterFront,
      frontAreaId
    )

    vehicleSettingsController.onChangeEvent(valueFanSpeed)
    vehicleSettingsController.onChangeEvent(valueFanDirection)
    vehicleSettingsController.onChangeEvent(valueRecircOn)
    vehicleSettingsController.onChangeEvent(valueDefrosterRear)
    vehicleSettingsController.onChangeEvent(valueDefrosterFront)
    vehicleSettingsController.onChangeEvent(valueAcOn)
    vehicleSettingsController.onChangeEvent(valueAutoOn)
    vehicleSettingsController.onChangeEvent(valueTemperatureSetDriver)
    vehicleSettingsController.onChangeEvent(valueTemperatureSetPassenger)
    assertThat(
      vehicleSettingsController.getCarStateValue(
        fanSpeed,
        globalAreaId
      )
    ).isEqualTo(25)
    assertThat(
      vehicleSettingsController.getCarStateValue(
        fanDirection,
        globalAreaId
      )
    ).isEqualTo(0)
    assertThat(
      vehicleSettingsController.getCarStateValue(
        recircOn,
        globalAreaId
      )
    ).isEqualTo(true)
    assertThat(
      vehicleSettingsController.getCarStateValue(
        defroster,
        rearAreaId
      )
    ).isEqualTo(true)
    assertThat(
      vehicleSettingsController.getCarStateValue(
        defroster,
        frontAreaId
      )
    ).isEqualTo(true)
    assertThat(
      vehicleSettingsController.getCarStateValue(
        acOn,
        globalAreaId
      )
    ).isEqualTo(true)
    assertThat(
      vehicleSettingsController.getCarStateValue(
        autoOn,
        globalAreaId
      )
    ).isEqualTo(true)
    assertThat(
      vehicleSettingsController.getCarStateValue(
        temperatureSet,
        driverAreaId
      )
    ).isEqualTo(29)
    assertThat(
      vehicleSettingsController.getCarStateValue(
        temperatureSet,
        passengerAreaId
      )
    ).isEqualTo(30)
  }
}
