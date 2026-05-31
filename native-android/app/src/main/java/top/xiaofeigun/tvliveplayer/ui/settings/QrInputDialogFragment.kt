package top.xiaofeigun.tvliveplayer.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import top.xiaofeigun.tvliveplayer.R
import top.xiaofeigun.tvliveplayer.util.QrCodeHelper
import top.xiaofeigun.tvliveplayer.util.QrCodeServer
import top.xiaofeigun.tvliveplayer.util.QrScanResult
import kotlinx.coroutines.launch

class QrInputDialogFragment : DialogFragment() {

    private var onResultReceived: ((QrScanResult) -> Unit)? = null
    private var onCancel: (() -> Unit)? = null
    private var server: QrCodeServer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.dialog_qr_input, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val ivQr = view.findViewById<ImageView>(R.id.iv_qr_code)
        val tvInfo = view.findViewById<TextView>(R.id.tv_qr_info)
        val btnCancel = view.findViewById<TextView>(R.id.btn_qr_cancel)

        btnCancel.setOnClickListener {
            server?.stop()
            onCancel?.invoke()
            dismiss()
        }
        btnCancel.requestFocus()

        lifecycleScope.launch {
            val srv = QrCodeServer()
            server = srv
            if (!srv.start()) {
                tvInfo.text = "无法获取设备IP地址，请确保已连接网络"
                return@launch
            }
            tvInfo.text = "请扫描二维码\n地址: ${srv.localUrl}"
            ivQr.setImageBitmap(QrCodeHelper.generate(srv.localUrl, 512))

            val result = srv.waitForUrl()
            if (result != null) {
                onResultReceived?.invoke(result)
                dismiss()
            }
        }
    }

    override fun onDestroyView() {
        server?.stop()
        super.onDestroyView()
    }

    fun setOnResultReceived(listener: (QrScanResult) -> Unit) {
        onResultReceived = listener
    }

    fun setOnCancel(listener: () -> Unit) {
        onCancel = listener
    }
}
