package top.xiaofeigun.tvliveplayer.domain.usecase

import com.google.gson.Gson
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import top.xiaofeigun.tvliveplayer.data.local.entity.ChannelEntity
import top.xiaofeigun.tvliveplayer.data.repository.ChannelRepositoryImpl
import top.xiaofeigun.tvliveplayer.testutil.FakeChannelBackupHelper
import top.xiaofeigun.tvliveplayer.testutil.FakeChannelDao
import top.xiaofeigun.tvliveplayer.testutil.FakeSourceDao
import top.xiaofeigun.tvliveplayer.testutil.ImmediateTransactionRunner

@OptIn(ExperimentalCoroutinesApi::class)
class SyncChannelBackupUseCaseTest {

    @Test
    fun testClearAllIsSnapshottedWithoutWaitingForBackground() = runTest {
        val dao = FakeChannelDao().apply {
            seed(listOf(ChannelEntity(id = "a", name = "A", url = "http://a", category = "c")))
        }
        val repository = ChannelRepositoryImpl(dao, FakeSourceDao(), Gson(), ImmediateTransactionRunner)
        val backup = FakeChannelBackupHelper()
        val sync = SyncChannelBackupUseCase(repository, backup)

        backgroundScope.launch { sync() }
        advanceTimeBy(SyncChannelBackupUseCase.DEBOUNCE_MS + 1)
        runCurrent()
        assertTrue(backup.snapshot!!.contains("http://a"))

        repository.clearAll()
        advanceTimeBy(SyncChannelBackupUseCase.DEBOUNCE_MS + 1)
        runCurrent()
        // 快照已同步为空：下次启动不会把已清空的频道恢复回来
        assertEquals("""{"channels":[],"sources":[]}""", backup.snapshot)
    }
}
