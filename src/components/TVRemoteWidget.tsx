import React from "react";
import { 
  ChevronUp, 
  ChevronDown, 
  ChevronLeft, 
  ChevronRight, 
  CornerDownLeft, 
  Circle,
  Tv, 
  Volume2, 
  VolumeX,
  X 
} from "lucide-react";

interface TVRemoteWidgetProps {
  onPressKey: (key: string) => void;
  onToggleRemote: () => void;
  isOpen: boolean;
}

export default function TVRemoteWidget({
  onPressKey,
  onToggleRemote,
  isOpen,
}: TVRemoteWidgetProps) {
  if (!isOpen) {
    return (
      <button
        id="btn_toggle_remote_on"
        onClick={onToggleRemote}
        className="fixed bottom-6 right-6 z-40 p-4 bg-gradient-to-tr from-[#1a1a1a] to-[#2a2a2a] hover:from-[#252525] hover:to-[#353535] text-white rounded-full shadow-2xl border border-white/5 flex items-center gap-2 group transition-all duration-300 hover:scale-105"
        title="打开虚拟电视遥控器"
      >
        <Tv className="w-5 h-5 text-amber-500 group-hover:rotate-12 transition-transform" />
        <span className="text-xs font-semibold font-sans tracking-wide pr-1">电视遥控器助手</span>
      </button>
    );
  }

  return (
    <div className="fixed bottom-6 right-6 z-40 bg-[#121212]/95 backdrop-blur-md border border-white/5 rounded-[36px] p-5 shadow-3xl w-[190px] flex flex-col items-center select-none animate-slide-up">
      {/* Header bar */}
      <div className="flex items-center justify-between w-full mb-4 px-1">
        <div className="flex items-center gap-1">
          <Tv className="w-3.5 h-3.5 text-amber-500" />
          <span className="text-[10px] font-bold tracking-widest text-neutral-400 uppercase">Remote</span>
        </div>
        <button
          id="btn_toggle_remote_off"
          onClick={onToggleRemote}
          className="text-neutral-550 hover:text-white transition-colors"
        >
          <X className="w-4 h-4" />
        </button>
      </div>

      {/* Numerical buttons / Extra controls container */}
      <div className="flex gap-2 mb-3 w-full justify-around">
        <button
          id="btn_remote_vol_down"
          onClick={() => onPressKey("VolumeDown")}
          className="w-10 h-10 bg-neutral-800 active:bg-neutral-750 hover:bg-neutral-750 rounded-full flex flex-col items-center justify-center text-neutral-300 active:scale-95 transition-all border border-neutral-800"
          title="减少音量"
        >
          <VolumeX className="w-3.5 h-3.5" />
          <span className="text-[8px] font-bold font-mono mt-0.5">V-</span>
        </button>
        <button
          id="btn_remote_vol_up"
          onClick={() => onPressKey("VolumeUp")}
          className="w-10 h-10 bg-neutral-800 active:bg-neutral-750 hover:bg-neutral-750 rounded-full flex flex-col items-center justify-center text-neutral-300 active:scale-95 transition-all border border-neutral-800"
          title="增加音量"
        >
          <Volume2 className="w-3.5 h-3.5" />
          <span className="text-[8px] font-bold font-mono mt-0.5">V+</span>
        </button>
      </div>

      {/* D-Pad Controller Wheel */}
      <div className="relative w-36 h-36 bg-neutral-950 rounded-full flex items-center justify-center border border-neutral-800 p-2 shadow-inner">
        
        {/* Up direction */}
        <button
          id="btn_remote_up"
          onClick={() => onPressKey("ArrowUp")}
          className="absolute top-1.5 w-11 h-9 bg-neutral-850 hover:bg-neutral-800 active:bg-neutral-700 text-white rounded-t-full flex justify-center items-center transition-colors active:scale-95 border-b border-neutral-900"
          title="换台上 (ArrowUp)"
        >
          <ChevronUp className="w-5 h-5" />
        </button>

        {/* Left direction */}
        <button
          id="btn_remote_left"
          onClick={() => onPressKey("ArrowLeft")}
          className="absolute left-1.5 w-9 h-11 bg-neutral-850 hover:bg-neutral-800 active:bg-neutral-700 text-white rounded-l-full flex justify-center items-center transition-colors active:scale-95 border-r border-neutral-900"
          title="分类左 (ArrowLeft)"
        >
          <ChevronLeft className="w-5 h-5" />
        </button>

        {/* Center OK Button */}
        <button
          id="btn_remote_ok"
          onClick={() => onPressKey("Enter")}
          className="w-14 h-14 bg-amber-600 hover:bg-amber-550 active:bg-amber-700 text-neutral-950 rounded-full flex flex-col items-center justify-center font-bold tracking-wider text-xs shadow-md active:scale-90 transition-all border border-amber-500/50"
          title="确认选择 (Enter)"
        >
          <Circle className="w-3.5 h-3.5 fill-current opacity-80 mb-0.5" />
          <span className="text-[10px] font-sans">OK</span>
        </button>

        {/* Right direction */}
        <button
          id="btn_remote_right"
          onClick={() => onPressKey("ArrowRight")}
          className="absolute right-1.5 w-9 h-11 bg-neutral-850 hover:bg-neutral-800 active:bg-neutral-700 text-white rounded-r-full flex justify-center items-center transition-colors active:scale-95 border-l border-neutral-900"
          title="分类右 (ArrowRight)"
        >
          <ChevronRight className="w-5 h-5" />
        </button>

        {/* Down direction */}
        <button
          id="btn_remote_down"
          onClick={() => onPressKey("ArrowDown")}
          className="absolute bottom-1.5 w-11 h-9 bg-neutral-850 hover:bg-neutral-800 active:bg-neutral-700 text-white rounded-b-full flex justify-center items-center transition-colors active:scale-95 border-t border-neutral-900"
          title="换台下 (ArrowDown)"
        >
          <ChevronDown className="w-5 h-5" />
        </button>

      </div>

      {/* Back button & Instructions */}
      <button
        id="btn_remote_back"
        onClick={() => onPressKey("Backspace")}
        className="mt-4 w-full py-2 bg-neutral-800 hover:bg-neutral-750 active:bg-red-950 active:hover:bg-red-900 active:text-red-200 text-neutral-300 rounded-xl text-xs flex items-center justify-center gap-1 transition-all active:scale-95 font-sans font-medium border border-neutral-800"
        title="返回 (Backspace/Esc)"
      >
        <CornerDownLeft className="w-3.5 h-3.5" />
        返回键 (Back)
      </button>

      <span className="mt-2.5 text-[8px] tracking-wide text-neutral-500 text-center font-sans">
        适配物理遥控器的方向键/OK键/返回键，可直接使用键盘操作。
      </span>
    </div>
  );
}
