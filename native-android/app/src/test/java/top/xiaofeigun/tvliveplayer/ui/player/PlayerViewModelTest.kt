package top.xiaofeigun.tvliveplayer.ui.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import top.xiaofeigun.tvliveplayer.data.local.entity.ChannelEntity
import top.xiaofeigun.tvliveplayer.data.repository.ChannelRepositoryImpl
import top.xiaofeigun.tvliveplayer.domain.repository.ChannelRepository
import top.xiaofeigun.tvliveplayer.testutil.FakeAppPreferences
import top.xiaofeigun.tvliveplayer.testutil.FakeChannelDao
import top.xiaofeigun.tvliveplayer.testutil.FakeSourceDao
import top.xiaofeigun.tvliveplayer.testutil.ImmediateTransactionRunner

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeDao: FakeChannelDao
    private lateinit var fakePrefs: FakeAppPreferences
    private lateinit var viewModel: PlayerViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeDao = FakeChannelDao()
        val repository = ChannelRepositoryImpl(fakeDao, FakeSourceDao(), com.google.gson.Gson(), ImmediateTransactionRunner)

        // Seed channels across two categories
        fakeDao.seed(
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

        val expected = listOf("cctv2" to "央视频道", "cctv3" to "央视频道", "hnws" to "卫视频道", "zjws" to "卫视频道", "cctv1" to "央视频道")
        for ((id, cat) in expected) {
            viewModel.nextChannel()
            advanceUntilIdle()
            assertEquals(id, viewModel.state.value.currentChannel?.id)
            assertEquals(cat, viewModel.state.value.currentCategory)
        }

        // Previous channel loops back to 浙江卫视
        viewModel.previousChannel()
        advanceUntilIdle()
        assertEquals("zjws", viewModel.state.value.currentChannel?.id)
        assertEquals("卫视频道", viewModel.state.value.currentCategory)
        assertEquals("zjws", fakePrefs.getLastChannelId())
    }

    @Test
    fun testSelectChannelFromDrawer() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.enterCategorySelect()
        advanceUntilIdle()
        assertEquals(listOf(Page.PLAYER, Page.CATEGORY), viewModel.state.value.navStack)

        val weiShiIndex = viewModel.state.value.allCategories.indexOf("卫视频道")
        viewModel.selectCategory(weiShiIndex)
        advanceUntilIdle()

        viewModel.focusCategoryChannel(0)
        viewModel.confirmCategorySelect()
        advanceUntilIdle()

        assertEquals("hnws", viewModel.state.value.currentChannel?.id)
        assertEquals("卫视频道", viewModel.state.value.currentCategory)
        assertEquals(listOf(Page.PLAYER), viewModel.state.value.navStack)

        viewModel.nextChannel()
        advanceUntilIdle()
        assertEquals("zjws", viewModel.state.value.currentChannel?.id)
    }

    @Test
    fun testDrawerFocusesCurrentChannel() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.nextChannel()
        viewModel.nextChannel()
        advanceUntilIdle()

        viewModel.enterCategorySelect()
        advanceUntilIdle()
        val s = viewModel.state.value
        assertEquals("央视频道", s.selectedCategory)
        assertEquals("cctv3", s.categoryChannels[s.selectedChannelIndex].id)
    }

    @Test
    fun testFavoriteCategoryAppearingKeepsDrawerSelection() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.enterCategorySelect()
        viewModel.selectCategory(viewModel.state.value.allCategories.indexOf("卫视频道"))
        advanceUntilIdle()

        // 首次收藏会在分类列表首位插入「我的收藏」，抽屉仍应停留在原分类
        viewModel.toggleFavorite("hnws")
        advanceUntilIdle()
        val s = viewModel.state.value
        assertEquals(ChannelRepository.FAVORITE_CATEGORY, s.allCategories.first())
        assertEquals("卫视频道", s.selectedCategory)
        assertEquals(s.allCategories.indexOf("卫视频道"), s.selectedCategoryIndex)
    }

    @Test
    fun testDeletingDrawerCategoryFallsBackToNeighbor() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.enterCategorySelect()
        viewModel.selectCategory(viewModel.state.value.allCategories.indexOf("卫视频道"))
        advanceUntilIdle()

        viewModel.deleteCategory("卫视频道")
        advanceUntilIdle()
        val s = viewModel.state.value
        assertEquals(listOf("央视频道"), s.allCategories)
        assertEquals("央视频道", s.selectedCategory)
        assertEquals(3, s.categoryChannels.size)
    }

    @Test
    fun testMergeCategoryRefreshesDrawer() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.enterCategorySelect()
        advanceUntilIdle()

        viewModel.mergeCategory("卫视频道", "央视频道")
        advanceUntilIdle()
        val s = viewModel.state.value
        assertEquals(listOf("央视频道"), s.allCategories)
        assertEquals(5, s.categoryChannels.size)
    }

    @Test
    fun testBackFlowWithExitConfirm() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.pushPage(Page.ICON)
        viewModel.pushPage(Page.SETTINGS)
        viewModel.pushPage(Page.UPDATE)
        assertEquals(listOf(Page.PLAYER, Page.ICON, Page.SETTINGS, Page.UPDATE), viewModel.state.value.navStack)

        assertFalse(viewModel.onBack())
        assertFalse(viewModel.onBack())
        assertFalse(viewModel.onBack())
        assertEquals(listOf(Page.PLAYER), viewModel.state.value.navStack)

        // Level 0：第一次返回只提示，第二次才退出
        assertFalse(viewModel.onBack())
        assertTrue(viewModel.state.value.showExitConfirm)
        assertTrue(viewModel.onBack())
    }

    @Test
    fun testPushRules() {
        val p = PlayerViewModel.Companion
        // Level 1 互斥
        assertEquals(listOf(Page.PLAYER, Page.CATEGORY), p.pushed(listOf(Page.PLAYER, Page.ICON), Page.CATEGORY))
        // 已在栈中的页面不重复叠加（例如源管理页内清空频道触发的重定向）
        val stack = listOf(Page.PLAYER, Page.ICON, Page.SOURCE_MGMT)
        assertEquals(stack, p.pushed(stack, Page.SOURCE_MGMT))
        // 回到栈中已有页面时截断其上方页面
        assertEquals(
            listOf(Page.PLAYER, Page.ICON),
            p.pushed(listOf(Page.PLAYER, Page.ICON, Page.SETTINGS, Page.UPDATE), Page.ICON)
        )
    }
}
