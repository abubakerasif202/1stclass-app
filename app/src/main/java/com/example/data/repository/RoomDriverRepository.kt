package com.example.data.repository

import com.example.data.local.dao.ReferenceDataDao
import com.example.domain.repository.DriverRepository
import com.example.model.Driver
import com.example.model.ShiftStatus

class RoomDriverRepository(
    private val referenceDataDao: ReferenceDataDao
) : DriverRepository {
    override suspend fun getPrototypeDriver(): Driver? = referenceDataDao.firstDriver()?.let { entity ->
        Driver(
            id = entity.id,
            name = entity.name,
            email = entity.email,
            shiftStatus = ShiftStatus.OFF_DUTY
        )
    }
}
