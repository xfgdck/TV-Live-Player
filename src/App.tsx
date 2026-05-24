import React, { useState, useEffect, useMemo, useCallback, useRef } from "react";
import { TVChannel, CustomSource } from "./types";
import { INITIAL_DEFAULT_CHANNELS } from "./data/defaultChannels";
import TVPlayer from "./components/TVPlayer";
import CustomSourceModal from "./components/CustomSourceModal";
import { Heart, ChevronUp, ChevronDown, Trash2, Settings, Tv, Play, Pause, X } from "lucide-react";

// Focus zones — layered from bottom to top
type FocusZone = "watching" | "overlay" | "categories" | "channels";

export default function App() {
  // ─── Data ───
  const [channels, setChannels] = useState<TVChannel[]>([]);
  const [favorites, setFavorites] = useState<string[]>([]);
  const [customSources, setCustomSources] = useState<CustomSource[]>([]);
  const [selectedChannel, setSelectedChannel] = useState<TVChannel | null>(null);
  const [selectedCategory, setSelectedCategory] = useState("全部");

  // ─── Focus ───
  const [focusZone, setFocusZone] = useState<FocusZone>("watching");
  const [focusCatIdx, setFocusCatIdx] = useState(0);
  const [focusChIdx, setFocusChIdx] = useState(0);

  // ─── Player state ───
  const [isPlaying, setIsPlaying] = useState(false);

  // ─── Modal ───
  const [showSourceModal, setShowSourceModal] = useState(false);

  // Overlay auto-hide timer
  const overlayTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  // ─── Init ───
  useEffect(() => {
    const loadSources = () => { try { const r = localStorage.getItem("tv_custom_playlist_sources"); if (r) setCustomSources(JSON.parse(r)); } catch {} };
    const loadFavs = () => { try { const r = localStorage.getItem("tv_favorites"); if (r) setFavorites(JSON.parse(r)); } catch {} };
    const loadCh = () => {
      let cust: TVChannel[] = [];
      try { const r = localStorage.getItem("tv_custom_channels"); if (r) cust = JSON.parse(r); } catch {}
      const all = [...INITIAL_DEFAULT_CHANNELS, ...cust];
      setChannels(all);
      if (all.length > 0) setSelectedChannel(all[0]);
    };
    loadSources(); loadFavs(); loadCh();
  }, []);

  // ─── Helpers ───
  const isBuiltin = (id: string) => INITIAL_DEFAULT_CHANNELS.some(d => d.id === id);
  const saveCh = (chs: TVChannel[]) => localStorage.setItem("tv_custom_channels", JSON.stringify(chs));
  const saveSrc = (ss: CustomSource[]) => localStorage.setItem("tv_custom_playlist_sources", JSON.stringify(ss));

  // ─── Categories ───
  const catList = useMemo(() => {
    const s = new Set<string>();
    if (favorites.length > 0) s.add("收藏");
    s.add("全部");
    channels.forEach(c => { if (c.category) s.add(c.category); });
    return Array.from(s);
  }, [channels, favorites]);

  // ─── Filtered channels ───
  const filteredChannels = useMemo(() => {
    return channels.filter(c => {
      if (selectedCategory === "收藏") return favorites.includes(c.id);
      if (selectedCategory !== "全部") return c.category === selectedCategory;
      return true;
    });
  }, [channels, selectedCategory, favorites]);

  // ─── Clamp ───
  useEffect(() => {
    if (focusCatIdx >= catList.length) setFocusCatIdx(Math.max(0, catList.length - 1));
    if (focusChIdx >= filteredChannels.length) setFocusChIdx(Math.max(0, filteredChannels.length - 1));
  }, [catList, filteredChannels]);

  useEffect(() => {
    if (focusZone === "channels") document.getElementById(`ch-${focusChIdx}`)?.scrollIntoView({ block: "nearest", behavior: "smooth" });
  }, [focusZone, focusChIdx]);

  // ─── Overlay (press OK to show/hide) ───
  const showOverlay = () => {
    setFocusZone("overlay");
    if (overlayTimer.current) clearTimeout(overlayTimer.current);
    overlayTimer.current = setTimeout(() => { if (focusZone === "overlay") setFocusZone("watching"); }, 5000);
  };

  const hideOverlay = () => {
    setFocusZone("watching");
    if (overlayTimer.current) clearTimeout(overlayTimer.current);
  };

  // ─── Favorites ───
  const toggleFav = (id: string) => {
    const next = favorites.includes(id) ? favorites.filter(x => x !== id) : [...favorites, id];
    setFavorites(next); localStorage.setItem("tv_favorites", JSON.stringify(next));
  };

  // ─── Delete ───
  const delChannel = (id: string) => {
    if (isBuiltin(id)) return;
    const ch = channels.find(c => c.id === id);
    if (!ch || !confirm(`删除「${ch.name}」？`)) return;
    const cust = channels.filter(c => !isBuiltin(c.id)).filter(c => c.id !== id);
    saveCh(cust); setChannels([...INITIAL_DEFAULT_CHANNELS, ...cust]);
    if (selectedChannel?.id === id) setSelectedChannel(channels[0] || null);
    if (favorites.includes(id)) { const f = favorites.filter(x => x !== id); setFavorites(f); localStorage.setItem("tv_favorites", JSON.stringify(f)); }
  };

  // ─── Move ───
  const moveChannel = (dir: "up" | "down") => {
    if (focusZone !== "channels") return;
    const f = filteredChannels[focusChIdx];
    if (!f || isBuiltin(f.id)) return;
    const cust = channels.filter(c => !isBuiltin(c.id));
    const i = cust.findIndex(c => c.id === f.id);
    if (i < 0) return; const t = dir === "up" ? i - 1 : i + 1;
    if (t < 0 || t >= cust.length) return;
    [cust[i], cust[t]] = [cust[t], cust[i]];
    saveCh(cust); setChannels([...INITIAL_DEFAULT_CHANNELS, ...cust]);
  };

  // ─── Add / Remove ───
  const addChannels = (newCh: TVChannel[], name: string, url: string) => {
    if (customSources.some(s => s.url === url)) delPlaylist(url);
    const rec: CustomSource = { id: `src_${Date.now()}`, name, url, isActive: true, createdAt: Date.now() };
    const ss = [...customSources, rec]; setCustomSources(ss); saveSrc(ss);
    const tagged = newCh.map(c => ({ ...c, id: `${rec.id}_${c.id}`, category: c.category || name }));
    const cust = channels.filter(c => !isBuiltin(c.id)).concat(tagged);
    saveCh(cust); setChannels([...INITIAL_DEFAULT_CHANNELS, ...cust]);
    if (tagged.length > 0) { setSelectedChannel(tagged[0]); setFocusZone("watching"); }
  };
  const addSingle = (ch: TVChannel) => {
    const cust = channels.filter(c => !isBuiltin(c.id)).concat(ch);
    saveCh(cust); setChannels([...INITIAL_DEFAULT_CHANNELS, ...cust]);
    setSelectedChannel(ch); setFocusZone("watching");
  };
  const delPlaylist = (url: string) => {
    const src = customSources.find(s => s.url === url); if (!src) return;
    setCustomSources(customSources.filter(s => s.url !== url)); saveSrc(customSources.filter(s => s.url !== url));
    const cust = channels.filter(c => !isBuiltin(c.id)).filter(c => !c.id.startsWith(src.id));
    saveCh(cust); setChannels([...INITIAL_DEFAULT_CHANNELS, ...cust]);
    if (selectedChannel?.id.startsWith(src.id)) setSelectedChannel(channels[0] || null);
  };
  const clearAll = () => {
    if (!confirm("清除所有自定义数据？")) return;
    ["tv_custom_playlist_sources","tv_custom_channels","tv_favorites"].forEach(k => localStorage.removeItem(k));
    setCustomSources([]); setFavorites([]); setChannels(INITIAL_DEFAULT_CHANNELS);
    setSelectedChannel(INITIAL_DEFAULT_CHANNELS[0] || null); setSelectedCategory("全部");
  };
  const exportState = () => {
    const data = { sources: customSources, customChannels: channels.filter(c => !isBuiltin(c.id)), favs: favorites };
    const b = new Blob([JSON.stringify(data)], { type: "application/json" });
    const a = document.createElement("a"); a.href = URL.createObjectURL(b);
    a.download = `tv-backup-${new Date().toISOString().slice(0,10)}.json`; a.click();
  };
  const importState = (e: React.ChangeEvent<HTMLInputElement>) => {
    const f = e.target.files?.[0]; if (!f) return;
    const r = new FileReader(); r.onload = () => {
      try {
        const d = JSON.parse(r.result as string);
        if (d.sources) { setCustomSources(d.sources); saveSrc(d.sources); }
        if (d.favs) { setFavorites(d.favs); localStorage.setItem("tv_favorites", JSON.stringify(d.favs)); }
        if (d.customChannels) { saveCh(d.customChannels); setChannels([...INITIAL_DEFAULT_CHANNELS,...d.customChannels]); }
      } catch { alert("无效备份"); }
    }; r.readAsText(f);
  };
  const playNextCh = () => {
    const idx = selectedChannel ? channels.findIndex(c => c.id === selectedChannel.id) : -1;
    setSelectedChannel(channels[idx < channels.length - 1 ? idx + 1 : 0] || channels[0]);
  };
  const handlePlayState = useCallback((p: boolean) => setIsPlaying(p), []);

  // ═══════════════════════════════════════
  // D-PAD
  // ═══════════════════════════════════════
  const handleKey = useCallback((action: string) => {
    if (showSourceModal) return;

    if (focusZone === "watching") {
      if (action === "Enter") { showOverlay(); return; }
      if (action === "ArrowDown") {
        setFocusZone("channels"); setFocusChIdx(0);
        if (selectedChannel) { const idx = filteredChannels.findIndex(c => c.id === selectedChannel.id); if (idx >= 0) setFocusChIdx(idx); }
        return;
      }
      if (action === "ArrowUp") { setFocusZone("categories"); setFocusCatIdx(catList.indexOf(selectedCategory)); return; }
      return;
    }

    if (focusZone === "overlay") {
      if (action === "Enter" || action === "Backspace") { hideOverlay(); return; }
      if (action === "ArrowDown") { setFocusZone("channels"); setFocusChIdx(0); return; }
      if (action === "ArrowUp") { setFocusZone("categories"); setFocusCatIdx(catList.indexOf(selectedCategory)); return; }
      return;
    }

    if (focusZone === "categories") {
      if (action === "ArrowLeft") { setFocusCatIdx(i => i > 0 ? i - 1 : catList.length - 1); return; }
      if (action === "ArrowRight") { setFocusCatIdx(i => i < catList.length - 1 ? i + 1 : 0); return; }
      if (action === "Enter") { setSelectedCategory(catList[focusCatIdx] || "全部"); setFocusZone("channels"); setFocusChIdx(0); return; }
      if (action === "ArrowDown") { setFocusZone("channels"); setFocusChIdx(0); return; }
      if (action === "ArrowUp" || action === "Backspace") { setFocusZone("watching"); return; }
      return;
    }

    if (focusZone === "channels") {
      if (action === "ArrowUp") { setFocusChIdx(i => Math.max(0, i - 1)); return; }
      if (action === "ArrowDown") { setFocusChIdx(i => Math.min(filteredChannels.length - 1, i + 1)); return; }
      if (action === "Enter") { const ch = filteredChannels[focusChIdx]; if (ch) { setSelectedChannel(ch); setFocusZone("watching"); } return; }
      if (action === "Backspace" || action === "ArrowRight") { setFocusZone("watching"); return; }
      if (action === "ArrowLeft") { setFocusZone("categories"); setFocusCatIdx(catList.indexOf(selectedCategory)); return; }
      if (action === "CtrlArrowUp") { moveChannel("up"); return; }
      if (action === "CtrlArrowDown") { moveChannel("down"); return; }
      if (action === "DeleteChannel") { const ch = filteredChannels[focusChIdx]; if (ch) delChannel(ch.id); return; }
      return;
    }
  }, [focusZone, focusCatIdx, focusChIdx, catList, filteredChannels, selectedChannel, selectedCategory, showSourceModal]);

  // ─── Keyboard ───
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (showSourceModal) return;
      if (["ArrowUp","ArrowDown","ArrowLeft","ArrowRight"," "].includes(e.key)) e.preventDefault();
      const map: Record<string,string> = {
        ArrowUp:"ArrowUp", ArrowDown:"ArrowDown", ArrowLeft:"ArrowLeft", ArrowRight:"ArrowRight",
        Enter:"Enter", " ":"Enter", Backspace:"Backspace", Escape:"Backspace", Delete:"DeleteChannel"
      };
      if (e.key === "ArrowUp" && (e.ctrlKey||e.metaKey)) return handleKey("CtrlArrowUp");
      if (e.key === "ArrowDown" && (e.ctrlKey||e.metaKey)) return handleKey("CtrlArrowDown");
      if (map[e.key]) handleKey(map[e.key]);
    };
    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  }, [handleKey, showSourceModal]);

  const showOSD = focusZone === "overlay" || focusZone === "categories" || focusZone === "channels";
  const focusRing = "outline-3 outline-amber-500 outline-offset-1 outline";

  // ═══════════════════════════════════════
  // RENDER
  // ═══════════════════════════════════════
  return (
    <div className="w-screen h-screen bg-black text-white font-sans overflow-hidden select-none relative">

      {/* ══════════════════════
          LAYER 0: Fullscreen video
          ══════════════════════ */}
      <TVPlayer
        channel={selectedChannel}
        onNetworkRetry={playNextCh}
        onPlayState={handlePlayState}
      />

      {/* ═══════════════════════════════════════
          LAYER 1: OSD — shown on Enter/Menu
          ═══════════════════════════════════════ */}
      <div className={`absolute inset-0 z-10 pointer-events-none transition-opacity duration-300 ${showOSD ? "opacity-100" : "opacity-0"}`}>

        {/* App logo — top-left */}
        <div className="absolute top-6 left-6 flex items-center gap-3 bg-black/50 backdrop-blur-md px-4 py-2.5 rounded-2xl border border-white/10">
          <div className="w-8 h-8 bg-amber-600 rounded-lg flex items-center justify-center">
            <Tv className="w-4 h-4 text-black" />
          </div>
          <span className="text-white/90 text-base font-bold tracking-wide">万能电视直播</span>
        </div>

        {/* Settings — top-right */}
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
              <span className="flex h-2.5 w-2.5 rounded-full bg-emerald-500" />
              <div>
                <div className="text-white text-lg font-bold">{selectedChannel.name}</div>
                <div className="text-white/40 text-sm">{selectedChannel.category}</div>
              </div>
              <div className="flex-1" />
              <div className="flex items-center gap-2 text-white/30 text-sm">
                <span className="flex items-center gap-1"><span className="px-2 py-0.5 bg-white/10 rounded text-xs">↓</span>换台</span>
                <span className="flex items-center gap-1"><span className="px-2 py-0.5 bg-white/10 rounded text-xs">Enter</span>播放/暂停</span>
                <span className="flex items-center gap-1"><span className="px-2 py-0.5 bg-white/10 rounded text-xs">← →</span>切分类</span>
                <span className="flex items-center gap-1"><span className="px-2 py-0.5 bg-white/10 rounded text-xs">Back</span>关菜单</span>
              </div>
            </div>
          </div>
        )}

        {/* Play/Pause button — bottom-center left area */}
        <button
          onClick={() => {
            const v = document.querySelector("video") as HTMLVideoElement | null;
            if (!v) return;
            if (v.paused) v.play().then(() => setIsPlaying(true)).catch(() => {});
            else { v.pause(); setIsPlaying(false); }
          }}
          className="pointer-events-auto absolute bottom-28 left-8 p-5 bg-white/15 hover:bg-white/25 backdrop-blur-md rounded-full transition-colors"
        >
          {isPlaying ? <Pause className="w-7 h-7 fill-white text-white" /> : <Play className="w-7 h-7 fill-white text-white ml-0.5" />}
        </button>
      </div>

      {/* ═══════════════════════════════════════
          LAYER 2: Category selector (top)
          ═══════════════════════════════════════ */}
      {(focusZone === "categories" || focusZone === "channels") && (
        <div className="absolute top-24 left-6 right-20 z-20">
          <div className="flex gap-2 overflow-x-auto no-scrollbar bg-black/70 backdrop-blur-xl p-2.5 rounded-2xl border border-white/5">
            {catList.map((cat, i) => {
              const sel = selectedCategory === cat;
              const foc = focusZone === "categories" && focusCatIdx === i;
              return (
                <button
                  key={cat}
                  onClick={() => { if (focusZone === "categories") { setSelectedCategory(cat); setFocusZone("channels"); setFocusChIdx(0); } }}
                  className={`shrink-0 px-5 py-2.5 rounded-xl text-sm font-bold whitespace-nowrap transition-all ${
                    sel ? "bg-amber-600 text-black" : "bg-white/10 text-white/80"
                  } ${foc ? `${focusRing} scale-105 z-10` : ""}`}
                >
                  {cat === "收藏" && <Heart className="w-3 h-3 inline mr-1 fill-rose-500 text-rose-500" />}
                  {cat}
                </button>
              );
            })}
          </div>
        </div>
      )}

      {/* ═══════════════════════════════════════
          LAYER 3: Channel panel (right-side slide)
          ═══════════════════════════════════════ */}
      <div className={`absolute right-0 top-0 bottom-0 w-[35%] min-w-[300px] bg-black/85 backdrop-blur-xl border-l border-white/10 z-20 transition-transform duration-300 flex flex-col ${
        focusZone === "channels" ? "translate-x-0" : "translate-x-full"
      }`}>
        <div className="shrink-0 px-5 py-4 border-b border-white/10 flex items-center justify-between">
          <div>
            <div className="text-base font-bold text-white">{selectedCategory}</div>
            <div className="text-xs text-white/40 mt-0.5">{filteredChannels.length} 个频道</div>
          </div>
          <button onClick={() => setFocusZone("watching")} className="p-2 hover:bg-white/10 rounded-lg text-white/50 hover:text-white transition-colors">
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto py-2 px-3 space-y-1">
          {filteredChannels.map((ch, i) => {
            const playing = selectedChannel?.id === ch.id;
            const foc = focusZone === "channels" && focusChIdx === i;
            const custom = !isBuiltin(ch.id);
            const isFav = favorites.includes(ch.id);
            const custList = channels.filter(c => !isBuiltin(c.id));
            const ci = custom ? custList.findIndex(c => c.id === ch.id) : -1;

            return (
              <div
                key={ch.id} id={`ch-${i}`}
                onClick={() => { setSelectedChannel(ch); setFocusZone("watching"); }}
                className={`px-4 py-3 rounded-xl flex items-center gap-3 cursor-pointer transition-all border ${
                  playing ? "bg-amber-600/15 border-amber-500/30" : "bg-white/5 border-transparent hover:bg-white/10"
                } ${foc ? `${focusRing} scale-[1.02] bg-white/15 border-amber-400 z-10` : ""}`}
              >
                {ch.logo
                  ? <img src={ch.logo} alt="" referrerPolicy="no-referrer" className="w-8 h-8 object-contain rounded bg-black/50 shrink-0" onError={e => { (e.target as HTMLElement).style.display = "none"; }} />
                  : <div className="w-8 h-8 rounded bg-white/10 flex items-center justify-center text-lg shrink-0">📺</div>
                }
                <div className="min-w-0 flex-1">
                  <div className={`text-sm font-semibold truncate ${playing ? "text-amber-400" : "text-white"}`}>{ch.name}</div>
                  <div className="text-xs text-white/40 truncate">{ch.category}</div>
                </div>
                {foc && custom && (
                  <div className="flex items-center gap-0.5 shrink-0 animate-fade-in">
                    <button onClick={e => { e.stopPropagation(); if (ci > 0) moveChannel("up"); }} disabled={ci <= 0} className={`p-2 rounded-lg ${ci > 0 ? "text-amber-400 hover:bg-amber-500/20" : "text-white/20"}`}><ChevronUp className="w-4 h-4" /></button>
                    <button onClick={e => { e.stopPropagation(); if (ci >= 0 && ci < custList.length - 1) moveChannel("down"); }} disabled={ci < 0 || ci >= custList.length - 1} className={`p-2 rounded-lg ${ci >= 0 && ci < custList.length - 1 ? "text-amber-400 hover:bg-amber-500/20" : "text-white/20"}`}><ChevronDown className="w-4 h-4" /></button>
                    <button onClick={e => { e.stopPropagation(); delChannel(ch.id); }} className="p-2 text-red-400 hover:bg-red-500/20 rounded-lg ml-0.5 pl-1.5 border-l border-white/10"><Trash2 className="w-4 h-4" /></button>
                  </div>
                )}
                {playing && !foc && <span className="text-xs text-amber-500 bg-amber-500/10 px-2 py-0.5 rounded-full font-bold shrink-0">ON</span>}
                <button onClick={e => { e.stopPropagation(); toggleFav(ch.id); }} className={`p-1.5 rounded-lg shrink-0 ${isFav ? "text-rose-500" : "text-white/30 hover:text-rose-400"}`}>
                  <Heart className={`w-4 h-4 ${isFav ? "fill-rose-500" : ""}`} />
                </button>
              </div>
            );
          })}
        </div>
      </div>

      {/* ═══════════════════════════════════════
          LAYER 99: Source Modal
          ═══════════════════════════════════════ */}
      {showSourceModal && (
        <CustomSourceModal
          onAddChannels={addChannels} onAddSingleChannel={addSingle}
          customSources={customSources} onRemovePlaylist={delPlaylist}
          onClose={() => setShowSourceModal(false)}
          onExport={exportState} onImport={importState}
        />
      )}
    </div>
  );
}
