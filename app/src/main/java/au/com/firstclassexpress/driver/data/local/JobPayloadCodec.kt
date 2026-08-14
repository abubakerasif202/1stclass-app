package au.com.firstclassexpress.driver.data.local

import au.com.firstclassexpress.driver.model.Job
import au.com.firstclassexpress.driver.model.JobStatus
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class JobPayloadCodec(
    moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
) {
    private val adapter = moshi.adapter(Job::class.java)

    fun encode(job: Job): String = adapter.toJson(job)

    fun decode(payloadJson: String, persistedStatus: String): Job =
        requireNotNull(adapter.fromJson(payloadJson)).copy(
            status = JobStatus.valueOf(persistedStatus)
        )
}
