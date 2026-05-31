package top.xiaofeigun.tvliveplayer.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import top.xiaofeigun.tvliveplayer.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class UpdateFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_update, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.btn_check_update).setOnClickListener {
            Toast.makeText(requireContext(), "当前已是最新版本", Toast.LENGTH_SHORT).show()
        }
    }
}
