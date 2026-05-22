import React, { useState, useEffect, useMemo, useRef } from "react";
import { 
  Tv, 
  Heart, 
  Search, 
  Settings, 
  Plus, 
  Smartphone, 
  Sliders, 
  Volume2, 
  VolumeX, 
  Info, 
  ArrowLeft, 
  Sparkles, 
  Trash2, 
  X,
  Radio,
  BookOpen,
  HelpCircle,
  FileCheck
} from "lucide-react";

import { TVChannel, TVCategory, CustomSource } from "./types";
import { INITIAL_DEFAULT_CHANNELS } from "./data/defaultChannels";
import TVPlayer from "./components/TVPlayer";
import CustomSourceModal from "./components/CustomSourceModal";
import TVRemoteWidget from "./components/TVRemoteWidget";

export default function App() {
  // UI & Platform States
  const [viewMode, setViewMode] = useState<"tv" | "mobile">("tv"); // Default to TV mode as it is exciting and unique
  const [isSystemMuted, setIsSystemMuted] = useState<boolean>(false);
  const [systemVolume, setSystemVolume] = useState<number>(85);
  const [aspectRatio, setAspectRatio] = useState<"16-9" | "4-3" | "fill" | "contain">("16-9");
  
  // Channels database states
  const [channels, setChannels] = useState<TVChannel[]>([]);
  const [favorites, setFavorites] = useState<string[]>([]);
  const [customSources, setCustomSources] = useState<CustomSource[]>([]);
  
  // Selection and Filter list states
  const [selectedChannel, setSelectedChannel] = useState<TVChannel | null>(null);
  const [selectedCategory, setSelectedCategory] = useState<string>("全部");
  const [searchQuery, setSearchQuery] = useState<string>("");
  
  // Focus States for TV Mode Navigation
  // "categories" = focus left list, "channels" = focus right grid, "player" = focus main player controller
  const [focusedArea, setFocusedArea] = useState<"categories" | "channels" | "player">("channels");
  const [focusedCategoryIdx, setFocusedCategoryIdx] = useState<number>(0);
  const [focusedChannelIdx, setFocusedChannelIdx] = useState<number>(0);

  // Modals / Overlays
  const [isSourceModalOpen, setIsSourceModalOpen] = useState<boolean>(false);
  const [isRemoteWidgetOpen, setIsRemoteWidgetOpen] = useState<boolean>(false);
  const [showWelcomeTip, setShowWelcomeTip] = useState<boolean>(true);

  // Load state from LocalStorage on mount
  useEffect(() => {
    // 1. Load Custom M3U playlists
    const storedSources = localStorage.getItem("tv_custom_playlist_sources");
    let loadedSources: CustomSource[] = [];
    if (storedSources) {
      try {
        loadedSources = JSON.parse(storedSources);
        setCustomSources(loadedSources);
      } catch (e) {
        console.error("Error parsing sources", e);
      }
    }

    // 2. Load Custom channels (either parsed from M3u or manually typed)
    const storedCustomChannels = localStorage.getItem("tv_custom_channels");
    let loadedCustomChannels: TVChannel[] = [];
    if (storedCustomChannels) {
      try {
        loadedCustomChannels = JSON.parse(storedCustomChannels);
      } catch (e) {
        console.error("Error parsing custom channels", e);
      }
    }

    // Integrate channels
    const totalChannels = [...INITIAL_DEFAULT_CHANNELS, ...loadedCustomChannels];
    setChannels(totalChannels);

    // Default select first channel if any
    if (totalChannels.length > 0) {
      setSelectedChannel(totalChannels[0]);
    }

    // 3. Load Favorites
    const storedFavs = localStorage.getItem("tv_favorites");
    if (storedFavs) {
      try {
        setFavorites(JSON.parse(storedFavs));
      } catch (e) {
        console.error("Error parsing favorites", e);
      }
    }

    // 4. Load View Mode
    const storedViewMode = localStorage.getItem("tv_view_mode");
    if (storedViewMode === "tv" || storedViewMode === "mobile") {
      setViewMode(storedViewMode);
    }

    // Auto close welcome tip after 8 seconds
    const timer = setTimeout(() => {
      setShowWelcomeTip(false);
    }, 10000);
    return () => clearTimeout(timer);
  }, []);

  // Save changes to LocalStorage
  const saveCustomChannelsToLocalStorage = (newCustomChannels: TVChannel[]) => {
    localStorage.setItem("tv_custom_channels", JSON.stringify(newCustomChannels));
  };

  const saveSourcesToLocalStorage = (newSources: CustomSource[]) => {
    localStorage.setItem("tv_custom_playlist_sources", JSON.stringify(newSources));
  };

  // Extract Categories uniquely
  const categoriesList = useMemo(() => {
    const listSet = new Set<string>();
    
    // Check if there are favorites to show the category
    if (favorites.length > 0) {
      listSet.add("我的收藏");
    }

    listSet.add("全部");

    channels.forEach((c) => {
      if (c.category) {
        listSet.add(c.category);
      }
    });

    return Array.from(listSet);
  }, [channels, favorites]);

  // Handle fallback if active category is deleted
  useEffect(() => {
    if (!categoriesList.includes(selectedCategory)) {
      setSelectedCategory("全部");
    }
  }, [categoriesList, selectedCategory]);

  // Filter channels based in Selected Category & Search Query
  const filteredChannels = useMemo(() => {
    return channels.filter((c) => {
      // Category filter
      if (selectedCategory === "我的收藏") {
        if (!favorites.includes(c.id)) return false;
      } else if (selectedCategory !== "全部") {
        if (c.category !== selectedCategory) return false;
      }

      // Search match
      if (searchQuery.trim()) {
        const query = searchQuery.toLowerCase();
        const nameMatch = c.name.toLowerCase().includes(query);
        const catMatch = c.category.toLowerCase().includes(query);
        return nameMatch || catMatch;
      }

      return true;
    });
  }, [channels, selectedCategory, favorites, searchQuery]);

  // Manage Favorites
  const toggleFavorite = (channelId: string) => {
    let nextFavs: string[];
    if (favorites.includes(channelId)) {
      nextFavs = favorites.filter((id) => id !== channelId);
    } else {
      nextFavs = [...favorites, channelId];
    }
    setFavorites(nextFavs);
    localStorage.setItem("tv_favorites", JSON.stringify(nextFavs));
  };

  // Add parsed channels from M3U Source
  const handleAddChannels = (newChannels: TVChannel[], playlistName: string, playlistUrl: string) => {
    // Check if source already exists
    if (customSources.some(src => src.url === playlistUrl)) {
      alert("此 M3U 订阅源已存在，我们将刷新并导入最新频道数据。");
      // Remove old channels of this URL first
      handleRemovePlaylist(playlistUrl);
    }

    // Process new custom source record
    const newSourceRecord: CustomSource = {
      id: `source_${Date.now()}`,
      name: playlistName,
      url: playlistUrl,
      isActive: true,
      createdAt: Date.now()
    };

    const updatedSources = [...customSources, newSourceRecord];
    setCustomSources(updatedSources);
    saveSourcesToLocalStorage(updatedSources);

    // Tag channels so we know they belong to this source (convenient for mass deletion)
    const taggedChannels = newChannels.map(c => ({
      ...c,
      id: `${newSourceRecord.id}_${c.id}`,
      category: c.category || playlistName // Use playlist name if category is empty
    }));

    // Find previous custom channels (i.e. not in INITIAL_DEFAULT_CHANNELS)
    const currentCustomChannels = channels.filter(c => !INITIAL_DEFAULT_CHANNELS.some(d => d.id === c.id));
    const nextCustomChannels = [...currentCustomChannels, ...taggedChannels];
    
    saveCustomChannelsToLocalStorage(nextCustomChannels);
    
    const combined = [...INITIAL_DEFAULT_CHANNELS, ...nextCustomChannels];
    setChannels(combined);

    // Focus first parsed channel
    if (taggedChannels.length > 0) {
      setSelectedChannel(taggedChannels[0]);
    }
  };

  // Add individual channel manually
  const handleAddSingleChannel = (newChannel: TVChannel) => {
    const currentCustomChannels = channels.filter(c => !INITIAL_DEFAULT_CHANNELS.some(d => d.id === c.id));
    const nextCustomChannels = [...currentCustomChannels, newChannel];
    
    saveCustomChannelsToLocalStorage(nextCustomChannels);

    const combined = [...INITIAL_DEFAULT_CHANNELS, ...nextCustomChannels];
    setChannels(combined);
    setSelectedChannel(newChannel);
  };

  // Remove elements linked to a custom playlist URL
  const handleRemovePlaylist = (playlistUrl: string) => {
    const sourceRecord = customSources.find(src => src.url === playlistUrl);
    if (!sourceRecord) return;

    // Remove source record
    const updatedSources = customSources.filter(src => src.url !== playlistUrl);
    setCustomSources(updatedSources);
    saveSourcesToLocalStorage(updatedSources);

    // Remove all channels belonging to this source's ID prefix
    const currentCustomChannels = channels.filter(c => !INITIAL_DEFAULT_CHANNELS.some(d => d.id === c.id));
    const nextCustomChannels = currentCustomChannels.filter(c => !c.id.startsWith(sourceRecord.id));
    
    saveCustomChannelsToLocalStorage(nextCustomChannels);

    const combined = [...INITIAL_DEFAULT_CHANNELS, ...nextCustomChannels];
    setChannels(combined);

    if (selectedChannel && selectedChannel.id.startsWith(sourceRecord.id)) {
      setSelectedChannel(combined[0] || null);
    }
  };

  // Delete all custom TV channel data completely
  const handleClearAllCustomData = () => {
    if (window.confirm("确定清除所有自定义直播源、添加的单台和您的收藏记录吗？此操作不可撤销。")) {
      localStorage.removeItem("tv_custom_playlist_sources");
      localStorage.removeItem("tv_custom_channels");
      localStorage.removeItem("tv_favorites");
      setCustomSources([]);
      setFavorites([]);
      setChannels(INITIAL_DEFAULT_CHANNELS);
      setSelectedChannel(INITIAL_DEFAULT_CHANNELS[0] || null);
      setSelectedCategory("全部");
      alert("已重置为系统内置出厂频道列表。");
    }
  };

  // State file backup Export / Import triggers
  const handleExportState = () => {
    const backupData = {
      sources: customSources,
      customChannels: channels.filter(c => !INITIAL_DEFAULT_CHANNELS.some(d => d.id === c.id)),
      favs: favorites,
    };
    const blob = new Blob([JSON.stringify(backupData, null, 2)], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `tv-live-player-backup-${new Date().toISOString().slice(0, 10)}.json`;
    link.click();
    URL.revokeObjectURL(url);
  };

  const handleImportState = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = (event) => {
      try {
        const parsed = JSON.parse(event.target?.result as string);
        if (parsed) {
          if (Array.isArray(parsed.sources)) {
            setCustomSources(parsed.sources);
            saveSourcesToLocalStorage(parsed.sources);
          }
          if (Array.isArray(parsed.favs)) {
            setFavorites(parsed.favs);
            localStorage.setItem("tv_favorites", JSON.stringify(parsed.favs));
          }
          if (Array.isArray(parsed.customChannels)) {
            saveCustomChannelsToLocalStorage(parsed.customChannels);
            const combined = [...INITIAL_DEFAULT_CHANNELS, ...parsed.customChannels];
            setChannels(combined);
            if (combined.length > 0) setSelectedChannel(combined[0]);
          }
          alert("✓ 备份恢复导入成功！");
        }
      } catch (err) {
        alert("导入失败：无效的 JSON 格式文件。");
      }
    };
    reader.readAsText(file);
  };

  // ==========================================
  // HARDWARE REMOTES / KEYBOARD NAVIGATION D-PAD LOGIC
  // ==========================================
  const executeRemoteAction = (actionKey: string) => {
    // 1. Handles adjustments
    if (actionKey === "VolumeUp") {
      setSystemVolume((prev) => Math.min(100, prev + 5));
      setIsSystemMuted(false);
      return;
    }
    if (actionKey === "VolumeDown") {
      setSystemVolume((prev) => Math.max(0, prev - 5));
      return;
    }
    if (actionKey === "Backspace") {
      // Exit modal or reset sidebar focus
      if (isSourceModalOpen) {
        setIsSourceModalOpen(false);
      } else {
        setFocusedArea("categories");
      }
      return;
    }

    // 2. Navigation when looking at Categories list (Left column)
    if (focusedArea === "categories") {
      if (actionKey === "ArrowUp") {
        setFocusedCategoryIdx((prev) => {
          const nextIdx = prev > 0 ? prev - 1 : categoriesList.length - 1;
          setSelectedCategory(categoriesList[nextIdx]);
          return nextIdx;
        });
      } else if (actionKey === "ArrowDown") {
        setFocusedCategoryIdx((prev) => {
          const nextIdx = prev < categoriesList.length - 1 ? prev + 1 : 0;
          setSelectedCategory(categoriesList[nextIdx]);
          return nextIdx;
        });
      } else if (actionKey === "ArrowRight") {
        // Move focus onto the channels grid
        if (filteredChannels.length > 0) {
          setFocusedArea("channels");
          setFocusedChannelIdx(0);
        }
      } else if (actionKey === "Enter") {
        // Confirm select category
        setSelectedCategory(categoriesList[focusedCategoryIdx]);
        if (filteredChannels.length > 0) {
          setFocusedArea("channels");
          setFocusedChannelIdx(0);
        }
      }
    } 

    // 3. Navigation when looking at Channels Grid (Right area)
    else if (focusedArea === "channels") {
      // Calculate layout dimension (e.g. 3 columns on desktop, 1 on mobile)
      const cols = viewMode === "tv" ? 3 : 2;
      const totalLen = filteredChannels.length;

      if (actionKey === "ArrowUp") {
        setFocusedChannelIdx((prev) => (prev >= cols ? prev - cols : prev));
      } else if (actionKey === "ArrowDown") {
        setFocusedChannelIdx((prev) => (prev + cols < totalLen ? prev + cols : prev));
      } else if (actionKey === "ArrowLeft") {
        // If they are on the leftmost column, trigger fallback selection list on left side!
        if (focusedChannelIdx % cols === 0) {
          setFocusedArea("categories");
        } else {
          setFocusedChannelIdx((prev) => prev - 1);
        }
      } else if (actionKey === "ArrowRight") {
        if (focusedChannelIdx % cols === cols - 1 || focusedChannelIdx === totalLen - 1) {
          // Wrap or hold
        } else {
          setFocusedChannelIdx((prev) => prev + 1);
        }
      } else if (actionKey === "Enter") {
        // Play the channel
        const chan = filteredChannels[focusedChannelIdx];
        if (chan) {
          setSelectedChannel(chan);
        }
      }
    }
  };

  // Keyboard Event Handlers bind
  useEffect(() => {
    const handlePhysicalKeyDown = (e: KeyboardEvent) => {
      // Prevent scrolling on arrow keys inside web application to ensure pristine TV experience
      const keysToPrevent = ["ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight", "Space"];
      if (keysToPrevent.includes(e.key) && !isSourceModalOpen && document.activeElement?.tagName !== "INPUT") {
        e.preventDefault();
      }

      if (document.activeElement?.tagName === "INPUT") {
        // Let them type search or input URL without intercepting basic keys
        return;
      }

      switch (e.key) {
        case "ArrowUp":
          executeRemoteAction("ArrowUp");
          break;
        case "ArrowDown":
          executeRemoteAction("ArrowDown");
          break;
        case "ArrowLeft":
          executeRemoteAction("ArrowLeft");
          break;
        case "ArrowRight":
          executeRemoteAction("ArrowRight");
          break;
        case "Enter":
        case " ":
          executeRemoteAction("Enter");
          break;
        case "Backspace":
        case "Escape":
          executeRemoteAction("Backspace");
          break;
        default:
          break;
      }
    };

    window.addEventListener("keydown", handlePhysicalKeyDown);
    return () => window.removeEventListener("keydown", handlePhysicalKeyDown);
  }, [focusedArea, focusedCategoryIdx, focusedChannelIdx, categoriesList, filteredChannels, isSourceModalOpen]);

  // Handle auto channel index tracking when changing Categories
  useEffect(() => {
    // Keep focused index within bounds
    if (focusedChannelIdx >= filteredChannels.length) {
      setFocusedChannelIdx(Math.max(0, filteredChannels.length - 1));
    }
  }, [filteredChannels]);

  // Helper routine to switch channel sequentially (next/prev channel) on Stream Controller
  const playNextChannel = () => {
    if (channels.length === 0) return;
    const currentIdx = selectedChannel ? channels.findIndex(c => c.id === selectedChannel.id) : -1;
    const nextIdx = currentIdx < channels.length - 1 ? currentIdx + 1 : 0;
    setSelectedChannel(channels[nextIdx]);
  };

  const toggleViewMode = (mode: "tv" | "mobile") => {
    setViewMode(mode);
    localStorage.setItem("tv_view_mode", mode);
  };

  return (
    <div className="min-h-screen bg-[#050505] text-[#eaeaea] font-sans overflow-x-hidden pb-10 flex flex-col justify-between selection:bg-amber-600 selection:text-neutral-950">
      
      {/* Top Universal Header Bar */}
      <header className="border-b border-white/5 bg-[#080808]/90 backdrop-blur-md sticky top-0 z-30 px-4 md:px-8 py-4 shadow-xl shadow-black/30">
        <div className="max-w-7xl mx-auto flex flex-col sm:flex-row items-center justify-between gap-4">
          
          {/* Logo Name */}
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 bg-gradient-to-tr from-amber-600 to-amber-500 rounded-2xl flex items-center justify-center shadow-lg shadow-amber-500/10 animate-pulse border border-amber-400/20">
              <Tv className="w-5 h-5 text-neutral-950" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h1 className="text-xl font-serif font-bold tracking-tight bg-gradient-to-r from-white via-amber-100 to-neutral-200 bg-clip-text text-transparent">
                  万能电视频道直播
                </h1>
                <span className="text-[10px] uppercase font-bold text-amber-500 bg-amber-500/10 px-2.5 py-0.5 rounded-full border border-amber-500/20 font-sans tracking-wide">
                  Universal TV
                </span>
              </div>
              <p className="text-neutral-500 text-xs hidden sm:block">
                极速换台体验，适配手机点触与安卓 TV 物理遥控操作
              </p>
            </div>
          </div>

          {/* Controls Right Section */}
          <div className="flex flex-wrap items-center gap-3 self-center sm:self-auto">
            
            {/* Display View Mode Selector */}
            <div className="bg-[#121212] border border-white/5 p-1 rounded-2xl flex shadow-inner">
              <button
                id="btn_mode_tv"
                onClick={() => toggleViewMode("tv")}
                className={`flex items-center gap-1.5 px-4 py-2 rounded-xl text-xs font-bold transition-all ${
                  viewMode === "tv" 
                    ? "bg-[#1f1f1f] text-amber-400 shadow-md border border-white/5" 
                    : "text-neutral-400 hover:text-neutral-200"
                }`}
                title="电视端模拟设计 (横屏布局，按键对齐)"
              >
                <Tv className="w-3.5 h-3.5" />
                电视模式 (经典)
              </button>
              <button
                id="btn_mode_mobile"
                onClick={() => toggleViewMode("mobile")}
                className={`flex items-center gap-1.5 px-4 py-2 rounded-xl text-xs font-bold transition-all ${
                  viewMode === "mobile" 
                    ? "bg-[#1f1f1f] text-amber-400 shadow-md border border-white/5" 
                    : "text-neutral-400 hover:text-neutral-200"
                }`}
                title="手机端高仿触屏模式"
              >
                <Smartphone className="w-3.5 h-3.5" />
                手机模式
              </button>
            </div>

            {/* Custom Sources Management trigger Button */}
            <button
              id="header_btn_custom_sources"
              onClick={() => setIsSourceModalOpen(true)}
              className="px-4 py-2 bg-gradient-to-r from-amber-600 to-amber-500 hover:from-amber-500 hover:to-amber-400 text-neutral-950 text-xs font-bold rounded-2xl shadow-lg transition-all flex items-center gap-1.5 focus:ring-1 focus:ring-amber-400 focus:outline-none cursor-pointer"
            >
              <Plus className="w-4 h-4" />
              自定义直播源
              {customSources.length > 0 && (
                <span className="bg-neutral-950 text-amber-400 text-[10px] w-4 h-4 rounded-full flex items-center justify-center font-extrabold">
                  {customSources.length}
                </span>
              )}
            </button>

            {/* Clear database fallback cleaner */}
            <button
              id="header_btn_clear_data"
              onClick={handleClearAllCustomData}
              title="清除所有导入的数据并恢复最初始的内置频道状态"
              className="p-2.5 bg-[#121212] hover:bg-neutral-800 text-neutral-400 hover:text-red-400 rounded-2xl border border-white/5 transition-colors cursor-pointer"
            >
              <Trash2 className="w-4.5 h-4.5" />
            </button>
          </div>

        </div>
      </header>

      {/* Main Grid Viewport */}
      <main className="max-w-7xl mx-auto w-full px-4 md:px-8 pt-6 flex-1 flex flex-col gap-6">
        
        {/* Universal Welcome Alert & Interactive tips */}
        {showWelcomeTip && (
          <div className="p-5 bg-gradient-to-r from-amber-950/20 via-neutral-900/70 to-neutral-950 border border-amber-900/20 rounded-3xl flex items-start justify-between gap-3 text-sm animate-fade-in shadow-xl backdrop-blur-md">
            <div className="flex gap-3">
              <span className="mt-1 flex h-2 w-2 relative">
                <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-amber-400 opacity-75"></span>
                <span className="relative inline-flex rounded-full h-2 w-2 bg-amber-500"></span>
              </span>
              <div>
                <p className="font-serif font-semibold text-amber-200 text-xs md:text-sm tracking-wide">
                  💡 极智换台体验介绍 (支持电视遥控器)
                </p>
                <p className="text-neutral-400 text-[11px] md:text-sm leading-relaxed mt-1">
                  1) 您可以直接按键盘的 <kbd className="px-1.5 py-0.5 bg-neutral-900 text-white rounded font-mono text-xs border border-white/5">Arrow Up ↑</kbd> / <kbd className="px-1.5 py-0.5 bg-neutral-900 text-white rounded font-mono text-xs border border-white/5">Arrow Down ↓</kbd> 极速换台。
                  2) 支持直接粘贴 M3U 文本或本地 <strong>.m3u/m3u8 播放源文件</strong> 直接解析导入，彻底绕开各种 CORS 网页跨域获取限制。
                  3) 100% 客户端离线安全：所有的解析、存储与播放全部运行在您的本地浏览器或编译的 APP 内部，零云端服务器中转依赖。
                </p>
              </div>
            </div>
            <button
              id="btn_close_tip"
              onClick={() => setShowWelcomeTip(false)}
              className="text-neutral-500 hover:text-white p-1"
            >
              <X className="w-4 h-4" />
            </button>
          </div>
        )}

        {/* 1. STAGE: CORE DUAL MODE LAYOUT */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
          
          {/* PLAYER COLUMN: TAKES 7/12 width */}
          <div className="col-span-1 lg:col-span-7 flex flex-col gap-4">
            
            {/* The Live stream Player module */}
            <TVPlayer
              channel={selectedChannel}
              aspectRatio={aspectRatio}
              isMuted={isSystemMuted}
              volume={systemVolume}
              onMuteToggle={() => setIsSystemMuted(!isSystemMuted)}
              onVolumeChange={(nextVol) => {
                setSystemVolume(nextVol);
                setIsSystemMuted(false);
              }}
              onNetworkRetry={playNextChannel}
            />

            {/* Quick stats & Settings Widget */}
            <div className="bg-[#121212]/90 border border-white/5 p-4 rounded-3xl flex flex-wrap items-center justify-between gap-4 shadow-xl">
              <div className="flex items-center gap-3">
                <span className="flex h-2.5 w-2.5 rounded-full bg-emerald-500 animate-pulse" />
                {selectedChannel ? (
                  <div>
                    <span className="text-xs text-neutral-400">正在播放电视频道</span>
                    <h5 className="font-bold text-sm tracking-wide text-neutral-100 flex items-center gap-2">
                      {selectedChannel.name}
                      <button 
                        id="btn_channel_header_fav"
                        onClick={() => toggleFavorite(selectedChannel.id)}
                        className="text-neutral-400 hover:text-rose-500 transition-colors"
                      >
                        <Heart className={`w-4 h-4 ${favorites.includes(selectedChannel.id) ? "fill-rose-500 text-rose-500" : ""}`} />
                      </button>
                    </h5>
                  </div>
                ) : (
                  <span className="text-xs text-neutral-400">未选中任何电视频道，请在右侧选择。</span>
                )}
              </div>

              {/* Adjust format aspects */}
              <div className="flex items-center gap-2">
                <span className="text-xs text-neutral-400 flex items-center gap-1">
                  <Sliders className="w-3.5 h-3.5 text-amber-500" /> 画幅比:
                </span>
                <div className="bg-[#0a0a0a] p-1 rounded-xl flex gap-1 border border-white/5">
                  {(["16-9", "4-3", "fill", "contain"] as const).map((ratio) => (
                    <button
                      id={`btn_aspect_${ratio}`}
                      key={ratio}
                      onClick={() => setAspectRatio(ratio)}
                      className={`px-2.5 py-1 rounded-lg text-[10px] font-bold tracking-wide uppercase transition-all ${
                        aspectRatio === ratio
                          ? "bg-amber-600 text-neutral-950 border border-amber-550/15"
                          : "text-neutral-400 hover:text-neutral-200"
                      }`}
                    >
                      {ratio.replace("-", ":")}
                    </button>
                  ))}
                </div>
              </div>
            </div>
          </div>

          {/* CHANNELS GRID & LIST DIRECTORY COLUMN: TAKES 5/12 width */}
          <div className="col-span-1 lg:col-span-5 flex flex-col gap-4 max-h-[750px] overflow-hidden">
            
            {/* Search filter widget */}
            <div className="bg-[#121212] p-4 rounded-3xl border border-white/5 flex items-center gap-3 shadow-md focus-within:border-amber-500/40 transition-all">
              <Search className="w-4 h-4 text-amber-500 shrink-0" />
              <input
                id="input_channel_search"
                type="text"
                placeholder="搜索频道名称或类别关键字..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="w-full bg-transparent focus:outline-none text-sm placeholder:text-neutral-600 border-none px-1 py-0.5 text-neutral-200"
              />
              {searchQuery && (
                <button
                  id="btn_clear_search"
                  onClick={() => setSearchQuery("")}
                  className="p-1 hover:bg-neutral-800 rounded-full text-neutral-400 hover:text-white transition-colors"
                >
                  <X className="w-3.5 h-3.5" />
                </button>
              )}
            </div>

            {/* Layout Wrapper depending on viewMode state */}
            {viewMode === "tv" ? (
              
              /* TV-STYLE FOCUS DRIVEN COMPANION MODE */
              <div className="bg-[#0c0c0c] border border-white/5 rounded-3xl flex flex-col overflow-hidden h-[500px] md:h-[600px] shadow-2xl">
                
                {/* Visual state headers */}
                <div className="bg-[#050505] p-4 border-b border-white/5 flex items-center justify-between text-xs text-neutral-400 font-mono">
                  <span className="flex items-center gap-1.5 font-sans tracking-wide">
                    <Radio className="w-3.5 h-3.5 text-amber-500 animate-pulse" />
                    遥控器导航盘模式
                  </span>
                  <span>焦点区: {focusedArea === "categories" ? "左侧类别" : "右侧频道"}</span>
                </div>

                <div className="flex-1 flex overflow-hidden">
                  
                  {/* Category sidebar selection (Left Part) */}
                  <div className="w-1/3 bg-[#0a0a0a]/60 p-2 overflow-y-auto border-r border-white/5 space-y-1">
                    {categoriesList.map((cat, idx) => {
                      const isSelected = selectedCategory === cat;
                      const isFocused = focusedArea === "categories" && focusedCategoryIdx === idx;
                      return (
                        <button
                          key={cat}
                          id={`tv_category_item_${idx}`}
                          onClick={() => {
                            setSelectedCategory(cat);
                            setFocusedArea("categories");
                            setFocusedCategoryIdx(idx);
                          }}
                          className={`w-full text-left px-3.5 py-3 rounded-xl text-xs font-semibold flex items-center justify-between transition-all ${
                            isSelected 
                              ? "bg-amber-500/10 text-amber-400 border-l-2 border-amber-500" 
                              : "text-neutral-400 hover:text-neutral-200 hover:bg-white/5"
                          } ${
                            isFocused 
                              ? "ring-2 ring-amber-500 scale-[1.03] bg-neutral-900 text-white border border-white/10 z-10 font-bold shadow-lg" 
                              : "border border-transparent"
                          }`}
                        >
                          <span className="truncate">{cat}</span>
                          {cat === "我的收藏" && <Heart className="w-3 h-3 fill-rose-500 text-rose-500 shrink-0" />}
                        </button>
                      );
                    })}
                  </div>

                  {/* Channel cells list container (Right Part) */}
                  <div className="w-2/3 p-3 overflow-y-auto space-y-1.5 bg-[#080808]/80">
                    {filteredChannels.length === 0 ? (
                      <div className="text-center py-12 text-neutral-500 text-xs font-serif italic">
                        当前频道过滤为空
                      </div>
                    ) : (
                      filteredChannels.map((chan, idx) => {
                        const isPlaying = selectedChannel?.id === chan.id;
                        const isFocused = focusedArea === "channels" && focusedChannelIdx === idx;

                        return (
                          <div
                            key={chan.id}
                            id={`tv_channel_item_${idx}`}
                            onClick={() => {
                              setSelectedChannel(chan);
                              setFocusedArea("channels");
                              setFocusedChannelIdx(idx);
                            }}
                            className={`p-3 rounded-2xl flex items-center justify-between cursor-pointer transition-all border ${
                              isPlaying
                                ? "bg-gradient-to-r from-amber-600/15 via-neutral-900/50 to-neutral-900 border-amber-500/30"
                                : "bg-[#0c0c0c] border-white/5 hover:bg-neutral-800/20 hover:border-neutral-800"
                            } ${
                              isFocused
                                ? "ring-2 ring-amber-500 scale-[1.02] shadow-xl border-amber-400 font-bold z-10"
                                : ""
                            }`}
                          >
                            <div className="flex items-center gap-2.5 min-w-0">
                               {/* Small index tag */}
                              <span className="text-[10px] text-neutral-500 font-mono w-4 text-right">
                                {idx + 1}
                              </span>
                              {chan.logo ? (
                                <img
                                  src={chan.logo}
                                  alt={chan.name}
                                  referrerPolicy="no-referrer"
                                  className="w-5 h-5 object-contain rounded shrink-0 bg-neutral-950"
                                  onError={(e) => {
                                    (e.target as HTMLImageElement).style.display = 'none';
                                  }}
                                />
                              ) : (
                                <div className="w-5 h-5 bg-neutral-900 rounded flex items-center justify-center text-[10px] text-neutral-500 shrink-0 border border-white/5">
                                  📺
                                </div>
                              )}
                              
                              <div className="min-w-0">
                                <span className={`text-xs block truncate ${isPlaying ? "text-amber-450 font-bold" : "text-neutral-200"}`}>
                                  {chan.name}
                                </span>
                                <span className="text-[9px] text-neutral-550 font-mono block">
                                  {chan.category}
                                </span>
                              </div>
                            </div>

                            {/* Fav icon */}
                            <button
                              id={`btn_channel_fav_${chan.id}`}
                              onClick={(e) => {
                                  e.stopPropagation();
                                  toggleFavorite(chan.id);
                              }}
                              className="p-1 px-2 hover:bg-neutral-800/80 text-neutral-500 hover:text-rose-500 rounded-lg shrink-0 transition-colors"
                            >
                              <Heart className={`w-3.5 h-3.5 ${favorites.includes(chan.id) ? "fill-rose-500 text-rose-500" : ""}`} />
                            </button>
                          </div>
                        );
                      })
                    )}
                  </div>

                </div>

                {/* Keyboard Quick Navigation Guide Footer bar */}
                <div className="bg-[#050505] p-3 border-t border-white/5 flex justify-between items-center text-[10px] text-neutral-500 font-mono">
                  <span>← → 键切换焦点栏</span>
                  <span>↑ ↓ 键选择频道</span>
                  <span>回车/OK 键播放</span>
                </div>

              </div>

            ) : (
              
              /* MOBILE SCROLL DRAWER VIEW (TOUCH READY GRID) */
              <div className="flex flex-col gap-3 h-[500px] md:h-[600px]">
                
                {/* Horizontal Category slider */}
                <div className="flex gap-1.5 overflow-x-auto pb-1 shrink-0 no-scrollbar select-none">
                  {categoriesList.map((cat) => {
                    const isSelected = selectedCategory === cat;
                    return (
                      <button
                        key={cat}
                        id={`mobile_category_${cat}`}
                        onClick={() => setSelectedCategory(cat)}
                        className={`px-4 py-2 rounded-full text-xs font-semibold whitespace-nowrap transition-all flex items-center gap-1 shrink-0 ${
                          isSelected 
                            ? "bg-amber-600 text-neutral-950 shadow-md shadow-amber-500/10 scale-105 font-bold" 
                            : "bg-[#121212] border border-white/5 text-neutral-400 hover:text-neutral-200 hover:bg-neutral-800"
                        }`}
                      >
                        {cat}
                        {cat === "我的收藏" && <Heart className="w-3 h-3 fill-rose-500 text-rose-500" />}
                      </button>
                    );
                  })}
                </div>

                {/* Vertical Scroll stream layout container */}
                <div className="flex-1 overflow-y-auto space-y-2 pr-1">
                  {filteredChannels.length === 0 ? (
                    <div className="text-center py-16 bg-[#0c0c0c] border border-white/5 rounded-3xl text-neutral-500 text-xs font-serif italic">
                      此分类下没有电视频道
                    </div>
                  ) : (
                    filteredChannels.map((chan, idx) => {
                      const isPlaying = selectedChannel?.id === chan.id;
                      return (
                        <div
                          key={chan.id}
                          id={`mobile_channel_item_${idx}`}
                          onClick={() => setSelectedChannel(chan)}
                          className={`p-3 rounded-2xl flex items-center justify-between cursor-pointer transition-all border ${
                            isPlaying
                              ? "bg-gradient-to-r from-amber-600/15 via-neutral-900/40 to-neutral-950 border-amber-500/25 shadow-md shadow-amber-500/5 animate-pulse"
                              : "bg-[#121212] border-white/5 hover:bg-[#1a1a1a]"
                          }`}
                        >
                          <div className="flex items-center gap-3 min-w-0">
                            {chan.logo ? (
                              <img
                                src={chan.logo}
                                alt={chan.name}
                                referrerPolicy="no-referrer"
                                className="w-8 h-8 object-contain rounded-lg shrink-0 bg-neutral-950 border border-white/5"
                                onError={(e) => {
                                  (e.target as HTMLImageElement).style.display = 'none';
                                }}
                              />
                            ) : (
                              <div className="w-8 h-8 bg-[#1f1f1f] rounded-lg flex items-center justify-center text-xs shrink-0 border border-white/5 text-neutral-400">
                                📺
                              </div>
                            )}

                            <div className="min-w-0">
                              <span className={`text-xs font-bold block truncate ${isPlaying ? "text-amber-450" : "text-neutral-200"}`}>
                                {chan.name}
                              </span>
                              <span className="text-[10px] text-neutral-500 block font-mono">
                                {chan.category}
                              </span>
                            </div>
                          </div>

                          <div className="flex items-center gap-2 shrink-0">
                            {isPlaying && (
                              <span className="text-[10px] font-bold tracking-widest text-amber-500 animate-pulse bg-amber-500/10 border border-amber-500/20 px-2 py-0.5 rounded-full font-sans uppercase">
                                Playing
                              </span>
                            )}
                            <button
                              id={`btn_mobile_fav_${chan.id}`}
                              onClick={(e) => {
                                e.stopPropagation();
                                toggleFavorite(chan.id);
                              }}
                              className="p-1 px-2.5 hover:bg-neutral-800 text-neutral-500 hover:text-rose-500 rounded-lg transition-colors"
                            >
                              <Heart className={`w-4 h-4 ${favorites.includes(chan.id) ? "fill-rose-500 text-rose-500" : ""}`} />
                            </button>
                          </div>
                        </div>
                      );
                    })
                  )}
                </div>

              </div>

            )}

          </div>

        </div>

      </main>

      {/* Floating virtual television remote simulation module widget helper */}
      <TVRemoteWidget
        onPressKey={executeRemoteAction}
        onToggleRemote={() => setIsRemoteWidgetOpen(!isRemoteWidgetOpen)}
        isOpen={isRemoteWidgetOpen}
      />

      {/* Custom sources wizard Modal element overlay */}
      {isSourceModalOpen && (
        <CustomSourceModal
          onAddChannels={handleAddChannels}
          onAddSingleChannel={handleAddSingleChannel}
          customSources={customSources}
          onRemovePlaylist={handleRemovePlaylist}
          onClose={() => setIsSourceModalOpen(false)}
          onExport={handleExportState}
          onImport={handleImportState}
        />
      )}

      {/* Footer System Version Indicators */}
      <footer className="mt-12 text-center text-neutral-650 text-[11px] font-mono border-t border-white/5 pt-6 px-4">
        <div className="max-w-7xl mx-auto flex flex-col sm:flex-row items-center justify-between gap-4 text-neutral-500">
          <div className="flex flex-wrap items-center justify-center gap-3">
            <span className="font-semibold text-neutral-400 font-serif">TV Live Player v1.50</span>
            <span>•</span>
            <span className="flex items-center gap-1 text-neutral-400">
              <FileCheck className="w-3.5 h-3.5 text-amber-500" />
              本地安全存储库 LocalStorage
            </span>
            <span>•</span>
            <span>适配 HLS.js 极速渲染流</span>
          </div>
          <div className="text-[10px] text-neutral-600 max-w-md">
            免责声明：本工具仅提供网络直播流解析与播放框架，用户自行添加或导入的直播源版权归原协议提供方所有。
          </div>
        </div>
      </footer>

    </div>
  );
}
