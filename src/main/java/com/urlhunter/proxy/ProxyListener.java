package com.urlhunter.proxy;

import burp.api.montoya.MontoyaApi;

import burp.api.montoya.proxy.http.InterceptedRequest;
import burp.api.montoya.proxy.http.InterceptedResponse;
import burp.api.montoya.proxy.http.ProxyRequestHandler;
import burp.api.montoya.proxy.http.ProxyRequestReceivedAction;
import burp.api.montoya.proxy.http.ProxyRequestToBeSentAction;
import burp.api.montoya.proxy.http.ProxyResponseHandler;
import burp.api.montoya.proxy.http.ProxyResponseReceivedAction;
import burp.api.montoya.proxy.http.ProxyResponseToBeSentAction;
import com.urlhunter.model.URLEntry;
import com.urlhunter.model.DomainConfig;
import com.urlhunter.utils.URLAnalyzer;
import com.urlhunter.database.DatabaseManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 自动监听Proxy流量，实时提取匹配主域名的子域名和URL
 */
public class ProxyListener implements ProxyRequestHandler, ProxyResponseHandler {
    private final MontoyaApi api;
    private final DatabaseManager dbManager;
    private final URLAnalyzer urlAnalyzer;
    private final Set<String> rootDomains;
    private final Map<String, Set<String>> discoveredSubdomains;
    private final Set<String> processedUrls;
    private final ScheduledExecutorService scheduler;
    private volatile DomainConfig currentConfig;
    
    // 回调接口，用于通知UI更新
    public interface ProxyDiscoveryCallback {
        void onSubdomainDiscovered(String rootDomain, String subdomain);
        void onURLDiscovered(URLEntry urlEntry);
        void onURLDiscoveredWithRequestResponse(URLEntry urlEntry, burp.api.montoya.http.message.requests.HttpRequest request, InterceptedResponse response);
        void onRootDomainsUpdated(Set<String> newRootDomains);
    }
    
    private ProxyDiscoveryCallback callback;
    private volatile boolean isEnabled;
    
    public ProxyListener(MontoyaApi api, DatabaseManager dbManager) {
        this.api = api;
        this.dbManager = dbManager;
        this.urlAnalyzer = new URLAnalyzer();
        this.rootDomains = ConcurrentHashMap.newKeySet();
        this.discoveredSubdomains = new ConcurrentHashMap<>();
        this.processedUrls = ConcurrentHashMap.newKeySet();
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.isEnabled = true;
        
        // 加载默认配置
        loadDefaultConfig();
        
        api.logging().logToOutput("ProxyListener 已启动，开始监听流量...");
    }
    
    /**
     * 加载默认配置
     */
    private void loadDefaultConfig() {
        try {
            this.currentConfig = dbManager.loadConfig();
            if (this.currentConfig == null) {
                this.currentConfig = new DomainConfig();
            }
        } catch (Exception e) {
            api.logging().logToError("加载配置失败，使用默认配置: " + e.getMessage());
            this.currentConfig = new DomainConfig();
        }
    }
    
    /**
     * 更新过滤配置
     */
    public void updateConfig(DomainConfig config) {
        this.currentConfig = config;
        api.logging().logToOutput("ProxyListener配置已更新");
    }
    
    public void setCallback(ProxyDiscoveryCallback callback) {
        this.callback = callback;
    }
    
    public void setEnabled(boolean enabled) {
        this.isEnabled = enabled;
        api.logging().logToOutput("ProxyListener " + (enabled ? "已启用" : "已禁用"));
    }
    
    public boolean isEnabled() {
        return isEnabled;
    }
    
    /**
     * 更新根域名列表
     */
    public void updateRootDomains(Set<String> newRootDomains) {
        this.rootDomains.clear();
        if (newRootDomains != null) {
            for (String domain : newRootDomains) {
                if (domain != null && !domain.trim().isEmpty()) {
                    this.rootDomains.add(domain.trim().toLowerCase());
                }
            }
        }
        
        if (callback != null) {
            callback.onRootDomainsUpdated(new HashSet<>(this.rootDomains));
        }
        
        api.logging().logToOutput("根域名列表已更新，共 " + this.rootDomains.size() + " 个域名");
    }
    
    @Override
    public ProxyRequestReceivedAction handleRequestReceived(InterceptedRequest interceptedRequest) {
        if (!isEnabled) {
            return ProxyRequestReceivedAction.continueWith(interceptedRequest);
        }
        
        // 在请求阶段暂时不处理URL发现，等到响应阶段再处理
        
        return ProxyRequestReceivedAction.continueWith(interceptedRequest);
    }
    
    @Override
    public ProxyRequestToBeSentAction handleRequestToBeSent(InterceptedRequest interceptedRequest) {
        // 请求即将发送时的处理（如果需要）
        return ProxyRequestToBeSentAction.continueWith(interceptedRequest);
    }
    
    @Override
    public ProxyResponseReceivedAction handleResponseReceived(InterceptedResponse interceptedResponse) {
        if (!isEnabled) {
            return ProxyResponseReceivedAction.continueWith(interceptedResponse);
        }
        
        try {
            String url = interceptedResponse.initiatingRequest().url();
            String method = interceptedResponse.initiatingRequest().method();
            // 主要的URL发现在响应阶段处理，这时我们有完整的请求响应对
            processURL(url, method, interceptedResponse.initiatingRequest(), interceptedResponse);
        } catch (Exception e) {
            api.logging().logToError("处理响应时发生错误: " + e.getMessage());
        }
        
        return ProxyResponseReceivedAction.continueWith(interceptedResponse);
    }
    
    @Override
    public ProxyResponseToBeSentAction handleResponseToBeSent(InterceptedResponse interceptedResponse) {
        // 响应即将发送时的处理（如果需要）
        return ProxyResponseToBeSentAction.continueWith(interceptedResponse);
    }
    
    /**
     * 处理发现的URL
     */
    private void processURL(String url, String method, burp.api.montoya.http.message.requests.HttpRequest request, InterceptedResponse response) {
        if (url == null || url.isEmpty() || rootDomains.isEmpty()) {
            return;
        }
        
        // 避免重复处理相同URL - 修改为只对路径部分去重，忽略参数
        String urlWithoutParams = removeUrlParameters(url);
        if (processedUrls.contains(urlWithoutParams)) {
            return;
        }
        
        try {
            String host = extractHostFromUrl(url);
            if (host == null || host.isEmpty()) {
                return;
            }
            
            // 检查是否匹配根域名
            String matchedRootDomain = findMatchingRootDomain(host, rootDomains);
            if (matchedRootDomain == null) {
                return;
            }
            
            // 应用过滤配置
            if (currentConfig != null) {
                // 1. 检查域名黑名单
                if (isHostBlacklisted(host)) {
                    return;
                }
                
                // 2. 检查URL扩展名黑名单
                if (isUrlExtensionBlacklisted(url)) {
                    return;
                }
                
                // 3. 如果有响应，检查状态码黑名单
                if (response != null && isStatusCodeBlacklisted(response.statusCode())) {
                    return;
                }
            }
            
            // 标记为已处理（使用去除参数后的URL）
            processedUrls.add(urlWithoutParams);
            
            // 发现新的子域名
            discoveredSubdomains.computeIfAbsent(matchedRootDomain, k -> ConcurrentHashMap.newKeySet()).add(host);
            
            // 通知UI子域名发现
            if (callback != null) {
                callback.onSubdomainDiscovered(matchedRootDomain, host);
            }
            
            // 创建URLEntry
            URLEntry entry = createURLEntry(url, method, host, request, response);
            if (entry != null) {
                // 异步保存到数据库
                scheduler.execute(() -> {
                    try {
                        dbManager.insertURL(entry);
                        
                        // 通知UI URL发现 - 使用新的回调方法传递完整的请求响应数据
                        if (callback != null) {
                            callback.onURLDiscoveredWithRequestResponse(entry, request, response);
                        }
                    } catch (Exception e) {
                        api.logging().logToError("保存URL到数据库失败: " + e.getMessage());
                    }
                });
            }
            
            api.logging().logToOutput("发现新URL: " + url + " (主域名: " + matchedRootDomain + ")");
            
        } catch (Exception e) {
            api.logging().logToError("处理URL失败: " + url + " - " + e.getMessage());
        }
    }
    
    /**
     * 移除URL中的参数部分，只保留路径用于去重
     */
    private String removeUrlParameters(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        
        int questionMarkIndex = url.indexOf('?');
        if (questionMarkIndex != -1) {
            return url.substring(0, questionMarkIndex);
        }
        
        return url;
    }
    
    /**
     * 从URL中提取主机名
     */
    private String extractHostFromUrl(String url) {
        try {
            if (url == null || url.isEmpty()) {
                return null;
            }
            
            // 移除协议部分
            if (url.startsWith("http://")) {
                url = url.substring(7);
            } else if (url.startsWith("https://")) {
                url = url.substring(8);
            }
            
            // 获取主机名部分
            int slashIndex = url.indexOf('/');
            if (slashIndex != -1) {
                url = url.substring(0, slashIndex);
            }
            
            int questionIndex = url.indexOf('?');
            if (questionIndex != -1) {
                url = url.substring(0, questionIndex);
            }
            
            // 移除端口号
            int colonIndex = url.lastIndexOf(':');
            if (colonIndex != -1 && colonIndex > url.lastIndexOf(']')) {
                url = url.substring(0, colonIndex);
            }
            
            return url.toLowerCase().trim();
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 找到匹配的根域名
     */
    private String findMatchingRootDomain(String host, Set<String> rootDomains) {
        if (host == null || host.isEmpty()) {
            return null;
        }
        
        host = host.toLowerCase();
        
        for (String rootDomain : rootDomains) {
            String normalizedRootDomain = rootDomain.toLowerCase();
            
            // 精确匹配
            if (host.equals(normalizedRootDomain)) {
                return rootDomain;
            }
            
            // 子域名匹配
            if (host.endsWith("." + normalizedRootDomain)) {
                return rootDomain;
            }
        }
        
        return null;
    }
    
    /**
     * 创建URLEntry
     */
    private URLEntry createURLEntry(String url, String method, String host, burp.api.montoya.http.message.requests.HttpRequest request, InterceptedResponse response) {
        try {
            URLEntry entry = new URLEntry();
            entry.setUrl(url);
            entry.setMethod(method != null ? method : "GET");
            entry.setHost(host);
            entry.setPath(urlAnalyzer.extractPath(url));
            entry.setQuery(urlAnalyzer.extractQuery(url));
            
            // 存储原始请求数据
            if (request != null) {
                try {
                    entry.setRequestData(request.toByteArray().getBytes());
                } catch (Exception e) {
                    api.logging().logToError("存储请求数据失败: " + e.getMessage());
                }
            }
            
            // 处理响应信息
            if (response != null) {
                entry.setStatusCode(response.statusCode());
                entry.setLength(response.body().length());
                
                // 存储原始响应数据
                try {
                    entry.setResponseData(response.toByteArray().getBytes());
                } catch (Exception e) {
                    api.logging().logToError("存储响应数据失败: " + e.getMessage());
                }
                
                try {
                    String responseBody = response.bodyToString();
                    entry.setTitle(urlAnalyzer.extractTitle(responseBody));
                } catch (Exception e) {
                    entry.setTitle("");
                }
            } else {
                entry.setStatusCode(0);
                entry.setLength(0);
                entry.setTitle("");
            }
            
            // 解析IP和内网判断
            try {
                String ip = urlAnalyzer.resolveIP(host);
                entry.setIp(ip);
                entry.setIsInternal(urlAnalyzer.isInternalIP(ip));
            } catch (Exception e) {
                entry.setIp("");
                entry.setIsInternal(false);
            }
            
            entry.setSubdomain(urlAnalyzer.extractSubdomain(host));
            entry.setTimestamp(new Date());
            entry.setIsChecked(false);
            entry.setNotes("自动从Proxy实时提取");
            
            return entry;
        } catch (Exception e) {
            api.logging().logToError("创建URLEntry失败: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 获取已发现的子域名统计
     */
    public Map<String, Set<String>> getDiscoveredSubdomains() {
        return new HashMap<>(discoveredSubdomains);
    }
    
    /**
     * 清理已处理的URL缓存（定期清理避免内存泄漏）
     */
    public void clearProcessedUrls() {
        processedUrls.clear();
        api.logging().logToOutput("已清理处理过的URL缓存");
    }
    
    /**
     * 关闭监听器
     */
    public void shutdown() {
        setEnabled(false);
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        api.logging().logToOutput("ProxyListener 已关闭");
    }
    
    /**
     * 检查主机是否在黑名单中
     */
    private boolean isHostBlacklisted(String host) {
        if (currentConfig == null || currentConfig.getDomainBlacklist() == null) {
            return false;
        }
        
        for (String blacklistDomain : currentConfig.getDomainBlacklist()) {
            if (blacklistDomain != null && !blacklistDomain.trim().isEmpty()) {
                String normalizedBlacklist = blacklistDomain.trim().toLowerCase();
                if (host.equals(normalizedBlacklist) || host.endsWith("." + normalizedBlacklist)) {
                    api.logging().logToOutput("URL被域名黑名单过滤: " + host);
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * 检查URL扩展名是否在黑名单中
     */
    private boolean isUrlExtensionBlacklisted(String url) {
        if (currentConfig == null || currentConfig.getBlacklistExtensions() == null) {
            return false;
        }
        
        // 提取文件扩展名
        String extension = extractFileExtension(url);
        if (extension == null || extension.isEmpty()) {
            return false;
        }
        
        for (String blacklistExt : currentConfig.getBlacklistExtensions()) {
            if (blacklistExt != null && blacklistExt.trim().equalsIgnoreCase(extension)) {
                api.logging().logToOutput("URL被扩展名黑名单过滤: " + url + " (扩展名: " + extension + ")");
                return true;
            }
        }
        return false;
    }
    
    /**
     * 检查状态码是否在黑名单中
     */
    private boolean isStatusCodeBlacklisted(int statusCode) {
        if (currentConfig == null || currentConfig.getBlacklistStatusCodes() == null) {
            return false;
        }
        
        for (Integer blacklistCode : currentConfig.getBlacklistStatusCodes()) {
            if (blacklistCode != null && blacklistCode == statusCode) {
                api.logging().logToOutput("URL被状态码黑名单过滤，状态码: " + statusCode);
                return true;
            }
        }
        return false;
    }
    
    /**
     * 提取文件扩展名
     */
    private String extractFileExtension(String url) {
        try {
            // 移除查询参数
            int questionIndex = url.indexOf('?');
            if (questionIndex != -1) {
                url = url.substring(0, questionIndex);
            }
            
            // 移除片段标识符
            int hashIndex = url.indexOf('#');
            if (hashIndex != -1) {
                url = url.substring(0, hashIndex);
            }
            
            // 查找最后一个点
            int lastDotIndex = url.lastIndexOf('.');
            int lastSlashIndex = url.lastIndexOf('/');
            
            // 确保点在最后一个斜杠之后，且不是隐藏文件
            if (lastDotIndex > lastSlashIndex && lastDotIndex < url.length() - 1) {
                return url.substring(lastDotIndex + 1).toLowerCase();
            }
            
            return null;
        } catch (Exception e) {
            return null;
        }
    }
} 