package au.com.firstclassexpress.driver.domain.repository

import au.com.firstclassexpress.driver.model.Driver

interface DriverRepository {
    suspend fun getPrototypeDriver(): Driver?
    suspend fun getDriver(id: String): Driver?
}
