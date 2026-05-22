import { TVChannel } from '../types';

export const DEFAULT_PLAYLIST_SOURCES = [
  {
    id: 'fanmingming-ipv6',
    name: '范明明 IPTV (IPv6)',
    url: 'https://raw.githubusercontent.com/fanmingming/live/main/tv/m3u/ipv6.m3u',
    description: '高清央视、卫视、地方台等，国内极速 (需要您的网络支持IPv6)'
  },
  {
    id: 'iptv-org-cn',
    name: 'IPTV-Org (中国大陆)',
    url: 'https://iptv-org.github.io/iptv/countries/cn.m3u',
    description: '全球开源IPTV项目维护的中国频道列表，兼容性好'
  },
  {
    id: 'iptv-org-hk',
    name: 'IPTV-Org (中国香港)',
    url: 'https://iptv-org.github.io/iptv/countries/hk.m3u',
    description: '香港地区公开电视频道，包含多语种新闻、纪实频道'
  },
  {
    id: 'iptv-org-tw',
    name: 'IPTV-Org (中国台湾)',
    url: 'https://iptv-org.github.io/iptv/countries/tw.m3u',
    description: '台湾地区公开电视频道，包含新闻、综艺与地方台'
  }
];

export const INITIAL_DEFAULT_CHANNELS: TVChannel[] = [
  {
    id: 'cgtn_news',
    name: 'CGTN 英语新闻 (HD)',
    url: 'https://news.cgtn.com/resource/live/english/cgtn-news.m3u8',
    category: 'CGTN国际',
    logo: 'https://news.cgtn.com/resource/live/english/cgtn-news-logo.png',
  },
  {
    id: 'cgtn_doc',
    name: 'CGTN 纪录国际 (HD)',
    url: 'https://news.cgtn.com/resource/live/document/cgtn-doc.m3u8',
    category: 'CGTN国际',
    logo: 'https://news.cgtn.com/resource/live/document/cgtn-doc-logo.png',
  },
  {
    id: 'cgtn_es',
    name: 'CGTN 西班牙语',
    url: 'https://news.cgtn.com/resource/live/spanish/cgtn-es.m3u8',
    category: 'CGTN国际',
  },
  {
    id: 'cgtn_fr',
    name: 'CGTN 法语频道',
    url: 'https://news.cgtn.com/resource/live/french/cgtn-fr.m3u8',
    category: 'CGTN国际',
  },
  {
    id: 'cgtn_ar',
    name: 'CGTN 阿拉伯语',
    url: 'https://news.cgtn.com/resource/live/arabic/cgtn-ar.m3u8',
    category: 'CGTN国际',
  },
  {
    id: 'cgtn_ru',
    name: 'CGTN 俄语频道',
    url: 'https://news.cgtn.com/resource/live/russian/cgtn-ru.m3u8',
    category: 'CGTN国际',
  },
  {
    id: 'nasa_tv',
    name: 'NASA TV (美国宇航局)',
    url: 'https://ntv1.nasatv.live/nasatv/nasa_hd.m3u8',
    category: '纪录纪实',
    logo: 'https://www.nasa.gov/favicon.ico',
  },
  {
    id: 'newsmax',
    name: 'Newsmax TV Channel',
    url: 'https://nmxlive.akamaized.net/hls/live/359755/newsmax/master.m3u8',
    category: '海外新闻',
  },
  {
    id: 'al_jazeera',
    name: '半岛电视台 (Al Jazeera 英)',
    url: 'https://live-amg-01.getbento.com/amg/aljazeera/adsl/playlist.m3u8',
    category: '海外新闻',
  },
  {
    id: 'redbull_tv',
    name: '红牛极限运动 (Red Bull TV)',
    url: 'https://rbmn-live.akamaized.net/hls/live/590964/BoRB-AT/master.m3u8',
    category: '体育运动',
  },
  {
    id: 'test_tears',
    name: '漫游太游 示范片 (HLS Test)',
    url: 'https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.m3u8',
    category: '演示测试',
  },
  {
    id: 'test_bunny',
    name: '大雄兔 演示片 (Big Buck Bunny)',
    url: 'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8',
    category: '演示测试',
  }
];
