package top.xiaofeigun.tvliveplayer.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import top.xiaofeigun.tvliveplayer.R
import top.xiaofeigun.tvliveplayer.ui.player.Page
import top.xiaofeigun.tvliveplayer.ui.player.PlayerViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private val playerViewModel: PlayerViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnUpdate = view.findViewById<TextView>(R.id.btn_update)
        btnUpdate.setOnClickListener { playerViewModel.pushPage(Page.UPDATE) }
        btnUpdate.requestFocus()
    }
}
