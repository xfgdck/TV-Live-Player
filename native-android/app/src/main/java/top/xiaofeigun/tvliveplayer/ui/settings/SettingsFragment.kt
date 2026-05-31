package top.xiaofeigun.tvliveplayer.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import top.xiaofeigun.tvliveplayer.R
import top.xiaofeigun.tvliveplayer.ui.settings.UpdateFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.btn_update).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, UpdateFragment())
                .addToBackStack("update")
                .commit()
        }

        view.findViewById<TextView>(R.id.btn_update).requestFocus()
    }
}
