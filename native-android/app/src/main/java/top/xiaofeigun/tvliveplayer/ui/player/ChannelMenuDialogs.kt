package top.xiaofeigun.tvliveplayer.ui.player

import android.app.AlertDialog
import android.content.Context
import android.widget.Toast
import top.xiaofeigun.tvliveplayer.domain.repository.ChannelRepository

/** 选台抽屉中的频道操作菜单（AGENTS.md 5.3 节） */
class ChannelMenuDialogs(
    private val context: Context,
    private val viewModel: PlayerViewModel
) {
    fun show(index: Int) {
        val state = viewModel.state.value
        val channel = state.categoryChannels.getOrNull(index) ?: return
        val curCat = state.selectedCategory.ifEmpty { return }

        if (curCat == ChannelRepository.FAVORITE_CATEGORY) {
            AlertDialog.Builder(context)
                .setTitle(channel.name)
                .setItems(arrayOf("取消收藏")) { _, _ ->
                    viewModel.toggleFavorite(channel.id)
                    toast("已取消收藏")
                }
                .setNegativeButton("返回", null)
                .show()
            return
        }

        val otherCats = state.allCategories.filter {
            it != ChannelRepository.FAVORITE_CATEGORY && it != curCat
        }
        val items = arrayOf("移动到其它分类", "合并当前分类", "删除当前频道", "清空当前分类")
        AlertDialog.Builder(context)
            .setTitle(channel.name)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> pickCategory("移动到", otherCats) { target ->
                        viewModel.moveCategoryChannel(index, target)
                        toast("已移动到 $target")
                    }
                    1 -> pickCategory("将「$curCat」合并到", otherCats) { target ->
                        viewModel.mergeCategory(curCat, target)
                        toast("已将「$curCat」合并到「$target」")
                    }
                    2 -> viewModel.deleteCategoryChannel(index)
                    3 -> confirmDeleteCategory(curCat)
                }
            }
            .setNegativeButton("返回", null)
            .show()
    }

    private fun pickCategory(title: String, categories: List<String>, onPicked: (String) -> Unit) {
        if (categories.isEmpty()) {
            toast("没有其它分类")
            return
        }
        AlertDialog.Builder(context)
            .setTitle(title)
            .setItems(categories.toTypedArray()) { _, which -> onPicked(categories[which]) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun confirmDeleteCategory(category: String) {
        AlertDialog.Builder(context)
            .setTitle("清空分类")
            .setMessage("确定要清空分类「$category」吗？该分类下的所有频道将被永久删除。")
            .setPositiveButton("确定清空") { _, _ ->
                viewModel.deleteCategory(category)
                toast("分类「$category」已清空")
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
