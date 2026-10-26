package top.xiaofeigun.tvliveplayer.data.local

import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 数据库事务执行器：保证「先清空再写入」等多步操作原子提交，
 * Room Flow 只在事务提交后推送一次最终结果，避免 UI 收到中间态空列表。
 */
interface TransactionRunner {
    suspend fun <T> run(block: suspend () -> T): T
}

@Singleton
class RoomTransactionRunner @Inject constructor(
    private val database: AppDatabase
) : TransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = database.withTransaction { block() }
}
