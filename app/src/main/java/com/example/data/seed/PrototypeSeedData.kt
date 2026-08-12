package com.example.data.seed

import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.JobPayloadCodec
import com.example.data.local.entity.DriverEntity
import com.example.data.local.entity.JobEntity
import com.example.model.Driver
import com.example.model.Job
import com.example.model.JobStatus
import com.example.model.Location
import com.example.model.Priority
import com.example.model.ShiftStatus

class PrototypeSeedData(
    private val database: AppDatabase,
    private val codec: JobPayloadCodec = JobPayloadCodec(),
    private val clock: () -> Long = System::currentTimeMillis
) {
    val driver = Driver(
        id = "DRV-8492",
        name = "James Miller",
        email = "james.miller@firstclassexpress.com.au",
        shiftStatus = ShiftStatus.OFF_DUTY
    )

    val jobs: List<Job> = listOf(
        Job(
            id = "JOB-20260812-01",
            reference = "PO-99432",
            status = JobStatus.ASSIGNED,
            pickup = Location(
                address = "14 Enterprise Drive",
                suburb = "Berrinba",
                lat = -27.6521,
                lng = 153.0768,
                companyName = "Logistics Hub QLD",
                contactName = "Sarah Jenkins",
                contactPhone = "0400 123 456",
                notes = "Report to gate 3"
            ),
            delivery = Location(
                address = "45 Industrial Ave",
                suburb = "Wacol",
                lat = -27.5755,
                lng = 152.9366,
                companyName = "Retail Distribution Centre",
                contactName = "Receiving Dock",
                contactPhone = "07 3333 4444",
                notes = "Must have safety vest and steel caps"
            ),
            pickupWindowStart = "06:00",
            pickupWindowEnd = "08:00",
            deliveryWindowStart = "09:00",
            deliveryWindowEnd = "12:00",
            freightDescription = "Mixed Pallets (Consumer Goods)",
            itemCount = 12,
            priority = Priority.NORMAL
        ),
        Job(
            id = "JOB-20260812-02",
            reference = "REF-URG-77",
            status = JobStatus.UNASSIGNED,
            pickup = Location(
                address = "22 Transport Way",
                suburb = "Heathwood",
                lat = -27.6321,
                lng = 152.9968,
                companyName = "Express Freight Depot",
                contactName = "Operations Manager",
                contactPhone = "0411 999 888"
            ),
            delivery = Location(
                address = "8 Port Road",
                suburb = "Port of Brisbane",
                lat = -27.3855,
                lng = 153.1666,
                companyName = "Wharf Services",
                contactName = "Gate 2",
                contactPhone = "07 5555 6666",
                notes = "MSIC required"
            ),
            pickupWindowStart = "13:00",
            pickupWindowEnd = "14:00",
            deliveryWindowStart = "15:00",
            deliveryWindowEnd = "17:00",
            freightDescription = "Heavy Machinery Parts",
            itemCount = 2,
            priority = Priority.URGENT
        )
    )

    suspend fun seedIfEmpty() {
        database.withTransaction {
            val referenceDao = database.referenceDataDao()
            if (referenceDao.driverCount() == 0) {
                referenceDao.insertDriver(DriverEntity(driver.id, driver.name, driver.email))
            }
            val jobDao = database.jobDao()
            if (jobDao.count() == 0) {
                val now = clock()
                jobDao.insertAll(
                    jobs.map { job ->
                        JobEntity(
                            id = job.id,
                            payloadJson = codec.encode(job),
                            status = job.status.name,
                            updatedAt = now
                        )
                    }
                )
            }
        }
    }
}
