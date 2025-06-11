package com.urlhunter.ui;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.proxy.ProxyHttpRequestResponse;
import burp.api.montoya.proxy.http.InterceptedResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.HttpRequestResponse;
import com.urlhunter.model.URLEntry;
import com.urlhunter.model.DomainConfig;
import com.urlhunter.database.DatabaseManager;
import com.urlhunter.scanner.URLScanner;
import com.urlhunter.utils.URLAnalyzer;
import com.urlhunter.proxy.ProxyListener;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.text.SimpleDateFormat;

public class MainPanel extends JPanel {
    private final MontoyaApi api;
    private final DatabaseManager dbManager;
    private final URLScanner urlScanner;
    private final URLAnalyzer urlAnalyzer;
    private final ProxyListener proxyListener;
    
    // UI组件
    private JTabbedPane mainTabbedPane;
    private TitlesTreePanel titlesTreePanel;
    
    // Domains标签页组件
    private JTree domainTree;
    private JTable domainsTable;
    private DefaultTableModel domainsTableModel;
    private JTextField searchField;
    private JButton addDomainButton;
    private JButton getDomainButton;
    private JButton clearDomainsButton;
    
    // Tools标签页组件
    private JTextArea toolsInputArea;
    private JTextArea toolsOutputArea;
    private JComboBox<String> toolsComboBox;
    
    // 通用组件
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private JSplitPane mainSplitPane;
    
    // 数据
    private Map<String, DefaultMutableTreeNode> domainNodes = new HashMap<>();
    private List<URLEntry> allEntries = new ArrayList<>();
    private Set<String> checkedDomains = new HashSet<>();
    
    // 表格列名
    private static final String[] DOMAINS_COLUMNS = {
        "#", "域名", "子域名数量", "状态", "IP段", "备注"
    };
    
    // 状态常量
    private static final String STATUS_UNCHECKED = "UnChecked";
    private static final String STATUS_CHECKING = "Checking";
    private static final String STATUS_DONE = "Done";
    
    public MainPanel(MontoyaApi api, DatabaseManager dbManager) {
        this.api = api;
        this.dbManager = dbManager;
        this.urlAnalyzer = new URLAnalyzer();
        this.urlScanner = new URLScanner(api, dbManager);
        this.proxyListener = new ProxyListener(api, dbManager);
        
        setupScanCallback();
        setupProxyCallback();
        
        // 注册ProxyListener到Burp
        api.proxy().registerRequestHandler(proxyListener);
        api.proxy().registerResponseHandler(proxyListener);
        
        createUI();
    }
    
    private void setupScanCallback() {
        urlScanner.setScanCallback(new URLScanner.ScanCallback() {
            @Override
            public void onURLScanned(URLEntry urlEntry) {
                SwingUtilities.invokeLater(() -> {
                    updateDomainTree(urlEntry);
                    updateDomainsTable();
                    
                    // 同时更新树形面板
                    titlesTreePanel.addURLEntry(urlEntry, null);
                });
            }
            
            @Override
            public void onScanProgress(int current, int total) {
                SwingUtilities.invokeLater(() -> {
                    progressBar.setValue((int) ((double) current / total * 100));
                    statusLabel.setText(String.format("扫描进度: %d/%d", current, total));
                });
            }
            
            @Override
            public void onScanComplete() {
                SwingUtilities.invokeLater(() -> {
                    progressBar.setValue(100);
                    statusLabel.setText("扫描完成");
                });
            }
            
            @Override
            public void onError(String message) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("错误: " + message);
                    JOptionPane.showMessageDialog(MainPanel.this, message, "扫描错误", JOptionPane.ERROR_MESSAGE);
                });
            }
        });
    }
    
    private void setupProxyCallback() {
        proxyListener.setCallback(new ProxyListener.ProxyDiscoveryCallback() {
            @Override
            public void onSubdomainDiscovered(String rootDomain, String subdomain) {
                SwingUtilities.invokeLater(() -> {
                    // 更新域名表格中的子域名计数
                    updateSubdomainCountForRootDomain(rootDomain);
                    statusLabel.setText("发现新子域名: " + subdomain + " (主域名: " + rootDomain + ")");
                });
            }
            
            @Override
            public void onURLDiscovered(URLEntry urlEntry) {
                SwingUtilities.invokeLater(() -> {
                    // 添加到树形面板和列表
                    titlesTreePanel.addURLEntry(urlEntry, null);
                    allEntries.add(urlEntry);
                    statusLabel.setText("自动发现新URL: " + urlEntry.getUrl());
                });
            }
            
            @Override
            public void onURLDiscoveredWithRequestResponse(URLEntry urlEntry, burp.api.montoya.http.message.requests.HttpRequest request, burp.api.montoya.proxy.http.InterceptedResponse response) {
                SwingUtilities.invokeLater(() -> {
                    // 创建HttpRequestResponse对象
                    try {
                        burp.api.montoya.http.message.HttpRequestResponse requestResponse = 
                            burp.api.montoya.http.message.HttpRequestResponse.httpRequestResponse(request, response);
                        
                        // 使用包含完整请求响应数据的方法添加URL
                        titlesTreePanel.addURLEntryWithRequestResponse(urlEntry, requestResponse);
                        allEntries.add(urlEntry);
                        statusLabel.setText("自动发现新URL (含响应): " + urlEntry.getUrl());
                    } catch (Exception e) {
                        // 如果转换失败，使用原来的方法
                        titlesTreePanel.addURLEntry(urlEntry, null);
                        allEntries.add(urlEntry);
                        statusLabel.setText("自动发现新URL: " + urlEntry.getUrl());
                        api.logging().logToError("转换HttpRequestResponse失败: " + e.getMessage());
                    }
                });
            }
            
            @Override
            public void onRootDomainsUpdated(Set<String> newRootDomains) {
                // 根域名列表已更新的回调
                api.logging().logToOutput("ProxyListener根域名列表已同步，共 " + newRootDomains.size() + " 个域名");
            }
        });
    }
    
    private void updateProxyListenerDomains() {
        // 确保domainsTableModel已经初始化
        if (domainsTableModel == null) {
            return;
        }
        
        Set<String> currentRootDomains = new HashSet<>();
        for (int i = 0; i < domainsTableModel.getRowCount(); i++) {
            String domain = (String) domainsTableModel.getValueAt(i, 1);
            if (domain != null && !domain.trim().isEmpty()) {
                currentRootDomains.add(domain.trim());
            }
        }
        proxyListener.updateRootDomains(currentRootDomains);
    }
    
    /**
     * 更新特定根域名的子域名计数
     */
    private void updateSubdomainCountForRootDomain(String rootDomain) {
        try {
            for (int i = 0; i < domainsTableModel.getRowCount(); i++) {
                String domain = (String) domainsTableModel.getValueAt(i, 1);
                if (domain != null && domain.equalsIgnoreCase(rootDomain)) {
                    // 获取ProxyListener中的子域名统计
                    Map<String, Set<String>> discoveredSubdomains = proxyListener.getDiscoveredSubdomains();
                    Set<String> subdomains = discoveredSubdomains.get(rootDomain);
                    
                    if (subdomains != null) {
                        Object currentCount = domainsTableModel.getValueAt(i, 2);
                        int baseCount = 0;
                        if (currentCount != null) {
                            try {
                                baseCount = Integer.parseInt(currentCount.toString());
                            } catch (NumberFormatException e) {
                                baseCount = 0;
                            }
                        }
                        
                        // 显示实时发现的总数
                        int totalCount = Math.max(baseCount, subdomains.size());
                        domainsTableModel.setValueAt(totalCount, i, 2);
                    }
                    break;
                }
            }
        } catch (Exception e) {
            api.logging().logToError("更新根域名子域名计数失败: " + e.getMessage());
        }
    }
    
    private void createUI() {
        setLayout(new BorderLayout());
        
        // 创建菜单栏
        JMenuBar menuBar = createMenuBar();
        add(menuBar, BorderLayout.NORTH);
        
        // 创建树形Titles面板
        titlesTreePanel = new TitlesTreePanel(api, urlScanner);
        
        // 创建主标签页
        mainTabbedPane = new JTabbedPane();
        
        // Domains标签页
        JPanel domainsPanel = createDomainsPanel();
        mainTabbedPane.addTab("域名管理", domainsPanel);
        
        // Titles标签页 - 使用新的树形界面
        mainTabbedPane.addTab("Titles", titlesTreePanel);
        
        // Tools标签页
        JPanel toolsPanel = createToolsPanel();
        mainTabbedPane.addTab("Tools", toolsPanel);
        
        add(mainTabbedPane, BorderLayout.CENTER);
        
        // 底部状态栏
        JPanel statusPanel = createStatusPanel();
        add(statusPanel, BorderLayout.SOUTH);
        
        // 加载数据
        loadDataFromDatabase();
    }
    
    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        
        // 项目菜单
        JMenu projectMenu = new JMenu("项目");
        
        JMenuItem newProjectItem = new JMenuItem("新建项目");
        newProjectItem.addActionListener(e -> newProject());
        projectMenu.add(newProjectItem);
        
        JMenuItem openProjectItem = new JMenuItem("打开项目");
        openProjectItem.addActionListener(e -> openProject());
        projectMenu.add(openProjectItem);
        
        JMenuItem importDomainListItem = new JMenuItem("导入域名列表");
        importDomainListItem.addActionListener(e -> importDomainList());
        projectMenu.add(importDomainListItem);
        
        projectMenu.addSeparator();
        
        JMenuItem exitItem = new JMenuItem("退出");
        exitItem.addActionListener(e -> System.exit(0));
        projectMenu.add(exitItem);
        
        menuBar.add(projectMenu);
        
        // 配置菜单
        JMenu configMenu = new JMenu("配置");
        
        JMenuItem configItem = new JMenuItem("扫描配置");
        configItem.addActionListener(e -> showConfigDialog());
        configMenu.add(configItem);
        
        menuBar.add(configMenu);
        
        return menuBar;
    }
    
    private JPanel createDomainsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        // 顶部操作栏
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        
        addDomainButton = new JButton("添加域名");
        addDomainButton.addActionListener(e -> addDomain());
        topPanel.add(addDomainButton);
        
        JButton editDomainButton = new JButton("修改域名");
        editDomainButton.addActionListener(e -> editSelectedDomain());
        topPanel.add(editDomainButton);
        
        JButton deleteDomainButton = new JButton("删除域名");
        deleteDomainButton.addActionListener(e -> deleteSelectedDomain());
        topPanel.add(deleteDomainButton);
        
        getDomainButton = new JButton("从Proxy提取URL");
        getDomainButton.addActionListener(e -> getDomains());
        topPanel.add(getDomainButton);
        
        clearDomainsButton = new JButton("清空");
        clearDomainsButton.addActionListener(e -> clearDomains());
        topPanel.add(clearDomainsButton);
        
        topPanel.add(new JSeparator(SwingConstants.VERTICAL));
        
        JButton deleteAllButton = new JButton("删除选中域名的所有子域名和URL");
        deleteAllButton.addActionListener(e -> deleteAllSubdomainsAndUrls());
        
        // 自动监听控制按钮
        JButton toggleListenerButton = new JButton("关闭自动监听");
        toggleListenerButton.addActionListener(e -> {
            boolean currentStatus = proxyListener.isEnabled();
            proxyListener.setEnabled(!currentStatus);
            toggleListenerButton.setText(currentStatus ? "开启自动监听" : "关闭自动监听");
            
            String status = currentStatus ? "已关闭" : "已开启";
            statusLabel.setText("自动监听功能" + status);
        });
        
        topPanel.add(deleteAllButton);
        topPanel.add(toggleListenerButton);
        
        topPanel.add(new JSeparator(SwingConstants.VERTICAL));
        
        topPanel.add(new JLabel("搜索:"));
        searchField = new JTextField(20);
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                filterDomainsTable();
            }
        });
        topPanel.add(searchField);
        
        panel.add(topPanel, BorderLayout.NORTH);
        
        // 主域名表格（全屏显示，不再分割）
        createDomainsTable();
        JScrollPane tableScrollPane = new JScrollPane(domainsTable);
        panel.add(tableScrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createToolsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        // 顶部工具选择
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        
        topPanel.add(new JLabel("工具:"));
        toolsComboBox = new JComboBox<>(new String[]{
            "URL列表转换", "从JSON提取URL", "编码/解码", "IP段计算", "批量打开URL"
        });
        topPanel.add(toolsComboBox);
        
        JButton executeButton = new JButton("执行");
        executeButton.addActionListener(e -> executeTool());
        topPanel.add(executeButton);
        
        panel.add(topPanel, BorderLayout.NORTH);
        
        // 中间输入输出区域
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        
        // 输入区域
        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.setBorder(BorderFactory.createTitledBorder("输入"));
        toolsInputArea = new JTextArea(10, 50);
        inputPanel.add(new JScrollPane(toolsInputArea), BorderLayout.CENTER);
        splitPane.setTopComponent(inputPanel);
        
        // 输出区域
        JPanel outputPanel = new JPanel(new BorderLayout());
        outputPanel.setBorder(BorderFactory.createTitledBorder("输出"));
        toolsOutputArea = new JTextArea(10, 50);
        toolsOutputArea.setEditable(false);
        outputPanel.add(new JScrollPane(toolsOutputArea), BorderLayout.CENTER);
        splitPane.setBottomComponent(outputPanel);
        
        panel.add(splitPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    private void createDomainTree() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("域名列表");
        domainTree = new JTree(root);
        domainTree.setRootVisible(true);
        domainTree.setShowsRootHandles(true);
        
        // 添加右键菜单
        domainTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    showDomainTreeContextMenu(e);
                }
            }
        });
    }
    
    private void createDomainsTable() {
        domainsTableModel = new DefaultTableModel(DOMAINS_COLUMNS, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 5; // 只有备注列可编辑
            }
        };
        
        domainsTable = new JTable(domainsTableModel);
        domainsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        domainsTable.getTableHeader().setReorderingAllowed(false);
        domainsTable.setRowHeight(25);
        
        // 添加鼠标监听器
        domainsTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    showDomainsTableContextMenu(e);
                }
            }
        });
    }
    
    private JPanel createStatusPanel() {
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(BorderFactory.createEtchedBorder());
        
        statusLabel = new JLabel("就绪");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        statusPanel.add(statusLabel, BorderLayout.WEST);
        
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        statusPanel.add(progressBar, BorderLayout.CENTER);
        
        return statusPanel;
    }
    
    // 其余方法保持不变...
    // [保留其他必要的方法实现，但移除所有titles表格相关代码]
    
    public void shutdown() {
        urlScanner.shutdown();
    }
    
    // 从Proxy发现更新
    public void updateFromProxyDiscovery(URLEntry entry) {
        // 更新树形面板，尝试从Proxy历史中找到对应的请求响应
        if (titlesTreePanel != null) {
            ProxyHttpRequestResponse proxyRequestResponse = findProxyRequestResponseForURL(entry.getUrl());
            titlesTreePanel.addURLEntry(entry, proxyRequestResponse);
        }
        
        // 更新域名相关显示
        updateDomainTree(entry);
        updateDomainsTable();
    }
    
    /**
     * 从Proxy历史中查找对应URL的请求响应
     */
    private ProxyHttpRequestResponse findProxyRequestResponseForURL(String targetUrl) {
        try {
            // 遍历Proxy历史记录，查找匹配的URL
            for (var proxyItem : api.proxy().history()) {
                if (targetUrl.equals(proxyItem.request().url())) {
                    return proxyItem;
                }
            }
        } catch (Exception e) {
            api.logging().logToError("查找Proxy请求响应失败: " + e.getMessage());
        }
        return null;
    }
    
    // 简化的方法实现...
    private void newProject() { 
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("创建新项目数据库文件");
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("SQLite数据库文件 (*.db)", "db"));
        
        // 设置默认文件名
        fileChooser.setSelectedFile(new File("URLHunter_" + System.currentTimeMillis() + ".db"));
        
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            
            // 确保文件扩展名为.db
            String filePath = selectedFile.getAbsolutePath();
            if (!filePath.toLowerCase().endsWith(".db")) {
                filePath += ".db";
                selectedFile = new File(filePath);
            }
            
            // 检查文件是否已存在
            if (selectedFile.exists()) {
                int overwrite = JOptionPane.showConfirmDialog(this,
                    "文件已存在，是否覆盖？",
                    "文件已存在",
                    JOptionPane.YES_NO_OPTION);
                if (overwrite != JOptionPane.YES_OPTION) {
                    return;
                }
            }
            
            try {
                // 创建新的数据库文件
                dbManager.createNewDatabase(selectedFile.getAbsolutePath());
                clearAllData();
                statusLabel.setText("新项目已创建: " + selectedFile.getName());
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, 
                    "创建新项目失败: " + e.getMessage(), 
                    "错误", 
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void openProject() { 
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("SQLite数据库文件", "db"));
        
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            try {
                // 先清空所有现有数据
                clearAllData();
                
                // 切换数据库文件
                dbManager.switchDatabase(selectedFile.getAbsolutePath());
                
                // 从新数据库加载数据
                loadDataFromDatabase();
                
                statusLabel.setText("项目已打开: " + selectedFile.getName());
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "打开项目失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void addDomain() { 
        String domain = JOptionPane.showInputDialog(this, "请输入域名:", "添加域名", JOptionPane.PLAIN_MESSAGE);
        if (domain != null && !domain.trim().isEmpty()) {
            // 添加到域名表格
            domainsTableModel.addRow(new Object[] {
                domainsTableModel.getRowCount() + 1,
                domain.trim(),
                0,
                STATUS_UNCHECKED,
                "",
                ""
            });
            
            // 更新ProxyListener的根域名列表
            updateProxyListenerDomains();
            
            statusLabel.setText("域名 " + domain + " 已添加");
        }
    }
    
    private void getDomains() { 
        // 检查是否有主域名
        if (domainsTableModel.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "请先在域名管理中添加主域名", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // 获取所有已添加的主域名
        Set<String> rootDomains = new HashSet<>();
        for (int i = 0; i < domainsTableModel.getRowCount(); i++) {
            String domain = (String) domainsTableModel.getValueAt(i, 1);
            if (domain != null && !domain.trim().isEmpty()) {
                rootDomains.add(domain.trim().toLowerCase());
            }
        }
        
        if (rootDomains.isEmpty()) {
            JOptionPane.showMessageDialog(this, "域名管理中没有有效的主域名", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        getDomainButton.setEnabled(false);
        getDomainButton.setText("正在从Proxy提取...");
        
        CompletableFuture.runAsync(() -> {
            try {
                Map<String, Set<String>> discoveredSubdomains = new HashMap<>();
                Map<String, List<URLEntry>> discoveredUrls = new HashMap<>();
                
                // 遍历Proxy历史记录
                for (var proxyItem : api.proxy().history()) {
                    try {
                        String url = proxyItem.request().url();
                        String host = extractHostFromUrl(url);
                        
                        if (host != null && !host.isEmpty()) {
                            // 检查是否属于已添加的主域名
                            String matchedRootDomain = findMatchingRootDomain(host, rootDomains);
                            
                            if (matchedRootDomain != null) {
                                // 添加子域名到发现列表
                                discoveredSubdomains.computeIfAbsent(matchedRootDomain, k -> new HashSet<>()).add(host);
                                
                                // 创建URLEntry并添加到发现列表
                                URLEntry entry = createURLEntryFromProxy(proxyItem, url, host);
                                if (entry != null) {
                                    discoveredUrls.computeIfAbsent(matchedRootDomain, k -> new ArrayList<>()).add(entry);
                                }
                            }
                        }
                    } catch (Exception e) {
                        // 忽略单个条目的错误，继续处理
                        api.logging().logToError("处理单个proxy条目时出错: " + e.getMessage());
                    }
                }
                
                // 更新UI
                SwingUtilities.invokeLater(() -> {
                    try {
                        int totalSubdomains = 0;
                        int totalUrls = 0;
                        
                        // 处理发现的子域名和URL
                        for (String rootDomain : rootDomains) {
                            Set<String> subdomains = discoveredSubdomains.get(rootDomain);
                            List<URLEntry> urls = discoveredUrls.get(rootDomain);
                            
                            if (subdomains != null) {
                                totalSubdomains += subdomains.size();
                                
                                // 更新域名表格中的子域名数量
                                updateSubdomainCountForDomain(rootDomain, subdomains.size());
                            }
                            
                            if (urls != null) {
                                totalUrls += urls.size();
                                
                                // 保存URL到数据库并添加到树形面板
                                for (URLEntry entry : urls) {
                                    // 检查是否已存在相同URL，避免重复
                                    if (!isDuplicateURL(entry.getUrl())) {
                                        dbManager.insertURL(entry);
                                        titlesTreePanel.addURLEntry(entry, null);
                                        allEntries.add(entry);
                                    }
                                }
                            }
                        }
                        
                        statusLabel.setText(String.format("从Proxy提取完成：发现 %d 个子域名，%d 个URL", 
                            totalSubdomains, totalUrls));
                        
                        // 询问是否立即扫描发现的URL
                        if (totalUrls > 0) {
                            int result = JOptionPane.showConfirmDialog(this, 
                                String.format("发现 %d 个新URL，是否立即扫描获取详细信息？", totalUrls), 
                                "扫描确认", 
                                JOptionPane.YES_NO_OPTION,
                                JOptionPane.QUESTION_MESSAGE);
                            
                            if (result == JOptionPane.YES_OPTION) {
                                List<String> urlsToScan = new ArrayList<>();
                                for (List<URLEntry> urlList : discoveredUrls.values()) {
                                    for (URLEntry entry : urlList) {
                                        urlsToScan.add(entry.getUrl());
                                    }
                                }
                                urlScanner.scanURLList(urlsToScan);
                            }
                        }
                        
                    } catch (Exception e) {
                        statusLabel.setText("处理提取结果时发生错误: " + e.getMessage());
                        JOptionPane.showMessageDialog(this, "处理提取结果时发生错误: " + e.getMessage(), 
                            "错误", JOptionPane.ERROR_MESSAGE);
                    } finally {
                        getDomainButton.setEnabled(true);
                        getDomainButton.setText("从Proxy提取URL");
                    }
                });
                
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    getDomainButton.setEnabled(true);
                    getDomainButton.setText("从Proxy提取URL");
                    statusLabel.setText("从Proxy提取失败: " + e.getMessage());
                    JOptionPane.showMessageDialog(this, "从Proxy提取失败: " + e.getMessage(), 
                        "错误", JOptionPane.ERROR_MESSAGE);
                });
            }
        });
    }
    
    private void clearDomains() { 
        int result = JOptionPane.showConfirmDialog(this, 
            "确定要清空所有域名数据吗？", 
            "清空确认", 
            JOptionPane.YES_NO_OPTION);
        
        if (result == JOptionPane.YES_OPTION) {
            domainsTableModel.setRowCount(0);
            DefaultMutableTreeNode root = (DefaultMutableTreeNode) domainTree.getModel().getRoot();
            root.removeAllChildren();
            ((DefaultTreeModel) domainTree.getModel()).reload();
            domainNodes.clear();
            checkedDomains.clear();
            statusLabel.setText("域名数据已清空");
        }
    }
    
    private void filterDomainsTable() { 
        String searchText = searchField.getText().trim().toLowerCase();
        TableRowSorter<DefaultTableModel> sorter = (TableRowSorter<DefaultTableModel>) domainsTable.getRowSorter();
        if (searchText.isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + searchText));
        }
    }
    
    private void executeTool() { 
        String selectedTool = (String) toolsComboBox.getSelectedItem();
        String input = toolsInputArea.getText();
        String output = "";
        
        switch (selectedTool) {
            case "URL列表转换":
                output = convertURLList(input);
                break;
            case "从JSON提取URL":
                output = extractFromJSON(input);
                break;
            case "编码/解码":
                output = encodeDecodeText(input);
                break;
            case "IP段计算":
                output = calculateIPRange(input);
                break;
            case "批量打开URL":
                batchOpenURLs(input);
                output = "已在浏览器中打开URL";
                break;
        }
        
        toolsOutputArea.setText(output);
    }
    
    private String convertURLList(String input) {
        StringBuilder sb = new StringBuilder();
        String[] lines = input.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && (trimmed.startsWith("http://") || trimmed.startsWith("https://"))) {
                sb.append(trimmed).append("\n");
            }
        }
        return sb.toString();
    }
    
    private String extractFromJSON(String input) {
        StringBuilder sb = new StringBuilder();
        // 简单的URL提取逻辑
        String[] parts = input.split("\"");
        for (String part : parts) {
            if (part.startsWith("http://") || part.startsWith("https://")) {
                sb.append(part).append("\n");
            }
        }
        return sb.toString();
    }
    
    private String encodeDecodeText(String input) {
        try {
            // 尝试URL解码
            String decoded = java.net.URLDecoder.decode(input, "UTF-8");
            return "解码结果:\n" + decoded + "\n\n编码结果:\n" + java.net.URLEncoder.encode(input, "UTF-8");
        } catch (Exception e) {
            return "编码/解码失败: " + e.getMessage();
        }
    }
    
    private String calculateIPRange(String input) {
        return "IP段计算功能待实现";
    }
    
    private void batchOpenURLs(String input) {
        String[] lines = input.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && (trimmed.startsWith("http://") || trimmed.startsWith("https://"))) {
                try {
                    Desktop.getDesktop().browse(new java.net.URI(trimmed));
                    Thread.sleep(100); // 避免同时打开太多
                } catch (Exception e) {
                    // 忽略错误
                }
            }
        }
    }
    
    private void importDomainList() { 
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("文本文件", "txt"));
        
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                File selectedFile = fileChooser.getSelectedFile();
                List<String> lines = Files.readAllLines(selectedFile.toPath());
                
                for (String line : lines) {
                    String domain = line.trim();
                    if (!domain.isEmpty()) {
                        domainsTableModel.addRow(new Object[] {
                            domainsTableModel.getRowCount() + 1,
                            domain,
                            0,
                            STATUS_UNCHECKED,
                            "",
                            ""
                        });
                    }
                }
                
                statusLabel.setText("已导入 " + lines.size() + " 个域名");
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "导入失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void showConfigDialog() { 
        // 获取父窗口
        Window parentWindow = SwingUtilities.getWindowAncestor(this);
        ConfigDialog dialog = new ConfigDialog(parentWindow, dbManager);
        dialog.setVisible(true);
        
        // 配置对话框关闭后，更新ProxyListener的配置
        try {
            if (proxyListener != null) {
                com.urlhunter.model.DomainConfig config = dbManager.loadConfig();
                proxyListener.updateConfig(config);
                api.logging().logToOutput("已同步配置到ProxyListener");
            }
        } catch (Exception e) {
            api.logging().logToError("更新ProxyListener配置时发生错误: " + e.getMessage());
        }
    }
    
    private void showDomainTreeContextMenu(MouseEvent e) { 
        // 右键菜单实现
    }
    
    private void showDomainsTableContextMenu(MouseEvent e) { 
        // 右键菜单实现
    }
    
    private void loadDataFromDatabase() { 
        // 从数据库加载数据
        try {
            List<URLEntry> entries = dbManager.getAllURLs();
            
            // 重新构建allEntries列表
            allEntries.clear();
            allEntries.addAll(entries);
            
            // 加载URL数据到树形面板
            titlesTreePanel.setEntries(entries);
            
            // 更新域名树（需要保留这个逻辑）
            for (URLEntry entry : entries) {
                updateDomainTree(entry);
            }
            
            // 从URL数据中提取和重建域名表格数据
            rebuildDomainsTableFromURLs(entries);
            
            // 加载数据完成后，更新ProxyListener的根域名列表和配置
            updateProxyListenerDomains();
            updateProxyListenerConfig();
            
            statusLabel.setText("数据加载完成，共 " + entries.size() + " 个URL");
        } catch (Exception e) {
            statusLabel.setText("加载数据失败: " + e.getMessage());
            api.logging().logToError("加载数据失败: " + e.getMessage());
        }
    }
    
    private void clearAllData() { 
        // 清空所有数据
        titlesTreePanel.clearAllEntries();
        domainsTableModel.setRowCount(0);
        checkedDomains.clear();
        allEntries.clear();
    }
    
    private void updateDomainTree(URLEntry entry) { 
        // 域名树已移除，此方法暂时保留空实现以避免其他地方的调用错误
        // 可以在这里添加其他需要的逻辑
    }
    
    private void updateDomainsTable() { 
        // 更新域名表格中的子域名数量
        Map<String, Integer> domainCounts = new HashMap<>();
        for (URLEntry entry : allEntries) {
            String host = entry.getHost();
            domainCounts.put(host, domainCounts.getOrDefault(host, 0) + 1);
        }
        
        for (int i = 0; i < domainsTableModel.getRowCount(); i++) {
            String domain = (String) domainsTableModel.getValueAt(i, 1);
            Integer count = domainCounts.get(domain);
            if (count != null) {
                domainsTableModel.setValueAt(count, i, 2);
            }
        }
    }
    
    /**
     * 从URL数据重建域名表格
     */
    private void rebuildDomainsTableFromURLs(List<URLEntry> entries) {
        // 清空现有域名表格
        domainsTableModel.setRowCount(0);
        
        // 统计各主域名下的URL数量
        Map<String, Integer> domainCounts = new HashMap<>();
        Set<String> rootDomains = new HashSet<>();
        
        for (URLEntry entry : entries) {
            String host = entry.getHost();
            if (host != null && !host.isEmpty()) {
                // 提取根域名（简单实现：取最后两级域名）
                String rootDomain = extractRootDomain(host);
                if (rootDomain != null) {
                    rootDomains.add(rootDomain);
                    domainCounts.put(rootDomain, domainCounts.getOrDefault(rootDomain, 0) + 1);
                }
            }
        }
        
        // 将根域名添加到表格中
        int index = 1;
        for (String rootDomain : rootDomains) {
            int count = domainCounts.getOrDefault(rootDomain, 0);
            domainsTableModel.addRow(new Object[] {
                index++,
                rootDomain,
                count,
                STATUS_UNCHECKED,
                "",
                ""
            });
        }
    }
    
    /**
     * 从完整域名中提取根域名
     */
    private String extractRootDomain(String host) {
        if (host == null || host.isEmpty()) {
            return null;
        }
        
        // 移除端口号
        int colonIndex = host.indexOf(':');
        if (colonIndex != -1) {
            host = host.substring(0, colonIndex);
        }
        
        String[] parts = host.split("\\.");
        if (parts.length >= 2) {
            // 返回最后两级域名作为根域名
            return parts[parts.length - 2] + "." + parts[parts.length - 1];
        }
        
        return host;
    }
    
    private void editSelectedDomain() {
        int selectedRow = domainsTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "请先选择要修改的域名", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        int modelRow = domainsTable.convertRowIndexToModel(selectedRow);
        String oldDomain = (String) domainsTableModel.getValueAt(modelRow, 1);
        
        String newDomain = (String) JOptionPane.showInputDialog(this, 
            "请输入新的域名:", 
            "修改域名", 
            JOptionPane.PLAIN_MESSAGE, 
            null, 
            null, 
            oldDomain);
        
        if (newDomain != null && !newDomain.trim().isEmpty() && !newDomain.equals(oldDomain)) {
            // 更新域名表格
            domainsTableModel.setValueAt(newDomain, selectedRow, 1);
            
            // 更新数据库中的所有相关记录
            updateDomainReferences(oldDomain, newDomain);
            
            // 更新ProxyListener的根域名列表
            updateProxyListenerDomains();
            
            statusLabel.setText("域名已从 " + oldDomain + " 修改为 " + newDomain);
        }
    }
    
    private void deleteSelectedDomain() {
        int selectedRow = domainsTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "请先选择要删除的域名", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        int modelRow = domainsTable.convertRowIndexToModel(selectedRow);
        String domain = (String) domainsTableModel.getValueAt(modelRow, 1);
        
        int result = JOptionPane.showConfirmDialog(this, 
            "确定要删除域名 \"" + domain + "\" 吗？\n注意：这不会删除相关的子域名和URL数据。", 
            "删除确认", 
            JOptionPane.YES_NO_OPTION);
        
        if (result == JOptionPane.YES_OPTION) {
            domainsTableModel.removeRow(modelRow);
            
            // 重新编号
            updateRowNumbers();
            
            // 更新ProxyListener的根域名列表
            updateProxyListenerDomains();
            
            statusLabel.setText("已删除域名: " + domain);
        }
    }
    
    private void deleteAllSubdomainsAndUrls() {
        int selectedRow = domainsTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "请先选择要清理的域名", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        int modelRow = domainsTable.convertRowIndexToModel(selectedRow);
        String domain = (String) domainsTableModel.getValueAt(modelRow, 1);
        
        int result = JOptionPane.showConfirmDialog(this, 
            "确定要删除域名 \"" + domain + "\" 下的所有子域名和URL数据吗？\n这个操作不可恢复！", 
            "删除确认", 
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
        
        if (result == JOptionPane.YES_OPTION) {
            // 删除数据库中相关的URL条目
            try {
                List<URLEntry> allEntries = dbManager.getAllURLs();
                int deletedCount = 0;
                
                for (URLEntry entry : allEntries) {
                    String host = entry.getHost();
                    if (host != null && (host.equals(domain) || host.endsWith("." + domain))) {
                        dbManager.deleteURL(entry.getId());
                        deletedCount++;
                    }
                }
                
                // 从树形面板中移除相关条目
                if (titlesTreePanel != null) {
                    // titlesTreePanel.removeEntriesForDomain(domain); // 暂时注释，需要在TitlesTreePanel中实现
                }
                
                // 清空allEntries中的相关条目
                allEntries.removeIf(entry -> {
                    String host = entry.getHost();
                    return host != null && (host.equals(domain) || host.endsWith("." + domain));
                });
                
                // 重置域名表格中的子域名数量
                domainsTableModel.setValueAt(0, modelRow, 2);
                domainsTableModel.setValueAt(STATUS_UNCHECKED, modelRow, 3);
                
                statusLabel.setText("已删除域名 " + domain + " 下的 " + deletedCount + " 个子域名和URL");
                
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, 
                    "删除过程中发生错误: " + e.getMessage(), 
                    "错误", 
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void updateDomainReferences(String oldDomain, String newDomain) {
        // 更新数据库中的相关记录
        try {
            List<URLEntry> allEntries = dbManager.getAllURLs();
            
            for (URLEntry entry : allEntries) {
                String host = entry.getHost();
                if (host != null && (host.equals(oldDomain) || host.endsWith("." + oldDomain))) {
                    // 更新host字段
                    String newHost = host.equals(oldDomain) ? newDomain : host.replace("." + oldDomain, "." + newDomain);
                    entry.setHost(newHost);
                    
                    // 更新URL字段
                    String oldUrl = entry.getUrl();
                    String newUrl = oldUrl.replace(host, newHost);
                    entry.setUrl(newUrl);
                    
                    // 更新数据库
                    dbManager.updateURL(entry);
                }
            }
            
            // 更新树形面板
            if (titlesTreePanel != null) {
                // titlesTreePanel.updateDomainReferences(oldDomain, newDomain); // 暂时注释，需要在TitlesTreePanel中实现
            }
            
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, 
                "更新域名引用时发生错误: " + e.getMessage(), 
                "错误", 
                JOptionPane.ERROR_MESSAGE);
        }
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
            
            // 获取主机名部分（移除路径和查询参数）
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
            if (colonIndex != -1 && colonIndex > url.lastIndexOf(']')) { // 排除IPv6地址中的冒号
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
            
            // 子域名匹配（必须以.rootDomain结尾）
            if (host.endsWith("." + normalizedRootDomain)) {
                return rootDomain;
            }
        }
        
        return null;
    }
    
    /**
     * 从Proxy项目创建URLEntry
     */
    private URLEntry createURLEntryFromProxy(burp.api.montoya.proxy.ProxyHttpRequestResponse proxyItem, String url, String host) {
        try {
            URLEntry entry = new URLEntry();
            entry.setUrl(url);
            entry.setMethod(proxyItem.request().method());
            entry.setHost(host);
            entry.setPath(urlAnalyzer.extractPath(url));
            entry.setQuery(urlAnalyzer.extractQuery(url));
            
            // 处理响应信息
            if (proxyItem.response() != null) {
                entry.setStatusCode(proxyItem.response().statusCode());
                entry.setLength(proxyItem.response().body().length());
                
                try {
                    String responseBody = proxyItem.response().bodyToString();
                    entry.setTitle(urlAnalyzer.extractTitle(responseBody));
                } catch (Exception e) {
                    // 如果无法解析响应体，继续处理
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
            
            // 设置子域名
            entry.setSubdomain(urlAnalyzer.extractSubdomain(host));
            entry.setTimestamp(new Date());
            entry.setIsChecked(false);
            entry.setNotes("自动从Proxy提取");
            
            return entry;
        } catch (Exception e) {
            api.logging().logToError("创建URLEntry失败: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 更新指定域名的子域名数量
     */
    private void updateSubdomainCountForDomain(String rootDomain, int subdomainCount) {
        try {
            for (int i = 0; i < domainsTableModel.getRowCount(); i++) {
                String domain = (String) domainsTableModel.getValueAt(i, 1);
                if (domain != null && domain.equalsIgnoreCase(rootDomain)) {
                    // 获取当前子域名数量并更新
                    Object currentCount = domainsTableModel.getValueAt(i, 2);
                    int existingCount = 0;
                    if (currentCount != null) {
                        try {
                            existingCount = Integer.parseInt(currentCount.toString());
                        } catch (NumberFormatException e) {
                            existingCount = 0;
                        }
                    }
                    
                    // 更新为新发现的子域名数量（累加）
                    int newCount = existingCount + subdomainCount;
                    domainsTableModel.setValueAt(newCount, i, 2);
                    break;
                }
            }
        } catch (Exception e) {
            api.logging().logToError("更新子域名数量失败: " + e.getMessage());
        }
    }
    
    /**
     * 检查URL是否已存在（避免重复）
     */
    private boolean isDuplicateURL(String url) {
        if (url == null || url.isEmpty()) {
            return true;
        }
        
        // 在allEntries中查找是否已存在相同URL
        for (URLEntry entry : allEntries) {
            if (url.equals(entry.getUrl())) {
                return true;
            }
        }
        
        // 也可以查询数据库确认
        try {
            List<URLEntry> existingEntries = dbManager.getAllURLs();
            for (URLEntry entry : existingEntries) {
                if (url.equals(entry.getUrl())) {
                    return true;
                }
            }
        } catch (Exception e) {
            // 如果数据库查询失败，允许添加
            api.logging().logToError("检查重复URL时数据库查询失败: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * 更新行号
     */
    private void updateRowNumbers() {
        for (int i = 0; i < domainsTableModel.getRowCount(); i++) {
            domainsTableModel.setValueAt(i + 1, i, 0);
        }
    }
    
    /**
     * 清理资源
     */
    public void cleanup() {
        if (urlScanner != null) {
            urlScanner.shutdown();
        }
        if (proxyListener != null) {
            proxyListener.shutdown();
        }
    }
    
    /**
     * 更新ProxyListener的过滤配置
     */
    private void updateProxyListenerConfig() {
        if (proxyListener == null) {
            return;
        }
        
        try {
            com.urlhunter.model.DomainConfig config = dbManager.loadConfig();
            proxyListener.updateConfig(config);
            api.logging().logToOutput("已更新ProxyListener过滤配置");
        } catch (Exception e) {
            api.logging().logToError("更新ProxyListener配置失败: " + e.getMessage());
        }
    }
} 