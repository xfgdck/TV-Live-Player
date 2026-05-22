import { TVChannel } from "../types";

/**
 * Parses M3U/M3U8 text content into TVChannel objects on client-side.
 */
export function parseM3uPlaylist(m3uContent: string): TVChannel[] {
  const channels: TVChannel[] = [];
  const lines = m3uContent.split(/\r?\n/);
  
  let currentMetadata: {
    name?: string;
    logo?: string;
    category?: string;
    tvgId?: string;
  } | null = null;

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i].trim();
    if (!line) continue;

    if (line.startsWith("#EXTM3U")) {
      continue;
    }

    if (line.startsWith("#EXTINF:")) {
      currentMetadata = {};
      
      // Parse tvg-logo
      const logoMatch = line.match(/tvg-logo="([^"]+)"/) || line.match(/tvg-logo='([^']+)'/);
      if (logoMatch) currentMetadata.logo = logoMatch[1];

      // Parse group-title (Category)
      const groupMatch = line.match(/group-title="([^"]+)"/) || line.match(/group-title='([^']+)'/);
      if (groupMatch) {
        currentMetadata.category = groupMatch[1];
      } else {
        currentMetadata.category = "自定义频道";
      }

      // Parse tvg-id
      const tvgIdMatch = line.match(/tvg-id="([^"]+)"/) || line.match(/tvg-id='([^']+)'/);
      if (tvgIdMatch) currentMetadata.tvgId = tvgIdMatch[1];

      // Parse channel name (the part after the last comma)
      const commaIndex = line.lastIndexOf(",");
      if (commaIndex !== -1) {
        currentMetadata.name = line.substring(commaIndex + 1).trim();
      } else {
        currentMetadata.name = "未命名频道";
      }
    } else if (
      line.startsWith("http://") || 
      line.startsWith("https://") || 
      line.startsWith("rtmp://") || 
      line.startsWith("rtsp://")
    ) {
      // It's a stream URL!
      const channelUrl = line;
      const name = currentMetadata?.name || `频道 ${channels.length + 1}`;
      const category = currentMetadata?.category || "自定义频道";
      const logo = currentMetadata?.logo || "";
      const tvgId = currentMetadata?.tvgId || "";

      channels.push({
        id: `custom_${channels.length}_${hashCode(channelUrl)}`,
        name,
        url: channelUrl,
        category,
        logo,
        tvgId,
      });

      // Clear metadata for the next item
      currentMetadata = null;
    }
  }

  return channels;
}

function hashCode(str: string): string {
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    const char = str.charCodeAt(i);
    hash = (hash << 5) - hash + char;
    hash |= 0; // Convert to 32bit integer
  }
  return Math.abs(hash).toString(36);
}
