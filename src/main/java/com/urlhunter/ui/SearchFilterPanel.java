package com.urlhunter.ui;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

/**
 * 搜索和过滤面板组件
 * 类似domain_hunter_pro的搜索界面
 */
public class SearchFilterPanel extends JPanel {
    
    public interface SearchFilterListener {
        void onSearchTextChanged(String searchText);
        void onStatusFilterChanged(boolean unchecked, boolean checking, boolean done, boolean moreAction);
        void onAdvancedSearch(SearchCriteria criteria);
        void onClearFilters();
    }
    
    // 搜索条件类
    public static class SearchCriteria {
        private String urlPattern;
        private String hostPattern;
        private String pathPattern;
        private String titlePattern;
        private Integer minStatusCode;
        private Integer maxStatusCode;
        private Integer minLength;
        private Integer maxLength;
        private String ipPattern;
        private Boolean isInternal;
        private Boolean isChecked;
        
        // Getters and setters
        public String getUrlPattern() { return urlPattern; }
        public void setUrlPattern(String urlPattern) { this.urlPattern = urlPattern; }
        
        public String getHostPattern() { return hostPattern; }
        public void setHostPattern(String hostPattern) { this.hostPattern = hostPattern; }
        
        public String getPathPattern() { return pathPattern; }
        public void setPathPattern(String pathPattern) { this.pathPattern = pathPattern; }
        
        public String getTitlePattern() { return titlePattern; }
        public void setTitlePattern(String titlePattern) { this.titlePattern = titlePattern; }
        
        public Integer getMinStatusCode() { return minStatusCode; }
        public void setMinStatusCode(Integer minStatusCode) { this.minStatusCode = minStatusCode; }
        
        public Integer getMaxStatusCode() { return maxStatusCode; }
        public void setMaxStatusCode(Integer maxStatusCode) { this.maxStatusCode = maxStatusCode; }
        
        public Integer getMinLength() { return minLength; }
        public void setMinLength(Integer minLength) { this.minLength = minLength; }
        
        public Integer getMaxLength() { return maxLength; }
        public void setMaxLength(Integer maxLength) { this.maxLength = maxLength; }
        
        public String getIpPattern() { return ipPattern; }
        public void setIpPattern(String ipPattern) { this.ipPattern = ipPattern; }
        
        public Boolean getIsInternal() { return isInternal; }
        public void setIsInternal(Boolean isInternal) { this.isInternal = isInternal; }
        
        public Boolean getIsChecked() { return isChecked; }
        public void setIsChecked(Boolean isChecked) { this.isChecked = isChecked; }
    }
    
    private SearchFilterListener listener;
    
    // 基本搜索组件
    private JTextField searchField;
    private JButton searchButton;
    private JButton clearButton;
    
    // 状态过滤组件
    private JCheckBox uncheckedFilter;
    private JCheckBox checkingFilter;
    private JCheckBox doneFilter;
    private JCheckBox moreActionFilter;
    
    // 高级搜索组件
    private JTextField urlField;
    private JTextField hostField;
    private JTextField pathField;
    private JTextField titleField;
    private JTextField statusCodeMinField;
    private JTextField statusCodeMaxField;
    private JTextField lengthMinField;
    private JTextField lengthMaxField;
    private JTextField ipField;
    private JComboBox<String> internalComboBox;
    private JComboBox<String> checkedComboBox;
    
    private JButton advancedSearchButton;
    private JDialog advancedSearchDialog;
    
    public SearchFilterPanel() {
        initializeComponents();
        setupLayout();
        setupEventHandlers();
    }
    
    public void setSearchFilterListener(SearchFilterListener listener) {
        this.listener = listener;
    }
    
    private void initializeComponents() {
        // 基本搜索组件
        searchField = new JTextField(20);
        searchField.setToolTipText("输入搜索关键词，支持正则表达式");
        
        searchButton = new JButton("搜索");
        clearButton = new JButton("清空");
        
        // 状态过滤组件
        uncheckedFilter = new JCheckBox("UnChecked", true);
        checkingFilter = new JCheckBox("Checking", true);
        doneFilter = new JCheckBox("Done", true);
        moreActionFilter = new JCheckBox("MoreAction", true);
        
        // 高级搜索按钮
        advancedSearchButton = new JButton("高级搜索");
        
        // 高级搜索对话框组件
        urlField = new JTextField(20);
        hostField = new JTextField(20);
        pathField = new JTextField(20);
        titleField = new JTextField(20);
        statusCodeMinField = new JTextField(10);
        statusCodeMaxField = new JTextField(10);
        lengthMinField = new JTextField(10);
        lengthMaxField = new JTextField(10);
        ipField = new JTextField(20);
        
        internalComboBox = new JComboBox<>(new String[]{"全部", "内网", "外网"});
        checkedComboBox = new JComboBox<>(new String[]{"全部", "已检测", "未检测"});
    }
    
    private void setupLayout() {
        setLayout(new BorderLayout());
        setBorder(new TitledBorder("搜索和过滤"));
        
        // 顶部搜索栏
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        
        topPanel.add(new JLabel("搜索:"));
        topPanel.add(searchField);
        topPanel.add(searchButton);
        topPanel.add(clearButton);
        
        topPanel.add(Box.createHorizontalStrut(20));
        topPanel.add(advancedSearchButton);
        
        add(topPanel, BorderLayout.NORTH);
        
        // 中间状态过滤
        JPanel centerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        centerPanel.setBorder(new TitledBorder("状态过滤"));
        
        centerPanel.add(uncheckedFilter);
        centerPanel.add(checkingFilter);
        centerPanel.add(doneFilter);
        centerPanel.add(moreActionFilter);
        
        add(centerPanel, BorderLayout.CENTER);
        
        // 创建高级搜索对话框
        createAdvancedSearchDialog();
    }
    
    private void createAdvancedSearchDialog() {
        advancedSearchDialog = new JDialog((Frame) null, "高级搜索", true);
        advancedSearchDialog.setLayout(new BorderLayout());
        advancedSearchDialog.setSize(500, 400);
        advancedSearchDialog.setLocationRelativeTo(this);
        
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        // URL模式
        gbc.gridx = 0; gbc.gridy = 0;
        formPanel.add(new JLabel("URL模式:"), gbc);
        gbc.gridx = 1;
        formPanel.add(urlField, gbc);
        
        // 主机模式
        gbc.gridx = 0; gbc.gridy = 1;
        formPanel.add(new JLabel("主机模式:"), gbc);
        gbc.gridx = 1;
        formPanel.add(hostField, gbc);
        
        // 路径模式
        gbc.gridx = 0; gbc.gridy = 2;
        formPanel.add(new JLabel("路径模式:"), gbc);
        gbc.gridx = 1;
        formPanel.add(pathField, gbc);
        
        // 标题模式
        gbc.gridx = 0; gbc.gridy = 3;
        formPanel.add(new JLabel("标题模式:"), gbc);
        gbc.gridx = 1;
        formPanel.add(titleField, gbc);
        
        // 状态码范围
        gbc.gridx = 0; gbc.gridy = 4;
        formPanel.add(new JLabel("状态码范围:"), gbc);
        gbc.gridx = 1;
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        statusPanel.add(statusCodeMinField);
        statusPanel.add(new JLabel(" - "));
        statusPanel.add(statusCodeMaxField);
        formPanel.add(statusPanel, gbc);
        
        // 长度范围
        gbc.gridx = 0; gbc.gridy = 5;
        formPanel.add(new JLabel("长度范围:"), gbc);
        gbc.gridx = 1;
        JPanel lengthPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        lengthPanel.add(lengthMinField);
        lengthPanel.add(new JLabel(" - "));
        lengthPanel.add(lengthMaxField);
        formPanel.add(lengthPanel, gbc);
        
        // IP模式
        gbc.gridx = 0; gbc.gridy = 6;
        formPanel.add(new JLabel("IP模式:"), gbc);
        gbc.gridx = 1;
        formPanel.add(ipField, gbc);
        
        // 内网/外网
        gbc.gridx = 0; gbc.gridy = 7;
        formPanel.add(new JLabel("网络类型:"), gbc);
        gbc.gridx = 1;
        formPanel.add(internalComboBox, gbc);
        
        // 检测状态
        gbc.gridx = 0; gbc.gridy = 8;
        formPanel.add(new JLabel("检测状态:"), gbc);
        gbc.gridx = 1;
        formPanel.add(checkedComboBox, gbc);
        
        advancedSearchDialog.add(formPanel, BorderLayout.CENTER);
        
        // 按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout());
        JButton searchBtn = new JButton("搜索");
        JButton resetBtn = new JButton("重置");
        JButton cancelBtn = new JButton("取消");
        
        searchBtn.addActionListener(e -> {
            performAdvancedSearch();
            advancedSearchDialog.setVisible(false);
        });
        
        resetBtn.addActionListener(e -> resetAdvancedSearchFields());
        
        cancelBtn.addActionListener(e -> advancedSearchDialog.setVisible(false));
        
        buttonPanel.add(searchBtn);
        buttonPanel.add(resetBtn);
        buttonPanel.add(cancelBtn);
        
        advancedSearchDialog.add(buttonPanel, BorderLayout.SOUTH);
    }
    
    private void setupEventHandlers() {
        // 搜索框事件
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (listener != null) {
                    listener.onSearchTextChanged(searchField.getText());
                }
            }
        });
        
        // 搜索按钮事件
        searchButton.addActionListener(e -> {
            if (listener != null) {
                listener.onSearchTextChanged(searchField.getText());
            }
        });
        
        // 清空按钮事件
        clearButton.addActionListener(e -> {
            searchField.setText("");
            resetStatusFilters();
            resetAdvancedSearchFields();
            if (listener != null) {
                listener.onClearFilters();
            }
        });
        
        // 高级搜索按钮事件
        advancedSearchButton.addActionListener(e -> {
            advancedSearchDialog.setVisible(true);
        });
        
        // 状态过滤事件
        ActionListener statusFilterListener = e -> {
            if (listener != null) {
                listener.onStatusFilterChanged(
                    uncheckedFilter.isSelected(),
                    checkingFilter.isSelected(),
                    doneFilter.isSelected(),
                    moreActionFilter.isSelected()
                );
            }
        };
        
        uncheckedFilter.addActionListener(statusFilterListener);
        checkingFilter.addActionListener(statusFilterListener);
        doneFilter.addActionListener(statusFilterListener);
        moreActionFilter.addActionListener(statusFilterListener);
    }
    
    private void performAdvancedSearch() {
        SearchCriteria criteria = new SearchCriteria();
        
        // 设置搜索条件
        criteria.setUrlPattern(getTextOrNull(urlField));
        criteria.setHostPattern(getTextOrNull(hostField));
        criteria.setPathPattern(getTextOrNull(pathField));
        criteria.setTitlePattern(getTextOrNull(titleField));
        
        // 状态码范围
        try {
            String minStatus = statusCodeMinField.getText().trim();
            if (!minStatus.isEmpty()) {
                criteria.setMinStatusCode(Integer.parseInt(minStatus));
            }
            
            String maxStatus = statusCodeMaxField.getText().trim();
            if (!maxStatus.isEmpty()) {
                criteria.setMaxStatusCode(Integer.parseInt(maxStatus));
            }
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "状态码必须是数字", "输入错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        // 长度范围
        try {
            String minLength = lengthMinField.getText().trim();
            if (!minLength.isEmpty()) {
                criteria.setMinLength(Integer.parseInt(minLength));
            }
            
            String maxLength = lengthMaxField.getText().trim();
            if (!maxLength.isEmpty()) {
                criteria.setMaxLength(Integer.parseInt(maxLength));
            }
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "长度必须是数字", "输入错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        criteria.setIpPattern(getTextOrNull(ipField));
        
        // 内网/外网
        String internalSelection = (String) internalComboBox.getSelectedItem();
        if ("内网".equals(internalSelection)) {
            criteria.setIsInternal(true);
        } else if ("外网".equals(internalSelection)) {
            criteria.setIsInternal(false);
        }
        
        // 检测状态
        String checkedSelection = (String) checkedComboBox.getSelectedItem();
        if ("已检测".equals(checkedSelection)) {
            criteria.setIsChecked(true);
        } else if ("未检测".equals(checkedSelection)) {
            criteria.setIsChecked(false);
        }
        
        if (listener != null) {
            listener.onAdvancedSearch(criteria);
        }
    }
    
    private String getTextOrNull(JTextField field) {
        String text = field.getText().trim();
        return text.isEmpty() ? null : text;
    }
    
    private void resetStatusFilters() {
        uncheckedFilter.setSelected(true);
        checkingFilter.setSelected(true);
        doneFilter.setSelected(true);
        moreActionFilter.setSelected(true);
    }
    
    private void resetAdvancedSearchFields() {
        urlField.setText("");
        hostField.setText("");
        pathField.setText("");
        titleField.setText("");
        statusCodeMinField.setText("");
        statusCodeMaxField.setText("");
        lengthMinField.setText("");
        lengthMaxField.setText("");
        ipField.setText("");
        internalComboBox.setSelectedIndex(0);
        checkedComboBox.setSelectedIndex(0);
    }
    
    // 公共方法
    public void setSearchText(String text) {
        searchField.setText(text);
    }
    
    public String getSearchText() {
        return searchField.getText();
    }
    
    public void clearSearch() {
        clearButton.doClick();
    }
} 