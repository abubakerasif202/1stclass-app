package au.com.firstclassexpress.driver.data.repository

import au.com.firstclassexpress.driver.data.local.dao.ReferenceDataDao
import au.com.firstclassexpress.driver.domain.repository.DriverRepository
import au.com.firstclassexpress.driver.model.Driver
import au.com.firstclassexpress.driver.model.ShiftStatus

class RoomDriverRepository(
    private val referenceDataDao: ReferenceDataDao
) : DriverRepository {
    override suspend fun getPrototypeDriver(): Driver? =
        referenceDataDao.firstDriver()?.toDomain()

    override suspend fun getDriver(id: String): Driver? =
        referenceDataDao.findDriver(id)?.toDomain()

    private fun au.com.firstclassexpress.driver.data.local.entity.DriverEntity.toDomain() = Driver(
        id = id,
        name = name,
        email = email,
        shiftStatus = ShiftStatus.OFF_DUTY,
        phone = phone
    )
}
