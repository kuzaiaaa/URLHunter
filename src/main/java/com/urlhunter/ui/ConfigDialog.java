package com.urlhunter.ui;

import com.urlhunter.model.DomainConfig;
import com.urlhunter.database.DatabaseManager;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.List;

public class ConfigDialog extends JDialog {
    private final DatabaseManager dbManager;
    private DomainConfig config;
    
    // UI组件
    private JTextArea blacklistExtensionsArea;
    private JTextArea blacklistStatusCodesArea;
    private JTextArea domainBlacklistArea;
    private JTextArea fuzzDictionaryArea;
    private JSpinner shortLinkMaxLengthSpinner;
    private JTextField shortLinkCharsetField;
    
    public ConfigDialog(Window parent, DatabaseManager dbManager) {
        super(parent, "配置", ModalityType.APPLICATION_MODAL);
        this.dbManager = dbManager;
        this.config = dbManager.loadConfig();
        
        initializeUI();
        loadConfigToUI();
        setLocationRelativeTo(parent);
    }
    
    private void initializeUI() {
        setLayout(new BorderLayout());
        setSize(600, 500);
        
        // 创建选项卡面板
        JTabbedPane tabbedPane = new JTabbedPane();
        
        // 过滤配置选项卡
        JPanel filterPanel = createFilterPanel();
        tabbedPane.addTab("过滤配置", filterPanel);
        
        // 字典配置选项卡
        JPanel dictionaryPanel = createDictionaryPanel();
        tabbedPane.addTab("字典配置", dictionaryPanel);
        
        // 短链接配置选项卡
        JPanel shortLinkPanel = createShortLinkPanel();
        tabbedPane.addTab("短链接配置", shortLinkPanel);
        
        add(tabbedPane, BorderLayout.CENTER);
        
        // 按钮面板
        JPanel buttonPanel = createButtonPanel();
        add(buttonPanel, BorderLayout.SOUTH);
    }
    
    private JPanel createFilterPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.BOTH;
        
        // 文件扩展名黑名单
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 1.0; gbc.weighty = 0.0;
        panel.add(new JLabel("文件扩展名黑名单（每行一个）:"), gbc);
        
        gbc.gridy = 1; gbc.weighty = 0.3;
        blacklistExtensionsArea = new JTextArea(6, 30);
        blacklistExtensionsArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane extensionsScrollPane = new JScrollPane(blacklistExtensionsArea);
        extensionsScrollPane.setBorder(new TitledBorder("文件扩展名"));
        panel.add(extensionsScrollPane, gbc);
        
        // 状态码黑名单
        gbc.gridy = 2; gbc.weighty = 0.0;
        panel.add(new JLabel("状态码黑名单（每行一个）:"), gbc);
        
        gbc.gridy = 3; gbc.weighty = 0.2;
        blacklistStatusCodesArea = new JTextArea(4, 30);
        blacklistStatusCodesArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane statusCodesScrollPane = new JScrollPane(blacklistStatusCodesArea);
        statusCodesScrollPane.setBorder(new TitledBorder("状态码"));
        panel.add(statusCodesScrollPane, gbc);
        
        // 域名黑名单
        gbc.gridy = 4; gbc.weighty = 0.0;
        panel.add(new JLabel("域名黑名单（每行一个）:"), gbc);
        
        gbc.gridy = 5; gbc.weighty = 0.3;
        domainBlacklistArea = new JTextArea(6, 30);
        domainBlacklistArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane domainScrollPane = new JScrollPane(domainBlacklistArea);
        domainScrollPane.setBorder(new TitledBorder("域名"));
        panel.add(domainScrollPane, gbc);
        
        return panel;
    }
    
    private JPanel createDictionaryPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        JLabel label = new JLabel("Fuzz字典（每行一个）:");
        panel.add(label, BorderLayout.NORTH);
        
        fuzzDictionaryArea = new JTextArea(20, 30);
        fuzzDictionaryArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(fuzzDictionaryArea);
        scrollPane.setBorder(new TitledBorder("Fuzz字典"));
        panel.add(scrollPane, BorderLayout.CENTER);
        
        // 预设字典按钮面板
        JPanel presetPanel = new JPanel(new FlowLayout());
        
        JButton commonDictButton = new JButton("加载常用字典");
        commonDictButton.addActionListener(e -> loadCommonDictionary());
        presetPanel.add(commonDictButton);
        
        JButton dirDictButton = new JButton("加载目录字典");
        dirDictButton.addActionListener(e -> loadDirectoryDictionary());
        presetPanel.add(dirDictButton);
        
        JButton clearDictButton = new JButton("清空字典");
        clearDictButton.addActionListener(e -> fuzzDictionaryArea.setText(""));
        presetPanel.add(clearDictButton);
        
        panel.add(presetPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    private JPanel createShortLinkPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.anchor = GridBagConstraints.WEST;
        
        // 字符集配置
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("字符集:"), gbc);
        
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        shortLinkCharsetField = new JTextField("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789", 30);
        panel.add(shortLinkCharsetField, gbc);
        
        // 最大长度配置
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0;
        panel.add(new JLabel("最大长度:"), gbc);
        
        gbc.gridx = 1;
        shortLinkMaxLengthSpinner = new JSpinner(new SpinnerNumberModel(4, 1, 10, 1));
        panel.add(shortLinkMaxLengthSpinner, gbc);
        
        // 说明文本
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.BOTH; gbc.weightx = 1.0; gbc.weighty = 1.0;
        JTextArea helpText = new JTextArea(
            "短链接爆破配置说明：\n\n" +
            "• 字符集：用于生成短链接的字符组合\n" +
            "• 最大长度：生成字符串的最大长度（1到该长度的所有组合）\n" +
            "• 例如：字符集为'abc'，最大长度为2，则会生成：\n" +
            "  a, b, c, aa, ab, ac, ba, bb, bc, ca, cb, cc\n\n" +
            "注意：长度越大生成的组合越多，扫描时间越长！"
        );
        helpText.setEditable(false);
        helpText.setBackground(panel.getBackground());
        helpText.setBorder(new TitledBorder("说明"));
        panel.add(helpText, gbc);
        
        return panel;
    }
    
    private JPanel createButtonPanel() {
        JPanel buttonPanel = new JPanel(new FlowLayout());
        
        JButton saveButton = new JButton("保存");
        saveButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                saveConfig();
            }
        });
        buttonPanel.add(saveButton);
        
        JButton cancelButton = new JButton("取消");
        cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });
        buttonPanel.add(cancelButton);
        
        JButton resetButton = new JButton("重置为默认");
        resetButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                resetToDefaults();
            }
        });
        buttonPanel.add(resetButton);
        
        return buttonPanel;
    }
    
    private void loadConfigToUI() {
        // 加载文件扩展名黑名单
        blacklistExtensionsArea.setText(String.join("\n", config.getBlacklistExtensions()));
        
        // 加载状态码黑名单
        StringBuilder statusCodes = new StringBuilder();
        for (Integer code : config.getBlacklistStatusCodes()) {
            statusCodes.append(code).append("\n");
        }
        blacklistStatusCodesArea.setText(statusCodes.toString());
        
        // 加载域名黑名单
        domainBlacklistArea.setText(String.join("\n", config.getDomainBlacklist()));
        
        // 加载Fuzz字典
        fuzzDictionaryArea.setText(String.join("\n", config.getFuzzDictionary()));
        
        // 加载短链接配置
        shortLinkCharsetField.setText(config.getShortLinkCharset());
        shortLinkMaxLengthSpinner.setValue(config.getShortLinkMaxLength());
    }
    
    private void saveConfig() {
        try {
            // 保存文件扩展名黑名单
            String[] extensions = blacklistExtensionsArea.getText().split("\n");
            config.setBlacklistExtensions(Arrays.asList(extensions));
            
            // 保存状态码黑名单
            String[] statusCodeStrings = blacklistStatusCodesArea.getText().split("\n");
            List<Integer> statusCodes = new java.util.ArrayList<>();
            for (String codeStr : statusCodeStrings) {
                try {
                    if (!codeStr.trim().isEmpty()) {
                        statusCodes.add(Integer.parseInt(codeStr.trim()));
                    }
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this, 
                        "无效的状态码: " + codeStr, 
                        "配置错误", 
                        JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }
            config.setBlacklistStatusCodes(statusCodes);
            
            // 保存域名黑名单
            String[] domains = domainBlacklistArea.getText().split("\n");
            List<String> domainList = new java.util.ArrayList<>();
            for (String domain : domains) {
                if (!domain.trim().isEmpty()) {
                    domainList.add(domain.trim());
                }
            }
            config.setDomainBlacklist(domainList);
            
            // 保存Fuzz字典
            String[] fuzzWords = fuzzDictionaryArea.getText().split("\n");
            List<String> fuzzList = new java.util.ArrayList<>();
            for (String word : fuzzWords) {
                if (!word.trim().isEmpty()) {
                    fuzzList.add(word.trim());
                }
            }
            config.setFuzzDictionary(fuzzList);
            
            // 保存短链接配置
            config.setShortLinkCharset(shortLinkCharsetField.getText());
            config.setShortLinkMaxLength((Integer) shortLinkMaxLengthSpinner.getValue());
            
            // 保存到数据库
            dbManager.saveConfig(config);
            
            JOptionPane.showMessageDialog(this, "配置已保存", "成功", JOptionPane.INFORMATION_MESSAGE);
            dispose();
            
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, 
                "保存配置失败: " + e.getMessage(), 
                "错误", 
                JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void resetToDefaults() {
        int result = JOptionPane.showConfirmDialog(this, 
            "确定要重置为默认配置吗？", 
            "重置确认", 
            JOptionPane.YES_NO_OPTION);
        
        if (result == JOptionPane.YES_OPTION) {
            config = new DomainConfig(); // 创建新的默认配置
            loadConfigToUI();
        }
    }
    
    private void loadCommonDictionary() {
        String commonDict = "admin\ntest\nbackup\napi\nupload\ndownload\nimages\njs\ncss\nstatic\ndev\ndemo\ntemp\ntmp\nold\nnew\nv1\nv2\nconfig\nsetup\ninstall\nlogin\nregister\nuser\nmanagement\ndashboard\npanel\ncontrol\nsystem\nservice\ndata\nfile\ndoc\nlog\ninfo\nhelp\nabout\ncontact\nsearch\nindex\nhome\nmain\nwww\nftp\nmail\nmobile\napp\nbeta\nstaging\ndev\nprod\nproduction";
        fuzzDictionaryArea.setText(commonDict);
    }
    
    private void loadDirectoryDictionary() {
        String directoryDict = "admin\nadmin/\nadministrator\nadministrator/\nbackup\nbackup/\napi\napi/\nv1\nv1/\nv2\nv2/\ntest\ntest/\ndemo\ndemo/\ndev\ndev/\nstatic\nstatic/\nimages\nimages/\nupload\nupload/\ndownload\ndownload/\ntemp\ntemp/\ntmp\ntmp/\nconfig\nconfig/\nsetup\nsetup/\ninstall\ninstall/\nmanagement\nmanagement/\ndashboard\ndashboard/\npanel\npanel/\ncontrol\ncontrol/\nsystem\nsystem/\nservice\nservice/\ndata\ndata/\nfile\nfile/\ndoc\ndoc/\nlog\nlog/\ninfo\ninfo/\nhelp\nhelp/\nabout\nabout/\ncontact\ncontact/";
        fuzzDictionaryArea.setText(directoryDict);
    }
} 