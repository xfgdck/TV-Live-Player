package top.xiaofeigun.tvliveplayer.util

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class QrScanResult(
    val type: String, // "m3u_url", "channel", "file"
    val url: String = "",
    val name: String = "",
    val category: String = "",
    val logo: String = "",
    val fileName: String = "",
    val fileContent: String = ""
)

class QrCodeServer(private val port: Int = 0) {

    private var serverSocket: ServerSocket? = null
    var localUrl: String = ""
        private set

    fun start(): Boolean {
        try {
            val ip = getLocalIpAddress() ?: return false
            serverSocket = ServerSocket(port)
            localUrl = "http://${ip}:${serverSocket!!.localPort}"
            return true
        } catch (e: Exception) {
            return false
        }
    }

    suspend fun waitForUrl(): QrScanResult? = withContext(Dispatchers.IO) {
        val socket = serverSocket ?: return@withContext null
        try {
            while (!socket.isClosed) {
                val client = socket.accept()
                val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
                val requestLine = reader.readLine() ?: continue
                val parts = requestLine.split(" ")
                val method = parts.getOrElse(0) { "" }
                val path = parts.getOrElse(1) { "" }

                var contentLength = 0
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val header = line!!
                    if (header.isBlank()) break
                    if (header.startsWith("Content-Length:", ignoreCase = true))
                        contentLength = header.substringAfter(":").trim().toIntOrNull() ?: 0
                }

                if (method == "POST" && path == "/submit") {
                    val body = if (contentLength > 0) {
                        val chars = CharArray(contentLength)
                        val totalRead = reader.read(chars)
                        if (totalRead <= 0) "" else String(chars, 0, totalRead)
                    } else {
                        ""
                    }
                    val out = OutputStreamWriter(client.getOutputStream())
                    val result = parseJson(body)
                    if (result != null) {
                        out.write("HTTP/1.1 200 OK\r\nContent-Type: text/plain; charset=utf-8\r\nConnection: close\r\n\r\n\u63d0\u4ea4\u6210\u529f\uff0c\u53ef\u4ee5\u5173\u95ed\u6b64\u9875\u9762")
                        out.flush()
                        client.close()
                        stop()
                        return@withContext result
                    } else {
                        out.write("HTTP/1.1 400 Bad Request\r\nContent-Type: text/plain; charset=utf-8\r\nConnection: close\r\n\r\n\u6570\u636e\u683c\u5f0f\u65e0\u6548")
                        out.flush()
                        client.close()
                    }
                } else {
                    val html = HTML_PAGE
                    val out = OutputStreamWriter(client.getOutputStream())
                    out.write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${html.toByteArray().size}\r\nConnection: close\r\n\r\n$html")
                    out.flush()
                    client.close()
                }
            }
        } catch (_: Exception) {}
        return@withContext null
    }

    fun stop() {
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
    }

    private fun parseJson(body: String): QrScanResult? {
        return try {
            val root = com.google.gson.JsonParser.parseString(body).asJsonObject
            val type = root.get("type")?.asString ?: return null
            when (type) {
                "m3u_url" -> QrScanResult(type = type, url = root.get("url")?.asString ?: "")
                "channel" -> QrScanResult(type = type,
                    url = root.get("url")?.asString ?: "",
                    name = root.get("name")?.asString ?: "",
                    category = root.get("category")?.asString ?: "",
                    logo = root.get("logo")?.asString ?: "")
                "file" -> QrScanResult(type = type,
                    fileName = root.get("name")?.asString ?: "",
                    fileContent = root.get("content")?.asString ?: "")
                else -> null
            }
        } catch (_: Exception) { null }
    }

    private fun getLocalIpAddress(): String? {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
        while (interfaces.hasMoreElements()) {
            val iface = interfaces.nextElement()
            if (iface.isLoopback || !iface.isUp) continue
            val addresses = iface.inetAddresses
            while (addresses.hasMoreElements()) {
                val addr = addresses.nextElement()
                if (!addr.isLoopbackAddress && addr is Inet4Address)
                    return addr.hostAddress
            }
        }
        return null
    }

    companion object {
        private val HTML_PAGE = """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta charset="utf-8">
<style>
* { box-sizing: border-box; }
body { font-family: sans-serif; padding: 16px; background: #1a1a2e; color: #fff; margin: 0; }
.container { max-width: 420px; margin: 20px auto; }
h2 { color: #4a90d9; text-align: center; margin-bottom: 20px; }
.tabs { display: flex; gap: 4px; margin-bottom: 16px; }
.tab { flex: 1; padding: 10px; text-align: center; background: #0f3460; color: #888; border: none; border-radius: 6px 6px 0 0; font-size: 14px; cursor: pointer; }
.tab.active { background: #4a90d9; color: #fff; }
.tab-content { display: none; }
.tab-content.active { display: block; }
input, textarea { width: 100%; padding: 12px; margin: 8px 0; border-radius: 6px; border: none; background: #0f3460; color: #fff; font-size: 15px; }
input::placeholder, textarea::placeholder { color: #666; }
textarea { resize: vertical; min-height: 120px; font-family: monospace; }
button { width: 100%; padding: 14px; background: #4a90d9; color: #fff; border: none; border-radius: 6px; font-size: 16px; cursor: pointer; margin-top: 8px; }
button:hover { background: #2b579a; }
button:disabled { opacity: 0.6; }
.file-label { display: block; padding: 24px; text-align: center; background: #0f3460; border-radius: 6px; margin: 8px 0; cursor: pointer; border: 2px dashed #4a90d9; color: #aaa; }
.file-label.has-file { border-color: #2ecc71; color: #2ecc71; }
#status { margin-top: 12px; padding: 12px; border-radius: 6px; display: none; text-align: center; }
#status.success { display: block; background: #2ecc71; }
#status.error { display: block; background: #e74c3c; }
</style>
</head>
<body>
<div class="container">
<h2>TV Player</h2>
<div class="tabs">
<button class="tab active" onclick="switchTab('m3u')">M3U链接</button>
<button class="tab" onclick="switchTab('channel')">单个频道</button>
<button class="tab" onclick="switchTab('file')">文件上传</button>
</div>

<div id="tab_m3u" class="tab-content active">
<p style="color:#aaa;margin:4px 0;">输入 M3U 直播源链接地址</p>
<input type="url" id="m3uUrl" placeholder="https://example.com/playlist.m3u" autofocus />
<button onclick="submitM3U()">提交</button>
</div>

<div id="tab_channel" class="tab-content">
<p style="color:#aaa;margin:4px 0;">输入单个频道信息</p>
<input type="text" id="chName" placeholder="频道名称 *" />
<input type="url" id="chUrl" placeholder="频道播放地址 *" />
<input type="text" id="chCategory" placeholder="分类（可选，默认 自定义）" />
<input type="url" id="chLogo" placeholder="图标链接（可选）" />
<button onclick="submitChannel()">提交</button>
</div>

<div id="tab_file" class="tab-content">
<p style="color:#aaa;margin:4px 0;">从手机上传 M3U 文件</p>
<label class="file-label" id="fileLabel" onclick="document.getElementById('fileInput').click()">
点击选择文件
</label>
<input type="file" id="fileInput" accept=".m3u,.txt,.m3u8,*" style="display:none" onchange="onFileSelect(event)" />
<button id="fileSubmitBtn" onclick="submitFile()" disabled>上传并提交</button>
</div>

<div id="status"></div>
</div>
<script>
var selectedFile = null;
function switchTab(name) {
    document.querySelectorAll('.tab').forEach(function(t) { t.className = 'tab'; });
    document.querySelectorAll('.tab-content').forEach(function(c) { c.className = 'tab-content'; });
    document.querySelector('.tab[onclick*="'+name+'"]').className = 'tab active';
    document.getElementById('tab_'+name).className = 'tab-content active';
    hideStatus();
}
function hideStatus() { document.getElementById('status').style.display = 'none'; }
function showStatus(cls, msg) { var s=document.getElementById('status'); s.className=cls; s.textContent=msg; s.style.display='block'; }
function disableAll() { document.querySelectorAll('button').forEach(function(b){b.disabled=true;}); }
function submitM3U() {
    var url = document.getElementById('m3uUrl').value.trim();
    if (!url) { alert('请输入链接地址'); return; }
    disableAll(); showStatus('success','提交中...');
    fetch('/submit', { method:'POST', body:JSON.stringify({type:'m3u_url',url:url}) })
    .then(function(r){return r.text();}).then(function(m){showStatus('success',m);})
    .catch(function(){showStatus('error','提交失败');});
}
function submitChannel() {
    var name = document.getElementById('chName').value.trim();
    var url = document.getElementById('chUrl').value.trim();
    if (!name || !url) { alert('频道名称和播放地址不能为空'); return; }
    disableAll(); showStatus('success','提交中...');
    fetch('/submit', { method:'POST', body:JSON.stringify({type:'channel',name:name,url:url,
        category:document.getElementById('chCategory').value.trim(),
        logo:document.getElementById('chLogo').value.trim()}) })
    .then(function(r){return r.text();}).then(function(m){showStatus('success',m);})
    .catch(function(){showStatus('error','提交失败');});
}
function onFileSelect(e) {
    selectedFile = e.target.files[0];
    if (selectedFile) {
        document.getElementById('fileLabel').textContent = '\u2705 '+selectedFile.name;
        document.getElementById('fileLabel').className = 'file-label has-file';
        document.getElementById('fileSubmitBtn').disabled = false;
    }
}
function submitFile() {
    if (!selectedFile) { alert('请先选择文件'); return; }
    disableAll(); showStatus('success','上传中...');
    var reader = new FileReader();
    reader.onload = function() {
        fetch('/submit', { method:'POST', body:JSON.stringify({type:'file',name:selectedFile.name,content:reader.result}) })
        .then(function(r){return r.text();}).then(function(m){showStatus('success',m);})
        .catch(function(){showStatus('error','上传失败');});
    };
    reader.readAsText(selectedFile);
}
</script>
</body>
</html>
""".trimIndent()
    }
}
