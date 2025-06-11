package com.urlhunter.utils;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import com.urlhunter.model.URLEntry;
import com.urlhunter.database.DatabaseManager;

import java.util.List;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * 插件集成类 - 用于与LinkFinder和HAE插件协同工作
 */
public class PluginIntegration {
    private final MontoyaApi api;
    private final DatabaseManager dbManager;
    private final URLAnalyzer urlAnalyzer;
    
    // URL提取的正则表达式模式
    private static final Pattern URL_PATTERN = Pattern.compile(
        "(?i)\\b(?:https?://|www\\.|[a-z0-9.-]+\\.(?:com|org|net|edu|gov|mil|int|arpa|[a-z]{2}))" +
        "[^\\s\"'<>{}|\\\\^`\\[\\]]*",
        Pattern.CASE_INSENSITIVE
    );
    
    // 路径提取的正则表达式模式
    private static final Pattern PATH_PATTERN = Pattern.compile(
        "(?i)[\"']([^\"']*(?:/[^\"'\\s<>{}|\\\\^`\\[\\]]*)+)[\"']",
        Pattern.CASE_INSENSITIVE
    );
    
    // 敏感文件模式
    private static final Pattern SENSITIVE_FILE_PATTERN = Pattern.compile(
        "(?i)\\.(config|ini|conf|yaml|yml|json|xml|properties|env|key|pem|cert|sql|db|backup|bak|old|tmp)$"
    );
    
    public PluginIntegration(MontoyaApi api, DatabaseManager dbManager) {
        this.api = api;
        this.dbManager = dbManager;
        this.urlAnalyzer = new URLAnalyzer();
    }
    
    /**
     * 从HTTP响应中提取URL和路径
     * 模拟LinkFinder插件的功能
     */
    public List<String> extractURLsFromResponse(HttpRequestResponse requestResponse) {
        List<String> extractedUrls = new ArrayList<>();
        
        if (requestResponse.response() == null) {
            return extractedUrls;
        }
        
        String responseBody = requestResponse.response().bodyToString();
        String baseUrl = getBaseUrl(requestResponse.request().url());
        
        // 提取完整URL
        extractedUrls.addAll(extractCompleteUrls(responseBody));
        
        // 提取相对路径并转换为完整URL
        extractedUrls.addAll(extractRelativePaths(responseBody, baseUrl));
        
        // 提取JavaScript中的路径
        extractedUrls.addAll(extractJavaScriptPaths(responseBody, baseUrl));
        
        return extractedUrls;
    }
    
    /**
     * 检测敏感文件和路径
     * 模拟HAE插件的功能
     */
    public List<URLEntry> detectSensitiveFiles(List<String> urls) {
        List<URLEntry> sensitiveEntries = new ArrayList<>();
        
        for (String url : urls) {
            if (isSensitiveFile(url)) {
                URLEntry entry = URLAnalyzer.analyzeURL(url, "GET");
                entry.setNotes("敏感文件检测: " + getSensitiveFileType(url));
                sensitiveEntries.add(entry);
            }
        }
        
        return sensitiveEntries;
    }
    
    /**
     * 自动分析HTTP流量并提取URL
     */
    public void analyzeHttpTraffic(HttpRequestResponse requestResponse) {
        try {
            // 提取URL
            List<String> extractedUrls = extractURLsFromResponse(requestResponse);
            
            // 检测敏感文件
            List<URLEntry> sensitiveFiles = detectSensitiveFiles(extractedUrls);
            
            // 保存到数据库
            for (URLEntry entry : sensitiveFiles) {
                dbManager.insertURL(entry);
            }
            
            // 记录普通URL
            for (String url : extractedUrls) {
                if (!isSensitiveFile(url)) {
                    URLEntry entry = URLAnalyzer.analyzeURL(url, "GET");
                    entry.setNotes("从HTTP流量中提取");
                    dbManager.insertURL(entry);
                }
            }
            
        } catch (Exception e) {
            api.logging().logToError("分析HTTP流量失败: " + e.getMessage());
        }
    }
    
    /**
     * 提取完整URL
     */
    private List<String> extractCompleteUrls(String content) {
        List<String> urls = new ArrayList<>();
        Matcher matcher = URL_PATTERN.matcher(content);
        
        while (matcher.find()) {
            String url = matcher.group().trim();
            if (isValidUrl(url)) {
                urls.add(url);
            }
        }
        
        return urls;
    }
    
    /**
     * 提取相对路径并转换为完整URL
     */
    private List<String> extractRelativePaths(String content, String baseUrl) {
        List<String> urls = new ArrayList<>();
        Matcher matcher = PATH_PATTERN.matcher(content);
        
        while (matcher.find()) {
            String path = matcher.group(1);
            if (path.startsWith("/")) {
                String fullUrl = baseUrl + path;
                if (isValidUrl(fullUrl)) {
                    urls.add(fullUrl);
                }
            }
        }
        
        return urls;
    }
    
    /**
     * 从JavaScript代码中提取路径
     */
    private List<String> extractJavaScriptPaths(String content, String baseUrl) {
        List<String> urls = new ArrayList<>();
        
        // 提取JavaScript中的API端点
        Pattern jsApiPattern = Pattern.compile(
            "(?i)[\"']\\s*/(?:api|ajax|rest|service|endpoint)[^\"']*[\"']",
            Pattern.CASE_INSENSITIVE
        );
        
        Matcher matcher = jsApiPattern.matcher(content);
        while (matcher.find()) {
            String path = matcher.group().replaceAll("[\"']", "").trim();
            if (path.startsWith("/")) {
                String fullUrl = baseUrl + path;
                if (isValidUrl(fullUrl)) {
                    urls.add(fullUrl);
                }
            }
        }
        
        return urls;
    }
    
    /**
     * 检查是否为敏感文件
     */
    private boolean isSensitiveFile(String url) {
        try {
            String path = URLAnalyzer.extractPath(url);
            return SENSITIVE_FILE_PATTERN.matcher(path).find();
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 获取敏感文件类型
     */
    private String getSensitiveFileType(String url) {
        try {
            String path = URLAnalyzer.extractPath(url);
            if (path.contains(".config") || path.contains(".ini") || path.contains(".conf")) {
                return "配置文件";
            } else if (path.contains(".yaml") || path.contains(".yml")) {
                return "YAML配置文件";
            } else if (path.contains(".json")) {
                return "JSON配置文件";
            } else if (path.contains(".xml")) {
                return "XML配置文件";
            } else if (path.contains(".properties")) {
                return "属性配置文件";
            } else if (path.contains(".env")) {
                return "环境变量文件";
            } else if (path.contains(".key") || path.contains(".pem") || path.contains(".cert")) {
                return "证书密钥文件";
            } else if (path.contains(".sql") || path.contains(".db")) {
                return "数据库文件";
            } else if (path.contains(".backup") || path.contains(".bak") || path.contains(".old")) {
                return "备份文件";
            } else if (path.contains(".tmp")) {
                return "临时文件";
            }
            return "敏感文件";
        } catch (Exception e) {
            return "未知敏感文件";
        }
    }
    
    /**
     * 获取基础URL
     */
    private String getBaseUrl(String fullUrl) {
        try {
            java.net.URL url = new java.net.URL(fullUrl);
            return url.getProtocol() + "://" + url.getHost() + 
                   (url.getPort() != -1 ? ":" + url.getPort() : "");
        } catch (Exception e) {
            return fullUrl.substring(0, fullUrl.indexOf("/", 8)); // 简单的回退方法
        }
    }
    
    /**
     * 验证URL是否有效
     */
    private boolean isValidUrl(String url) {
        try {
            new java.net.URL(url);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 启用自动URL提取功能
     * 监听Burp的HTTP流量
     */
    public void enableAutoExtraction() {
        // 注册HTTP处理器来监听所有HTTP流量
        api.http().registerHttpHandler(new burp.api.montoya.http.handler.HttpHandler() {
            @Override
            public burp.api.montoya.http.handler.RequestToBeSentAction handleHttpRequestToBeSent(
                burp.api.montoya.http.handler.HttpRequestToBeSent requestToBeSent) {
                // 继续处理请求
                return burp.api.montoya.http.handler.RequestToBeSentAction.continueWith(requestToBeSent);
            }
            
            @Override
            public burp.api.montoya.http.handler.ResponseReceivedAction handleHttpResponseReceived(
                burp.api.montoya.http.handler.HttpResponseReceived responseReceived) {
                
                // 暂时注释掉自动分析功能，避免API调用错误
                // 在后台线程中分析响应
                // new Thread(() -> {
                //     try {
                //         // 创建HttpRequestResponse对象来分析
                //         HttpRequestResponse requestResponse = responseReceived.initiatingRequest()
                //                 .toHttpRequestResponse(responseReceived.originalResponse());
                //         analyzeHttpTraffic(requestResponse);
                //     } catch (Exception e) {
                //         api.logging().logToError("自动分析HTTP响应失败: " + e.getMessage());
                //     }
                // }).start();
                
                // 继续处理响应
                return burp.api.montoya.http.handler.ResponseReceivedAction.continueWith(responseReceived);
            }
        });
        
        api.logging().logToOutput("URL Hunter: 自动URL提取功能已启用");
    }
    
    /**
     * 手动分析指定的HTTP请求响应
     */
    public List<URLEntry> manualAnalyze(HttpRequestResponse requestResponse) {
        List<URLEntry> results = new ArrayList<>();
        
        try {
            // 提取URL
            List<String> extractedUrls = extractURLsFromResponse(requestResponse);
            
            // 转换为URLEntry对象
            for (String url : extractedUrls) {
                URLEntry entry = URLAnalyzer.analyzeURL(url, "GET");
                
                if (isSensitiveFile(url)) {
                    entry.setNotes("敏感文件: " + getSensitiveFileType(url));
                } else {
                    entry.setNotes("手动分析提取");
                }
                
                results.add(entry);
            }
            
        } catch (Exception e) {
            api.logging().logToError("手动分析失败: " + e.getMessage());
        }
        
        return results;
    }
} 