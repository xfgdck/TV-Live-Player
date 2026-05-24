import React, { useState } from "react";
import { 
  X, 
  Plus, 
  Globe, 
  Trash2, 
  FileInput, 
  Download, 
  Upload, 
  Loader2, 
  Check, 
  AlertCircle, 
  Bookmark, 
  Shuffle, 
  Play,
  ChevronUp,
  ChevronDown,
  Pencil,
  List
} from "lucide-react";
import { Capacitor, CapacitorHttp } from "@capacitor/core";
import { DEFAULT_PLAYLIST_SOURCES } from "../data/defaultChannels";
import { CustomSource, TVChannel } from "../types";
import { parseM3uPlaylist } from "../utils/m3uParser";

interface CustomSourceModalProps {
  onAddChannels: (channels: TVChannel[], playlistName: string, playlistUrl: string) => void;
  onAddSingleChannel: (channel: TVChannel) => void;
  customSources: CustomSource[];
  onRemovePlaylist: (playlistUrl: string) => void;
  onClose: () => void;
  onExport: () => void;
  onImport: (e: React.ChangeEvent<HTMLInputElement>) => void;
  channels: TVChannel[];
  onDeleteCategory: (category: string) => void;
  onRenameCategory: (oldName: string, newName: string) => void;
  onDeleteChannel: (id: string) => void;
  onMoveChannelUp: (id: string) => void;
  onMoveChannelDown: (id: string) => void;
  onRenameChannel: (id: string, newName: string) => void;
  onFactoryReset: () => void;
}

export default function CustomSourceModal({
  onAddChannels,
  onAddSingleChannel,
  customSources,
  onRemovePlaylist,
  onClose,
  onExport,
  onImport,
  channels,
  onDeleteCategory,
  onRenameCategory,
  onDeleteChannel,
  onMoveChannelUp,
  onMoveChannelDown,
  onRenameChannel,
  onFactoryReset,
}: CustomSourceModalProps) {
  const [activeTab, setActiveTab] = useState<"m3u" | "single" | "channels" | "manage" | "backup">("m3u");
  const [m3uName, setM3uName] = useState<string>("");
  const [m3uUrl, setM3uUrl] = useState<string>("");
  const [isParsing, setIsParsing] = useState<boolean>(false);
  const [parseError, setParseError] = useState<string | null>(null);
  const [parseSuccessMsg, setParseSuccessMsg] = useState<string | null>(null);

  // Paste & upload M3U states
  const [pastedM3uText, setPastedM3uText] = useState<string>("");
  const [pastedM3uName, setPastedM3uName] = useState<string>("");

  // Single manual channel state
  const [singleName, setSingleName] = useState<string>("");
  const [singleUrl, setSingleUrl] = useState<string>("");
  const [singleCategory, setSingleCategory] = useState<string>("我的自定义");
  const [singleLogo, setSingleLogo] = useState<string>("");

  // Channel management search/filter
  const [chFilter, setChFilter] = useState<string>("");

  // Editing state for category rename
  const [editingCat, setEditingCat] = useState<string | null>(null);
  const [editingCatName, setEditingCatName] = useState<string>("");

  // Parse custom M3U playlist locally entirely (client-side)
  const handleParseM3u = async (urlToParse?: string, nameToParse?: string) => {
    const targetUrl = urlToParse || m3uUrl.trim();
    const targetName = nameToParse || m3uName.trim() || "自定义订阅源";

    if (!targetUrl) {
      setParseError("请输入 M3U 播放列表 URL 链接地址");
      return;
    }

    setIsParsing(true);
    setParseError(null);
    setParseSuccessMsg(null);

    try {
      let m3uText = "";
      
      // If we are running inside native app WebView, use Native HTTP (completely immune to CORS)
      if (Capacitor.isNativePlatform()) {
        console.log(`[Native-M3u] Fetching via CapacitorHttp: ${targetUrl}`);
        const response = await CapacitorHttp.get({ 
          url: targetUrl,
          connectTimeout: 10000,
          readTimeout: 15000
        });
        
        if (response.status === 200) {
          m3uText = typeof response.data === "string" 
            ? response.data 
            : JSON.stringify(response.data);
        } else {
          throw new Error(`获取订阅源失败 (HTTP ${response.status})，请检查链接地址是否正确。`);
        }
      } else {
        // Fallback for browser / development
        console.log(`[Browser-M3u] Fetching directly: ${targetUrl}`);
        try {
          const res = await fetch(targetUrl, { signal: AbortSignal.timeout(10000) });
          if (res.ok) {
            m3uText = await res.text();
          } else {
            throw new Error(`HTTP ${res.status}`);
          }
        } catch (directErr) {
          console.warn("[Browser-M3u] Direct fetch failed. Trying CORS proxy...", directErr);
          // Fallback to AllOrigins public CORS proxy
          const proxyUrl = `https://api.allorigins.win/raw?url=${encodeURIComponent(targetUrl)}`;
          const res = await fetch(proxyUrl, { signal: AbortSignal.timeout(12000) });
          if (res.ok) {
            m3uText = await res.text();
          } else {
            throw new Error("获取该订阅源失败。可能是该源链接已失效，或者服务器防盗链拦截。");
          }
        }
      }

      if (!m3uText || !m3uText.trim()) {
        throw new Error("获取到的播放源内容为空，请检查链接可用性");
      }

      const parsedChannels = parseM3uPlaylist(m3uText);

      if (!parsedChannels || parsedChannels.length === 0) {
        throw new Error("解析完毕，但未在该 M3U 文件中找到有效的频道（链接需以 http/https 开头，并包含 #EXTINF 信息）");
      }

      // Add actual channels
      onAddChannels(parsedChannels, targetName, targetUrl);
      setParseSuccessMsg(`✓ 成功解析并导入了 ${parsedChannels.length} 个电视频道！`);
      if (!urlToParse) {
        setM3uUrl("");
        setM3uName("");
      }
    } catch (err: any) {
      console.error(err);
      setParseError(err.message || "解析此订阅源时发生网络请求错误。您可以尝试‘导入本地文件’或‘直接粘贴文本’。");
    } finally {
      setIsParsing(false);
    }
  };

  // Parse pasted M3U text directly
  const handleParsePastedM3u = () => {
    setParseError(null);
    setParseSuccessMsg(null);

    const m3uText = pastedM3uText.trim();
    const targetName = pastedM3uName.trim() || "手贴订阅源";

    if (!m3uText) {
      setParseError("请先在下方输入框中粘贴 M3U 文本内容");
      return;
    }

    try {
      const parsedChannels = parseM3uPlaylist(m3uText);
      if (!parsedChannels || parsedChannels.length === 0) {
        throw new Error("未解析到任何有效频道，请确认粘贴的内容符合 EXTM3U 规范，包含 #EXTINF 和 直播 URL。");
      }

      // Virtual unique URL identifier
      const virtualUrl = `pasted_source_${Date.now()}`;
      onAddChannels(parsedChannels, targetName, virtualUrl);
      
      setParseSuccessMsg(`✓ 成功通过文本粘贴导入了 ${parsedChannels.length} 个电视频道！`);
      setPastedM3uText("");
      setPastedM3uName("");
    } catch (err: any) {
      setParseError(err.message || "解析文本内容失败。");
    }
  };

  // Parse chosen local .m3u file
  const handleParseLocalM3uFile = (e: React.ChangeEvent<HTMLInputElement>) => {
    setParseError(null);
    setParseSuccessMsg(null);

    const file = e.target.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = (event) => {
      try {
        const m3uText = event.target?.result as string;
        if (!m3uText || !m3uText.trim()) {
          throw new Error("所选的 M3U 文件内容为空！");
        }

        const parsedChannels = parseM3uPlaylist(m3uText);
        if (!parsedChannels || parsedChannels.length === 0) {
          throw new Error("该 M3U 文件的格式可能不正确，未能解析出有效的直播频道列表。");
        }

        const fileName = file.name.replace(/\.[^/.]+$/, ""); // strip extension
        const virtualUrl = `local_file_${Date.now()}_${file.name}`;
        onAddChannels(parsedChannels, fileName, virtualUrl);

        setParseSuccessMsg(`✓ 成功从本地文件 [${file.name}] 导入了 ${parsedChannels.length} 个电视频道！`);
      } catch (err: any) {
        setParseError(err.message || "读取或解析本地 M3U 文件出错。");
      }
    };
    reader.readAsText(file);
  };

  const handlePresetSelect = (preset: typeof DEFAULT_PLAYLIST_SOURCES[number]) => {
    setM3uName(preset.name);
    setM3uUrl(preset.url);
  };

  const handleAddSingle = (e: React.FormEvent) => {
    e.preventDefault();
    if (!singleName.trim() || !singleUrl.trim()) {
      alert("请填写频道名称和直播 URL 流链接！");
      return;
    }

    const newChannel: TVChannel = {
      id: `manual_${Date.now()}_${Math.floor(Math.random() * 1000)}`,
      name: singleName.trim(),
      url: singleUrl.trim(),
      category: singleCategory.trim() || "我的自定义",
      logo: singleLogo.trim() || undefined,
    };

    onAddSingleChannel(newChannel);
    setSingleName("");
    setSingleUrl("");
    setSingleLogo("");
    alert("手工频道添加成功！已放入类别：" + newChannel.category + " 中。");
  };

  // Category management
  const catMap = (() => {
    const m = new Map<string, number>();
    channels.forEach(c => { m.set(c.category, (m.get(c.category) || 0) + 1); });
    return Array.from(m.entries()).sort((a, b) => b[1] - a[1]);
  })();

  const handleRenameCategory = (oldName: string) => {
    setEditingCat(oldName);
    setEditingCatName(oldName);
  };

  const submitRenameCategory = () => {
    if (editingCat && editingCatName.trim() && editingCatName.trim() !== editingCat) {
      onRenameCategory(editingCat, editingCatName.trim());
    }
    setEditingCat(null);
    setEditingCatName("");
  };

  // Channel management
  const filteredChs = chFilter.trim()
    ? channels.filter(c => c.name.toLowerCase().includes(chFilter.toLowerCase()) || c.category.toLowerCase().includes(chFilter.toLowerCase()))
    : channels;

  const handleRenameChannel = (ch: TVChannel) => {
    const newName = prompt("重命名频道：", ch.name);
    if (newName && newName.trim() && newName.trim() !== ch.name) {
      onRenameChannel(ch.id, newName.trim());
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/75 backdrop-blur-md animate-fade-in">
      <div 
        onClick={(e) => e.stopPropagation()}
        className="relative w-full max-w-2xl bg-neutral-900 border border-neutral-800 rounded-3xl shadow-3xl text-white overflow-hidden flex flex-col max-h-[90vh] md:max-h-[80vh]"
      >
        {/* Header */}
        <div className="flex items-center justify-between p-6 border-b border-white/5 bg-[#0a0a0a]">
          <div className="flex items-center gap-3">
            <Globe className="w-6 h-6 text-amber-500" />
            <div>
              <h2 className="text-xl font-serif font-bold tracking-tight text-white">设置中心</h2>
              <p className="text-neutral-400 text-xs">直播源 · 频道管理 · 分类编辑 · 备份恢复</p>
            </div>
          </div>
          <button 
            id="btn_close_modal"
            onClick={onClose}
            className="p-2 hover:bg-neutral-800 text-neutral-400 hover:text-white rounded-full transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Tab Selection */}
        <div className="flex border-b border-neutral-800 bg-neutral-950/50 p-1 overflow-x-auto">
          <button
            id="tab_m3u"
            onClick={() => setActiveTab("m3u")}
            className={`flex-1 py-2.5 text-xs font-medium transition-all rounded-xl whitespace-nowrap ${
              activeTab === "m3u" ? "bg-neutral-800 text-white shadow-sm" : "text-neutral-400 hover:text-neutral-200"
            }`}
          >
            订阅 M3U
          </button>
          <button
            id="tab_single"
            onClick={() => setActiveTab("single")}
            className={`flex-1 py-2.5 text-xs font-medium transition-all rounded-xl whitespace-nowrap ${
              activeTab === "single" ? "bg-neutral-800 text-white shadow-sm" : "text-neutral-400 hover:text-neutral-200"
            }`}
          >
            手动添加
          </button>
          <button
            id="tab_channels"
            onClick={() => setActiveTab("channels")}
            className={`flex-1 py-2.5 text-xs font-medium transition-all rounded-xl whitespace-nowrap flex items-center justify-center gap-1 ${
              activeTab === "channels" ? "bg-neutral-800 text-white shadow-sm" : "text-neutral-400 hover:text-neutral-200"
            }`}
          >
            <List className="w-3 h-3" />
            频道管理
            <span className="bg-amber-500 text-neutral-950 text-[10px] px-1.5 py-0.5 rounded-full font-bold ml-1">
              {channels.length}
            </span>
          </button>
          <button
            id="tab_manage"
            onClick={() => setActiveTab("manage")}
            className={`flex-1 py-2.5 text-xs font-medium transition-all rounded-xl whitespace-nowrap ${
              activeTab === "manage" ? "bg-neutral-800 text-white shadow-sm" : "text-neutral-400 hover:text-neutral-200"
            }`}
          >
            订阅源与分类
          </button>
          <button
            id="tab_backup"
            onClick={() => setActiveTab("backup")}
            className={`flex-1 py-2.5 text-xs font-medium transition-all rounded-xl whitespace-nowrap ${
              activeTab === "backup" ? "bg-neutral-800 text-white shadow-sm" : "text-neutral-400 hover:text-neutral-200"
            }`}
          >
            备份恢复
          </button>
        </div>

        {/* Modal Scroll Content */}
        <div className="flex-1 overflow-y-auto p-6 space-y-6">
          
          {/* TAB 1: ADD M3U PLAYLIST */}
          {activeTab === "m3u" && (
            <div className="space-y-6">
              <div className="space-y-2">
                <label className="text-sm font-semibold text-neutral-300 block">
                  第一步：可以使用我们整理的公共直播源预设 (一键解析)
                </label>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                  {DEFAULT_PLAYLIST_SOURCES.map((preset) => (
                    <div 
                      key={preset.id}
                      className="p-3 bg-neutral-950 hover:bg-neutral-800/80 rounded-2xl border border-neutral-800 transition-all flex flex-col justify-between"
                    >
                      <div>
                        <span className="text-white text-xs font-bold font-sans block">
                          {preset.name}
                        </span>
                        <p className="text-neutral-400 text-[11px] leading-relaxed mt-1">
                          {preset.description}
                        </p>
                      </div>
                      <button
                        id={`btn_preset_${preset.id}`}
                        disabled={isParsing}
                        onClick={() => handleParseM3u(preset.url, preset.name)}
                        className="mt-3 py-1.5 px-3 bg-amber-600/15 hover:bg-amber-500 border border-amber-500/30 text-amber-400 hover:text-neutral-950 text-xs rounded-xl font-semibold transition-all self-start flex items-center gap-1.5 disabled:opacity-50"
                      >
                        {isParsing ? <Loader2 className="w-3 h-3 animate-spin" /> : <Plus className="w-3.5 h-3.5" />}
                        导入此预设源
                      </button>
                    </div>
                  ))}
                </div>
              </div>

              <div className="border-t border-neutral-800/50 my-6"></div>

              <div className="space-y-6">
                {/* Mode A: URL */}
                <div className="space-y-3 bg-neutral-950/50 p-4 rounded-2xl border border-white/5">
                  <span className="text-xs uppercase font-extrabold text-amber-500 tracking-wider">方法一：在线订阅链接</span>
                  <p className="text-neutral-400 text-xs leading-relaxed">
                    输入公网 M3U 订阅源链接，即可在线解析并导入最新的电视频道列表。
                  </p>
                  <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
                    <div className="md:col-span-1">
                      <input
                        id="input_m3u_name"
                        type="text"
                        placeholder="订阅源名称 (可选)"
                        value={m3uName}
                        onChange={(e) => setM3uName(e.target.value)}
                        className="w-full bg-neutral-900 border border-neutral-800 focus:border-amber-500 rounded-xl px-4 py-2.5 text-xs focus:outline-none placeholder:text-neutral-500 font-sans"
                      />
                    </div>
                    <div className="md:col-span-2">
                      <input
                        id="input_m3u_url"
                        type="url"
                        placeholder="复制并粘贴 M3U 链接 (例如 https://.../playlist.m3u)"
                        value={m3uUrl}
                        onChange={(e) => setM3uUrl(e.target.value)}
                        className="w-full bg-neutral-900 border border-neutral-800 focus:border-amber-500 rounded-xl px-4 py-2.5 text-xs focus:outline-none placeholder:text-neutral-500 font-mono"
                      />
                    </div>
                  </div>

                  <button
                    id="btn_submit_m3u"
                    disabled={isParsing}
                    onClick={() => handleParseM3u()}
                    className="w-full py-2 bg-amber-600 hover:bg-amber-500 disabled:bg-amber-800 disabled:opacity-50 text-neutral-950 font-bold rounded-xl text-xs transition-all flex items-center justify-center gap-2 shadow"
                  >
                    {isParsing ? (
                      <>
                        <Loader2 className="w-3.5 h-3.5 animate-spin" />
                        正在下载并本地解析频道列表中，请稍候...
                      </>
                    ) : (
                      <>
                        <Download className="w-3.5 h-3.5" />
                        一键下载并导入在线订阅源
                      </>
                    )}
                  </button>
                </div>

                {/* Mode B: Local File & Mode C: Raw Text Paste (2 columns on medium screens) */}
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  
                  {/* Mode B: Local M3U File */}
                  <div className="space-y-3 bg-neutral-950/50 p-4 rounded-2xl border border-white/5 flex flex-col justify-between">
                    <div>
                      <span className="text-xs uppercase font-extrabold text-amber-500 tracking-wider block mb-1">方法二：本地 M3U 文件导入</span>
                      <p className="text-neutral-400 text-[11px] leading-relaxed mb-3">
                        选择您的手机、电脑或电视本地存储的 <strong>.m3u / .m3u8</strong> 直播源文件，支持完全离线导入使用。
                      </p>
                    </div>
                    
                    <div>
                      <input
                        id="input_local_m3u_file"
                        type="file"
                        accept=".m3u,.m3u8,.txt"
                        onChange={handleParseLocalM3uFile}
                        className="hidden"
                      />
                      <label
                        htmlFor="input_local_m3u_file"
                        className="w-full py-2 bg-neutral-800 hover:bg-neutral-700 text-white rounded-xl text-xs transition-colors cursor-pointer flex items-center justify-center gap-1.5 font-bold border border-neutral-700 shadow text-center"
                      >
                        <Upload className="w-3.5 h-3.5 mr-1" />
                        选择本地直播源文件
                      </label>
                    </div>
                  </div>

                  {/* Mode C: Raw Text Paste */}
                  <div className="space-y-3 bg-neutral-950/50 p-4 rounded-2xl border border-white/5">
                    <span className="text-xs uppercase font-extrabold text-amber-500 tracking-wider">方法三：直接粘贴 M3U 文本</span>
                    <input
                      id="input_pasted_m3u_name"
                      type="text"
                      placeholder="临时源名称 (默认: 手贴订阅源)"
                      value={pastedM3uName}
                      onChange={(e) => setPastedM3uName(e.target.value)}
                      className="w-full bg-neutral-900 border border-neutral-800 focus:border-amber-500 rounded-xl px-3 py-2 text-[11px] focus:outline-none placeholder:text-neutral-500 font-sans"
                    />
                    <textarea
                      id="textarea_pasted_m3u"
                      rows={3}
                      placeholder="在此处直接粘贴您的 #EXTM3U 频道文本..."
                      value={pastedM3uText}
                      onChange={(e) => setPastedM3uText(e.target.value)}
                      className="w-full bg-neutral-900 border border-neutral-800 focus:border-amber-500 rounded-xl px-3 py-2 text-[10px] focus:outline-none placeholder:text-neutral-500 font-mono resize-none"
                    />
                    <button
                      id="btn_submit_pasted_m3u"
                      type="button"
                      onClick={handleParsePastedM3u}
                      className="w-full py-2 bg-neutral-800 hover:bg-neutral-750 text-white border border-neutral-700 hover:border-neutral-600 rounded-xl text-xs transition-all flex items-center justify-center gap-1.5 font-bold cursor-pointer"
                    >
                      <FileInput className="w-3.5 h-3.5" />
                      直接解析导入所贴文本
                    </button>
                  </div>

                </div>

                {parseError && (
                  <div className="p-4 bg-red-950/40 border border-red-900/50 text-red-200 text-xs rounded-2xl flex items-start gap-2 animate-shake">
                    <AlertCircle className="w-4 h-4 text-red-500 mt-0.5 shrink-0" />
                    <p className="leading-relaxed">
                      <strong>解析或加载失败:</strong> {parseError} <br />
                      提示：如果播放列表包含大量视频（&gt;5000），可能会耗费一些本地运算时间。
                    </p>
                  </div>
                )}

                {parseSuccessMsg && (
                  <div className="p-4 bg-emerald-950/40 border border-emerald-900/50 text-emerald-200 text-xs rounded-2xl flex items-start gap-2">
                    <Check className="w-4 h-4 text-emerald-500 mt-0.5 shrink-0" />
                    <p>{parseSuccessMsg}</p>
                  </div>
                )}
              </div>
            </div>
          )}

          {/* TAB 2: ADD SINGLE CHANNEL MANUALLY */}
          {activeTab === "single" && (
            <form onSubmit={handleAddSingle} className="space-y-4">
              <p className="text-neutral-400 text-xs mb-2">
                不需要复杂的 M3U 文件，直接输入你想添加的单个电视直播流地址即可。
              </p>
              
              <div className="space-y-3">
                <div>
                  <label className="text-xs text-neutral-400 block mb-1">频道名称 <span className="text-red-500">*</span></label>
                  <input
                    id="input_manual_name"
                    type="text"
                    required
                    placeholder="例如: 湖南卫视, 东方卫视, 游戏热播等"
                    value={singleName}
                    onChange={(e) => setSingleName(e.target.value)}
                    className="w-full bg-neutral-950 border border-neutral-800 focus:border-amber-500 rounded-xl px-4 py-2.5 text-sm focus:outline-none"
                  />
                </div>

                <div>
                  <label className="text-xs text-neutral-400 block mb-1">直播流链接 URL (m3u8 格式) <span className="text-red-500">*</span></label>
                  <input
                    id="input_manual_url"
                    type="url"
                    required
                    placeholder="https://.../live.m3u8"
                    value={singleUrl}
                    onChange={(e) => setSingleUrl(e.target.value)}
                    className="w-full bg-neutral-950 border border-neutral-800 focus:border-amber-500 rounded-xl px-4 py-2.5 text-sm focus:outline-none font-mono text-xs text-amber-200"
                  />
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="text-xs text-neutral-400 block mb-1">频道分类 (Group)</label>
                    <input
                      id="input_manual_category"
                      type="text"
                      placeholder="默认: 我的自定义"
                      value={singleCategory}
                      onChange={(e) => setSingleCategory(e.target.value)}
                      className="w-full bg-neutral-950 border border-neutral-800 focus:border-amber-500 rounded-xl px-4 py-2.5 text-sm focus:outline-none"
                    />
                  </div>
                  <div>
                    <label className="text-xs text-neutral-400 block mb-1">台标 Logo URL (可选)</label>
                    <input
                      id="input_manual_logo"
                      type="url"
                      placeholder="https://.../logo.png"
                      value={singleLogo}
                      onChange={(e) => setSingleLogo(e.target.value)}
                      className="w-full bg-neutral-950 border border-neutral-800 focus:border-amber-500 rounded-xl px-4 py-2.5 text-sm focus:outline-none font-mono text-xs"
                    />
                  </div>
                </div>
              </div>

              <div className="pt-4">
                <button
                  id="btn_submit_manual_channel"
                  type="submit"
                  className="w-full py-2.5 bg-amber-600 hover:bg-amber-500 text-neutral-950 font-bold rounded-xl text-sm transition-all flex items-center justify-center gap-2 shadow-lg"
                >
                  <Plus className="w-4 h-4" />
                  保存并加入频道库
                </button>
              </div>
            </form>
          )}

          {/* TAB 3: CHANNEL MANAGEMENT */}
          {activeTab === "channels" && (
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <h3 className="text-sm font-semibold text-neutral-300">
                  频道列表
                  <span className="text-neutral-500 text-xs ml-2">共 {channels.length} 个</span>
                </h3>
              </div>

              {/* Search filter */}
              <input
                type="text"
                placeholder="搜索频道名称或分类..."
                value={chFilter}
                onChange={(e) => setChFilter(e.target.value)}
                className="w-full bg-neutral-950 border border-neutral-800 focus:border-amber-500 rounded-xl px-4 py-2.5 text-xs focus:outline-none placeholder:text-neutral-500"
              />

              {filteredChs.length === 0 ? (
                <div className="text-center py-8 bg-neutral-950/50 rounded-2xl border border-neutral-800 border-dashed">
                  <Bookmark className="w-10 h-10 text-neutral-600 mx-auto mb-2" />
                  <p className="text-neutral-400 text-sm">暂无频道</p>
                </div>
              ) : (
                <div className="space-y-1">
                  {filteredChs.map((ch, i) => {
                    const idx = channels.findIndex(c => c.id === ch.id);
                    const canUp = idx > 0;
                    const canDown = idx >= 0 && idx < channels.length - 1;
                    return (
                      <div key={ch.id} className="flex items-center gap-2 px-3 py-2 bg-neutral-950 rounded-xl border border-neutral-800 hover:border-neutral-700 transition-colors">
                        {/* Logo / icon */}
                        {ch.logo
                          ? <img src={ch.logo} alt="" referrerPolicy="no-referrer" className="w-6 h-6 object-contain rounded shrink-0 bg-black/50" onError={e => { (e.target as HTMLElement).style.display = "none"; }} />
                          : <span className="text-sm shrink-0">📺</span>
                        }
                        
                        {/* Name + Category */}
                        <div className="min-w-0 flex-1">
                          <div className="text-xs font-medium text-white truncate">{ch.name}</div>
                          <div className="text-[10px] text-neutral-500 truncate">{ch.category}</div>
                        </div>

                        {/* Action buttons */}
                        <div className="flex items-center gap-0.5 shrink-0">
                          <button
                            onClick={() => handleRenameChannel(ch)}
                            className="p-1.5 text-neutral-400 hover:text-amber-400 hover:bg-amber-500/10 rounded-lg transition-colors"
                            title="重命名"
                          >
                            <Pencil className="w-3.5 h-3.5" />
                          </button>
                          <button
                            onClick={() => onMoveChannelUp(ch.id)}
                            disabled={!canUp}
                            className={`p-1.5 rounded-lg transition-colors ${canUp ? "text-neutral-400 hover:text-amber-400 hover:bg-amber-500/10" : "text-white/10"}`}
                            title="上移"
                          >
                            <ChevronUp className="w-3.5 h-3.5" />
                          </button>
                          <button
                            onClick={() => onMoveChannelDown(ch.id)}
                            disabled={!canDown}
                            className={`p-1.5 rounded-lg transition-colors ${canDown ? "text-neutral-400 hover:text-amber-400 hover:bg-amber-500/10" : "text-white/10"}`}
                            title="下移"
                          >
                            <ChevronDown className="w-3.5 h-3.5" />
                          </button>
                          <button
                            onClick={() => onDeleteChannel(ch.id)}
                            className="p-1.5 text-neutral-400 hover:text-red-400 hover:bg-red-500/10 rounded-lg transition-colors ml-0.5 pl-1.5 border-l border-white/10"
                            title="删除"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </button>
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          )}

          {/* TAB 4: SOURCE & CATEGORY MANAGEMENT */}
          {activeTab === "manage" && (
            <div className="space-y-6">
              {/* Sources */}
              <div className="space-y-3">
                <h3 className="text-sm font-semibold text-neutral-300">已订阅的播放源</h3>
                {customSources.length === 0 ? (
                  <div className="text-center py-8 bg-neutral-950/50 rounded-2xl border border-neutral-800 border-dashed">
                    <Bookmark className="w-10 h-10 text-neutral-600 mx-auto mb-2" />
                    <p className="text-neutral-400 text-sm">暂无自定义 M3U 源</p>
                  </div>
                ) : (
                  <div className="space-y-2">
                    {customSources.map((src) => (
                      <div key={src.id} className="p-4 bg-neutral-950 border border-neutral-800 rounded-2xl flex items-center justify-between">
                        <div className="space-y-1">
                          <div className="flex items-center gap-2">
                            <span className="font-semibold text-white text-sm">{src.name}</span>
                            <span className="text-[10px] bg-neutral-800 text-neutral-400 px-2 py-0.5 rounded-full font-mono">M3U</span>
                          </div>
                          <p className="text-xs text-neutral-500 truncate font-mono max-w-[400px]" title={src.url}>{src.url}</p>
                        </div>
                        <button onClick={() => onRemovePlaylist(src.url)}
                          className="p-2.5 text-neutral-400 hover:text-red-500 hover:bg-red-500/10 rounded-xl transition-all">
                          <Trash2 className="w-5 h-5" />
                        </button>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              {/* Categories */}
              <div className="space-y-3">
                <h3 className="text-sm font-semibold text-neutral-300">分类管理</h3>
                <div className="space-y-1.5">
                  {catMap.map(([cat, count]) => {
                    const isSpecial = cat === "全部" || cat === "收藏";
                    const isEditing = editingCat === cat;
                    return (
                      <div key={cat} className="flex items-center justify-between px-4 py-2.5 bg-neutral-950 rounded-xl border border-neutral-800">
                        <div className="flex items-center gap-3 min-w-0 flex-1">
                          <span className="text-base shrink-0">📂</span>
                          {isEditing ? (
                            <div className="flex items-center gap-2 flex-1 min-w-0">
                              <input
                                type="text"
                                value={editingCatName}
                                onChange={(e) => setEditingCatName(e.target.value)}
                                onKeyDown={(e) => { if (e.key === "Enter") submitRenameCategory(); if (e.key === "Escape") setEditingCat(null); }}
                                className="bg-neutral-900 border border-amber-500 rounded-lg px-2 py-1 text-sm text-white focus:outline-none min-w-0 flex-1"
                                autoFocus
                              />
                              <button onClick={submitRenameCategory} className="text-emerald-400 hover:text-emerald-300 text-xs px-2 py-1 bg-emerald-500/10 rounded">✓</button>
                              <button onClick={() => setEditingCat(null)} className="text-neutral-400 hover:text-neutral-300 text-xs px-2 py-1 bg-neutral-800 rounded">✕</button>
                            </div>
                          ) : (
                            <div className="min-w-0">
                              <span className="text-sm text-white font-medium">{cat}</span>
                              <span className="text-xs text-neutral-500 ml-2">{count} 个频道</span>
                            </div>
                          )}
                        </div>
                        {isSpecial ? (
                          <span className="text-[10px] text-neutral-600 bg-neutral-900 px-2 py-1 rounded shrink-0">系统</span>
                        ) : (
                          <div className="flex items-center gap-1 shrink-0">
                            <button onClick={() => handleRenameCategory(cat)}
                              className="text-xs text-amber-400 hover:text-amber-300 hover:bg-amber-500/10 px-2 py-1 rounded-lg transition-colors"
                              title="重命名">
                              <Pencil className="w-3 h-3" />
                            </button>
                            <button onClick={() => onDeleteCategory(cat)}
                              className="text-xs text-red-400 hover:text-red-300 hover:bg-red-500/10 px-2 py-1 rounded-lg transition-colors"
                              title="删除">
                              <Trash2 className="w-3 h-3" />
                            </button>
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              </div>

              {/* Factory Reset */}
              <div className="border-t border-neutral-800 pt-4">
                <button onClick={onFactoryReset}
                  className="w-full py-3 bg-red-950/40 hover:bg-red-900/40 border border-red-900/50 text-red-400 hover:text-red-300 rounded-xl text-sm font-bold transition-all">
                  🔄 恢复出厂设置
                </button>
                <p className="text-neutral-500 text-[10px] mt-2 text-center">
                  将清除所有自定义频道、订阅源和收藏，恢复为初始频道列表。此操作不可撤销。
                </p>
              </div>
            </div>
          )}

          {/* TAB 5: BACKUP & RESTORE */}
          {activeTab === "backup" && (
            <div className="space-y-6">
              <div className="space-y-2">
                <h3 className="text-sm font-semibold text-neutral-300">本地频道源备份导出 / 导入</h3>
                <p className="text-neutral-400 text-xs leading-relaxed">
                  所有的自定义数据均保存在您当前浏览器的 LocalStorage 中。为了防止数据清除或方便在多设备（例如：电视盒子与手机浏览器）之间共享：
                </p>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4 pt-2">
                <div className="p-4 bg-[#0a0a0a] border border-white/5 rounded-2xl space-y-3">
                  <h4 className="font-semibold text-sm flex items-center gap-2 text-white">
                    <Download className="w-4 h-4 text-amber-500" />
                    备份导出成文件
                  </h4>
                  <p className="text-neutral-500 text-[11px] leading-relaxed">
                    将您当前所有的本地自定义频道、喜欢的收藏电视频道备份，生成一个 JSON 文件下载保存。
                  </p>
                  <button
                    id="btn_export_all_json"
                    onClick={onExport}
                    className="w-full py-2 bg-neutral-800 hover:bg-neutral-700 text-white rounded-xl text-xs transition-colors flex items-center justify-center gap-1.5 font-medium border border-neutral-700 shadow"
                  >
                    下载备份文件 (JSON)
                  </button>
                </div>

                <div className="p-4 bg-neutral-950 border border-neutral-800 rounded-2xl space-y-3">
                  <h4 className="font-semibold text-sm flex items-center gap-2">
                    <Upload className="w-4 h-4 text-emerald-500" />
                    恢复导入文件
                  </h4>
                  <p className="text-neutral-500 text-[11px] leading-relaxed">
                    选择您之前导出的 JSON 备份文件，还原您以前保存的频道和收藏列表。此操作不会破坏您原先的数据。
                  </p>
                  
                  <div className="relative">
                    <input
                      id="input_restore_file"
                      type="file"
                      accept=".json"
                      onChange={onImport}
                      className="hidden"
                    />
                    <label
                      id="label_restore_file"
                      htmlFor="input_restore_file"
                      className="w-full py-2 bg-neutral-800 hover:bg-neutral-700 text-white rounded-xl text-xs transition-colors cursor-pointer flex items-center justify-center gap-1.5 font-medium border border-neutral-700 shadow text-center"
                    >
                      选择备份文件并恢复
                    </label>
                  </div>
                </div>
              </div>
            </div>
          )}

        </div>

        {/* Footer */}
        <div className="p-4 bg-neutral-950 border-t border-neutral-850 flex items-center justify-between text-neutral-500 text-[11px] font-mono">
          <span>按 Back 键关闭设置</span>
          <span className="text-neutral-400">{channels.length} 个频道 · {catMap.length} 个分类</span>
        </div>
      </div>
    </div>
  );
}
