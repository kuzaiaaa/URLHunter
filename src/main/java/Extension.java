import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.proxy.ProxyHttpRequestResponse;
import com.urlhunter.database.DatabaseManager;
import com.urlhunter.ui.MainPanel;
import com.urlhunter.model.URLEntry;
import com.urlhunter.utils.URLAnalyzer;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class Extension implements BurpExtension {
    private MainPanel mainPanel;
    private DatabaseManager dbManager;
    private URLAnalyzer urlAnalyzer;
    private MontoyaApi api;
    private Set<String> targetDomains;
    
    @Override
    public void initialize(MontoyaApi api) {
        this.api = api;
        this.urlAnalyzer = new URLAnalyzer();
        this.targetDomains = new HashSet<>();
        
        api.extension().setName("URL Hunter - URL安全检测库");
        api.logging().logToOutput("URL Hunter extension loading...");
        
        try {
            // 先检查SQLite驱动是否可用
            try {
                Class.forName("org.sqlite.JDBC");
                api.logging().logToOutput("SQLite JDBC driver loaded successfully");
            } catch (ClassNotFoundException e) {
                api.logging().logToError("SQLite JDBC driver not found: " + e.getMessage());
                throw new RuntimeException("SQLite驱动未找到，插件无法运行", e);
            }
            
            // 初始化数据库管理器
            api.logging().logToOutput("Initializing database manager...");
            dbManager = new DatabaseManager();
            api.logging().logToOutput("Database manager initialized successfully");
            
            // 在EDT线程中创建UI
            SwingUtilities.invokeLater(() -> {
                try {
                    api.logging().logToOutput("Creating main panel...");
                    
                    // 创建主面板
                    mainPanel = new MainPanel(api, dbManager);
                    
                    // 将面板添加到Burp的UI中
                    api.userInterface().registerSuiteTab("URL Hunter", mainPanel);
                    
                    // 启动时处理现有历史记录
                    processExistingProxyHistory();
                    
                    api.logging().logToOutput("URL Hunter UI initialized successfully!");
                } catch (Exception e) {
                    api.logging().logToError("Failed to initialize URL Hunter UI: " + e.getMessage());
                    api.logging().logToError("Stack trace: ");
                    for (StackTraceElement element : e.getStackTrace()) {
                        api.logging().logToError("  " + element.toString());
                    }
                    
                    // 显示错误对话框
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(null, 
                            "URL Hunter插件初始化失败:\n" + e.getMessage() + 
                            "\n\n请检查Burp Suite的Output和Errors标签获取详细信息", 
                            "URL Hunter初始化错误", 
                            JOptionPane.ERROR_MESSAGE);
                    });
                }
            });
            
        } catch (Exception e) {
            api.logging().logToError("Failed to initialize URL Hunter extension: " + e.getMessage());
            api.logging().logToError("Stack trace: ");
            for (StackTraceElement element : e.getStackTrace()) {
                api.logging().logToError("  " + element.toString());
            }
            
            // 显示错误对话框
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(null, 
                    "URL Hunter插件加载失败:\n" + e.getMessage() + 
                    "\n\n请检查Burp Suite的Output和Errors标签获取详细信息", 
                    "URL Hunter加载错误", 
                    JOptionPane.ERROR_MESSAGE);
            });
        }
    }
    
    private boolean isTargetDomain(String host) {
        synchronized (targetDomains) {
            if (targetDomains.isEmpty()) {
                return false; // 没有目标域名时不处理
            }
            
            for (String rootDomain : targetDomains) {
                if (host.equals(rootDomain) || host.endsWith("." + rootDomain)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    private void processExistingProxyHistory() {
        CompletableFuture.runAsync(() -> {
            try {
                if (api == null) return;
                
                api.logging().logToOutput("开始处理现有Proxy历史记录...");
                var proxyHistory = api.proxy().history();
                int processed = 0;
                
                for (ProxyHttpRequestResponse item : proxyHistory) {
                    try {
                        String url = item.request().url();
                        String host = urlAnalyzer.extractHost(url);
                        
                        if (isTargetDomain(host)) {
                            URLEntry entry = createURLEntryFromProxyItem(item);
                            if (entry != null) {
                                entry.setNotes("历史记录导入");
                                dbManager.insertURL(entry);
                                
                                // 更新UI
                                SwingUtilities.invokeLater(() -> {
                                    if (mainPanel != null) {
                                        mainPanel.updateFromProxyDiscovery(entry);
                                    }
                                });
                                processed++;
                            }
                        }
                    } catch (Exception e) {
                        // 忽略单个项目的错误
                    }
                }
                
                api.logging().logToOutput("历史记录处理完成，共处理 " + processed + " 条记录");
            } catch (Exception e) {
                if (api != null) {
                    api.logging().logToError("处理历史记录失败: " + e.getMessage());
                }
            }
        });
    }
    
    private URLEntry createURLEntryFromProxyItem(ProxyHttpRequestResponse item) {
        try {
            String url = item.request().url();
            
            URLEntry entry = new URLEntry();
            entry.setUrl(url);
            entry.setMethod(item.request().method());
            entry.setHost(urlAnalyzer.extractHost(url));
            entry.setPath(urlAnalyzer.extractPath(url));
            entry.setQuery(urlAnalyzer.extractQuery(url));
            
            if (item.response() != null) {
                entry.setStatusCode(item.response().statusCode());
                entry.setLength(item.response().body().length());
                entry.setTitle(urlAnalyzer.extractTitle(item.response().bodyToString()));
            } else {
                entry.setStatusCode(0);
                entry.setLength(0);
                entry.setTitle("");
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
            if (api != null) {
                api.logging().logToError("从Proxy项目创建URLEntry失败: " + e.getMessage());
            }
            return null;
        }
    }
    
    // 公共方法，供MainPanel调用
    public void updateTargetDomains(Set<String> domains) {
        synchronized (targetDomains) {
            targetDomains.clear();
            targetDomains.addAll(domains);
        }
        
        if (api != null) {
            api.logging().logToOutput("目标域名已更新，共 " + domains.size() + " 个域名");
        }
    }
    
    /**
     * 扩展卸载时的清理
     */
    public void unload() {
        try {
            if (mainPanel != null) {
                mainPanel.cleanup();
            }
            if (dbManager != null) {
                dbManager.close();
            }
            if (api != null) {
                api.logging().logToOutput("URL Hunter extension unloaded");
            }
        } catch (Exception e) {
            if (api != null) {
                api.logging().logToError("清理资源时发生错误: " + e.getMessage());
            }
        }
    }
}