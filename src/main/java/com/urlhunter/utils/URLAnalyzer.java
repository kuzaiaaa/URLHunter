package com.urlhunter.utils;

import com.urlhunter.model.URLEntry;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

public class URLAnalyzer {
    
    private static final Pattern INTERNAL_IP_PATTERN = Pattern.compile(
        "^(?:" +
        "10\\.|" +                          // 10.0.0.0/8
        "192\\.168\\.|" +                   // 192.168.0.0/16
        "172\\.(?:1[6-9]|2[0-9]|3[01])\\." + // 172.16.0.0/12
        "127\\.|" +                         // 127.0.0.0/8
        "169\\.254\\.|" +                   // 169.254.0.0/16 (Link-Local)
        "::1|" +                           // IPv6 localhost
        "fe80:" +                          // IPv6 Link-Local
        ")"
    );

    public static URLEntry analyzeURL(String url, String method) {
        URLEntry entry = new URLEntry(url, method, extractHost(url));
        
        try {
            URL urlObj = new URL(url);
            entry.setHost(urlObj.getHost());
            entry.setPath(urlObj.getPath());
            entry.setQuery(urlObj.getQuery());
            
            // 提取子域名
            String subdomain = extractSubdomain(urlObj.getHost());
            entry.setSubdomain(subdomain);
            
            // 解析IP地址
            String ip = resolveIP(urlObj.getHost());
            entry.setIp(ip);
            entry.setInternal(isInternalIP(ip));
            
        } catch (MalformedURLException e) {
            // URL格式错误，保留基本信息
        }
        
        return entry;
    }

    public static String extractHost(String url) {
        try {
            URL urlObj = new URL(url);
            return urlObj.getHost();
        } catch (MalformedURLException e) {
            // 尝试从字符串中提取主机名
            if (url.contains("://")) {
                String afterProtocol = url.substring(url.indexOf("://") + 3);
                int slashIndex = afterProtocol.indexOf('/');
                if (slashIndex > 0) {
                    return afterProtocol.substring(0, slashIndex);
                } else {
                    return afterProtocol;
                }
            }
            return url;
        }
    }

    public static String extractSubdomain(String host) {
        if (host == null) return null;
        
        String[] parts = host.split("\\.");
        if (parts.length >= 3) {
            // 提取子域名部分 (例如: www.example.com -> www)
            return parts[0];
        }
        return null;
    }

    public static String resolveIP(String hostname) {
        if (hostname == null) return null;
        
        try {
            InetAddress address = InetAddress.getByName(hostname);
            return address.getHostAddress();
        } catch (UnknownHostException e) {
            return null;
        }
    }

    public static boolean isInternalIP(String ip) {
        if (ip == null) return false;
        return INTERNAL_IP_PATTERN.matcher(ip).find();
    }

    public static String extractTitle(String htmlContent) {
        if (htmlContent == null || htmlContent.trim().isEmpty()) {
            return null;
        }
        
        try {
            Document doc = Jsoup.parse(htmlContent);
            String title = doc.title();
            return title.isEmpty() ? null : title.trim();
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean shouldFilter(String url, com.urlhunter.model.DomainConfig config) {
        if (url == null || config == null) return false;
        
        try {
            URL urlObj = new URL(url);
            String host = urlObj.getHost();
            String path = urlObj.getPath();
            
            // 检查域名黑名单
            for (String blacklistDomain : config.getBlacklistDomains()) {
                if (host.contains(blacklistDomain)) {
                    return true;
                }
            }
            
            // 检查扩展名黑名单
            for (String ext : config.getBlacklistExtensions()) {
                if (path.toLowerCase().endsWith("." + ext.toLowerCase())) {
                    return true;
                }
            }
            
        } catch (MalformedURLException e) {
            // URL格式错误，不过滤
        }
        
        return false;
    }

    public static boolean shouldFilterByStatusCode(int statusCode, com.urlhunter.model.DomainConfig config) {
        if (config == null) return false;
        return config.getBlacklistStatusCodes().contains(statusCode);
    }

    public static String generateShortLinkURL(String baseUrl, String shortCode) {
        try {
            URL url = new URL(baseUrl);
            String protocol = url.getProtocol();
            String host = url.getHost();
            int port = url.getPort();
            
            StringBuilder sb = new StringBuilder();
            sb.append(protocol).append("://").append(host);
            if (port != -1 && port != 80 && port != 443) {
                sb.append(":").append(port);
            }
            sb.append("/").append(shortCode);
            
            return sb.toString();
        } catch (MalformedURLException e) {
            return baseUrl + "/" + shortCode;
        }
    }

    public static boolean isValidURL(String url) {
        try {
            new URL(url);
            return true;
        } catch (MalformedURLException e) {
            return false;
        }
    }

    public static String normalizeURL(String url) {
        if (url == null) return null;
        
        // 移除URL末尾的斜杠
        if (url.endsWith("/") && url.length() > 1) {
            url = url.substring(0, url.length() - 1);
        }
        
        // 确保URL有协议
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }
        
        return url;
    }

    public static boolean isHttps(String url) {
        return url != null && url.toLowerCase().startsWith("https://");
    }

    public static String extractPath(String url) {
        try {
            URL urlObj = new URL(url);
            return urlObj.getPath();
        } catch (MalformedURLException e) {
            return "/";
        }
    }

    public static String extractQuery(String url) {
        try {
            URL urlObj = new URL(url);
            return urlObj.getQuery();
        } catch (MalformedURLException e) {
            return null;
        }
    }

    public boolean shouldFilter(String url, java.util.List<String> blacklistExtensions) {
        if (url == null || blacklistExtensions == null) return false;
        
        String lowerUrl = url.toLowerCase();
        for (String ext : blacklistExtensions) {
            if (lowerUrl.endsWith("." + ext.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
} 