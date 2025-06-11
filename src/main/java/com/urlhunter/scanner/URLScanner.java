package com.urlhunter.scanner;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.urlhunter.model.URLEntry;
import com.urlhunter.model.DomainConfig;
import com.urlhunter.utils.URLAnalyzer;
import com.urlhunter.database.DatabaseManager;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

public class URLScanner {
    private final MontoyaApi api;
    private final DatabaseManager dbManager;
    private final URLAnalyzer urlAnalyzer;
    private final ExecutorService executorService;
    private final AtomicBoolean isScanning;
    private final AtomicBoolean isFuzzEnabled;
    private final AtomicBoolean isShortLinkBruteForceEnabled;
    
    // 扫描状态回调接口
    public interface ScanCallback {
        void onURLScanned(URLEntry urlEntry);
        void onScanProgress(int current, int total);
        void onScanComplete();
        void onError(String message);
    }
    
    private ScanCallback callback;
    
    public URLScanner(MontoyaApi api, DatabaseManager dbManager) {
        this.api = api;
        this.dbManager = dbManager;
        this.urlAnalyzer = new URLAnalyzer();
        this.executorService = Executors.newFixedThreadPool(10);
        this.isScanning = new AtomicBoolean(false);
        this.isFuzzEnabled = new AtomicBoolean(true);
        this.isShortLinkBruteForceEnabled = new AtomicBoolean(true);
    }
    
    public void setScanCallback(ScanCallback callback) {
        this.callback = callback;
    }
    
    /**
     * 扫描单个URL并获取详细信息
     */
    public URLEntry scanURL(String url) {
        try {
            HttpRequest request = HttpRequest.httpRequestFromUrl(url);
            HttpService service = HttpService.httpService(urlAnalyzer.extractHost(url), 
                urlAnalyzer.isHttps(url) ? 443 : 80, urlAnalyzer.isHttps(url));
            
            HttpRequestResponse response = api.http().sendRequest(request.withService(service));
            
            URLEntry entry = new URLEntry();
            entry.setUrl(url);
            entry.setMethod("GET");
            entry.setHost(urlAnalyzer.extractHost(url));
            entry.setPath(urlAnalyzer.extractPath(url));
            entry.setQuery(urlAnalyzer.extractQuery(url));
            
            if (response.response() != null) {
                HttpResponse httpResponse = response.response();
                entry.setStatusCode(httpResponse.statusCode());
                entry.setLength(httpResponse.body().length());
                entry.setTitle(urlAnalyzer.extractTitle(httpResponse.bodyToString()));
            }
            
            // 解析IP地址
            String ip = urlAnalyzer.resolveIP(entry.getHost());
            entry.setIp(ip);
            entry.setIsInternal(urlAnalyzer.isInternalIP(ip));
            
            // 设置子域名
            entry.setSubdomain(urlAnalyzer.extractSubdomain(entry.getHost()));
            
            entry.setTimestamp(new Date());
            entry.setIsChecked(false);
            
            return entry;
            
        } catch (Exception e) {
            api.logging().logToError("扫描URL失败: " + url + " - " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 批量扫描URL列表
     */
    public void scanURLList(List<String> urls) {
        if (isScanning.get()) {
            if (callback != null) {
                callback.onError("扫描正在进行中，请稍后再试");
            }
            return;
        }
        
        isScanning.set(true);
        
        CompletableFuture.runAsync(() -> {
            try {
                DomainConfig config = dbManager.loadConfig();
                List<String> filteredUrls = filterUrls(urls, config);
                
                int total = filteredUrls.size();
                int current = 0;
                
                for (String url : filteredUrls) {
                    if (!isScanning.get()) break;
                    
                    URLEntry entry = scanURL(url);
                    if (entry != null && !shouldFilterByStatusCode(entry.getStatusCode(), config)) {
                        dbManager.insertURL(entry);
                        
                        if (callback != null) {
                            callback.onURLScanned(entry);
                        }
                        
                        // 如果启用Fuzz扫描，执行Fuzz攻击
                        if (isFuzzEnabled.get()) {
                            performFuzzAttack(entry, config);
                        }
                    }
                    
                    current++;
                    if (callback != null) {
                        callback.onScanProgress(current, total);
                    }
                    
                    // 添加延迟避免过于频繁的请求
                    Thread.sleep(100);
                }
                
                if (callback != null) {
                    callback.onScanComplete();
                }
                
            } catch (Exception e) {
                if (callback != null) {
                    callback.onError("扫描过程中发生错误: " + e.getMessage());
                }
            } finally {
                isScanning.set(false);
            }
        }, executorService);
    }
    
    /**
     * 过滤URL列表
     */
    private List<String> filterUrls(List<String> urls, DomainConfig config) {
        List<String> filteredUrls = new ArrayList<>();
        
        for (String url : urls) {
            if (urlAnalyzer.shouldFilter(url, config.getBlacklistExtensions())) {
                continue;
            }
            
            // 检查主域名黑名单
            String host = urlAnalyzer.extractHost(url);
            boolean isBlacklisted = false;
            for (String blacklistDomain : config.getDomainBlacklist()) {
                if (host.endsWith(blacklistDomain)) {
                    isBlacklisted = true;
                    break;
                }
            }
            
            if (!isBlacklisted) {
                filteredUrls.add(url);
            }
        }
        
        return filteredUrls;
    }
    
    /**
     * 根据状态码过滤
     */
    private boolean shouldFilterByStatusCode(int statusCode, DomainConfig config) {
        return config.getBlacklistStatusCodes().contains(statusCode);
    }
    
    /**
     * 执行Fuzz攻击
     */
    private void performFuzzAttack(URLEntry baseEntry, DomainConfig config) {
        if (!isFuzzEnabled.get()) return;
        
        try {
            String baseUrl = baseEntry.getUrl();
            String basePath = baseEntry.getPath();
            
            // 目录级别的Fuzz
            for (String fuzzWord : config.getFuzzDictionary()) {
                if (!isScanning.get()) break;
                
                // 在当前路径后添加Fuzz词汇
                String fuzzUrl = baseUrl.endsWith("/") ? baseUrl + fuzzWord : baseUrl + "/" + fuzzWord;
                
                URLEntry fuzzEntry = scanURL(fuzzUrl);
                if (fuzzEntry != null && !shouldFilterByStatusCode(fuzzEntry.getStatusCode(), config)) {
                    fuzzEntry.setNotes("Fuzz发现: " + fuzzWord);
                    dbManager.insertURL(fuzzEntry);
                    
                    if (callback != null) {
                        callback.onURLScanned(fuzzEntry);
                    }
                }
                
                Thread.sleep(50); // Fuzz攻击间隔
            }
            
        } catch (Exception e) {
            api.logging().logToError("Fuzz攻击失败: " + e.getMessage());
        }
    }
    
    /**
     * 短链接爆破
     */
    public void performShortLinkBruteForce(String baseUrl) {
        if (!isShortLinkBruteForceEnabled.get()) return;
        
        CompletableFuture.runAsync(() -> {
            try {
                DomainConfig config = dbManager.loadConfig();
                
                // 生成短链接字符集
                String charset = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
                
                // 1-4位字符的组合
                for (int length = 1; length <= 4; length++) {
                    if (!isShortLinkBruteForceEnabled.get()) break;
                    
                    generateAndTestShortLinks(baseUrl, charset, length, config);
                }
                
            } catch (Exception e) {
                api.logging().logToError("短链接爆破失败: " + e.getMessage());
            }
        }, executorService);
    }
    
    /**
     * 生成并测试指定长度的短链接
     */
    private void generateAndTestShortLinks(String baseUrl, String charset, int length, DomainConfig config) {
        generateCombinations(charset, length, "", baseUrl, config);
    }
    
    /**
     * 递归生成字符组合
     */
    private void generateCombinations(String charset, int length, String current, String baseUrl, DomainConfig config) {
        if (!isShortLinkBruteForceEnabled.get()) return;
        
        if (current.length() == length) {
            String shortLinkUrl = baseUrl.endsWith("/") ? baseUrl + current : baseUrl + "/" + current;
            
            URLEntry entry = scanURL(shortLinkUrl);
            if (entry != null && !shouldFilterByStatusCode(entry.getStatusCode(), config)) {
                entry.setNotes("短链接爆破发现: " + current);
                dbManager.insertURL(entry);
                
                if (callback != null) {
                    callback.onURLScanned(entry);
                }
            }
            
            try {
                Thread.sleep(50); // 爆破间隔
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return;
        }
        
        for (char c : charset.toCharArray()) {
            if (!isShortLinkBruteForceEnabled.get()) break;
            generateCombinations(charset, length, current + c, baseUrl, config);
        }
    }
    
    /**
     * 停止扫描
     */
    public void stopScanning() {
        isScanning.set(false);
    }
    
    /**
     * 停止短链接爆破
     */
    public void stopShortLinkBruteForce() {
        isShortLinkBruteForceEnabled.set(false);
    }
    
    /**
     * 设置Fuzz扫描开关
     */
    public void setFuzzEnabled(boolean enabled) {
        isFuzzEnabled.set(enabled);
    }
    
    /**
     * 设置短链接爆破开关
     */
    public void setShortLinkBruteForceEnabled(boolean enabled) {
        isShortLinkBruteForceEnabled.set(enabled);
    }
    
    /**
     * 获取扫描状态
     */
    public boolean isScanning() {
        return isScanning.get();
    }
    
    /**
     * 关闭扫描器
     */
    public void shutdown() {
        stopScanning();
        stopShortLinkBruteForce();
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
} 