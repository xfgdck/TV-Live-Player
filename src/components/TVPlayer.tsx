import React, { useEffect, useRef, useState, useCallback } from "react";
import Hls from "hls.js";
import { TVChannel } from "../types";
import { Loader2, AlertTriangle, Maximize, Minimize } from "lucide-react";

interface TVPlayerProps {
  channel: TVChannel | null;
}

export default function TVPlayer({ channel }: TVPlayerProps) {
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const containerRef = useRef<HTMLDivElement | null>(null);
  const hlsRef = useRef<Hls | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [isFullscreen, setIsFullscreen] = useState(false);

  // ─── Fullscreen tracking ───
  useEffect(() => {
    const handler = () => {
      setIsFullscreen(!!document.fullscreenElement);
    };
    document.addEventListener("fullscreenchange", handler);
    return () => document.removeEventListener("fullscreenchange", handler);
  }, []);

  const toggleFullscreen = useCallback(() => {
    try {
      if (document.fullscreenElement) {
        document.exitFullscreen?.();
      } else {
        containerRef.current?.requestFullscreen?.().catch(() => {});
      }
    } catch {}
  }, []);

  const chUrl = channel?.url || "";
  useEffect(() => {
    setErrorMsg(null);
    const video = videoRef.current;
    if (!video || !chUrl) { setIsLoading(false); return; }
    setIsLoading(true);
    if (hlsRef.current) { hlsRef.current.destroy(); hlsRef.current = null; }

    if (Hls.isSupported()) {
      const hls = new Hls({
        enableWorker: true, lowLatencyMode: true,
        manifestLoadingTimeOut: 10000, manifestLoadingMaxRetry: 4,
        levelLoadingTimeOut: 10000, levelLoadingMaxRetry: 4,
      });
      hlsRef.current = hls;
      hls.loadSource(chUrl);
      hls.attachMedia(video);
      hls.on(Hls.Events.MANIFEST_PARSED, () => { setIsLoading(false); video.play().catch(() => {}); });
      hls.on(Hls.Events.ERROR, (_, data) => {
        if (data.fatal) {
          if (data.type === Hls.ErrorTypes.NETWORK_ERROR) hls.startLoad();
          else if (data.type === Hls.ErrorTypes.MEDIA_ERROR) hls.recoverMediaError();
          else { setIsLoading(false); setErrorMsg("直播流解码失败"); hls.destroy(); hlsRef.current = null; }
        }
      });
    } else if (video.canPlayType("application/vnd.apple.mpegurl")) {
      video.src = chUrl;
      const onMeta = () => { setIsLoading(false); video.play().catch(() => {}); };
      const onErr = () => { setIsLoading(false); setErrorMsg("连接失败"); };
      video.addEventListener("loadedmetadata", onMeta);
      video.addEventListener("error", onErr);
      return () => { video.removeEventListener("loadedmetadata", onMeta); video.removeEventListener("error", onErr); };
    } else {
      setIsLoading(false); setErrorMsg("不支持 HLS 格式");
    }
    return () => { if (hlsRef.current) { hlsRef.current.destroy(); hlsRef.current = null; } };
  }, [chUrl]);

  return (
    <div ref={containerRef} className="absolute inset-0 bg-black" onDoubleClick={toggleFullscreen}>
      <video ref={videoRef} playsInline className="w-full h-full object-cover" />

      {/* Fullscreen toggle button (hover to reveal) */}
      <button
        onClick={toggleFullscreen}
        className="absolute bottom-20 right-6 z-10 p-2 bg-black/40 hover:bg-black/60 backdrop-blur-sm rounded-lg text-white/50 hover:text-white/80 transition-all opacity-0 hover:opacity-100"
        title={isFullscreen ? "退出全屏" : "全屏"}
      >
        {isFullscreen ? <Minimize className="w-5 h-5" /> : <Maximize className="w-5 h-5" />}
      </button>

      {isLoading && (
        <div className="absolute inset-0 flex flex-col items-center justify-center bg-black/90 z-10">
          <Loader2 className="w-20 h-20 text-amber-500 animate-spin mb-6" />
          <p className="text-white text-2xl font-medium">连接直播流...</p>
          {channel && <p className="text-amber-200/60 text-base mt-3">{channel.name}</p>}
        </div>
      )}
      {errorMsg && !isLoading && (
        <div className="absolute inset-0 flex flex-col items-center justify-center bg-black/90 z-10 px-12 text-center">
          <AlertTriangle className="w-20 h-20 text-amber-500 mb-4" />
          <p className="text-white text-2xl font-bold mb-2">连接失败</p>
          <p className="text-neutral-400 text-lg">{errorMsg}</p>
        </div>
      )}
      {!channel && !errorMsg && !isLoading && (
        <div className="absolute inset-0 flex flex-col items-center justify-center bg-black z-10">
          <span className="text-8xl mb-8">📺</span>
          <p className="text-white text-3xl font-bold mb-3">欢迎使用电视直播</p>
          <p className="text-neutral-400 text-xl">↑↓ 换台 · ←→ 切分类 · Menu 设置</p>
        </div>
      )}
    </div>
  );
}
