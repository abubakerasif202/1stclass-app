package com.example.data.repository

import com.example.data.local.dao.ReferenceDataDao
import com.example.domain.repository.DriverRepository
import com.example.model.Driver
import com.example.model.ShiftStatus

class RoomDriverRepository(
    private val referenceDataDao: ReferenceDataDao
) : DriverRepository {
    override suspend fun getPrototypeDriver(): Driver? =
        referenceDataDao.firstDriver()?.toDomain()

    override suspend fun getDriver(id: String): Driver? =
        referenceDataDao.findDriver(id)?.toDomain()

    private fun com.example.data.local.entity.DriverEntity.toDomain() = Driver(
        id = id,
        name = name,
        email = email,
        shiftStatus = ShiftStatus.OFF_DUTY,
        phone = phone
    )
}
