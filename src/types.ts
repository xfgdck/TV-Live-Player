export interface TVChannel {
  id: string; // unique identifier
  name: string; // channel name
  url: string; // m3u8 stream URL
  category: string; // category e.g., CCTV, Satellite, Local, Custom, etc.
  logo?: string; // channel logo image URL
  tvgId?: string; // EPG channel ID if available
  isFavorite?: boolean;
}

export interface TVCategory {
  id: string;
  name: string;
}

export interface CustomSource {
  id: string;
  name: string;
  url: string;
  isActive: boolean;
  createdAt: number;
}
