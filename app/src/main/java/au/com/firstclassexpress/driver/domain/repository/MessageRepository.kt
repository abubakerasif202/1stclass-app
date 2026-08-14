package au.com.firstclassexpress.driver.domain.repository

import au.com.firstclassexpress.driver.domain.model.DriverMessage
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    fun observeMessages(): Flow<List<DriverMessage>>
    fun observeUnreadCount(): Flow<Int>
    suspend fun markAsRead(messageId: String): Result<Unit>
    suspend fun insertMessage(message: DriverMessage): Result<Unit>
    suspend fun seedInitialMessagesIfEmpty(): Result<Unit>
}
