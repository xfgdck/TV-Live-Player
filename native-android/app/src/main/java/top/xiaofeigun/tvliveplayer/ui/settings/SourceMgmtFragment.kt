package top.xiaofeigun.tvliveplayer.ui.settings

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import top.xiaofeigun.tvliveplayer.MainActivity
import top.xiaofeigun.tvliveplayer.R
import top.xiaofeigun.tvliveplayer.domain.model.Channel
import top.xiaofeigun.tvliveplayer.domain.repository.ChannelRepository
import top.xiaofeigun.tvliveplayer.domain.usecase.ParseAndImportM3UUseCase
import top.xiaofeigun.tvliveplayer.parser.M3UParser
import top.xiaofeigun.tvliveplayer.util.QrScanResult
import dagger.hilt.android.AndroidEntryPoint
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class SourceMgmtFragment : Fragment() {

    @Inject lateinit var channelRepository: ChannelRepository
    @Inject lateinit var m3uParser: M3UParser

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_source_mgmt, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.btn_qr_scan).setOnClickListener { showQrScanDialog() }
        view.findViewById<TextView>(R.id.btn_url).setOnClickListener { showUrlDialog() }
        view.findViewById<TextView>(R.id.btn_clear).setOnClickListener { showClearDialog() }

        view.findViewById<TextView>(R.id.btn_qr_scan).requestFocus()
    }

    private fun closeSelf() {
        (activity as? MainActivity)?.playerViewModel?.popPage()
        parentFragmentManager.popBackStack()
    }

    private fun showUrlDialog() {
        val view = LayoutInflater.from(requireActivity()).inflate(R.layout.dialog_input_url, null)
        val input = view.findViewById<EditText>(R.id.et_url)
        AlertDialog.Builder(requireActivity())
            .setTitle("在线链接解析")
            .setView(view)
            .setPositiveButton("确定") { _, _ ->
                val url = input.text.toString().trim { it <= ' ' || it == '\uFEFF' || it == '\u200B' || it == '\u200C' || it == '\u200D' || it == '\u00A0' }
                if (url.isNotEmpty()) downloadAndParse(url)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showQrScanDialog() {
        val dialog = QrInputDialogFragment()
        dialog.setOnResultReceived { result ->
            when (result.type) {
                "m3u_url" -> showUrlDialogWithUrl(result.url)
                "channel" -> addChannelFromQr(result)
                "file" -> parseM3uFromQr(result)
            }
        }
        dialog.setOnCancel {
            Toast.makeText(requireContext(), "已取消", Toast.LENGTH_SHORT).show()
        }
        dialog.show(parentFragmentManager, "qr_input")
    }

    private fun showUrlDialogWithUrl(url: String) {
        val view = LayoutInflater.from(requireActivity()).inflate(R.layout.dialog_input_url, null)
        val input = view.findViewById<EditText>(R.id.et_url)
        input.setText(url)
        input.setSelection(url.length)
        AlertDialog.Builder(requireActivity())
            .setTitle("在线链接解析")
            .setView(view)
            .setPositiveButton("确定") { _, _ ->
                val text = input.text.toString().trim { it <= ' ' || it == '\uFEFF' || it == '\u200B' || it == '\u200C' || it == '\u200D' || it == '\u00A0' }
                if (text.isNotEmpty()) downloadAndParse(text)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun addChannelFromQr(result: QrScanResult) {
        val cleanUrl = result.url.trim { it <= ' ' || it == '\uFEFF' || it == '\u200B' || it == '\u200C' || it == '\u200D' || it == '\u00A0' }
        lifecycleScope.launch {
            channelRepository.addChannels(listOf(
                Channel(
                    id = java.util.UUID.randomUUID().toString(),
                    name = result.name.trim(),
                    url = cleanUrl,
                    category = result.category.ifEmpty { "自定义" },
                    logo = result.logo.ifEmpty { null }
                )
            ))
            Toast.makeText(requireContext(), "频道「${result.name}」添加成功", Toast.LENGTH_SHORT).show()
            closeSelf()
        }
    }

    private fun parseM3uFromQr(result: QrScanResult) {
        val loadingDialog = showLoadingDialog()
        lifecycleScope.launch {
            try {
                val count = ParseAndImportM3UUseCase(m3uParser, channelRepository)(result.fileContent, null)
                loadingDialog.dismiss()
                Toast.makeText(requireContext(), "导入成功！共 $count 个频道", Toast.LENGTH_SHORT).show()
                closeSelf()
            } catch (e: Exception) {
                loadingDialog.dismiss()
                Toast.makeText(requireContext(), "导入失败：${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showClearDialog() {
        AlertDialog.Builder(requireActivity())
            .setTitle("确认清空")
            .setMessage("确定要清空所有节目单吗？此操作不可撤销！")
            .setPositiveButton("确定") { _, _ ->
                lifecycleScope.launch {
                    channelRepository.clearAllChannels()
                    Toast.makeText(requireContext(), "节目单已清空，请添加新的直播源", Toast.LENGTH_SHORT).show()
                    (activity as? MainActivity)?.playerViewModel?.resetToPlayer()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showLoadingDialog(): AlertDialog {
        val progressBar = ProgressBar(requireActivity(), null, android.R.attr.progressBarStyleLarge)
        progressBar.isIndeterminate = true
        val padding = resources.getDimensionPixelSize(android.R.dimen.app_icon_size)
        progressBar.setPadding(padding, padding / 2, padding, padding / 2)
        return AlertDialog.Builder(requireActivity())
            .setMessage("正在解析中，请稍候...")
            .setView(progressBar)
            .setCancelable(false)
            .show()
    }

    private fun downloadAndParse(url: String) {
        val loadingDialog = showLoadingDialog()
        lifecycleScope.launch {
            try {
                val client = buildUnsafeOkHttpClient()
                val request = okhttp3.Request.Builder().url(url).build()
                val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
                val body = response.body?.string() ?: return@launch
                val count = ParseAndImportM3UUseCase(m3uParser, channelRepository)(body, null)
                loadingDialog.dismiss()
                Toast.makeText(requireContext(), "导入成功！共 $count 个频道", Toast.LENGTH_SHORT).show()
                closeSelf()
            } catch (e: Exception) {
                loadingDialog.dismiss()
                Toast.makeText(requireContext(), "导入失败：${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun buildUnsafeOkHttpClient(): okhttp3.OkHttpClient {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, SecureRandom())
        return okhttp3.OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

}
