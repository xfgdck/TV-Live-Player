import React, { useState, useEffect, useMemo, useRef } from "react";
import { 
  Tv, 
  Heart, 
  Search, 
  Plus, 
  Sliders, 
  Volume2, 
  VolumeX, 
  Trash2, 
  X,
  Radio,
  FileCheck,
  ChevronUp,
  ChevronDown
} from "lucide-react";

import { TVChannel, CustomSource } from "./types";
import { INITIAL_DEFAULT_CHANNELS } from "./data/defaultChannels";
import TVPlayer from "./components/TVPlayer";
import CustomSourceModal from "./components/CustomSourceModal";
import TVRemoteWidget from "./components/TVRemoteWidget";

export default function App() {
  // UI & Platform States
  const [isSystemMuted, setIsSystemMuted] = useState<boolean>(false);
  const [systemVolume, setSystemVolume] = useState<number>(85);
  
  // Channels database states
  const [channels, setChannels] = useState<TVChannel[]>([]);
  const [favorites, setFavorites] = useState<string[]>([]);
  const [customSources, setCustomSources] = useState<CustomSource[]>([]);
  
  // Selection and Filter list states
  const [selectedChannel, setSelectedChannel] = useState<TVChannel | null>(null);
  const [selectedCategory, setSelectedCategory] = useState<string>("全部");
  const [searchQuery, setSearchQuery] = useState<string>("");
  
  // Focus States for TV Mode Navigation
  // "categories" = focus left list/top bar, "channels" = focus right cells list
  const [focusedArea, setFocusedArea] = useState<"categories" | "channels">("channels");
  const [focusedCategoryIdx, setFocusedCategoryIdx] = useState<number>(0);
  const [focusedChannelIdx, setFocusedChannelIdx] = useState<number>(0);

  // Modals / Overlays
  const [isSourceModalOpen, setIsSourceModalOpen] = useState<boolean>(false);
  const [isRemoteWidgetOpen, setIsRemoteWidgetOpen] = useState<boolean>(false);

  // Drag and Drop Reorder State (TV-adapted: move buttons + keyboard shortcuts)
  const [lastMovedChannelId, setLastMovedChannelId] = useState<string | null>(null);

  // Helper: check if a channel is custom (not built-in)
  const isCustomChannel = (channelId: string) => !INITIAL_DEFAULT_CHANNELS.some(d => d.id === channelId);

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

    // 2. Load Custom channels
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

  // Filter channels based on Selected Category & Search Query
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

    // Tag channels so we know they belong to this source
    const taggedChannels = newChannels.map(c => ({
      ...c,
      id: `${newSourceRecord.id}_${c.id}`,
      category: c.category || playlistName
    }));

    // Find previous custom channels
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

  // Delete a single custom channel
  const handleDeleteChannel = (channelId: string) => {
    if (!isCustomChannel(channelId)) return;

    const channelToDelete = channels.find(c => c.id === channelId);
    if (!channelToDelete) return;

    if (!window.confirm(`确定要删除频道「${channelToDelete.name}」吗？`)) return;

    const currentCustomChannels = channels.filter(c => isCustomChannel(c.id));
    const nextCustomChannels = currentCustomChannels.filter(c => c.id !== channelId);

    saveCustomChannelsToLocalStorage(nextCustomChannels);

    const combined = [...INITIAL_DEFAULT_CHANNELS, ...nextCustomChannels];
    setChannels(combined);

    // Reset selection if deleted channel was selected
    if (selectedChannel?.id === channelId) {
      setSelectedChannel(combined[0] || null);
    }

    // Also remove from favorites
    if (favorites.includes(channelId)) {
      const nextFavs = favorites.filter(id => id !== channelId);
      setFavorites(nextFavs);
      localStorage.setItem("tv_favorites", JSON.stringify(nextFavs));
    }
  };

  // ----- TV-friendly Reorder: Move focused channel up/down -----
  const handleMoveFocusedChannel = (direction: "up" | "down") => {
    if (focusedArea !== "channels") return;

    const focusedChannel = filteredChannels[focusedChannelIdx];
    if (!focusedChannel || !isCustomChannel(focusedChannel.id)) return;

    const currentCustomChannels = channels.filter(c => isCustomChannel(c.id));
    const customIdx = currentCustomChannels.findIndex(c => c.id === focusedChannel.id);
    if (customIdx === -1) return;

    const targetIdx = direction === "up" ? customIdx - 1 : customIdx + 1;
    if (targetIdx < 0 || targetIdx >= currentCustomChannels.length) return;

    const reordered = [...currentCustomChannels];
    [reordered[customIdx], reordered[targetIdx]] = [reordered[targetIdx], reordered[customIdx]];

    saveCustomChannelsToLocalStorage(reordered);

    const combined = [...INITIAL_DEFAULT_CHANNELS, ...reordered];
    setChannels(combined);
    setLastMovedChannelId(focusedChannel.id);
  };

  // Track moved channel and update focus index after reorder
  useEffect(() => {
    if (lastMovedChannelId) {
      const idx = filteredChannels.findIndex(c => c.id === lastMovedChannelId);
      if (idx !== -1) {
        setFocusedChannelIdx(idx);
      }
      setLastMovedChannelId(null);
    }
  }, [filteredChannels, lastMovedChannelId]);

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

    // 2. Navigation when looking at Categories list (sidebar or horizontal bar)
    if (focusedArea === "categories") {
      const isLg = window.innerWidth >= 1024;
      
      // If we are on vertical screen (lg: sidebar), ArrowUp/Down changes selection.
      // If we are on horizontal scroll (mobile: top bar), ArrowLeft/Right changes selection.
      if (actionKey === "ArrowUp" || (!isLg && actionKey === "ArrowLeft")) {
        setFocusedCategoryIdx((prev) => {
          const nextIdx = prev > 0 ? prev - 1 : categoriesList.length - 1;
          setSelectedCategory(categoriesList[nextIdx]);
          return nextIdx;
        });
      } else if (actionKey === "ArrowDown" || (!isLg && actionKey === "ArrowRight")) {
        setFocusedCategoryIdx((prev) => {
          const nextIdx = prev < categoriesList.length - 1 ? prev + 1 : 0;
          setSelectedCategory(categoriesList[nextIdx]);
          return nextIdx;
        });
      } else if ((isLg && actionKey === "ArrowRight") || (!isLg && actionKey === "ArrowDown")) {
        // Move focus onto the channels list
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

    // 3. Navigation when looking at Channels list (Vertical column)
    else if (focusedArea === "channels") {
      const isLg = window.innerWidth >= 1024;
      const totalLen = filteredChannels.length;

      // Ctrl+ArrowUp/Down = Move channel order (TV-friendly reorder)
      if (actionKey === "CtrlArrowUp") {
        handleMoveFocusedChannel("up");
        return;
      }
      if (actionKey === "CtrlArrowDown") {
        handleMoveFocusedChannel("down");
        return;
      }
      // Delete key or D key = Delete focused custom channel
      if (actionKey === "DeleteChannel") {
        const focused = filteredChannels[focusedChannelIdx];
        if (focused && isCustomChannel(focused.id)) {
          handleDeleteChannel(focused.id);
        }
        return;
      }

      if (actionKey === "ArrowUp") {
        setFocusedChannelIdx((prev) => (prev > 0 ? prev - 1 : prev));
      } else if (actionKey === "ArrowDown") {
        setFocusedChannelIdx((prev) => (prev < totalLen - 1 ? prev + 1 : prev));
      } else if ((isLg && actionKey === "ArrowLeft") || (!isLg && actionKey === "ArrowUp" && focusedChannelIdx === 0)) {
        // Return focus to categories (either left sidebar or top horizontal strip)
        setFocusedArea("categories");
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
          if (e.ctrlKey || e.metaKey) {
            executeRemoteAction("CtrlArrowUp");
          } else {
            executeRemoteAction("ArrowUp");
          }
          break;
        case "ArrowDown":
          if (e.ctrlKey || e.metaKey) {
            executeRemoteAction("CtrlArrowDown");
          } else {
            executeRemoteAction("ArrowDown");
          }
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
        case "Delete":
          executeRemoteAction("DeleteChannel");
          break;
        default:
          break;
      }
    };

    window.addEventListener("keydown", handlePhysicalKeyDown);
    return () => window.removeEventListener("keydown", handlePhysicalKeyDown);
  }, [focusedArea, focusedCategoryIdx, focusedChannelIdx, categoriesList, filteredChannels, isSourceModalOpen]);

  // Scroll focused element into view
  useEffect(() => {
    if (focusedArea === "categories") {
      const isLg = window.innerWidth >= 1024;
      const elementId = isLg ? `tv_category_item_${focusedCategoryIdx}` : `mobile_category_${focusedCategoryIdx}`;
      const element = document.getElementById(elementId);
      if (element) {
        element.scrollIntoView({ behavior: "smooth", block: "nearest", inline: "nearest" });
      }
    } else if (focusedArea === "channels") {
      const element = document.getElementById(`tv_channel_item_${focusedChannelIdx}`);
      if (element) {
        element.scrollIntoView({ behavior: "smooth", block: "nearest" });
      }
    }
  }, [focusedArea, focusedCategoryIdx, focusedChannelIdx]);

  // Handle auto channel index tracking when changing Categories
  useEffect(() => {
    if (focusedChannelIdx >= filteredChannels.length) {
      setFocusedChannelIdx(Math.max(0, filteredChannels.length - 1));
    }
  }, [filteredChannels]);

  // Helper routine to switch channel sequentially on Stream Controller
  const playNextChannel = () => {
    if (channels.length === 0) return;
    const currentIdx = selectedChannel ? channels.findIndex(c => c.id === selectedChannel.id) : -1;
    const nextIdx = currentIdx < channels.length - 1 ? currentIdx + 1 : 0;
    setSelectedChannel(channels[nextIdx]);
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-[#060608] via-[#0c0c10] to-[#030304] text-[#eaeaea] font-sans overflow-x-hidden pb-10 flex flex-col justify-between selection:bg-amber-600 selection:text-neutral-950">
      
      {/* Top Universal Header Bar */}
      <header className="border-b border-white/5 bg-[#08080c]/85 backdrop-blur-xl sticky top-0 z-30 px-4 md:px-8 py-4 shadow-2xl shadow-black/40">
        <div className="max-w-7xl mx-auto flex flex-row items-center justify-between gap-4">
          
          {/* Logo Name */}
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 bg-gradient-to-tr from-amber-600 to-amber-500 rounded-2xl flex items-center justify-center shadow-lg shadow-amber-500/10 border border-amber-400/20">
              <Tv className="w-5 h-5 text-neutral-950" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h1 className="text-lg md:text-xl font-serif font-bold tracking-tight bg-gradient-to-r from-white via-amber-100 to-neutral-200 bg-clip-text text-transparent">
                  万能电视频道直播
                </h1>
                <span className="text-[10px] uppercase font-bold text-amber-500 bg-amber-500/10 px-2.5 py-0.5 rounded-full border border-amber-500/20 font-sans tracking-wide">
                  Cinema TV
                </span>
              </div>
              <p className="text-neutral-500 text-[10px] md:text-xs hidden sm:block">
                极简奢华的智能电视直播舱 • 适配遥控器与点触操作
              </p>
            </div>
          </div>

          {/* Controls Right Section */}
          <div className="flex items-center gap-3">
            {/* Custom Sources Management Button */}
            <button
              id="header_btn_custom_sources"
              onClick={() => setIsSourceModalOpen(true)}
              className="px-4 py-2 bg-gradient-to-r from-amber-600 to-amber-500 hover:from-amber-500 hover:to-amber-400 text-neutral-950 text-xs font-bold rounded-2xl shadow-lg shadow-amber-600/10 transition-all flex items-center gap-1.5 focus:ring-1 focus:ring-amber-400 focus:outline-none cursor-pointer"
            >
              <Plus className="w-4 h-4" />
              自定义直播源
              {customSources.length > 0 && (
                <span className="bg-neutral-950 text-amber-400 text-[10px] w-4 h-4 rounded-full flex items-center justify-center font-extrabold">
                  {customSources.length}
                </span>
              )}
            </button>

            {/* Reset fallback cleaner */}
            <button
              id="header_btn_clear_data"
              onClick={handleClearAllCustomData}
              title="清除所有导入的数据并恢复最初始的内置频道状态"
              className="p-2.5 bg-[#121216]/90 hover:bg-neutral-850 text-neutral-400 hover:text-red-400 rounded-2xl border border-white/5 transition-colors cursor-pointer"
            >
              <Trash2 className="w-4 h-4" />
            </button>
          </div>

        </div>
      </header>

      {/* Main Grid Viewport */}
      <main className="max-w-7xl mx-auto w-full px-4 md:px-8 pt-6 flex-1 flex flex-col gap-6">
        
        {/* Cinematic Dashboard Layout */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
          
          {/* PLAYER COLUMN: TAKES 7/12 width */}
          <div className="col-span-1 lg:col-span-7 flex flex-col gap-4">
            
            {/* Live TV Player module */}
            <TVPlayer
              channel={selectedChannel}
              aspectRatio="16-9"
              isMuted={isSystemMuted}
              volume={systemVolume}
              onMuteToggle={() => setIsSystemMuted(!isSystemMuted)}
              onVolumeChange={(nextVol) => {
                setSystemVolume(nextVol);
                setIsSystemMuted(false);
              }}
              onNetworkRetry={playNextChannel}
            />

            {/* Sleek Channel Details and Favorite bar */}
            <div className="bg-[#0e0e13]/60 backdrop-blur-xl border border-white/[0.03] p-4 rounded-3xl flex items-center justify-between gap-4 shadow-2xl">
              <div className="flex items-center gap-3">
                <span className="flex h-2 w-2 rounded-full bg-emerald-500 animate-pulse" />
                {selectedChannel ? (
                  <div>
                    <span className="text-[10px] text-neutral-500 uppercase tracking-widest block font-medium">Playing Live Channel</span>
                    <h5 className="font-bold text-sm md:text-base tracking-wide text-neutral-100 flex items-center gap-2 mt-0.5">
                      {selectedChannel.name}
                      <button 
                        id="btn_channel_header_fav"
                        onClick={() => toggleFavorite(selectedChannel.id)}
                        className="text-neutral-500 hover:text-rose-500 transition-colors ml-1"
                      >
                        <Heart className={`w-4 h-4 ${favorites.includes(selectedChannel.id) ? "fill-rose-500 text-rose-500" : ""}`} />
                      </button>
                    </h5>
                  </div>
                ) : (
                  <span className="text-xs text-neutral-400">未选中任何电视频道，请在右侧选择频道播放。</span>
                )}
              </div>

              {/* D-Pad Support Badge */}
              <div className="hidden sm:flex items-center gap-1.5 px-3 py-1.5 bg-[#121216]/80 rounded-xl border border-white/5 text-[10px] font-mono text-neutral-400">
                <Radio className="w-3.5 h-3.5 text-amber-500 animate-pulse" />
                <span>遥控方向盘 & 键盘操作已激活</span>
              </div>
            </div>
          </div>

          {/* CHANNELS GRID & LIST COLUMN: TAKES 5/12 width */}
          <div className="col-span-1 lg:col-span-5 flex flex-col gap-4 max-h-[750px] overflow-hidden">
            
            {/* Search filter widget */}
            <div className="bg-[#0e0e13]/60 backdrop-blur-xl p-4 rounded-3xl border border-white/[0.03] flex items-center gap-3 shadow-md focus-within:border-amber-500/40 transition-all">
              <Search className="w-4 h-4 text-amber-500 shrink-0" />
              <input
                id="input_channel_search"
                type="text"
                placeholder="搜索频道名称或类别关键字..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="w-full bg-transparent focus:outline-none text-xs md:text-sm placeholder:text-neutral-600 border-none px-1 py-0.5 text-neutral-200"
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

            {/* Unified Category & Channels Container */}
            <div className="bg-[#0c0c0f]/80 border border-white/5 rounded-3xl flex flex-col lg:flex-row overflow-hidden h-[500px] md:h-[600px] shadow-2xl backdrop-blur-md">
              
              {/* Left Panel: Category selection (Vertical on LG screens, hidden/responsive on mobile) */}
              <div className="hidden lg:block lg:w-1/3 bg-[#0a0a0e]/80 p-2 overflow-y-auto border-r border-white/5 space-y-1">
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
                          ? "ring-2 ring-amber-500 scale-[1.03] bg-neutral-900 text-white border border-white/10 z-10 font-bold shadow-lg shadow-amber-500/10" 
                          : "border border-transparent"
                      }`}
                    >
                      <span className="truncate">{cat}</span>
                      {cat === "我的收藏" && <Heart className="w-3 h-3 fill-rose-500 text-rose-500 shrink-0" />}
                    </button>
                  );
                })}
              </div>

              {/* Top Category pills (Visible on screen width smaller than LG) */}
              <div className="block lg:hidden bg-[#09090c]/80 p-3 border-b border-white/5 shrink-0 overflow-x-auto select-none no-scrollbar flex gap-2">
                {categoriesList.map((cat, idx) => {
                  const isSelected = selectedCategory === cat;
                  const isFocused = focusedArea === "categories" && focusedCategoryIdx === idx;
                  return (
                    <button
                      key={cat}
                      id={`mobile_category_${idx}`}
                      onClick={() => {
                        setSelectedCategory(cat);
                        setFocusedArea("categories");
                        setFocusedCategoryIdx(idx);
                      }}
                      className={`px-4 py-2 rounded-full text-xs font-semibold whitespace-nowrap transition-all flex items-center gap-1.5 shrink-0 ${
                        isSelected 
                          ? "bg-amber-600 text-neutral-950 shadow-md shadow-amber-500/10 font-bold" 
                          : "bg-[#121216] border border-white/5 text-neutral-400 hover:text-neutral-200 hover:bg-neutral-850"
                      } ${
                        isFocused 
                          ? "ring-2 ring-amber-500 scale-105" 
                          : ""
                      }`}
                    >
                      {cat}
                      {cat === "我的收藏" && <Heart className="w-3 h-3 fill-rose-500 text-rose-500" />}
                    </button>
                  );
                })}
              </div>

              {/* Right Panel: Channels list (Vertical scrolling on all viewports) */}
              <div className="flex-1 p-3 overflow-y-auto space-y-1.5 bg-[#08080c]/80">
                {filteredChannels.length === 0 ? (
                  <div className="text-center py-16 text-neutral-500 text-xs font-serif italic">
                    当前分类下空空如也
                  </div>
                ) : (
                  filteredChannels.map((chan, idx) => {
                    const isPlaying = selectedChannel?.id === chan.id;
                    const isFocused = focusedArea === "channels" && focusedChannelIdx === idx;
                    const isCustom = isCustomChannel(chan.id);
                    const showActions = isFocused && isCustom;
                    
                    // Compute whether this specific channel can move up/down
                    const customList = channels.filter(c => isCustomChannel(c.id));
                    const thisCustomIdx = isCustom ? customList.findIndex(c => c.id === chan.id) : -1;
                    const canUp = isCustom && thisCustomIdx > 0;
                    const canDown = isCustom && thisCustomIdx >= 0 && thisCustomIdx < customList.length - 1;

                    return (
                      <div
                        key={chan.id}
                        id={`tv_channel_item_${idx}`}
                        onClick={(e) => {
                          const target = e.target as HTMLElement;
                          if (target.closest("button")) return;
                          setSelectedChannel(chan);
                          setFocusedArea("channels");
                          setFocusedChannelIdx(idx);
                        }}
                        className={`group p-3 rounded-2xl flex items-center gap-2 justify-between cursor-pointer transition-all border ${
                          isPlaying
                            ? "bg-gradient-to-r from-amber-600/15 via-[#1a1a1f] to-neutral-900 border-amber-500/30"
                            : "bg-[#0c0c0f] border-white/5 hover:bg-neutral-800/20 hover:border-neutral-800"
                        } ${
                          isFocused
                            ? "ring-2 ring-amber-500 scale-[1.015] shadow-xl shadow-amber-500/5 border-amber-400 font-bold z-10 bg-neutral-900"
                            : ""
                        }`}
                      >
                        {/* Channel info area */}
                        <div className="flex items-center gap-2.5 min-w-0 flex-1">
                          {!isCustom && (
                            <span className="text-[10px] text-neutral-500 font-mono w-4 text-right shrink-0">
                              {idx + 1}
                            </span>
                          )}
                          {chan.logo ? (
                            <img
                              src={chan.logo}
                              alt={chan.name}
                              referrerPolicy="no-referrer"
                              className="w-6 h-6 object-contain rounded shrink-0 bg-neutral-950 border border-white/5"
                              onError={(e) => {
                                (e.target as HTMLImageElement).style.display = 'none';
                              }}
                            />
                          ) : (
                            <div className="w-6 h-6 bg-neutral-900 rounded flex items-center justify-center text-[10px] text-neutral-500 shrink-0 border border-white/5">
                              📺
                            </div>
                          )}
                          
                          <div className="min-w-0">
                            <span className={`text-xs block truncate ${isPlaying ? "text-amber-450 font-bold" : "text-neutral-200"}`}>
                              {chan.name}
                            </span>
                            <span className="text-[9px] text-neutral-500 font-mono block">
                              {chan.category}
                            </span>
                          </div>
                        </div>

                        {/* Action buttons area */}
                        <div className="flex items-center gap-1 shrink-0">
                          
                          {/* TV-friendly action buttons: visible when focused */}
                          {showActions && (
                            <div className="flex items-center gap-0.5 mr-1 animate-fade-in">
                              {/* Move Up */}
                              <button
                                onClick={(e) => {
                                  e.stopPropagation();
                                  if (canUp) handleMoveFocusedChannel("up");
                                }}
                                disabled={!canUp}
                                className={`p-1.5 rounded-lg transition-all ${
                                  canUp
                                    ? "text-amber-400 hover:bg-amber-500/20 hover:text-amber-300"
                                    : "text-neutral-700 cursor-not-allowed"
                                }`}
                                title="上移 (Ctrl+↑)"
                              >
                                <ChevronUp className="w-4 h-4" />
                              </button>
                              {/* Move Down */}
                              <button
                                onClick={(e) => {
                                  e.stopPropagation();
                                  if (canDown) handleMoveFocusedChannel("down");
                                }}
                                disabled={!canDown}
                                className={`p-1.5 rounded-lg transition-all ${
                                  canDown
                                    ? "text-amber-400 hover:bg-amber-500/20 hover:text-amber-300"
                                    : "text-neutral-700 cursor-not-allowed"
                                }`}
                                title="下移 (Ctrl+↓)"
                              >
                                <ChevronDown className="w-4 h-4" />
                              </button>
                              {/* Delete */}
                              <button
                                onClick={(e) => {
                                  e.stopPropagation();
                                  handleDeleteChannel(chan.id);
                                }}
                                className="p-1.5 text-red-400 hover:bg-red-500/20 hover:text-red-300 rounded-lg transition-all ml-0.5 border-l border-neutral-700 pl-1.5"
                                title="删除频道 (Delete)"
                              >
                                <Trash2 className="w-4 h-4" />
                              </button>
                            </div>
                          )}

                          {isPlaying && !showActions && (
                            <span className="text-[9px] font-bold tracking-wider text-amber-500 animate-pulse bg-amber-500/10 border border-amber-500/20 px-2 py-0.5 rounded-full font-sans uppercase">
                              Playing
                            </span>
                          )}
                          {isPlaying && showActions && (
                            <span className="text-[9px] font-bold tracking-wider text-amber-500 animate-pulse bg-amber-500/10 border border-amber-500/20 px-1.5 py-0.5 rounded-full font-sans uppercase">
                              ON
                            </span>
                          )}

                          <button
                            id={`btn_channel_fav_${chan.id}`}
                            onClick={(e) => {
                                e.stopPropagation();
                                toggleFavorite(chan.id);
                            }}
                            className="p-1 px-2 hover:bg-neutral-800/80 text-neutral-550 hover:text-rose-500 rounded-lg shrink-0 transition-colors"
                          >
                            <Heart className={`w-3.5 h-3.5 ${favorites.includes(chan.id) ? "fill-rose-500 text-rose-500" : ""}`} />
                          </button>
                        </div>
                      </div>
                    );
                  })
                )}
              </div>

            </div>

            {/* Keyboard / D-pad Navigation Indicator Bar */}
            <div className="bg-[#0c0c0f]/80 p-3 border border-white/5 rounded-2xl flex flex-col md:flex-row justify-between items-center gap-2 text-[9px] text-neutral-500 font-mono">
              <div className="flex items-center gap-1">
                <span className="px-1.5 py-0.5 bg-neutral-900 rounded border border-white/5">↑↓</span>
                <span>选择 Select</span>
                <span className="text-neutral-700">|</span>
                <span className="px-1.5 py-0.5 bg-neutral-900 rounded border border-white/5">←</span>
                <span className="px-1.5 py-0.5 bg-neutral-900 rounded border border-white/5">→</span>
                <span>分栏 Tab</span>
                <span className="text-neutral-700">|</span>
                <span className="px-1.5 py-0.5 bg-neutral-900 rounded border border-white/5 font-bold text-amber-500">Enter</span>
                <span>播放</span>
              </div>
              <div className="flex items-center gap-1 text-[9px]">
                <span className="px-1.5 py-0.5 bg-amber-500/10 rounded border border-amber-500/20 text-amber-400">Ctrl</span>
                <span>+</span>
                <span className="px-1.5 py-0.5 bg-neutral-900 rounded border border-white/5">↑↓</span>
                <span>排序</span>
                <span className="text-neutral-700">|</span>
                <span className="px-1.5 py-0.5 bg-neutral-900 rounded border border-red-500/30 text-red-400">Del</span>
                <span>删除</span>
              </div>
            </div>

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
      <footer className="mt-12 text-center text-neutral-600 text-[10px] font-mono border-t border-white/5 pt-6 px-4">
        <div className="max-w-7xl mx-auto flex flex-col sm:flex-row items-center justify-between gap-4 text-neutral-500">
          <div className="flex flex-wrap items-center justify-center gap-3">
            <span className="font-semibold text-neutral-400 font-serif">TV Live Player v2.0</span>
            <span>•</span>
            <span className="flex items-center gap-1 text-neutral-400">
              <FileCheck className="w-3.5 h-3.5 text-amber-500" />
              本地安全存储库 LocalStorage
            </span>
            <span>•</span>
            <span>适配 HLS.js 极速渲染流</span>
          </div>
          <div className="text-[9px] text-neutral-600 max-w-md">
            免责声明：本工具仅提供网络直播流解析与播放框架，用户自行添加或导入的直播源版权归原协议提供方所有。
          </div>
        </div>
      </footer>

    </div>
  );
}
