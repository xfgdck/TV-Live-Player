import React, { useState, useEffect, useMemo, useCallback, useRef } from "react";
import { TVChannel, CustomSource } from "./types";
import { INITIAL_DEFAULT_CHANNELS } from "./data/defaultChannels";
import TVPlayer from "./components/TVPlayer";
import CustomSourceModal from "./components/CustomSourceModal";
import { Heart, ChevronUp, ChevronDown, Trash2, Settings, Tv, X } from "lucide-react";

type FocusZone = "watching" | "overlay" | "categories" | "channels";

export default function App() {
  const [channels, setChannels] = useState<TVChannel[]>([]);
  const [favorites, setFavorites] = useState<string[]>([]);
  const [customSources, setCustomSources] = useState<CustomSource[]>([]);
  const [selectedChannel, setSelectedChannel] = useState<TVChannel | null>(null);
  const [selectedCategory, setSelectedCategory] = useState("全部");

  const [focusZone, setFocusZone] = useState<FocusZone>("watching");
  const [focusCatIdx, setFocusCatIdx] = useState(0);
  const [focusChIdx, setFocusChIdx] = useState(0);
  const [opsIdx, setOpsIdx] = useState(-1); // -1=not in ops, 0=⬆, 1=⬇, 2=🗑

  const [showSourceModal, setShowSourceModal] = useState(false);

  // Exit prompt
  const [exitPrompt, setExitPrompt] = useState(false);
  const exitTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const osdTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

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

  // ─── Save last channel on every switch ───
  const switchToChannel = useCallback((ch: TVChannel) => {
    setSelectedChannel(ch);
    localStorage.setItem("tv_last_channel", ch.id);
    setFocusZone("watching");
  }, []);

  // Persist channels to localStorage whenever they change
  useEffect(() => {
    save("tv_channels", channels);
  }, [channels]);

  useEffect(() => {
    save("tv_sources", customSources);
  }, [customSources]);

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

  // ─── Clamp indices ───
  useEffect(() => {
    if (focusCatIdx >= catList.length) setFocusCatIdx(Math.max(0, catList.length - 1));
  }, [catList, focusCatIdx]);
  useEffect(() => {
    if (focusChIdx >= filteredChannels.length) setFocusChIdx(Math.max(0, filteredChannels.length - 1));
    if (opsIdx >= 0 && opsIdx >= 3) setOpsIdx(2);
  }, [filteredChannels, focusChIdx, opsIdx]);

  useEffect(() => {
    if (focusZone === "channels" && opsIdx < 0) {
      document.getElementById(`ch-${focusChIdx}`)?.scrollIntoView({ block: "nearest", behavior: "smooth" });
    }
  }, [focusZone, focusChIdx, opsIdx]);

  // ─── OSD ───
  const showOsd = () => {
    setFocusZone("overlay");
    if (osdTimer.current) clearTimeout(osdTimer.current);
    osdTimer.current = setTimeout(() => { setFocusZone(p => p === "overlay" ? "watching" : p); }, 5000);
  };
  const hideOsd = () => { setFocusZone("watching"); if (osdTimer.current) clearTimeout(osdTimer.current); };

  // ─── Exit ───
  const handleExitBack = () => {
    if (exitPrompt) {
      // Second press: save & close
      localStorage.setItem("tv_last_channel", selectedChannel?.id || "");
      if (exitTimer.current) clearTimeout(exitTimer.current);
      // WebView exit: go back in history to close
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

  // ─── Delete channel ───
  const delChannel = (id: string) => {
    const ch = channels.find(c => c.id === id);
    if (!ch || !window.confirm(`确定要删除「${ch.name}」吗？`)) return;
    const next = channels.filter(c => c.id !== id);
    setChannels(next);
    if (selectedChannel?.id === id) setSelectedChannel(next[0] || null);
    if (favorites.includes(id)) { const f = favorites.filter(x => x !== id); setFavorites(f); save("tv_favorites", f); }
    setOpsIdx(-1);
  };

  // ─── Move ───
  const moveChannel = (dir: "up" | "down") => {
    if (focusZone !== "channels" || opsIdx < 0) return;
    const f = filteredChannels[focusChIdx];
    if (!f) return;
    const idx = channels.findIndex(c => c.id === f.id);
    if (idx < 0) return;
    const t = dir === "up" ? idx - 1 : idx + 1;
    if (t < 0 || t >= channels.length) return;
    const next = [...channels];
    [next[idx], next[t]] = [next[t], next[idx]];
    setChannels(next);
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

  // ─── Delete category ───
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
  };

  // ─── Factory reset ───
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
  // D-PAD
  // ═══════════════════════════════════════
  const handleKey = useCallback((action: string) => {
    if (showSourceModal) return;
    if (exitPrompt) { setExitPrompt(false); if (exitTimer.current) clearTimeout(exitTimer.current); }

    // ── Menu always opens settings ──
    if (action === "Menu") { setShowSourceModal(true); return; }

    // ── WATCHING ──
    if (focusZone === "watching") {
      if (action === "Enter") { showOsd(); return; }
      if (action === "Backspace") { handleExitBack(); return; }
      return; // ↑↓←→ do nothing in pure watching
    }

    // ── OVERLAY ──
    if (focusZone === "overlay") {
      if (action === "Enter") { setShowSourceModal(true); return; }
      if (action === "Backspace") { hideOsd(); return; }
      return; // ↑↓←→ do nothing in OSD
    }

    // ── CATEGORIES ──
    if (focusZone === "categories") {
      if (action === "ArrowLeft") { setFocusCatIdx(i => i > 0 ? i - 1 : catList.length - 1); return; }
      if (action === "ArrowRight") { setFocusCatIdx(i => i < catList.length - 1 ? i + 1 : 0); return; }
      if (action === "ArrowDown") { setFocusZone("channels"); setFocusChIdx(0); setOpsIdx(-1); return; }
      if (action === "ArrowUp" || action === "Backspace") { setFocusZone("watching"); return; }
      if (action === "Enter") {
        setSelectedCategory(catList[focusCatIdx] || "全部");
        setFocusZone("channels"); setFocusChIdx(0); setOpsIdx(-1);
        return;
      }
      return;
    }

    // ── CHANNELS ──
    if (focusZone === "channels") {
      // If in operations sub-mode
      if (opsIdx >= 0) {
        if (action === "ArrowUp" || action === "ArrowDown") { setOpsIdx(-1); return; }
        if (action === "ArrowLeft") { setOpsIdx(i => i > 0 ? i - 1 : 2); return; }
        if (action === "ArrowRight") { setOpsIdx(i => i < 2 ? i + 1 : 0); return; }
        if (action === "Enter") {
          if (opsIdx === 0) moveChannel("up");
          else if (opsIdx === 1) moveChannel("down");
          else if (opsIdx === 2) delChannel(filteredChannels[focusChIdx]?.id || "");
          return;
        }
        if (action === "Backspace") { setOpsIdx(-1); return; }
        return;
      }

      // Channel list navigation
      if (action === "ArrowUp") { setFocusChIdx(i => Math.max(0, i - 1)); return; }
      if (action === "ArrowDown") { setFocusChIdx(i => Math.min(filteredChannels.length - 1, i + 1)); return; }
      if (action === "ArrowLeft") { setFocusZone("categories"); setFocusCatIdx(catList.indexOf(selectedCategory)); return; }
      if (action === "ArrowRight") { setOpsIdx(0); return; } // Enter operations area
      if (action === "Enter") {
        const ch = filteredChannels[focusChIdx];
        if (ch) switchToChannel(ch);
        return;
      }
      if (action === "Backspace") { setFocusZone("watching"); return; }
      return;
    }
  }, [focusZone, focusCatIdx, focusChIdx, opsIdx, catList, filteredChannels, selectedChannel, selectedCategory, showSourceModal, exitPrompt]);

  // ─── Keyboard ───
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (showSourceModal) return;
      if (["ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight", " "].includes(e.key)) e.preventDefault();
      const map: Record<string, string> = {
        ArrowUp: "ArrowUp", ArrowDown: "ArrowDown", ArrowLeft: "ArrowLeft", ArrowRight: "ArrowRight",
        Enter: "Enter", " ": "Menu", Backspace: "Backspace", Escape: "Backspace",
      };
      // Map M/m or context menu key to Menu action
      if (e.key === "m" || e.key === "M" || e.key === "ContextMenu") return handleKey("Menu");
      if (map[e.key]) handleKey(map[e.key]);
    };
    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  }, [handleKey, showSourceModal]);

  const showAnyOverlay = focusZone === "overlay" || focusZone === "categories" || focusZone === "channels";
  const focusRing = "outline-3 outline-amber-500 outline-offset-1 outline";

  // ═══════════════════════════════════════
  // RENDER
  // ═══════════════════════════════════════
  return (
    <div className="w-screen h-screen bg-black text-white font-sans overflow-hidden select-none relative">

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

      {/* ── LAYER 1: OSD ── */}
      <div className={`absolute inset-0 z-10 pointer-events-none transition-opacity duration-300 ${showAnyOverlay ? "opacity-100" : "opacity-0"}`}>
        {/* Logo top-left */}
        <div className="absolute top-6 left-6 flex items-center gap-3 bg-black/50 backdrop-blur-md px-4 py-2.5 rounded-2xl border border-white/10">
          <div className="w-8 h-8 bg-amber-600 rounded-lg flex items-center justify-center">
            <Tv className="w-4 h-4 text-black" />
          </div>
          <span className="text-white/90 text-base font-bold tracking-wide">万能电视直播</span>
        </div>

        {/* Settings gear — focusable in overlay mode */}
        <button
          onClick={() => setShowSourceModal(true)}
          className={`absolute top-6 right-6 pointer-events-auto p-3 bg-black/50 hover:bg-black/70 backdrop-blur-md rounded-xl border transition-all ${
            focusZone === "overlay" ? "border-amber-500 text-amber-400 scale-110" : "border-white/10 text-white/60 hover:text-amber-400"
          }`}
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
              <div className="flex items-center gap-3 text-white/30 text-sm">
                <span className="flex items-center gap-1"><span className="px-2 py-0.5 bg-white/10 rounded text-xs">↓</span>换台</span>
                <span className="flex items-center gap-1"><span className="px-2 py-0.5 bg-white/10 rounded text-xs">← →</span>切分类</span>
                <span className="flex items-center gap-1"><span className="px-2 py-0.5 bg-white/10 rounded text-xs">OK</span>设置</span>
                <span className="flex items-center gap-1"><span className="px-2 py-0.5 bg-white/10 rounded text-xs">Menu</span>设置</span>
                <span className="flex items-center gap-1"><span className="px-2 py-0.5 bg-white/10 rounded text-xs">Back</span>关菜单</span>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* ── LAYER 2: Category selector ── */}
      {(focusZone === "categories" || focusZone === "channels") && (
        <div className="absolute top-24 left-6 right-20 z-20">
          <div className="flex gap-2 overflow-x-auto no-scrollbar bg-black/70 backdrop-blur-xl p-2.5 rounded-2xl border border-white/5">
            {catList.map((cat, i) => {
              const sel = selectedCategory === cat;
              const foc = focusZone === "categories" && focusCatIdx === i;
              return (
                <button
                  key={cat}
                  onClick={() => { if (focusZone === "categories") { setSelectedCategory(cat); setFocusZone("channels"); setFocusChIdx(0); setOpsIdx(-1); } }}
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

      {/* ── LAYER 3: Channel panel (right) ── */}
      <div className={`absolute right-0 top-0 bottom-0 w-[35%] min-w-[300px] bg-black/85 backdrop-blur-xl border-l border-white/10 z-20 transition-transform duration-300 flex flex-col ${
        focusZone === "channels" ? "translate-x-0" : "translate-x-full"
      }`}>
        <div className="shrink-0 px-5 py-4 border-b border-white/10 flex items-center justify-between">
          <div>
            <div className="text-base font-bold text-white">{selectedCategory}</div>
            <div className="text-xs text-white/40 mt-0.5">{filteredChannels.length} 个频道</div>
          </div>
          <button onClick={() => { setFocusZone("watching"); setOpsIdx(-1); }} className="p-2 hover:bg-white/10 rounded-lg text-white/50 hover:text-white transition-colors">
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto py-2 px-3 space-y-1">
          {filteredChannels.length === 0 ? (
            <div className="text-center py-16 text-white/30 text-base">暂无频道</div>
          ) : (
            filteredChannels.map((ch, i) => {
              const playing = selectedChannel?.id === ch.id;
              const foc = focusZone === "channels" && focusChIdx === i && opsIdx < 0;
              const inOps = focusZone === "channels" && focusChIdx === i && opsIdx >= 0;
              const isFav = favorites.includes(ch.id);
              const idx = channels.findIndex(c => c.id === ch.id);
              const canUp = idx > 0;
              const canDown = idx >= 0 && idx < channels.length - 1;

              return (
                <div key={ch.id} id={`ch-${i}`}
                  onClick={() => switchToChannel(ch)}
                  className={`px-4 py-3 rounded-xl flex items-center gap-3 cursor-pointer transition-all border ${
                    playing ? "bg-amber-600/15 border-amber-500/30" : "bg-white/5 border-transparent hover:bg-white/10"
                  } ${foc ? `${focusRing} scale-[1.02] bg-white/15 border-amber-400 z-10` : ""}
                  ${inOps ? "bg-white/10 border-amber-500/20" : ""}`}
                >
                  {ch.logo
                    ? <img src={ch.logo} alt="" referrerPolicy="no-referrer" className="w-8 h-8 object-contain rounded bg-black/50 shrink-0" onError={e => { (e.target as HTMLElement).style.display = "none"; }} />
                    : <div className="w-8 h-8 rounded bg-white/10 flex items-center justify-center text-lg shrink-0">📺</div>
                  }
                  <div className="min-w-0 flex-1">
                    <div className={`text-sm font-semibold truncate ${playing ? "text-amber-400" : "text-white"}`}>{ch.name}</div>
                    <div className="text-xs text-white/40 truncate">{ch.category}</div>
                  </div>

                  {/* Operation buttons — when this channel has ops focus */}
                  {inOps && (
                    <div className="flex items-center gap-0.5 shrink-0 animate-fade-in">
                      <button onClick={e => { e.stopPropagation(); if (canUp) moveChannel("up"); }}
                        disabled={!canUp}
                        className={`p-2 rounded-lg ${opsIdx === 0 ? `${focusRing} scale-110` : ""} ${canUp ? "text-amber-400 hover:bg-amber-500/20" : "text-white/20"}`}
                      ><ChevronUp className="w-4 h-4" /></button>
                      <button onClick={e => { e.stopPropagation(); if (canDown) moveChannel("down"); }}
                        disabled={!canDown}
                        className={`p-2 rounded-lg ${opsIdx === 1 ? `${focusRing} scale-110` : ""} ${canDown ? "text-amber-400 hover:bg-amber-500/20" : "text-white/20"}`}
                      ><ChevronDown className="w-4 h-4" /></button>
                      <button onClick={e => { e.stopPropagation(); delChannel(ch.id); }}
                        className={`p-2 rounded-lg ml-0.5 pl-1.5 border-l border-white/10 ${opsIdx === 2 ? `${focusRing} scale-110 text-red-300` : "text-red-400 hover:bg-red-500/20"}`}
                      ><Trash2 className="w-4 h-4" /></button>
                    </div>
                  )}

                  {/* Playing indicator */}
                  {playing && !foc && !inOps && (
                    <span className="text-xs text-amber-500 bg-amber-500/10 px-2 py-0.5 rounded-full font-bold shrink-0">ON</span>
                  )}

                  {/* Favorite */}
                  <button onClick={e => { e.stopPropagation(); toggleFav(ch.id); }}
                    className={`p-1.5 rounded-lg shrink-0 ${isFav ? "text-rose-500" : "text-white/30 hover:text-rose-400"}`}>
                    <Heart className={`w-4 h-4 ${isFav ? "fill-rose-500" : ""}`} />
                  </button>
                </div>
              );
            })
          )}
        </div>
      </div>

      {/* ── LAYER 99: Source Modal ── */}
      {showSourceModal && (
        <CustomSourceModal
          onAddChannels={addChannels} onAddSingleChannel={addSingle}
          customSources={customSources} onRemovePlaylist={delPlaylist}
          onClose={() => setShowSourceModal(false)}
          onExport={exportState} onImport={importState}
          channels={channels}
          onDeleteCategory={delCategory}
          onFactoryReset={factoryReset}
        />
      )}
    </div>
  );
}
