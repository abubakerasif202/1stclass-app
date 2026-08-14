package au.com.firstclassexpress.driver.data.repository

import au.com.firstclassexpress.driver.data.local.AppDatabase
import au.com.firstclassexpress.driver.data.local.entity.DriverMessageEntity
import au.com.firstclassexpress.driver.domain.model.DriverMessage
import au.com.firstclassexpress.driver.domain.model.MessageCategory
import au.com.firstclassexpress.driver.domain.model.MessageUrgency
import au.com.firstclassexpress.driver.domain.repository.MessageRepository
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomMessageRepository(
    private val database: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() }
) : MessageRepository {
    private val messageDao = database.driverMessageDao()

    override fun observeMessages(): Flow<List<DriverMessage>> =
        messageDao.observeAll().map { rows -> rows.map(::toMessage) }

    override fun observeUnreadCount(): Flow<Int> =
        messageDao.observeUnreadCount()

    override suspend fun markAsRead(messageId: String): Result<Unit> = runCatching {
        messageDao.markAsRead(messageId)
        Unit
    }

    override suspend fun insertMessage(message: DriverMessage): Result<Unit> = runCatching {
        messageDao.insert(
            DriverMessageEntity(
                id = message.id,
                category = message.category.name,
                title = message.title,
                body = message.body,
                jobId = message.jobId,
                timestamp = message.timestamp,
                isRead = message.isRead,
                urgency = message.urgency.name
            )
        )
    }

    override suspend fun seedInitialMessagesIfEmpty(): Result<Unit> = runCatching {
        if (messageDao.count() == 0) {
            val now = clock()
            val initial = listOf(
                DriverMessageEntity(
                    id = "msg-1",
                    category = MessageCategory.DISPATCH.name,
                    title = "Daily Route Optimization",
                    body = "Heavy traffic reported on Ipswich Motorway inbound. Take Logan Motorway where feasible.",
                    jobId = null,
                    timestamp = now - 3600_000L * 2,
                    isRead = false,
                    urgency = MessageUrgency.NORMAL.name
                ),
                DriverMessageEntity(
                    id = "msg-2",
                    category = MessageCategory.JOB_UPDATE.name,
                    title = "Job PO-99432 Gate Update",
                    body = "Receiving dock at Retail Distribution Centre Wacol moved to Gate 4 due to maintenance.",
                    jobId = "JOB-20260812-01",
                    timestamp = now - 3600_000L,
                    isRead = false,
                    urgency = MessageUrgency.HIGH.name
                ),
                DriverMessageEntity(
                    id = "msg-3",
                    category = MessageCategory.DRIVER_NOTICE.name,
                    title = "Safety Reminder: Chain of Responsibility",
                    body = "Ensure all pallet restraints and straps are checked before departing pickup. Safety is #1.",
                    jobId = null,
                    timestamp = now - 86400_000L,
                    isRead = true,
                    urgency = MessageUrgency.NORMAL.name
                )
            )
            messageDao.insertAll(initial)
        }
    }

    private fun toMessage(entity: DriverMessageEntity): DriverMessage =
        DriverMessage(
            id = entity.id,
            category = runCatching { MessageCategory.valueOf(entity.category) }.getOrDefault(MessageCategory.DISPATCH),
            title = entity.title,
            body = entity.body,
            jobId = entity.jobId,
            timestamp = entity.timestamp,
            isRead = entity.isRead,
            urgency = runCatching { MessageUrgency.valueOf(entity.urgency) }.getOrDefault(MessageUrgency.NORMAL)
        )
}
