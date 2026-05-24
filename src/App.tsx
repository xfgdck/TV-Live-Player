import React, { useState, useEffect, useMemo, useCallback, useRef } from "react";
import { TVChannel, CustomSource } from "./types";
import { INITIAL_DEFAULT_CHANNELS } from "./data/defaultChannels";
import TVPlayer from "./components/TVPlayer";
import CustomSourceModal from "./components/CustomSourceModal";
import { Heart, Settings, Tv } from "lucide-react";

type FocusZone = "watching" | "categories";

export default function App() {
  const [channels, setChannels] = useState<TVChannel[]>([]);
  const [favorites, setFavorites] = useState<string[]>([]);
  const [customSources, setCustomSources] = useState<CustomSource[]>([]);
  const [selectedChannel, setSelectedChannel] = useState<TVChannel | null>(null);
  const [selectedCategory, setSelectedCategory] = useState("全部");

  const [focusZone, setFocusZone] = useState<FocusZone>("watching");
  const [catPickerIdx, setCatPickerIdx] = useState(0);

  const [showSourceModal, setShowSourceModal] = useState(false);

  // OSD info display (triggered by OK press in watching)
  const [showOsdInfo, setShowOsdInfo] = useState(false);
  const osdTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Category picker auto-hide timer
  const catPickerTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Exit prompt
  const [exitPrompt, setExitPrompt] = useState(false);
  const exitTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Video element ref for fullscreen
  const videoContainerRef = useRef<HTMLDivElement | null>(null);

  // ─── load / save ───
  const load = <T,>(key: string, fallback: T): T => {
    try { const r = localStorage.getItem(key); return r ? JSON.parse(r) : fallback; } catch { return fallback; }
  };
  const save = (key: string, val: unknown) => localStorage.setItem(key, JSON.stringify(val));

  useEffect(() => {
    const src = load<CustomSource[]>("tv_sources", []);
    const favs = load<string[]>("tv_favorites", []);
    const chs = load<TVChannel[]>("tv_channels", INITIAL_DEFAULT_CHANNELS);
    const lastId = localStorage.getItem("tv_last_channel");
    setCustomSources(src);
    setFavorites(favs);
    setChannels(chs);
    const lastCh = lastId ? chs.find(c => c.id === lastId) : null;
    setSelectedChannel(lastCh || chs[0] || null);
  }, []);

  // ─── Persist channels to localStorage whenever they change ───
  useEffect(() => { save("tv_channels", channels); }, [channels]);
  useEffect(() => { save("tv_sources", customSources); }, [customSources]);

  // ─── Categories ───
  const catList = useMemo(() => {
    const s = new Set<string>();
    if (favorites.length > 0) s.add("收藏");
    s.add("全部");
    channels.forEach(c => { if (c.category) s.add(c.category); });
    return Array.from(s);
  }, [channels, favorites]);

  const filteredChannels = useMemo(() => {
    return channels.filter(c => {
      if (selectedCategory === "收藏") return favorites.includes(c.id);
      if (selectedCategory !== "全部") return c.category === selectedCategory;
      return true;
    });
  }, [channels, selectedCategory, favorites]);

  // ─── Save last channel on every switch ───
  const switchToChannel = useCallback((ch: TVChannel) => {
    setSelectedChannel(ch);
    localStorage.setItem("tv_last_channel", ch.id);
    setFocusZone("watching");
    // Request fullscreen on channel switch
    requestFullscreen();
  }, []);

  const requestFullscreen = () => {
    try {
      const el = videoContainerRef.current;
      if (el && document.fullscreenElement === null) {
        el.requestFullscreen?.().catch(() => {});
      }
    } catch {}
  };

  // ─── Switch to prev/next channel in current filtered list ───
  const switchToPrevChannel = useCallback(() => {
    if (!selectedChannel) return;
    const idx = filteredChannels.findIndex(c => c.id === selectedChannel.id);
    if (idx < 0) { switchToChannel(filteredChannels[0]); return; }
    const prev = idx > 0 ? filteredChannels[idx - 1] : filteredChannels[filteredChannels.length - 1];
    switchToChannel(prev);
  }, [selectedChannel, filteredChannels, switchToChannel]);

  const switchToNextChannel = useCallback(() => {
    if (!selectedChannel) return;
    const idx = filteredChannels.findIndex(c => c.id === selectedChannel.id);
    if (idx < 0) { switchToChannel(filteredChannels[0]); return; }
    const next = idx < filteredChannels.length - 1 ? filteredChannels[idx + 1] : filteredChannels[0];
    switchToChannel(next);
  }, [selectedChannel, filteredChannels, switchToChannel]);

  // ─── OSD Info ───
  const showInfo = () => {
    setShowOsdInfo(true);
    if (osdTimer.current) clearTimeout(osdTimer.current);
    osdTimer.current = setTimeout(() => setShowOsdInfo(false), 3000);
  };

  // ─── Category Picker ───
  const showCatPicker = (direction: "left" | "right") => {
    setFocusZone("categories");
    const curCatIdx = catList.indexOf(selectedCategory);
    const startIdx = curCatIdx >= 0 ? curCatIdx : 0;
    if (direction === "left") {
      setCatPickerIdx(startIdx > 0 ? startIdx - 1 : catList.length - 1);
    } else {
      setCatPickerIdx(startIdx < catList.length - 1 ? startIdx + 1 : 0);
    }
    // Auto-hide after 4 seconds
    if (catPickerTimer.current) clearTimeout(catPickerTimer.current);
    catPickerTimer.current = setTimeout(() => setFocusZone("watching"), 4000);
  };

  const dismissCatPicker = () => {
    setFocusZone("watching");
    if (catPickerTimer.current) clearTimeout(catPickerTimer.current);
  };

  const confirmCategory = () => {
    const cat = catList[catPickerIdx];
    if (!cat) { dismissCatPicker(); return; }
    setSelectedCategory(cat);
    // Switch to first channel in new category
    const newFiltered = channels.filter(c => {
      if (cat === "收藏") return favorites.includes(c.id);
      if (cat !== "全部") return c.category === cat;
      return true;
    });
    if (newFiltered.length > 0) {
      switchToChannel(newFiltered[0]);
    }
    dismissCatPicker();
  };

  // ─── Exit ───
  const handleExitBack = () => {
    if (exitPrompt) {
      localStorage.setItem("tv_last_channel", selectedChannel?.id || "");
      if (exitTimer.current) clearTimeout(exitTimer.current);
      if (window.history.length > 1) window.history.back();
      else (window as any).close?.();
    } else {
      setExitPrompt(true);
      exitTimer.current = setTimeout(() => setExitPrompt(false), 3000);
    }
  };

  // ─── Favorites ───
  const toggleFav = (id: string) => {
    const next = favorites.includes(id) ? favorites.filter(x => x !== id) : [...favorites, id];
    setFavorites(next); save("tv_favorites", next);
  };

  // ─── Channel operations (for settings modal) ───
  const delChannel = (id: string) => {
    const ch = channels.find(c => c.id === id);
    if (!ch || !window.confirm(`确定要删除「${ch.name}」吗？`)) return;
    const next = channels.filter(c => c.id !== id);
    setChannels(next);
    if (selectedChannel?.id === id) setSelectedChannel(next[0] || null);
    if (favorites.includes(id)) { const f = favorites.filter(x => x !== id); setFavorites(f); save("tv_favorites", f); }
  };

  const moveChannelUp = (id: string) => {
    const idx = channels.findIndex(c => c.id === id);
    if (idx <= 0) return;
    const next = [...channels];
    [next[idx], next[idx - 1]] = [next[idx - 1], next[idx]];
    setChannels(next);
  };

  const moveChannelDown = (id: string) => {
    const idx = channels.findIndex(c => c.id === id);
    if (idx < 0 || idx >= channels.length - 1) return;
    const next = [...channels];
    [next[idx], next[idx + 1]] = [next[idx + 1], next[idx]];
    setChannels(next);
  };

  const renameChannel = (id: string, newName: string) => {
    if (!newName.trim()) return;
    setChannels(prev => prev.map(c => c.id === id ? { ...c, name: newName.trim() } : c));
  };

  const renameCategory = (oldName: string, newName: string) => {
    if (!newName.trim() || oldName === newName.trim()) return;
    if (oldName === "全部" || oldName === "收藏") return;
    setChannels(prev => prev.map(c => c.category === oldName ? { ...c, category: newName.trim() } : c));
  };

  // ─── Add / Remove sources ───
  const addChannels = (newCh: TVChannel[], name: string, url: string) => {
    delPlaylist(url);
    const rec: CustomSource = { id: `src_${Date.now()}`, name, url, isActive: true, createdAt: Date.now() };
    setCustomSources(s => { const ss = [...s, rec]; save("tv_sources", ss); return ss; });
    const tagged = newCh.map(c => ({ ...c, id: `${rec.id}_${c.id}`, category: c.category || name }));
    setChannels(prev => [...prev, ...tagged]);
    if (tagged.length > 0) switchToChannel(tagged[0]);
  };

  const addSingle = (ch: TVChannel) => {
    setChannels(prev => [...prev, ch]);
    switchToChannel(ch);
  };

  const delPlaylist = (url: string) => {
    const src = customSources.find(s => s.url === url); if (!src) return;
    setCustomSources(s => { const ss = s.filter(x => x.url !== url); save("tv_sources", ss); return ss; });
    setChannels(prev => {
      const next = prev.filter(c => !c.id.startsWith(src.id));
      if (selectedChannel && selectedChannel.id.startsWith(src.id)) setSelectedChannel(next[0] || null);
      return next;
    });
  };

  const delCategory = (cat: string) => {
    if (cat === "全部" || cat === "收藏") return;
    const victims = channels.filter(c => c.category === cat);
    if (victims.length === 0) return;
    if (!window.confirm(`确定删除「${cat}」分类及其中 ${victims.length} 个频道吗？`)) return;
    setChannels(prev => {
      const next = prev.filter(c => c.category !== cat);
      if (selectedChannel && selectedChannel.category === cat) setSelectedChannel(next[0] || null);
      return next;
    });
    if (selectedCategory === cat) setSelectedCategory("全部");
  };

  const factoryReset = () => {
    if (!window.confirm("确定恢复出厂设置？所有自定义数据将被清除。")) return;
    ["tv_channels", "tv_favorites", "tv_sources", "tv_last_channel"].forEach(k => localStorage.removeItem(k));
    setCustomSources([]); setFavorites([]);
    setChannels(INITIAL_DEFAULT_CHANNELS);
    setSelectedChannel(INITIAL_DEFAULT_CHANNELS[0] || null);
    setSelectedCategory("全部");
    setFocusZone("watching");
  };

  const exportState = () => {
    const data = { sources: customSources, channels, favs: favorites };
    const b = new Blob([JSON.stringify(data)], { type: "application/json" });
    const a = document.createElement("a"); a.href = URL.createObjectURL(b);
    a.download = `tv-backup-${new Date().toISOString().slice(0, 10)}.json`; a.click();
  };

  const importState = (e: React.ChangeEvent<HTMLInputElement>) => {
    const f = e.target.files?.[0]; if (!f) return;
    const r = new FileReader(); r.onload = () => {
      try {
        const d = JSON.parse(r.result as string);
        if (d.sources) { setCustomSources(d.sources); save("tv_sources", d.sources); }
        if (d.favs) { setFavorites(d.favs); save("tv_favorites", d.favs); }
        if (d.channels) { setChannels(d.channels); if (d.channels.length > 0) switchToChannel(d.channels[0]); }
      } catch { alert("无效备份"); }
    }; r.readAsText(f);
  };

  // ═══════════════════════════════════════
  // D-PAD / KEY HANDLER
  // ═══════════════════════════════════════
  const handleKey = useCallback((action: string) => {
    if (showSourceModal) return;
    if (exitPrompt) { setExitPrompt(false); if (exitTimer.current) clearTimeout(exitTimer.current); }

    // ── Menu always opens settings ──
    if (action === "Menu") { setShowSourceModal(true); return; }

    // ── WATCHING ──
    if (focusZone === "watching") {
      if (action === "ArrowUp") { switchToPrevChannel(); return; }
      if (action === "ArrowDown") { switchToNextChannel(); return; }
      if (action === "ArrowLeft") { showCatPicker("left"); return; }
      if (action === "ArrowRight") { showCatPicker("right"); return; }
      if (action === "Enter" || action === "OK") { showInfo(); return; }
      if (action === "Backspace") { handleExitBack(); return; }
      return;
    }

    // ── CATEGORY PICKER ──
    if (focusZone === "categories") {
      if (action === "ArrowLeft") { setCatPickerIdx(i => i > 0 ? i - 1 : catList.length - 1); return; }
      if (action === "ArrowRight") { setCatPickerIdx(i => i < catList.length - 1 ? i + 1 : 0); return; }
      if (action === "Enter" || action === "OK") { confirmCategory(); return; }
      if (action === "ArrowUp") { dismissCatPicker(); switchToPrevChannel(); return; }
      if (action === "ArrowDown") { dismissCatPicker(); switchToNextChannel(); return; }
      if (action === "Backspace") { dismissCatPicker(); return; }
      return;
    }
  }, [focusZone, catPickerIdx, catList, selectedChannel, filteredChannels, showSourceModal, exitPrompt]);

  // ─── Keyboard listener ───
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (showSourceModal) return;
      if (["ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight", " "].includes(e.key)) e.preventDefault();
      const map: Record<string, string> = {
        ArrowUp: "ArrowUp", ArrowDown: "ArrowDown", ArrowLeft: "ArrowLeft", ArrowRight: "ArrowRight",
        Enter: "Enter", " ": "Menu", Backspace: "Backspace", Escape: "Backspace",
      };
      if (e.key === "m" || e.key === "M" || e.key === "ContextMenu") return handleKey("Menu");
      if (map[e.key]) handleKey(map[e.key]);
    };
    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  }, [handleKey, showSourceModal]);

  // ─── Request fullscreen on first user interaction ───
  useEffect(() => {
    const handler = () => {
      requestFullscreen();
      window.removeEventListener("click", handler);
      window.removeEventListener("keydown", handler);
    };
    window.addEventListener("click", handler);
    window.addEventListener("keydown", handler);
    return () => {
      window.removeEventListener("click", handler);
      window.removeEventListener("keydown", handler);
    };
  }, []);

  // ─── Keep focus on category picker timer reset ───
  useEffect(() => {
    if (focusZone === "categories") {
      if (catPickerTimer.current) clearTimeout(catPickerTimer.current);
      catPickerTimer.current = setTimeout(() => setFocusZone("watching"), 4000);
    }
    return () => { if (catPickerTimer.current) clearTimeout(catPickerTimer.current); };
  }, [focusZone, catPickerIdx]);

  // ═══════════════════════════════════════
  // RENDER
  // ═══════════════════════════════════════
  return (
    <div ref={videoContainerRef} className="w-screen h-screen bg-black text-white font-sans overflow-hidden select-none relative">

      {/* ── LAYER 0: Fullscreen video ── */}
      <TVPlayer channel={selectedChannel} />

      {/* ── Exit prompt ── */}
      <div className={`absolute bottom-24 left-1/2 -translate-x-1/2 z-50 pointer-events-none transition-all duration-300 ${
        exitPrompt ? "opacity-100 translate-y-0" : "opacity-0 translate-y-4"
      }`}>
        <div className="bg-black/85 backdrop-blur-xl px-8 py-4 rounded-2xl border border-white/10 text-center">
          <p className="text-white text-base font-semibold">再按一次返回键退出</p>
          <p className="text-white/40 text-xs mt-1">按其他键取消</p>
        </div>
      </div>

      {/* ── LAYER 1: OSD Info (shown on OK press, auto-hides) ── */}
      <div className={`absolute inset-0 z-10 pointer-events-none transition-opacity duration-300 ${showOsdInfo ? "opacity-100" : "opacity-0"}`}>
        {/* Logo top-left */}
        <div className="absolute top-6 left-6 flex items-center gap-3 bg-black/50 backdrop-blur-md px-4 py-2.5 rounded-2xl border border-white/10">
          <div className="w-8 h-8 bg-amber-600 rounded-lg flex items-center justify-center">
            <Tv className="w-4 h-4 text-black" />
          </div>
          <span className="text-white/90 text-base font-bold tracking-wide">万能电视直播</span>
        </div>

        {/* Settings gear — always clickable */}
        <button
          onClick={() => setShowSourceModal(true)}
          className="absolute top-6 right-6 pointer-events-auto p-3 bg-black/50 hover:bg-black/70 backdrop-blur-md rounded-xl border border-white/10 text-white/60 hover:text-amber-400 transition-all"
        >
          <Settings className="w-6 h-6" />
        </button>

        {/* Bottom info bar */}
        {selectedChannel && (
          <div className="absolute bottom-0 left-0 right-0 bg-gradient-to-t from-black/80 to-transparent px-8 pt-14 pb-6">
            <div className="flex items-center gap-4">
              <span className="flex h-2.5 w-2.5 rounded-full bg-emerald-500 animate-pulse" />
              <div>
                <div className="text-white text-lg font-bold">{selectedChannel.name}</div>
                <div className="text-white/40 text-sm">{selectedChannel.category}</div>
              </div>
              <div className="flex-1" />
              <div className="flex items-center gap-3 text-white/30 text-sm">
                <span className="flex items-center gap-1"><span className="px-2 py-0.5 bg-white/10 rounded text-xs">↑↓</span>换台</span>
                <span className="flex items-center gap-1"><span className="px-2 py-0.5 bg-white/10 rounded text-xs">←→</span>切分类</span>
                <span className="flex items-center gap-1"><span className="px-2 py-0.5 bg-white/10 rounded text-xs">OK</span>确认</span>
                <span className="flex items-center gap-1"><span className="px-2 py-0.5 bg-white/10 rounded text-xs">Menu</span>设置</span>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* ── LAYER 2: Category Picker (shown on Left/Right press) ── */}
      <div className={`absolute top-1/4 left-1/2 -translate-x-1/2 z-20 transition-all duration-300 ${
        focusZone === "categories" ? "opacity-100 translate-y-0" : "opacity-0 -translate-y-8 pointer-events-none"
      }`}>
        <div className="bg-black/85 backdrop-blur-xl px-6 py-4 rounded-3xl border border-white/15 shadow-2xl">
          <p className="text-white/40 text-xs text-center mb-3">← → 选择分类 · OK 确认 · ↑↓ 取消</p>
          <div className="flex gap-2 items-center">
            {catList.map((cat, i) => {
              const sel = selectedCategory === cat;
              const foc = focusZone === "categories" && catPickerIdx === i;
              return (
                <button
                  key={cat}
                  onClick={() => { setCatPickerIdx(i); }}
                  className={`shrink-0 px-5 py-2.5 rounded-xl text-sm font-bold whitespace-nowrap transition-all ${
                    sel ? "bg-amber-600 text-black" : "bg-white/10 text-white/70"
                  } ${foc ? "outline-3 outline-amber-400 outline-offset-1 outline scale-110 z-10" : ""}`}
                >
                  {cat === "收藏" && <Heart className="w-3 h-3 inline mr-1 fill-rose-500 text-rose-500" />}
                  {cat}
                </button>
              );
            })}
          </div>
        </div>
      </div>

      {/* ── LAYER 99: Settings Modal (Menu key) ── */}
      {showSourceModal && (
        <CustomSourceModal
          onAddChannels={addChannels}
          onAddSingleChannel={addSingle}
          customSources={customSources}
          onRemovePlaylist={delPlaylist}
          onClose={() => setShowSourceModal(false)}
          onExport={exportState}
          onImport={importState}
          channels={channels}
          onDeleteCategory={delCategory}
          onRenameCategory={renameCategory}
          onDeleteChannel={delChannel}
          onMoveChannelUp={moveChannelUp}
          onMoveChannelDown={moveChannelDown}
          onRenameChannel={renameChannel}
          onFactoryReset={factoryReset}
        />
      )}
    </div>
  );
}
