package com.wangyg.tvliveplayer.ui.settings

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.wangyg.tvliveplayer.R
import com.wangyg.tvliveplayer.domain.model.Channel
import com.wangyg.tvliveplayer.domain.repository.ChannelRepository
import com.wangyg.tvliveplayer.domain.usecase.ParseAndImportM3UUseCase
import com.wangyg.tvliveplayer.parser.M3UParser
import dagger.hilt.android.AndroidEntryPoint
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AddSourceFragment : Fragment() {

    @Inject lateinit var channelRepository: ChannelRepository
    @Inject lateinit var m3uParser: M3UParser

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_add_source, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.btn_url).setOnClickListener { showUrlDialog() }
        view.findViewById<TextView>(R.id.btn_manual).setOnClickListener { showManualDialog() }
        view.findViewById<TextView>(R.id.btn_paste).setOnClickListener { showPasteDialog() }

        view.findViewById<TextView>(R.id.btn_url).requestFocus()
    }

    private fun showUrlDialog() {
        val input = EditText(requireActivity())
        input.hint = "输入 M3U 链接地址"
        input.setText("https://raw.githubusercontent.com/YanG-1989/m3u/main/Gather.m3u")
        AlertDialog.Builder(requireActivity())
            .setTitle("在线链接解析")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotEmpty()) downloadAndParse(url)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showManualDialog() {
        val view = LayoutInflater.from(requireActivity()).inflate(R.layout.dialog_add_channel, null)
        val etName = view.findViewById<EditText>(R.id.et_channel_name)
        val etUrl = view.findViewById<EditText>(R.id.et_channel_url)
        val etCategory = view.findViewById<EditText>(R.id.et_channel_category)
        val etLogo = view.findViewById<EditText>(R.id.et_channel_logo)

        AlertDialog.Builder(requireActivity())
            .setTitle("手动添加频道")
            .setView(view)
            .setPositiveButton("添加") { _, _ ->
                val name = etName.text.toString().trim()
                val url = etUrl.text.toString().trim()
                if (name.isEmpty() || url.isEmpty()) {
                    Toast.makeText(requireContext(), "频道名称和URL不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    channelRepository.addChannels(listOf(
                        Channel(
                            id = java.util.UUID.randomUUID().toString(),
                            name = name,
                            url = url,
                            category = etCategory.text.toString().trim().ifEmpty { "自定义" },
                            logo = etLogo.text.toString().trim().ifEmpty { null }
                        )
                    ))
                    Toast.makeText(requireContext(), "添加成功", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showPasteDialog() {
        val input = EditText(requireActivity())
        input.hint = "粘贴 M3U 文本内容"
        input.minLines = 5
        AlertDialog.Builder(requireActivity())
            .setTitle("粘贴 M3U")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) {
                    lifecycleScope.launch {
                        val count = ParseAndImportM3UUseCase(m3uParser, channelRepository)(text, null)
                        Toast.makeText(requireContext(), "解析完成！共 $count 个频道", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun downloadAndParse(url: String) {
        lifecycleScope.launch {
            try {
                val client = buildUnsafeOkHttpClient()
                val request = okhttp3.Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@launch
                val count = ParseAndImportM3UUseCase(m3uParser, channelRepository)(body, null)
                Toast.makeText(requireContext(), "导入成功！共 $count 个频道", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
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
            .build()
    }
}
