package top.xiaofeigun.tvliveplayer.ui.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import top.xiaofeigun.tvliveplayer.data.local.entity.ChannelEntity
import top.xiaofeigun.tvliveplayer.data.preferences.AppPreferences
import top.xiaofeigun.tvliveplayer.data.repository.ChannelRepositoryImpl
import top.xiaofeigun.tvliveplayer.data.repository.ChannelRepositoryTest.FakeChannelDao
import top.xiaofeigun.tvliveplayer.domain.repository.ChannelRepository

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeDao: FakeChannelDao
    private lateinit var repository: ChannelRepositoryImpl
    private lateinit var fakePrefs: FakeAppPreferences
    private lateinit var viewModel: PlayerViewModel

    class FakeAppPreferences : AppPreferences(null) {
        private var lastId: String? = null
        override fun getLastChannelId(): String? = lastId
        override fun setLastChannelId(id: String) { lastId = id }
        override fun isFirstLaunch(): Boolean = false
        override fun setFirstLaunchDone() {}
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeDao = FakeChannelDao()
        val fakeSourceDao = object : top.xiaofeigun.tvliveplayer.data.local.dao.SourceDao {
            override fun getAllSources(): kotlinx.coroutines.flow.Flow<List<top.xiaofeigun.tvliveplayer.data.local.entity.SourceEntity>> = kotlinx.coroutines.flow.flowOf(emptyList())
            override suspend fun getSourceById(id: String): top.xiaofeigun.tvliveplayer.data.local.entity.SourceEntity? = null
            override suspend fun insert(source: top.xiaofeigun.tvliveplayer.data.local.entity.SourceEntity) {}
            override suspend fun delete(source: top.xiaofeigun.tvliveplayer.data.local.entity.SourceEntity) {}
            override suspend fun deleteAll() {}
        }
        repository = ChannelRepositoryImpl(fakeDao, fakeSourceDao, com.google.gson.Gson())

        // Seed channels across two categories
        fakeDao.channels.addAll(
            listOf(
                ChannelEntity(id = "cctv1", name = "CCTV-1", url = "http://cctv1", category = "央视频道", sortOrder = 0),
                ChannelEntity(id = "cctv2", name = "CCTV-2", url = "http://cctv2", category = "央视频道", sortOrder = 1),
                ChannelEntity(id = "cctv3", name = "CCTV-3", url = "http://cctv3", category = "央视频道", sortOrder = 2),
                ChannelEntity(id = "hnws", name = "湖南卫视", url = "http://hnws", category = "卫视频道", sortOrder = 3),
                ChannelEntity(id = "zjws", name = "浙江卫视", url = "http://zjws", category = "卫视频道", sortOrder = 4)
            )
        )

        fakePrefs = FakeAppPreferences()
        viewModel = PlayerViewModel(repository, fakePrefs)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testNextAndPreviousChannelAcrossAllChannels() = runTest(testDispatcher) {
        advanceUntilIdle()

        val state0 = viewModel.state.value
        assertEquals("央视频道", state0.currentCategory)
        assertEquals("cctv1", state0.currentChannel?.id)

        // Next channel -> CCTV-2
        viewModel.nextChannel()
        advanceUntilIdle()
        assertEquals("cctv2", viewModel.state.value.currentChannel?.id)
        assertEquals("央视频道", viewModel.state.value.currentCategory)

        // Next channel -> CCTV-3
        viewModel.nextChannel()
        advanceUntilIdle()
        assertEquals("cctv3", viewModel.state.value.currentChannel?.id)
        assertEquals("央视频道", viewModel.state.value.currentCategory)

        // Next channel crosses into next category: 湖南卫视 (卫视频道)
        viewModel.nextChannel()
        advanceUntilIdle()
        assertEquals("hnws", viewModel.state.value.currentChannel?.id)
        assertEquals("卫视频道", viewModel.state.value.currentCategory)

        // Next channel -> 浙江卫视 (卫视频道)
        viewModel.nextChannel()
        advanceUntilIdle()
        assertEquals("zjws", viewModel.state.value.currentChannel?.id)
        assertEquals("卫视频道", viewModel.state.value.currentCategory)

        // Next channel loops back to CCTV-1
        viewModel.nextChannel()
        advanceUntilIdle()
        assertEquals("cctv1", viewModel.state.value.currentChannel?.id)
        assertEquals("央视频道", viewModel.state.value.currentCategory)

        // Previous channel loops back to 浙江卫视
        viewModel.previousChannel()
        advanceUntilIdle()
        assertEquals("zjws", viewModel.state.value.currentChannel?.id)
        assertEquals("卫视频道", viewModel.state.value.currentCategory)
    }

    @Test
    fun testSelectChannelFromDrawer() = runTest(testDispatcher) {
        advanceUntilIdle()

        // Open drawer
        viewModel.enterCategorySelect()
        advanceUntilIdle()

        // Switch to "卫视频道" (index 1)
        val cats = viewModel.state.value.allCategories
        val weiShiIndex = cats.indexOf("卫视频道")
        viewModel.selectCategory(weiShiIndex)
        advanceUntilIdle()

        viewModel.focusCategoryChannel(0)
        viewModel.confirmCategorySelect()
        advanceUntilIdle()

        // Current channel is now 湖南卫视, category is 卫视频道
        assertEquals("hnws", viewModel.state.value.currentChannel?.id)
        assertEquals("卫视频道", viewModel.state.value.currentCategory)

        // Next channel continues to 浙江卫视
        viewModel.nextChannel()
        advanceUntilIdle()
        assertEquals("zjws", viewModel.state.value.currentChannel?.id)
        assertEquals("卫视频道", viewModel.state.value.currentCategory)
    }
}
