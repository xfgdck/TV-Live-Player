import React, { useEffect, useRef, useState } from "react";
import Hls from "hls.js";
import { TVChannel } from "../types";
import { 
  Play, 
  Pause, 
  Volume2, 
  VolumeX, 
  Maximize, 
  Minimize, 
  RotateCcw, 
  Sparkles,
  Tv, 
  Loader2, 
  AlertTriangle 
} from "lucide-react";

interface TVPlayerProps {
  channel: TVChannel | null;
  aspectRatio: "16-9" | "4-3" | "fill" | "contain";
  isMuted: boolean;
  volume: number;
  onMuteToggle: () => void;
  onVolumeChange: (vol: number) => void;
  onNetworkRetry?: () => void;
}

export default function TVPlayer({
  channel,
  aspectRatio,
  isMuted,
  volume,
  onMuteToggle,
  onVolumeChange,
  onNetworkRetry,
}: TVPlayerProps) {
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const hlsRef = useRef<Hls | null>(null);
  const [isPlaying, setIsPlaying] = useState<boolean>(false);
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [showControls, setShowControls] = useState<boolean>(true);
  const [isFullscreen, setIsFullscreen] = useState<boolean>(false);
  const controlsTimeoutRef = useRef<NodeJS.Timeout | null>(null);

  // Auto-hide controls after 3 seconds of inactivities
  const triggerShowControls = () => {
    setShowControls(true);
    if (controlsTimeoutRef.current) {
      clearTimeout(controlsTimeoutRef.current);
    }
    controlsTimeoutRef.current = setTimeout(() => {
      setShowControls(false);
    }, 3500);
  };

  useEffect(() => {
    const handleMouseMove = () => triggerShowControls();
    document.addEventListener("mousemove", handleMouseMove);
    return () => {
      document.removeEventListener("mousemove", handleMouseMove);
      if (controlsTimeoutRef.current) clearTimeout(controlsTimeoutRef.current);
    };
  }, []);

  // Update volume & mute states directly onto the media element
  useEffect(() => {
    if (videoRef.current) {
      videoRef.current.muted = isMuted;
      videoRef.current.volume = volume / 100;
    }
  }, [isMuted, volume]);

  // Load and play HLS stream on channel URL change
  const channelUrlPrimitive = channel?.url || "";
  useEffect(() => {
    setErrorMsg(null);
    setIsPlaying(false);
    
    const video = videoRef.current;
    if (!video || !channelUrlPrimitive) {
      setIsLoading(false);
      return;
    }

    setIsLoading(true);

    // Stop and destroy previous HLS instance if any
    if (hlsRef.current) {
      hlsRef.current.destroy();
      hlsRef.current = null;
    }

    // Playback logic
    if (Hls.isSupported()) {
      const hls = new Hls({
        enableWorker: true,
        lowLatencyMode: true,
        backBufferLength: 90,
        manifestLoadingTimeOut: 10000,
        manifestLoadingMaxRetry: 4,
        levelLoadingTimeOut: 10000,
        levelLoadingMaxRetry: 4,
      });

      hlsRef.current = hls;
      hls.loadSource(channelUrlPrimitive);
      hls.attachMedia(video);

      hls.on(Hls.Events.MANIFEST_PARSED, () => {
        setIsLoading(false);
        video.play()
          .then(() => setIsPlaying(true))
          .catch((err) => {
            console.warn("Autoplay block:", err);
            setIsPlaying(false);
          });
      });

      hls.on(Hls.Events.ERROR, (event, data) => {
        console.error("HLS error:", data);
        if (data.fatal) {
          switch (data.type) {
            case Hls.ErrorTypes.NETWORK_ERROR:
              console.log("Fatal network error, trying to recover...");
              hls.startLoad();
              break;
            case Hls.ErrorTypes.MEDIA_ERROR:
              console.log("Fatal media error, trying to recover...");
              hls.recoverMediaError();
              break;
            default:
              setIsLoading(false);
              setErrorMsg("直播流解码失败，此源可能已下线或不支持跨域播放。");
              hls.destroy();
              hlsRef.current = null;
              break;
          }
        }
      });
    } else if (video.canPlayType("application/vnd.apple.mpegurl")) {
      // Native HLS (mainly Safari / iOS)
      video.src = channelUrlPrimitive;
      
      const handleLoadedMetadata = () => {
        setIsLoading(false);
        video.play()
          .then(() => setIsPlaying(true))
          .catch((err) => {
            console.warn("Autoplay block:", err);
            setIsPlaying(false);
          });
      };

      const handleNativeError = () => {
        setIsLoading(false);
        setErrorMsg("此直播源连接失败。请稍后重试或更换其它频道。");
      };

      video.addEventListener("loadedmetadata", handleLoadedMetadata);
      video.addEventListener("error", handleNativeError);

      return () => {
        video.removeEventListener("loadedmetadata", handleLoadedMetadata);
        video.removeEventListener("error", handleNativeError);
      };
    } else {
      setIsLoading(false);
      setErrorMsg("您的浏览器暂不支持 HLS (.m3u8) 直播流格式。推荐使用 Chrome, Edge 或 Firefox 浏览器。");
    }

    return () => {
      if (hlsRef.current) {
        hlsRef.current.destroy();
        hlsRef.current = null;
      }
    };
  }, [channelUrlPrimitive]);

  // Handle Play/Pause
  const togglePlay = () => {
    const video = videoRef.current;
    if (!video) return;

    if (isPlaying) {
      video.pause();
      setIsPlaying(false);
    } else {
      video.play()
        .then(() => setIsPlaying(true))
        .catch(() => setIsPlaying(false));
    }
    triggerShowControls();
  };

  // Handle Fullscreen
  const playerContainerRef = useRef<HTMLDivElement | null>(null);
  const toggleFullscreen = () => {
    const container = playerContainerRef.current;
    if (!container) return;

    if (!document.fullscreenElement) {
      container.requestFullscreen()
        .then(() => setIsFullscreen(true))
        .catch((err) => console.error("Fullscreen error:", err));
    } else {
      document.exitFullscreen()
        .then(() => setIsFullscreen(false))
        .catch((err) => console.error("Exit fullscreen error:", err));
    }
    triggerShowControls();
  };

  useEffect(() => {
    const handleFullscreenChange = () => {
      setIsFullscreen(!!document.fullscreenElement);
    };
    document.addEventListener("fullscreenchange", handleFullscreenChange);
    return () => {
      document.removeEventListener("fullscreenchange", handleFullscreenChange);
    };
  }, []);

  // Determine aspect ratio class
  let aspectStyleClass = "aspect-video"; // Default 16:9
  let videoObjectFitClass = "object-contain";

  if (aspectRatio === "16-9") {
    aspectStyleClass = "aspect-video";
    videoObjectFitClass = "object-contain";
  } else if (aspectRatio === "4-3") {
    aspectStyleClass = "aspect-[4/3]";
    videoObjectFitClass = "object-contain";
  } else if (aspectRatio === "fill") {
    aspectStyleClass = "w-full h-full absolute inset-0";
    videoObjectFitClass = "object-stretch";
  } else if (aspectRatio === "contain") {
    aspectStyleClass = "w-full h-full absolute inset-0";
    videoObjectFitClass = "object-contain";
  }

  // Handle volume scroll wheel on stream
  const handleWheel = (e: React.WheelEvent) => {
    e.preventDefault();
    const delta = e.deltaY < 0 ? 5 : -5;
    const nextVolume = Math.min(100, Math.max(0, volume + delta));
    onVolumeChange(nextVolume);
    triggerShowControls();
  };

  const handleRetry = () => {
    setErrorMsg(null);
    setIsLoading(true);
    if (videoRef.current && channel?.url) {
      if (hlsRef.current) {
        hlsRef.current.destroy();
      }
      // Re-trigger load by simulating mounting
      const hls = new Hls({ enableWorker: true, lowLatencyMode: true });
      hlsRef.current = hls;
      hls.loadSource(channel.url);
      hls.attachMedia(videoRef.current);
      hls.on(Hls.Events.MANIFEST_PARSED, () => {
        setIsLoading(false);
        videoRef.current?.play().then(() => setIsPlaying(true));
      });
      hls.on(Hls.Events.ERROR, (_, data) => {
        if (data.fatal) {
          setIsLoading(false);
          setErrorMsg("重试失败，请检查您的网络连接或此源。");
        }
      });
    }
    if (onNetworkRetry) {
      onNetworkRetry();
    }
  };

  return (
    <div 
      ref={playerContainerRef}
      onWheel={handleWheel}
      onClick={triggerShowControls}
      className={`relative w-full bg-black overflow-hidden select-none group transition-all duration-300 rounded-2xl shadow-2xl border border-neutral-800 ${
        isFullscreen ? "h-screen rounded-none border-none" : "h-[240px] md:h-[480px] xl:h-[560px]"
      }`}
    >
      {/* Actual Video tag */}
      <video
        ref={videoRef}
        playsInline
        className={`w-full h-full transition-all duration-200 ${videoObjectFitClass}`}
        onClick={(e) => {
          e.stopPropagation();
          togglePlay();
        }}
        onPlay={() => setIsPlaying(true)}
        onPause={() => setIsPlaying(false)}
      />

      {/* Loading Overlay */}
      {isLoading && (
        <div className="absolute inset-0 z-10 flex flex-col items-center justify-center bg-black/85 backdrop-blur-md transition-opacity duration-300">
          <Loader2 className="w-12 h-12 text-amber-500 animate-spin mb-4" />
          <p className="text-white text-base font-medium font-sans tracking-wide">
            正在连接直播流...
          </p>
          {channel && (
            <p className="text-amber-200/60 text-xs mt-1 font-mono tracking-wider">
              {channel.name}
            </p>
          )}
        </div>
      )}

      {/* Error Overlay */}
      {errorMsg && !isLoading && (
        <div className="absolute inset-0 z-10 flex flex-col items-center justify-center bg-black/90 backdrop-blur-md px-6 text-center">
          <AlertTriangle className="w-14 h-14 text-amber-500 mb-4 animate-bounce" />
          <h3 className="text-white text-lg font-bold mb-2">流连接失败 / 离线</h3>
          <p className="text-neutral-300 text-sm max-w-md mb-6 leading-relaxed">
            {errorMsg}
          </p>
          <div className="flex gap-4">
            <button
              id="btn_retry_player"
              onClick={(e) => { e.stopPropagation(); handleRetry(); }}
              className="flex items-center gap-2 px-5 py-2.5 bg-neutral-900 hover:bg-neutral-800 text-neutral-200 rounded-xl text-sm transition-all border border-neutral-800 hover:border-neutral-700 shadow-xl font-medium"
            >
              <RotateCcw className="w-4 h-4 text-amber-500" /> 重新加载
            </button>
            {onNetworkRetry && (
              <button
                id="btn_fallback_default"
                onClick={(e) => { e.stopPropagation(); onNetworkRetry(); }}
                className="flex items-center gap-2 px-5 py-2.5 bg-amber-600 hover:bg-amber-500 text-neutral-950 font-bold rounded-xl text-sm transition-all shadow-lg hover:shadow-amber-500/15"
              >
                更换可用频道
              </button>
            )}
          </div>
        </div>
      )}

      {/* Empty State Overlay */}
      {!channel && !errorMsg && !isLoading && (
        <div className="absolute inset-0 z-10 flex flex-col items-center justify-center bg-[#050505] px-6 text-center">
          <Tv className="w-16 h-16 text-amber-550/20 mb-4 animate-pulse" />
          <h3 className="text-white text-lg font-serif font-semibold mb-1 tracking-wide">未选中频道 & No Selected Stream</h3>
          <p className="text-neutral-400 text-sm max-w-sm mb-4">
            请在侧边栏或下方选择一个电视频道，或在“自定义”中添加您的M3U订阅源。
          </p>
          <div className="flex items-center gap-2 px-3 py-1.5 bg-[#121212]/80 rounded-full border border-white/5 shadow-inner">
            <Sparkles className="w-3.5 h-3.5 text-amber-400 animate-pulse" />
            <span className="text-neutral-400 text-xs font-mono tracking-wider">
              支持遥控器 Arrow Up/Down 极速换台
            </span>
          </div>
        </div>
      )}

      {/* Controller Bars Overlay */}
      <div 
        className={`absolute inset-0 flex flex-col justify-between p-4 z-20 bg-gradient-to-t from-black/80 via-transparent to-black/60 pointer-events-none transition-all duration-300 ${
          showControls || !isPlaying ? "opacity-100" : "opacity-0 pointer-events-none translate-y-2"
        }`}
      >
        {/* Top bar */}
        <div className="flex items-center justify-between pointer-events-auto">
          <div className="flex items-center gap-3">
            <div className="px-2 py-1 bg-red-600 text-white text-[10px] font-bold tracking-wider rounded uppercase animate-pulse font-sans">
              LIVE
            </div>
            {channel && (
              <div>
                <h4 className="text-white font-sans font-semibold text-base shadow-sm">
                  {channel.name}
                </h4>
                <p className="text-neutral-300 text-[11px] font-mono shadow-sm">
                  [{channel.category}] {channel.url.substring(0, 48)}...
                </p>
              </div>
            )}
          </div>
          
          <div className="flex items-center gap-2 font-mono text-[10px] text-neutral-400 bg-black/40 px-3 py-1 rounded-full backdrop-blur-sm border border-neutral-800">
            音量 & 尺寸支持遥控器 D-pad 控制
          </div>
        </div>

        {/* Bottom bar */}
        <div className="flex flex-col gap-3 pointer-events-auto w-full">
          {/* Quick HUD Progress / Volume bar */}
          <div className="flex items-center justify-between w-full">
            <div className="flex items-center gap-4">
              <button
                id="btn_player_play_pause"
                onClick={(e) => { e.stopPropagation(); togglePlay(); }}
                className="p-2.5 bg-white text-black hover:bg-neutral-200 rounded-full transition-transform active:scale-95 shadow-lg"
              >
                {isPlaying ? <Pause className="w-5 h-5 fill-current" /> : <Play className="w-5 h-5 fill-current ml-0.5" />}
              </button>

              <div className="flex items-center gap-2 bg-black/50 backdrop-blur-sm border border-neutral-800 rounded-lg p-1 px-3">
                <button
                  id="btn_player_mute_toggle"
                  onClick={(e) => { e.stopPropagation(); onMuteToggle(); }}
                  className="text-neutral-300 hover:text-white transition-colors"
                >
                  {isMuted || volume === 0 ? <VolumeX className="w-4 h-4" /> : <Volume2 className="w-4 h-4" />}
                </button>
                <input
                  id="range_player_volume"
                  type="range"
                  min="0"
                  max="100"
                  value={isMuted ? 0 : volume}
                  onChange={(e) => {
                    e.stopPropagation();
                    onVolumeChange(Number(e.target.value));
                  }}
                  onClick={(e) => e.stopPropagation()}
                  className="w-16 md:w-24 accent-amber-500 h-1 rounded-lg cursor-pointer bg-neutral-700"
                />
                <span className="text-white text-xs font-mono min-w-[24px] text-right">
                  {isMuted ? 0 : volume}%
                </span>
              </div>
            </div>

            <div className="flex items-center gap-2">
              <div className="flex items-center gap-1 bg-black/50 backdrop-blur-sm border border-neutral-800 rounded-lg p-1 px-2.5 text-xs text-neutral-300">
                <span className="text-neutral-500 font-mono">画幅:</span>
                <span className="text-white uppercase font-sans font-medium">{aspectRatio}</span>
              </div>
              
              <button
                id="btn_player_fullscreen"
                onClick={(e) => { e.stopPropagation(); toggleFullscreen(); }}
                className="p-2 bg-black/50 backdrop-blur-sm border border-neutral-800 hover:bg-neutral-800 text-neutral-300 hover:text-white rounded-lg transition-transform active:scale-95"
                title="全屏模式 (F)"
              >
                {isFullscreen ? <Minimize className="w-4 h-4" /> : <Maximize className="w-4 h-4" />}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
