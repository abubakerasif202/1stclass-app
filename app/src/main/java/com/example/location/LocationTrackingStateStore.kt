package com.example.location

import com.example.domain.model.GpsStatus
import com.example.domain.model.LocationPoint
import com.example.domain.model.LocationTrackingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class LocationTrackingStateStore {
    private val mutableState = MutableStateFlow(LocationTrackingState())
    val state: StateFlow<LocationTrackingState> = mutableState.asStateFlow()

    fun updateStatus(status: GpsStatus, activeShift: Boolean) {
        mutableState.update { it.copy(status = status, isShiftActive = activeShift) }
    }

    fun updatePoint(point: LocationPoint, precise: Boolean) {
        mutableState.value = LocationTrackingState(
            status = if (precise) GpsStatus.ACTIVE else GpsStatus.LIMITED,
            lastPoint = point,
            isShiftActive = true
        )
    }

    fun restoreLastPoint(point: LocationPoint?) {
        mutableState.update { it.copy(lastPoint = point) }
    }
}
