package com.urlhunter.ui;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.proxy.ProxyHttpRequestResponse;
import burp.api.montoya.ui.editor.HttpRequestEditor;
import burp.api.montoya.ui.editor.HttpResponseEditor;
import burp.api.montoya.ui.editor.EditorOptions;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.urlhunter.model.URLEntry;
import com.urlhunter.utils.URLAnalyzer;
import com.urlhunter.scanner.URLScanner;
import com.urlhunter.model.URLEntry.CheckStatus;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.tree.TreePath;
import java.util.concurrent.CompletableFuture;

public class TitlesTreePanel extends JPanel {
    private final MontoyaApi api;
    private final URLAnalyzer urlAnalyzer;
    private final URLScanner urlScanner;
    
    private JTree urlTree;
    private DefaultTreeModel treeModel;
    private DefaultMutableTreeNode rootNode;
    private Map<String, DefaultMutableTreeNode> domainNodes;
    private Map<DefaultMutableTreeNode, URLEntry> nodeToEntryMap;
    private Map<DefaultMutableTreeNode, HttpRequestResponse> nodeToRequestResponseMap;
    
    // 使用Burp内置的编辑器组件
    private JSplitPane requestResponseSplitPane;
    private HttpRequestEditor requestEditor;
    private HttpResponseEditor responseEditor;
    
    // 当前选中的URL条目
    private URLEntry selectedEntry;
    private HttpRequestResponse selectedRequestResponse;
    
    // 表格列定义 - 保留核心列，删除Server和Asset
    private static final String[] COLUMN_NAMES = {
        "#", "URL", "Status", "Length", "Title", "Comments", "IP", "isCheck", "CheckDoneTime"
    };
    
    private static final int[] DEFAULT_COLUMN_WIDTHS = {
        40, 280, 60, 70, 150, 120, 100, 60, 120
    };
    
    // 动态列宽数组 - 用于拖动调整
    private int[] columnWidths;
    
    // 排序相关
    private int sortColumn = -1;
    private boolean sortAscending = true;
    
    // 直接编辑相关
    private JTextField editingTextField = null;
    private URLEntry editingEntry = null;
    private DefaultMutableTreeNode editingNode = null;
    
    // 筛选相关字段
    private Set<CheckStatus> selectedStatuses = new HashSet<>(); // 选中的状态集合
    private String searchText = ""; // 搜索关键字
    
    // 搜索范围：默认搜索所有内容
    // 已移除选项功能，固定搜索URL、标题、备注、请求内容、响应内容
    
    // UI组件
    private JTextField searchField;
    private JLabel statisticsLabel;
    
    // 完整数据备份（用于筛选时恢复）
    private Map<String, List<URLEntry>> allURLEntries = new HashMap<>();
    
    public TitlesTreePanel(MontoyaApi api, URLScanner urlScanner) {
        this.api = api;
        this.urlAnalyzer = new URLAnalyzer();
        this.urlScanner = urlScanner;
        this.domainNodes = new HashMap<>();
        this.nodeToEntryMap = new HashMap<>();
        this.nodeToRequestResponseMap = new HashMap<>();
        
        // 初始化列宽数组
        this.columnWidths = DEFAULT_COLUMN_WIDTHS.clone();
        
        initializeUI();
    }
    
    private void initializeUI() {
        setLayout(new BorderLayout());
        
        // 创建工具栏
        JPanel toolBar = createToolBar();
        add(toolBar, BorderLayout.NORTH);
        
        // 创建主分割面板
        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        
        // 上部：带表格头的树形结构
        JPanel treePanel = createTreePanelWithHeaders();
        mainSplitPane.setTopComponent(treePanel);
        
        // 下部：请求/响应显示
        createRequestResponsePanel();
        mainSplitPane.setBottomComponent(requestResponseSplitPane);
        
        mainSplitPane.setDividerLocation(400);
        mainSplitPane.setResizeWeight(0.6);
        
        add(mainSplitPane, BorderLayout.CENTER);
    }
    
    private JPanel createToolBar() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        
        JButton expandAllButton = new JButton("展开所有");
        expandAllButton.addActionListener(e -> expandAllNodes());
        panel.add(expandAllButton);
        
        JButton collapseAllButton = new JButton("折叠所有");
        collapseAllButton.addActionListener(e -> collapseAllNodes());
        panel.add(collapseAllButton);
        
        panel.add(new JSeparator(SwingConstants.VERTICAL));
        
        JButton refreshButton = new JButton("刷新");
        refreshButton.addActionListener(e -> refreshFromProxy());
        panel.add(refreshButton);
        
        panel.add(new JSeparator(SwingConstants.VERTICAL));
        
        // 创建搜索区域
        JPanel searchPanel = createSearchPanel();
        panel.add(searchPanel);
        
        return panel;
    }
    
    /**
     * 创建搜索面板，包含搜索框、按钮、状态选择器和统计信息
     */
    private JPanel createSearchPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        
        // 搜索框和按钮
        searchField = new JTextField(15);
        searchField.setToolTipText("输入搜索关键字，自动在URL、标题、备注、请求内容、响应内容中搜索");
        searchField.addActionListener(e -> performSearch()); // 支持回车搜索
        panel.add(searchField);
        
        JButton searchButton = new JButton("Search");
        searchButton.addActionListener(e -> performSearch());
        panel.add(searchButton);
        
        panel.add(new JSeparator(SwingConstants.VERTICAL));
        
        // 状态选择器（圆点形式）
        JPanel statusPanel = createStatusSelectionPanel();
        panel.add(statusPanel);
        
        panel.add(new JSeparator(SwingConstants.VERTICAL));
        
        // 统计信息显示
        statisticsLabel = new JLabel();
        updateStatistics();
        panel.add(statisticsLabel);
        
        return panel;
    }
    

    
    /**
     * 创建状态选择面板，使用圆点选择器
     */
    private JPanel createStatusSelectionPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        
        // 默认选中所有状态
        selectedStatuses.add(CheckStatus.UNCHECKED);
        selectedStatuses.add(CheckStatus.CHECKING);
        selectedStatuses.add(CheckStatus.DONE);
        
        // UnChecked 选择器
        JRadioButton uncheckedRadio = new JRadioButton("UnChecked", true);
        uncheckedRadio.addActionListener(e -> toggleStatusFilter(CheckStatus.UNCHECKED, uncheckedRadio.isSelected()));
        panel.add(uncheckedRadio);
        
        // Checking 选择器
        JRadioButton checkingRadio = new JRadioButton("Checking", true);
        checkingRadio.addActionListener(e -> toggleStatusFilter(CheckStatus.CHECKING, checkingRadio.isSelected()));
        panel.add(checkingRadio);
        
        // Done 选择器
        JRadioButton doneRadio = new JRadioButton("Done", true);
        doneRadio.addActionListener(e -> toggleStatusFilter(CheckStatus.DONE, doneRadio.isSelected()));
        panel.add(doneRadio);
        
        return panel;
    }
    
    /**
     * 切换状态筛选
     */
    private void toggleStatusFilter(CheckStatus status, boolean selected) {
        if (selected) {
            selectedStatuses.add(status);
        } else {
            selectedStatuses.remove(status);
        }
        
        // 应用筛选
        applyFilters();
    }
    
    /**
     * 执行搜索（点击Search按钮或回车）
     */
    private void performSearch() {
        searchText = searchField.getText().trim();
        
        // 如果有搜索内容，直接执行异步搜索（默认搜索所有内容：URL + 标题 + 备注 + 请求内容 + 响应内容）
        if (!searchText.isEmpty()) {
            // 直接异步执行搜索，无需确认
            performAsyncSearch();
        } else {
            // 清空搜索条件时同步执行
            applyFilters();
        }
    }
    
    /**
     * 异步执行搜索，避免阻塞UI
     */
    private void performAsyncSearch() {
        // 显示进度指示
        searchField.setEnabled(false);
        String originalText = searchText;
        searchField.setText("搜索中...");
        
        // 使用CompletableFuture异步执行搜索
        CompletableFuture.runAsync(() -> {
            // 在后台线程中执行搜索逻辑
            try {
                SwingUtilities.invokeLater(() -> applyFilters());
            } catch (Exception e) {
                api.logging().logToError("异步搜索失败: " + e.getMessage());
            }
        }).whenComplete((result, throwable) -> {
            // 恢复UI状态
            SwingUtilities.invokeLater(() -> {
                searchField.setEnabled(true);
                searchField.setText(originalText);
                if (throwable != null) {
                    JOptionPane.showMessageDialog(
                        this,
                        "搜索过程中发生错误: " + throwable.getMessage(),
                        "搜索错误",
                        JOptionPane.ERROR_MESSAGE
                    );
                }
            });
        });
    }
    
    /**
     * 应用所有筛选条件（状态筛选 + 文本搜索）
     */
    private void applyFilters() {
        // 保存展开状态
        Set<TreePath> expandedPaths = saveExpandedPaths();
        
        // 重新构建树结构
        rebuildTreeWithFilter();
        
        // 更新统计信息
        updateStatistics();
        
        // 恢复展开状态
        SwingUtilities.invokeLater(() -> {
            restoreExpandedPaths(expandedPaths);
        });
    }
    
    /**
     * 更新统计信息显示
     */
    private void updateStatistics() {
        int totalCount = 0;
        int uncheckedCount = 0;
        
        for (List<URLEntry> entries : allURLEntries.values()) {
            for (URLEntry entry : entries) {
                totalCount++;
                if (entry.getCheckStatus() == CheckStatus.UNCHECKED) {
                    uncheckedCount++;
                }
            }
        }
        
        statisticsLabel.setText(String.format("[ALL:%d Unchecked:%d]", totalCount, uncheckedCount));
    }
    
    private JPanel createTreePanelWithHeaders() {
        JPanel panel = new JPanel(new BorderLayout());
        
        // 创建表格头
        JPanel headerPanel = createTableHeader();
        panel.add(headerPanel, BorderLayout.NORTH);
        
        // 创建树形结构
        createURLTree();
        JScrollPane treeScrollPane = new JScrollPane(urlTree);
        treeScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        panel.add(treeScrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createTableHeader() {
        JPanel headerPanel = new ResizableHeaderPanel();
        headerPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
        headerPanel.setBackground(Color.LIGHT_GRAY);
        headerPanel.setBorder(BorderFactory.createRaisedBevelBorder());
        headerPanel.setPreferredSize(new Dimension(0, 25));
        
        // 计算总宽度并优化显示
        int totalWidth = 0;
        for (int width : columnWidths) {
            totalWidth += width;
        }
        
        for (int i = 0; i < COLUMN_NAMES.length; i++) {
            final int columnIndex = i;
            String columnText = COLUMN_NAMES[i];
            
            // 添加排序指示符
            if (sortColumn == i) {
                columnText += sortAscending ? " ↑" : " ↓";
            }
            
            JLabel label = new JLabel(columnText);
            // 使用Burp默认字体，仅设置为粗体
            label.setFont(label.getFont().deriveFont(Font.BOLD));
            label.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
            label.setHorizontalAlignment(SwingConstants.LEFT);
            label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            
            // 添加点击排序功能
            label.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    sortByColumn(columnIndex);
                }
            });
            
            // 设置固定宽度
            Dimension size = new Dimension(columnWidths[i], 21);
            label.setPreferredSize(size);
            label.setMinimumSize(size);
            label.setMaximumSize(size);
            
            headerPanel.add(label);
            
            // 添加分隔线（除了最后一列）
            if (i < COLUMN_NAMES.length - 1) {
                JLabel separator = new JLabel();
                separator.setOpaque(true);
                separator.setBackground(Color.GRAY);
                separator.setPreferredSize(new Dimension(2, 21));
                separator.setMinimumSize(new Dimension(2, 21));
                separator.setMaximumSize(new Dimension(2, 21));
                separator.setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
                headerPanel.add(separator);
            }
        }
        
        return headerPanel;
    }
    
    private void createURLTree() {
        rootNode = new DefaultMutableTreeNode("Root");
        treeModel = new DefaultTreeModel(rootNode);
        urlTree = new JTree(treeModel);
        
        // 设置自定义渲染器
        urlTree.setCellRenderer(new TableStyleTreeCellRenderer());
        
        // 隐藏根节点，直接显示域名节点
        urlTree.setRootVisible(false);
        urlTree.setShowsRootHandles(true);
        
        // 添加选择监听器
        urlTree.addTreeSelectionListener(e -> {
            DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) urlTree.getLastSelectedPathComponent();
            if (selectedNode != null && nodeToEntryMap.containsKey(selectedNode)) {
                selectedEntry = nodeToEntryMap.get(selectedNode);
                selectedRequestResponse = nodeToRequestResponseMap.get(selectedNode);
                updateRequestResponseDisplay();
            } else {
                clearRequestResponseDisplay();
            }
        });
        
        // 添加右键菜单和单击编辑
        urlTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    showContextMenu(e);
                }
            }
            
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 1) {  // 单击
                    handleSingleClick(e);
                }
            }
        });
        
        // 设置行高以容纳表格式内容
        urlTree.setRowHeight(22);
    }
    
    private void createRequestResponsePanel() {
        // 创建主分割面板用于请求和响应
        requestResponseSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        
        // 左侧：请求编辑器
        requestEditor = api.userInterface().createHttpRequestEditor(EditorOptions.READ_ONLY);
        JPanel requestPanel = new JPanel(new BorderLayout());
        requestPanel.setBorder(BorderFactory.createTitledBorder("Request"));
        requestPanel.add(requestEditor.uiComponent(), BorderLayout.CENTER);
        
        requestResponseSplitPane.setLeftComponent(requestPanel);
        
        // 右侧：响应编辑器
        responseEditor = api.userInterface().createHttpResponseEditor(EditorOptions.READ_ONLY);
        JPanel responsePanel = new JPanel(new BorderLayout());
        responsePanel.setBorder(BorderFactory.createTitledBorder("Response"));
        responsePanel.add(responseEditor.uiComponent(), BorderLayout.CENTER);
        
        requestResponseSplitPane.setRightComponent(responsePanel);
        
        // 设置分割面板比例
        requestResponseSplitPane.setDividerLocation(0.5);
        requestResponseSplitPane.setResizeWeight(0.5);
    }

    public void addURLEntry(URLEntry entry, ProxyHttpRequestResponse requestResponse) {
        // 检查去重 - 只对URL路径部分去重，忽略参数
        String entryUrlWithoutParams = removeUrlParameters(entry.getUrl());
        for (URLEntry existing : nodeToEntryMap.values()) {
            String existingUrlWithoutParams = removeUrlParameters(existing.getUrl());
            if (existingUrlWithoutParams.equals(entryUrlWithoutParams)) {
                // 更新现有条目
                updateExistingEntry(existing, entry);
                return;
            }
        }
        
        // 添加新条目
        String host = entry.getHost();
        
        // 获取或创建域名节点
        DefaultMutableTreeNode domainNode = domainNodes.get(host);
        if (domainNode == null) {
            domainNode = new DefaultMutableTreeNode(new DomainInfo(host));
            domainNodes.put(host, domainNode);
            rootNode.add(domainNode);
            
            // 按域名排序
            sortDomainNodes();
        }
        
        // 创建URL节点
        DefaultMutableTreeNode urlNode = new DefaultMutableTreeNode(new URLInfo(entry));
        nodeToEntryMap.put(urlNode, entry);
        
        // 如果有ProxyHttpRequestResponse，转换为HttpRequestResponse并存储原始数据
        if (requestResponse != null) {
            try {
                // 存储原始请求数据到URLEntry
                if (requestResponse.request() != null) {
                    entry.setRequestData(requestResponse.request().toByteArray().getBytes());
                }
                
                // 存储原始响应数据到URLEntry
                if (requestResponse.response() != null) {
                    entry.setResponseData(requestResponse.response().toByteArray().getBytes());
                }
                
                // 同时保持内存中的映射（用于即时访问）
                HttpRequestResponse httpRequestResponse = HttpRequestResponse.httpRequestResponse(
                    requestResponse.request(), 
                    requestResponse.response()
                );
                nodeToRequestResponseMap.put(urlNode, httpRequestResponse);
            } catch (Exception e) {
                api.logging().logToError("处理ProxyHttpRequestResponse失败: " + e.getMessage());
            }
        }
        
        domainNode.add(urlNode);
        
        // 按路径排序URL节点
        sortURLNodes(domainNode);
        
        // 更新域名节点的URL计数
        DomainInfo domainInfo = (DomainInfo) domainNode.getUserObject();
        domainInfo.setUrlCount(domainNode.getChildCount());
        
        // 通知模型更新
        final DefaultMutableTreeNode finalDomainNode = domainNode;
        SwingUtilities.invokeLater(() -> {
            treeModel.nodeStructureChanged(rootNode);
            treeModel.nodeChanged(finalDomainNode);
        });
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
     * 添加带有完整HttpRequestResponse的URL条目
     */
    public void addURLEntryWithRequestResponse(URLEntry entry, HttpRequestResponse requestResponse) {
        // 检查去重 - 只对URL路径部分去重，忽略参数
        String entryUrlWithoutParams = removeUrlParameters(entry.getUrl());
        for (URLEntry existing : nodeToEntryMap.values()) {
            String existingUrlWithoutParams = removeUrlParameters(existing.getUrl());
            if (existingUrlWithoutParams.equals(entryUrlWithoutParams)) {
                // 更新现有条目
                updateExistingEntry(existing, entry);
                return;
            }
        }
        
        // 添加新条目
        String host = entry.getHost();
        
        // 获取或创建域名节点
        DefaultMutableTreeNode domainNode = domainNodes.get(host);
        if (domainNode == null) {
            domainNode = new DefaultMutableTreeNode(new DomainInfo(host));
            domainNodes.put(host, domainNode);
            rootNode.add(domainNode);
            
            // 按域名排序
            sortDomainNodes();
        }
        
        // 创建URL节点
        DefaultMutableTreeNode urlNode = new DefaultMutableTreeNode(new URLInfo(entry));
        nodeToEntryMap.put(urlNode, entry);
        
        // 直接保存HttpRequestResponse并存储原始数据
        if (requestResponse != null) {
            try {
                // 存储原始请求数据到URLEntry
                if (requestResponse.request() != null) {
                    entry.setRequestData(requestResponse.request().toByteArray().getBytes());
                }
                
                // 存储原始响应数据到URLEntry
                if (requestResponse.response() != null) {
                    entry.setResponseData(requestResponse.response().toByteArray().getBytes());
                }
                
                // 保持内存中的映射
                nodeToRequestResponseMap.put(urlNode, requestResponse);
            } catch (Exception e) {
                api.logging().logToError("处理HttpRequestResponse失败: " + e.getMessage());
            }
        }
        
        // 添加到完整数据备份
        allURLEntries.computeIfAbsent(host, k -> new ArrayList<>()).add(entry);
        
        domainNode.add(urlNode);
        
        // 按路径排序URL节点
        sortURLNodes(domainNode);
        
        // 更新域名节点的URL计数
        DomainInfo domainInfo = (DomainInfo) domainNode.getUserObject();
        domainInfo.setUrlCount(domainNode.getChildCount());
        
        // 通知模型更新
        final DefaultMutableTreeNode finalDomainNode = domainNode;
        SwingUtilities.invokeLater(() -> {
            treeModel.nodeStructureChanged(rootNode);
            treeModel.nodeChanged(finalDomainNode);
        });
    }
    
    private void updateExistingEntry(URLEntry existing, URLEntry newEntry) {
        // 更新现有条目的信息
        if (newEntry.getStatusCode() != 0) {
            existing.setStatusCode(newEntry.getStatusCode());
        }
        if (newEntry.getLength() > 0) {
            existing.setLength(newEntry.getLength());
        }
        if (newEntry.getTitle() != null && !newEntry.getTitle().isEmpty()) {
            existing.setTitle(newEntry.getTitle());
        }
    }
    
    private void sortDomainNodes() {
        if (rootNode.getChildCount() <= 1) return;
        
        // 获取所有域名节点并排序
        List<DefaultMutableTreeNode> domainNodesList = new ArrayList<>();
        for (int i = 0; i < rootNode.getChildCount(); i++) {
            domainNodesList.add((DefaultMutableTreeNode) rootNode.getChildAt(i));
        }
        
        domainNodesList.sort((node1, node2) -> {
            DomainInfo info1 = (DomainInfo) node1.getUserObject();
            DomainInfo info2 = (DomainInfo) node2.getUserObject();
            return info1.getDomain().compareToIgnoreCase(info2.getDomain());
        });
        
        // 重建树结构
        rootNode.removeAllChildren();
        for (DefaultMutableTreeNode node : domainNodesList) {
            rootNode.add(node);
        }
    }
    
    private void sortURLNodes(DefaultMutableTreeNode domainNode) {
        if (domainNode.getChildCount() <= 1) return;
        
        // 获取所有URL节点并排序
        List<DefaultMutableTreeNode> urlNodesList = new ArrayList<>();
        for (int i = 0; i < domainNode.getChildCount(); i++) {
            urlNodesList.add((DefaultMutableTreeNode) domainNode.getChildAt(i));
        }
        
        urlNodesList.sort((node1, node2) -> {
            URLEntry entry1 = nodeToEntryMap.get(node1);
            URLEntry entry2 = nodeToEntryMap.get(node2);
            
            if (sortColumn == -1) {
                // 默认按路径排序
                return entry1.getPath().compareToIgnoreCase(entry2.getPath());
            }
            
            int result = 0;
            switch (sortColumn) {
                case 0: // #
                    // 序号按添加顺序排序，这里按URL长度近似
                    result = Integer.compare(entry1.getUrl().length(), entry2.getUrl().length());
                    break;
                case 1: // URL
                    result = entry1.getUrl().compareToIgnoreCase(entry2.getUrl());
                    break;
                case 2: // Status
                    result = Integer.compare(entry1.getStatusCode(), entry2.getStatusCode());
                    break;
                case 3: // Length
                    result = Integer.compare(entry1.getLength(), entry2.getLength());
                    break;
                case 4: // Title
                    String title1 = entry1.getTitle() != null ? entry1.getTitle() : "";
                    String title2 = entry2.getTitle() != null ? entry2.getTitle() : "";
                    result = title1.compareToIgnoreCase(title2);
                    break;
                case 5: // Comments
                    String notes1 = entry1.getNotes() != null ? entry1.getNotes() : "";
                    String notes2 = entry2.getNotes() != null ? entry2.getNotes() : "";
                    result = notes1.compareToIgnoreCase(notes2);
                    break;
                case 6: // IP
                    String ip1 = entry1.getIp() != null ? entry1.getIp() : "";
                    String ip2 = entry2.getIp() != null ? entry2.getIp() : "";
                    result = ip1.compareToIgnoreCase(ip2);
                    break;
                case 7: // isCheck
                    result = Boolean.compare(entry1.isChecked(), entry2.isChecked());
                    break;
                case 8: // CheckDoneTime
                    result = Long.compare(entry1.getTimestamp(), entry2.getTimestamp());
                    break;
                default:
                    result = entry1.getPath().compareToIgnoreCase(entry2.getPath());
            }
            
            return sortAscending ? result : -result;
        });
        
        // 重建URL节点结构
        domainNode.removeAllChildren();
        for (DefaultMutableTreeNode node : urlNodesList) {
            domainNode.add(node);
        }
    }
    
    public void clearAllEntries() {
        rootNode.removeAllChildren();
        domainNodes.clear();
        nodeToEntryMap.clear();
        nodeToRequestResponseMap.clear();
        allURLEntries.clear();
        // 重置筛选状态
        selectedStatuses.clear();
        selectedStatuses.add(CheckStatus.UNCHECKED);
        selectedStatuses.add(CheckStatus.CHECKING);
        selectedStatuses.add(CheckStatus.DONE);
        SwingUtilities.invokeLater(() -> treeModel.nodeStructureChanged(rootNode));
    }
    
    public void setEntries(List<URLEntry> entries) {
        clearAllEntries();
        for (URLEntry entry : entries) {
            // 对于从数据库加载的条目，尝试重建HttpRequestResponse映射
            addURLEntryFromDatabase(entry);
        }
    }
    
    /**
     * 从数据库添加URLEntry，如果有存储的request/response数据，重建映射
     */
    private void addURLEntryFromDatabase(URLEntry entry) {
        // 检查去重 - 只对URL路径部分去重，忽略参数
        String entryUrlWithoutParams = removeUrlParameters(entry.getUrl());
        for (URLEntry existing : nodeToEntryMap.values()) {
            String existingUrlWithoutParams = removeUrlParameters(existing.getUrl());
            if (existingUrlWithoutParams.equals(entryUrlWithoutParams)) {
                // 更新现有条目
                updateExistingEntry(existing, entry);
                return;
            }
        }
        
        // 添加新条目
        String host = entry.getHost();
        
        // 获取或创建域名节点
        DefaultMutableTreeNode domainNode = domainNodes.get(host);
        if (domainNode == null) {
            domainNode = new DefaultMutableTreeNode(new DomainInfo(host));
            domainNodes.put(host, domainNode);
            rootNode.add(domainNode);
            
            // 按域名排序
            sortDomainNodes();
        }
        
        // 创建URL节点
        DefaultMutableTreeNode urlNode = new DefaultMutableTreeNode(new URLInfo(entry));
        nodeToEntryMap.put(urlNode, entry);
        
        // 如果有存储的request/response数据，重建HttpRequestResponse映射
        if (entry.getRequestData() != null || entry.getResponseData() != null) {
            try {
                HttpRequest request = null;
                burp.api.montoya.http.message.responses.HttpResponse response = null;
                
                if (entry.getRequestData() != null) {
                    request = HttpRequest.httpRequest(
                        burp.api.montoya.core.ByteArray.byteArray(entry.getRequestData()));
                }
                
                if (entry.getResponseData() != null) {
                    response = burp.api.montoya.http.message.responses.HttpResponse.httpResponse(
                        burp.api.montoya.core.ByteArray.byteArray(entry.getResponseData()));
                }
                
                if (request != null) {
                    HttpRequestResponse httpRequestResponse = HttpRequestResponse.httpRequestResponse(request, response);
                    nodeToRequestResponseMap.put(urlNode, httpRequestResponse);
                }
            } catch (Exception e) {
                api.logging().logToError("从数据库重建HttpRequestResponse失败: " + e.getMessage());
            }
        }
        
        // 添加到完整数据备份
        allURLEntries.computeIfAbsent(host, k -> new ArrayList<>()).add(entry);
        
        domainNode.add(urlNode);
        
        // 按路径排序URL节点
        sortURLNodes(domainNode);
        
        // 更新域名节点的URL计数
        DomainInfo domainInfo = (DomainInfo) domainNode.getUserObject();
        domainInfo.setUrlCount(domainNode.getChildCount());
        
        // 通知模型更新
        final DefaultMutableTreeNode finalDomainNode = domainNode;
        SwingUtilities.invokeLater(() -> {
            treeModel.nodeStructureChanged(rootNode);
            treeModel.nodeChanged(finalDomainNode);
        });
    }
    
    private void expandAllNodes() {
        for (int i = 0; i < urlTree.getRowCount(); i++) {
            urlTree.expandRow(i);
        }
    }
    
    private void collapseAllNodes() {
        for (int i = urlTree.getRowCount() - 1; i >= 0; i--) {
            urlTree.collapseRow(i);
        }
    }
    
    private void filterTree(String searchText) {
        if (searchText == null || searchText.trim().isEmpty()) {
            expandAllNodes();
            return;
        }
        
        String filter = searchText.toLowerCase().trim();
        collapseAllNodes();
        expandNodesContaining(rootNode, filter);
    }
    
    private boolean expandNodesContaining(DefaultMutableTreeNode node, String filter) {
        boolean shouldExpand = false;
        
        // 检查当前节点
        if (node.getUserObject() instanceof URLInfo) {
            URLInfo urlInfo = (URLInfo) node.getUserObject();
            if (urlInfo.matchesFilter(filter)) {
                shouldExpand = true;
            }
        } else if (node.getUserObject() instanceof DomainInfo) {
            DomainInfo domainInfo = (DomainInfo) node.getUserObject();
            if (domainInfo.getDomain().toLowerCase().contains(filter)) {
                shouldExpand = true;
            }
        }
        
        // 检查子节点
        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
            if (expandNodesContaining(child, filter)) {
                shouldExpand = true;
            }
        }
        
        // 如果应该展开，则展开此节点
        if (shouldExpand && node != rootNode) {
            TreePath path = new TreePath(node.getPath());
            urlTree.expandPath(path);
        }
        
        return shouldExpand;
    }

    // 保留兼容性，但标记为废弃
    @Deprecated 
    private void filterByStatus(String status) {
        // 转换为新的状态筛选方式
        selectedStatuses.clear();
        switch (status) {
            case "ALL":
                selectedStatuses.add(CheckStatus.UNCHECKED);
                selectedStatuses.add(CheckStatus.CHECKING);
                selectedStatuses.add(CheckStatus.DONE);
                break;
            case "CHECKED":
                selectedStatuses.add(CheckStatus.DONE);
                break;
            case "UNCHECKED":
                selectedStatuses.add(CheckStatus.UNCHECKED);
                break;
        }
        applyFilters();
    }

    private void rebuildTreeWithFilter() {
        // 清空当前树结构和映射
        rootNode.removeAllChildren();
        nodeToEntryMap.clear();
        nodeToRequestResponseMap.clear(); // 同时清空请求响应映射
        domainNodes.clear(); // 必须清空域名节点映射！
        
        // 根据筛选条件重新添加节点
        for (Map.Entry<String, List<URLEntry>> domainEntry : allURLEntries.entrySet()) {
            String domain = domainEntry.getKey();
            List<URLEntry> entries = domainEntry.getValue();
            
            // 应用状态筛选 + 文本搜索筛选
            List<URLEntry> filteredEntries = entries.stream()
                .filter(this::shouldShowEntry) // 状态筛选
                .filter(this::matchesSearchText) // 文本搜索
                .collect(Collectors.toList());
            
            if (!filteredEntries.isEmpty()) {
                // 创建新的域名节点（因为已经清空了domainNodes）
                DomainInfo newDomainInfo = new DomainInfo(domain);
                DefaultMutableTreeNode domainNode = new DefaultMutableTreeNode(newDomainInfo);
                domainNodes.put(domain, domainNode);
                
                // 添加筛选后的URL节点
                for (URLEntry urlEntry : filteredEntries) {
                    URLInfo urlInfo = new URLInfo(urlEntry);
                    DefaultMutableTreeNode urlNode = new DefaultMutableTreeNode(urlInfo);
                    domainNode.add(urlNode);
                    nodeToEntryMap.put(urlNode, urlEntry);
                    
                    // 重建HttpRequestResponse映射（如果有存储的数据）
                    if (urlEntry.getRequestData() != null || urlEntry.getResponseData() != null) {
                        try {
                            HttpRequest request = null;
                            burp.api.montoya.http.message.responses.HttpResponse response = null;
                            
                            if (urlEntry.getRequestData() != null) {
                                request = HttpRequest.httpRequest(
                                    burp.api.montoya.core.ByteArray.byteArray(urlEntry.getRequestData()));
                            }
                            
                            if (urlEntry.getResponseData() != null) {
                                response = burp.api.montoya.http.message.responses.HttpResponse.httpResponse(
                                    burp.api.montoya.core.ByteArray.byteArray(urlEntry.getResponseData()));
                            }
                            
                            // 创建HttpRequestResponse并添加到映射
                            if (request != null) {
                                HttpRequestResponse httpRequestResponse = HttpRequestResponse.httpRequestResponse(request, response);
                                nodeToRequestResponseMap.put(urlNode, httpRequestResponse);
                            }
                        } catch (Exception e) {
                            api.logging().logToError("重建HttpRequestResponse映射失败: " + e.getMessage() + 
                                " URL: " + urlEntry.getUrl());
                        }
                    }
                }
                
                // 更新域名URL计数
                newDomainInfo.setUrlCount(domainNode.getChildCount());
                
                // 添加到根节点
                rootNode.add(domainNode);
            }
        }
        
        // 刷新显示
        treeModel.nodeStructureChanged(rootNode);
        urlTree.repaint();
    }

    private boolean shouldShowEntry(URLEntry entry) {
        return selectedStatuses.contains(entry.getCheckStatus());
    }
    
    private boolean matchesSearchText(URLEntry entry) {
        if (searchText.isEmpty()) {
            return true; // 无搜索条件时显示所有
        }
        
        String searchLower = searchText.toLowerCase();
        
        // 搜索URL
        if (entry.getUrl().toLowerCase().contains(searchLower)) {
            return true;
        }
        
        // 搜索标题
        if (entry.getTitle() != null && 
            entry.getTitle().toLowerCase().contains(searchLower)) {
            return true;
        }
        
        // 搜索备注
        if (entry.getNotes() != null && 
            entry.getNotes().toLowerCase().contains(searchLower)) {
            return true;
        }
        
        // 搜索请求内容
        if (searchInRequestData(entry, searchLower)) {
            return true;
        }
        
        // 搜索响应内容
        if (searchInResponseData(entry, searchLower)) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 在请求数据中搜索
     */
    private boolean searchInRequestData(URLEntry entry, String searchLower) {
        try {
            // 优先从内存中搜索（如果有的话）
            for (Map.Entry<DefaultMutableTreeNode, URLEntry> nodeEntry : nodeToEntryMap.entrySet()) {
                if (nodeEntry.getValue() == entry) {
                    HttpRequestResponse requestResponse = nodeToRequestResponseMap.get(nodeEntry.getKey());
                    if (requestResponse != null && requestResponse.request() != null) {
                        String requestText = requestResponse.request().toString().toLowerCase();
                        if (requestText.contains(searchLower)) {
                            return true;
                        }
                    }
                    break;
                }
            }
            
            // 如果内存中没有，从数据库中搜索
            if (entry.getRequestData() != null) {
                String requestText = new String(entry.getRequestData()).toLowerCase();
                return requestText.contains(searchLower);
            }
        } catch (Exception e) {
            api.logging().logToError("搜索请求数据失败: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * 在响应数据中搜索
     */
    private boolean searchInResponseData(URLEntry entry, String searchLower) {
        try {
            // 优先从内存中搜索（如果有的话）
            for (Map.Entry<DefaultMutableTreeNode, URLEntry> nodeEntry : nodeToEntryMap.entrySet()) {
                if (nodeEntry.getValue() == entry) {
                    HttpRequestResponse requestResponse = nodeToRequestResponseMap.get(nodeEntry.getKey());
                    if (requestResponse != null && requestResponse.response() != null) {
                        String responseText = requestResponse.response().toString().toLowerCase();
                        if (responseText.contains(searchLower)) {
                            return true;
                        }
                    }
                    break;
                }
            }
            
            // 如果内存中没有，从数据库中搜索
            if (entry.getResponseData() != null) {
                String responseText = new String(entry.getResponseData()).toLowerCase();
                return responseText.contains(searchLower);
            }
        } catch (Exception e) {
            api.logging().logToError("搜索响应数据失败: " + e.getMessage());
        }
        
        return false;
    }
    
    private void refreshFromProxy() {
        // TODO: 从Proxy历史记录刷新数据
        api.logging().logToOutput("刷新Proxy数据功能待实现");
    }
    
    private String extractDomain(String url) {
        try {
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "http://" + url;
            }
            java.net.URL urlObj = new java.net.URL(url);
            return urlObj.getHost();
        } catch (Exception e) {
            return "Unknown";
        }
    }
    
    private void updateRequestResponseDisplay() {
        if (selectedEntry != null) {
            // 更新请求和响应编辑器
            if (selectedRequestResponse != null) {
                requestEditor.setRequest(selectedRequestResponse.request());
                if (selectedRequestResponse.response() != null) {
                    responseEditor.setResponse(selectedRequestResponse.response());
                } else {
                    responseEditor.setResponse(null);
                }
            } else {
                // 如果内存中没有，尝试从数据库中恢复
                try {
                    if (selectedEntry.getRequestData() != null) {
                        HttpRequest request = HttpRequest.httpRequest(
                            burp.api.montoya.core.ByteArray.byteArray(selectedEntry.getRequestData()));
                        requestEditor.setRequest(request);
                    } else {
                        // 如果没有存储的请求数据，尝试构建一个基本的请求
                        HttpRequest request = HttpRequest.httpRequestFromUrl(selectedEntry.getUrl());
                        requestEditor.setRequest(request);
                    }
                    
                    if (selectedEntry.getResponseData() != null) {
                        burp.api.montoya.http.message.responses.HttpResponse response = 
                            burp.api.montoya.http.message.responses.HttpResponse.httpResponse(
                                burp.api.montoya.core.ByteArray.byteArray(selectedEntry.getResponseData()));
                        responseEditor.setResponse(response);
                    } else {
                        responseEditor.setResponse(null);
                    }
                } catch (Exception e) {
                    api.logging().logToError("从数据库恢复请求/响应数据失败: " + e.getMessage());
                    try {
                        // 降级处理：至少显示基本的请求
                        HttpRequest request = HttpRequest.httpRequestFromUrl(selectedEntry.getUrl());
                        requestEditor.setRequest(request);
                        responseEditor.setResponse(null);
                    } catch (Exception ex) {
                        requestEditor.setRequest(null);
                        responseEditor.setResponse(null);
                    }
                }
            }
        }
    }
    
    private void clearRequestResponseDisplay() {
        requestEditor.setRequest(null);
        responseEditor.setResponse(null);
    }
    
    private void showContextMenu(MouseEvent e) {
        TreePath path = urlTree.getPathForLocation(e.getX(), e.getY());
        if (path != null) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            urlTree.setSelectionPath(path);
            
            JPopupMenu menu = new JPopupMenu();
            
            if (node.getUserObject() instanceof URLInfo) {
                // URL节点的右键菜单
                URLEntry entry = nodeToEntryMap.get(node);
                if (entry != null) {
                    createURLContextMenu(menu, entry);
                }
            } else if (node.getUserObject() instanceof DomainInfo) {
                // 域名节点的右键菜单
                createDomainContextMenu(menu, node);
            }
            
            if (menu.getComponentCount() > 0) {
                menu.show(urlTree, e.getX(), e.getY());
            }
        }
    }
    
    private void createURLContextMenu(JPopupMenu menu, URLEntry entry) {
        JMenuItem copyURLItem = new JMenuItem("复制URL");
        copyURLItem.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                new java.awt.datatransfer.StringSelection(entry.getUrl()), null);
        });
        menu.add(copyURLItem);
        
        JMenuItem openInBrowserItem = new JMenuItem("在浏览器中打开");
        openInBrowserItem.addActionListener(e -> {
            try {
                Desktop.getDesktop().browse(new java.net.URI(entry.getUrl()));
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "打开浏览器失败: " + ex.getMessage());
            }
        });
        menu.add(openInBrowserItem);
        
        menu.addSeparator();
        
        JMenuItem sendToRepeaterItem = new JMenuItem("发送到Repeater");
        sendToRepeaterItem.addActionListener(e -> sendToRepeater(entry));
        menu.add(sendToRepeaterItem);
        
        JMenuItem sendToIntruderItem = new JMenuItem("发送到Intruder");
        sendToIntruderItem.addActionListener(e -> sendToIntruder(entry));
        menu.add(sendToIntruderItem);
        
        menu.addSeparator();
        
        // 三种状态选项
        JMenuItem markUncheckedItem = new JMenuItem("标记为未检查");
        markUncheckedItem.addActionListener(e -> setCheckStatus(entry, CheckStatus.UNCHECKED));
        markUncheckedItem.setEnabled(entry.getCheckStatus() != CheckStatus.UNCHECKED);
        menu.add(markUncheckedItem);
        
        JMenuItem markCheckingItem = new JMenuItem("标记为检查中");
        markCheckingItem.addActionListener(e -> setCheckStatus(entry, CheckStatus.CHECKING));
        markCheckingItem.setEnabled(entry.getCheckStatus() != CheckStatus.CHECKING);
        menu.add(markCheckingItem);
        
        JMenuItem markDoneItem = new JMenuItem("标记为已完成");
        markDoneItem.addActionListener(e -> setCheckStatus(entry, CheckStatus.DONE));
        markDoneItem.setEnabled(entry.getCheckStatus() != CheckStatus.DONE);
        menu.add(markDoneItem);
    }
    
    private void createDomainContextMenu(JPopupMenu menu, DefaultMutableTreeNode domainNode) {
        DomainInfo domainInfo = (DomainInfo) domainNode.getUserObject();
        
        JMenuItem expandItem = new JMenuItem("展开");
        expandItem.addActionListener(e -> {
            urlTree.expandPath(new TreePath(domainNode.getPath()));
        });
        menu.add(expandItem);
        
        JMenuItem collapseItem = new JMenuItem("折叠");
        collapseItem.addActionListener(e -> {
            urlTree.collapsePath(new TreePath(domainNode.getPath()));
        });
        menu.add(collapseItem);
        
        menu.addSeparator();
        
        JMenuItem copyAllURLsItem = new JMenuItem("复制所有URL");
        copyAllURLsItem.addActionListener(e -> {
            StringBuilder urls = new StringBuilder();
            for (int i = 0; i < domainNode.getChildCount(); i++) {
                DefaultMutableTreeNode urlNode = (DefaultMutableTreeNode) domainNode.getChildAt(i);
                URLEntry entry = nodeToEntryMap.get(urlNode);
                if (entry != null) {
                    urls.append(entry.getUrl()).append("\n");
                }
            }
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                new java.awt.datatransfer.StringSelection(urls.toString()), null);
        });
        menu.add(copyAllURLsItem);
    }
    
    private void sendToRepeater(URLEntry entry) {
        try {
            var request = burp.api.montoya.http.message.requests.HttpRequest.httpRequestFromUrl(entry.getUrl());
            var httpService = burp.api.montoya.http.HttpService.httpService(
                entry.getHost(), 
                urlAnalyzer.isHttps(entry.getUrl()) ? 443 : 80, 
                urlAnalyzer.isHttps(entry.getUrl())
            );
            
            api.repeater().sendToRepeater(request.withService(httpService));
        } catch (Exception ex) {
            api.logging().logToError("发送到Repeater失败: " + ex.getMessage());
        }
    }
    
    private void sendToIntruder(URLEntry entry) {
        try {
            var request = burp.api.montoya.http.message.requests.HttpRequest.httpRequestFromUrl(entry.getUrl());
            var httpService = burp.api.montoya.http.HttpService.httpService(
                entry.getHost(), 
                urlAnalyzer.isHttps(entry.getUrl()) ? 443 : 80, 
                urlAnalyzer.isHttps(entry.getUrl())
            );
            
            api.intruder().sendToIntruder(request.withService(httpService));
        } catch (Exception ex) {
            api.logging().logToError("发送到Intruder失败: " + ex.getMessage());
        }
    }
    
    private void setCheckStatus(URLEntry entry, CheckStatus status) {
        // 设置新状态
        entry.setCheckStatus(status);
        
        // 重新应用筛选和更新统计
        applyFilters();
    }
    
    // 保持向后兼容的方法
    @Deprecated
    private void setCheckStatus(URLEntry entry, boolean checked) {
        setCheckStatus(entry, checked ? CheckStatus.DONE : CheckStatus.UNCHECKED);
    }
    
    private void sortByColumn(int columnIndex) {
        // 如果点击同一列，切换排序方向
        if (sortColumn == columnIndex) {
            sortAscending = !sortAscending;
        } else {
            sortColumn = columnIndex;
            sortAscending = true;
        }
        
        // 保存当前展开状态
        Set<TreePath> expandedPaths = new HashSet<>();
        for (int i = 0; i < urlTree.getRowCount(); i++) {
            TreePath path = urlTree.getPathForRow(i);
            if (urlTree.isExpanded(path)) {
                expandedPaths.add(path);
            }
        }
        
        // 对所有域名节点下的URL进行排序
        for (DefaultMutableTreeNode domainNode : domainNodes.values()) {
            sortURLNodes(domainNode);
        }
        
        // 刷新表格头部显示排序指示符
        refreshTableHeader();
        
        // 刷新树形显示并恢复展开状态
        SwingUtilities.invokeLater(() -> {
            treeModel.nodeStructureChanged(rootNode);
            
            // 恢复展开状态
            for (TreePath path : expandedPaths) {
                urlTree.expandPath(path);
            }
            
            urlTree.repaint();
        });
    }
    
    private void refreshTableHeader() {
        // 简化实现：通过重新初始化整个UI来更新表格头
        SwingUtilities.invokeLater(() -> {
            // 找到树形面板并刷新其头部
            Container treePanel = null;
            Component[] components = getComponents();
            for (Component comp : components) {
                if (comp instanceof JSplitPane) {
                    JSplitPane splitPane = (JSplitPane) comp;
                    Component top = splitPane.getTopComponent();
                    if (top instanceof JPanel) {
                        treePanel = (Container) top;
                        break;
                    }
                }
            }
            
            if (treePanel != null) {
                Component[] treeComponents = treePanel.getComponents();
                for (Component comp : treeComponents) {
                    if (comp instanceof ResizableHeaderPanel) {
                        // 找到表格头面板并重新创建
                        treePanel.remove(comp);
                        treePanel.add(createTableHeader(), BorderLayout.NORTH);
                        treePanel.revalidate();
                        treePanel.repaint();
                        break;
                    }
                }
            }
        });
    }
    
    private void handleSingleClick(MouseEvent e) {
        TreePath path = urlTree.getPathForLocation(e.getX(), e.getY());
        if (path != null) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            if (node.getUserObject() instanceof URLInfo) {
                URLEntry entry = nodeToEntryMap.get(node);
                if (entry != null) {
                    // 检查是否点击在Comments列区域
                    if (isClickInCommentsColumn(e.getX())) {
                        startInPlaceEdit(entry, node, e);
                    }
                }
            }
        }
    }
    
    private boolean isClickInCommentsColumn(int x) {
        int startX = 0;
        for (int i = 0; i < 5; i++) {  // Comments是第5列（索引为5）
            startX += columnWidths[i];
        }
        int endX = startX + columnWidths[5];
        return x >= startX && x <= endX;
    }
    
    private void startInPlaceEdit(URLEntry entry, DefaultMutableTreeNode node, MouseEvent e) {
        // 如果已经在编辑，先完成当前编辑
        if (editingTextField != null) {
            finishEditing();
        }
        
        // 保存当前展开状态
        Set<TreePath> expandedPaths = saveExpandedPaths();
        
        // 计算编辑框位置
        Rectangle rowBounds = urlTree.getRowBounds(urlTree.getRowForPath(new TreePath(node.getPath())));
        if (rowBounds == null) return;
        
        int commentsColumnX = getCommentsColumnX();
        int commentsColumnWidth = columnWidths[5];
        
        // 创建编辑文本框
        editingTextField = new JTextField(entry.getNotes() != null ? entry.getNotes() : "");
        editingTextField.setBounds(commentsColumnX, rowBounds.y, commentsColumnWidth, rowBounds.height);
        editingTextField.setBorder(BorderFactory.createLineBorder(Color.BLUE, 1));
        
        // 保存编辑状态
        editingEntry = entry;
        editingNode = node;
        
        // 添加事件监听器
        editingTextField.addActionListener(evt -> finishEditing());
        editingTextField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent evt) {
                finishEditing();
            }
        });
        
        // 添加到树组件上
        urlTree.add(editingTextField);
        editingTextField.selectAll();
        editingTextField.requestFocusInWindow();
        
        // 恢复展开状态
        restoreExpandedPaths(expandedPaths);
    }
    
    private int getCommentsColumnX() {
        int x = 0;
        for (int i = 0; i < 5; i++) {  // Comments是第5列（索引为5）
            x += columnWidths[i];
        }
        return x;
    }
    
    private void finishEditing() {
        if (editingTextField != null && editingEntry != null) {
            // 保存当前展开状态
            Set<TreePath> expandedPaths = saveExpandedPaths();
            
            // 保存编辑内容
            String newText = editingTextField.getText();
            editingEntry.setNotes(newText);
            
            // 清理编辑组件
            urlTree.remove(editingTextField);
            editingTextField = null;
            editingEntry = null;
            editingNode = null;
            
            // 刷新显示
            SwingUtilities.invokeLater(() -> {
                treeModel.nodeStructureChanged(rootNode);
                urlTree.repaint();
                // 恢复展开状态
                restoreExpandedPaths(expandedPaths);
            });
        }
    }
    
    private Set<TreePath> saveExpandedPaths() {
        Set<TreePath> expandedPaths = new HashSet<>();
        for (int i = 0; i < urlTree.getRowCount(); i++) {
            TreePath path = urlTree.getPathForRow(i);
            if (urlTree.isExpanded(path)) {
                expandedPaths.add(path);
            }
        }
        return expandedPaths;
    }
    
    private void restoreExpandedPaths(Set<TreePath> expandedPaths) {
        for (TreePath path : expandedPaths) {
            urlTree.expandPath(path);
        }
    }
    
    // 域名信息类
    private static class DomainInfo {
        private final String domain;
        private int urlCount;
        
        public DomainInfo(String domain) {
            this.domain = domain;
            this.urlCount = 0;
        }
        
        public String getDomain() {
            return domain;
        }
        
        public void setUrlCount(int count) {
            this.urlCount = count;
        }
        
        @Override
        public String toString() {
            return domain + " (" + urlCount + ")";
        }
    }
    
    // URL信息类
    private static class URLInfo {
        private final URLEntry entry;
        
        public URLInfo(URLEntry entry) {
            this.entry = entry;
        }
        
        public URLEntry getEntry() {
            return entry;
        }
        
        public boolean matchesFilter(String filter) {
            return entry.getUrl().toLowerCase().contains(filter) ||
                   (entry.getTitle() != null && entry.getTitle().toLowerCase().contains(filter)) ||
                   entry.getPath().toLowerCase().contains(filter);
        }
        
        @Override
        public String toString() {
            return entry.getPath();
        }
    }
    
    // 自定义树形组件渲染器 - 表格式显示
    private class TableStyleTreeCellRenderer extends DefaultTreeCellRenderer {
        private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected,
                boolean expanded, boolean leaf, int row, boolean hasFocus) {
            
            if (value instanceof DefaultMutableTreeNode) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
                Object userObject = node.getUserObject();
                
                if (userObject instanceof DomainInfo) {
                    // 域名节点 - 使用默认渲染
                    super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
                    setIcon(expanded ? getOpenIcon() : getClosedIcon());
                    setForeground(selected ? getTextSelectionColor() : Color.BLUE);
                    return this;
                    
                } else if (userObject instanceof URLInfo) {
                    // URL节点 - 使用表格式渲染
                    URLInfo urlInfo = (URLInfo) userObject;
                    URLEntry entry = urlInfo.getEntry();
                    
                    // 创建表格式的面板
                    JPanel panel = new JPanel();
                    panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
                    panel.setOpaque(true);
                    
                    if (selected) {
                        panel.setBackground(getBackgroundSelectionColor());
                    } else {
                        panel.setBackground(getBackgroundNonSelectionColor());
                    }
                    
                    // 计算序号
                    int index = getURLIndexInTree(node);
                    
                    // 添加各列内容
                    addTableCell(panel, String.valueOf(index), columnWidths[0], selected);
                    addTableCell(panel, truncateText(entry.getUrl(), 50), columnWidths[1], selected);
                    addTableCell(panel, String.valueOf(entry.getStatusCode()), columnWidths[2], selected);
                    addTableCell(panel, String.valueOf(entry.getLength()), columnWidths[3], selected);
                    addTableCell(panel, truncateText(entry.getTitle(), 25), columnWidths[4], selected);
                    addTableCell(panel, truncateText(entry.getNotes(), 20), columnWidths[5], selected);
                    addTableCell(panel, entry.getIp() != null ? entry.getIp() : "", columnWidths[6], selected);
                    // isCheck列 - 显示三种状态
                    String checkStatusText;
                    switch (entry.getCheckStatus()) {
                        case UNCHECKED:
                            checkStatusText = "○";
                            break;
                        case CHECKING:
                            checkStatusText = "◐";
                            break;
                        case DONE:
                            checkStatusText = "✓";
                            break;
                        default:
                            checkStatusText = "○";
                    }
                    addTableCell(panel, checkStatusText, columnWidths[7], selected);
                    addTableCell(panel, formatTimestamp(entry.getTimestamp()), columnWidths[8], selected);
                    
                    return panel;
                }
            }
            
            // 默认渲染
            return super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
        }
        
        private void addTableCell(JPanel parent, String text, int width, boolean selected) {
            addTableCell(parent, text, width, selected, null);
        }
        
        private void addTableCell(JPanel parent, String text, int width, boolean selected, Color textColor) {
            JLabel label = new JLabel(text);
            // 使用Burp默认字体，不自定义字体
            label.setBorder(BorderFactory.createEmptyBorder(1, 3, 1, 3));
            
            // 设置颜色 - 使用Burp默认颜色，不自定义状态颜色
            if (selected) {
                label.setForeground(getTextSelectionColor());
            } else {
                label.setForeground(getTextNonSelectionColor());
            }
            
            // 设置固定宽度
            Dimension size = new Dimension(width, 20);
            label.setPreferredSize(size);
            label.setMinimumSize(size);
            label.setMaximumSize(size);
            
            parent.add(label);
        }
        
        private String truncateText(String text, int maxLength) {
            if (text == null) return "";
            if (text.length() <= maxLength) return text;
            return text.substring(0, maxLength - 3) + "...";
        }
        
        private String formatTimestamp(long timestamp) {
            if (timestamp <= 0) return "";
            return DATE_FORMAT.format(new Date(timestamp));
        }
        
        private int getURLIndexInTree(DefaultMutableTreeNode urlNode) {
            // 计算在整个树中的URL序号
            int index = 1;
            DefaultMutableTreeNode root = (DefaultMutableTreeNode) urlTree.getModel().getRoot();
            return calculateIndex(root, urlNode, new int[]{0});
        }
        
        private int calculateIndex(DefaultMutableTreeNode node, DefaultMutableTreeNode target, int[] counter) {
            if (node.getUserObject() instanceof URLInfo) {
                counter[0]++;
                if (node == target) {
                    return counter[0];
                }
            }
            
            for (int i = 0; i < node.getChildCount(); i++) {
                DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
                int result = calculateIndex(child, target, counter);
                if (result > 0) {
                    return result;
                }
            }
            
            return 0;
        }
    }
    
    // 可调整大小的表格头部面板
    private class ResizableHeaderPanel extends JPanel {
        private boolean dragging = false;
        private int dragColumn = -1;
        private int startX = 0;
        private int startWidth = 0;
        private Component resizeComponent = null;
        
        public ResizableHeaderPanel() {
            super();
            
            MouseAdapter mouseHandler = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    Component component = getComponentAt(e.getPoint());
                    if (component != null && component.getCursor().getType() == Cursor.E_RESIZE_CURSOR) {
                        // 找到对应的列索引
                        int columnIndex = findColumnIndexFromComponent(component);
                        if (columnIndex >= 0 && columnIndex < COLUMN_NAMES.length - 1) {
                            dragging = true;
                            dragColumn = columnIndex;
                            startX = e.getX();
                            startWidth = columnWidths[columnIndex];
                            resizeComponent = component;
                            
                            setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
                        }
                    }
                }
                
                @Override
                public void mouseReleased(MouseEvent e) {
                    if (dragging) {
                        dragging = false;
                        dragColumn = -1;
                        resizeComponent = null;
                        setCursor(Cursor.getDefaultCursor());
                    }
                }
                
                @Override
                public void mouseDragged(MouseEvent e) {
                    if (dragging && dragColumn >= 0) {
                        int deltaX = e.getX() - startX;
                        int newWidth = Math.max(30, startWidth + deltaX); // 最小宽度30
                        
                        if (newWidth != columnWidths[dragColumn]) {
                            columnWidths[dragColumn] = newWidth;
                            refreshHeaderAndTree();
                        }
                    }
                }
                
                @Override
                public void mouseMoved(MouseEvent e) {
                    if (!dragging) {
                        Component component = getComponentAt(e.getPoint());
                        if (component != null && component instanceof JLabel && 
                            component.getBackground() == Color.GRAY) {
                            setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
                        } else {
                            setCursor(Cursor.getDefaultCursor());
                        }
                    }
                }
            };
            
            addMouseListener(mouseHandler);
            addMouseMotionListener(mouseHandler);
        }
        
        private int findColumnIndexFromComponent(Component component) {
            int componentIndex = -1;
            Component[] components = getComponents();
            for (int i = 0; i < components.length; i++) {
                if (components[i] == component) {
                    componentIndex = i;
                    break;
                }
            }
            
            if (componentIndex >= 0) {
                // 每两个组件为一对：标签 + 分隔符
                // 分隔符的索引是奇数，对应的列索引是 index / 2
                if (componentIndex % 2 == 1) {
                    return componentIndex / 2;
                }
            }
            return -1;
        }
        
        private void refreshHeaderAndTree() {
            // 刷新头部显示
            removeAll();
            
            for (int i = 0; i < COLUMN_NAMES.length; i++) {
                String columnText = COLUMN_NAMES[i];
                
                // 添加排序指示符
                if (sortColumn == i) {
                    columnText += sortAscending ? " ↑" : " ↓";
                }
                
                JLabel label = new JLabel(columnText);
                label.setFont(label.getFont().deriveFont(Font.BOLD));
                label.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
                label.setHorizontalAlignment(SwingConstants.LEFT);
                label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                
                // 添加点击排序功能
                final int columnIndex = i;
                label.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        sortByColumn(columnIndex);
                    }
                });
                
                // 设置固定宽度
                Dimension size = new Dimension(columnWidths[i], 21);
                label.setPreferredSize(size);
                label.setMinimumSize(size);
                label.setMaximumSize(size);
                
                add(label);
                
                // 添加分隔线（除了最后一列）
                if (i < COLUMN_NAMES.length - 1) {
                    JLabel separator = new JLabel();
                    separator.setOpaque(true);
                    separator.setBackground(Color.GRAY);
                    separator.setPreferredSize(new Dimension(2, 21));
                    separator.setMinimumSize(new Dimension(2, 21));
                    separator.setMaximumSize(new Dimension(2, 21));
                    separator.setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
                    add(separator);
                }
            }
            
            revalidate();
            repaint();
            
            // 刷新树形显示以应用新的列宽
            if (urlTree != null) {
                urlTree.repaint();
            }
        }
    }
} 