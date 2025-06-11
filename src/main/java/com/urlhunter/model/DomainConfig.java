package com.urlhunter.model;

import java.util.ArrayList;
import java.util.List;

public class DomainConfig {
    private List<String> blacklistDomains;
    private List<String> blacklistExtensions;
    private List<Integer> blacklistStatusCodes;
    private List<String> fuzzDictionary;
    private boolean autoFuzzEnabled;
    private boolean shortLinkBruteEnabled;
    private String shortLinkCharset;
    private int shortLinkMinLength;
    private int shortLinkMaxLength;

    public DomainConfig() {
        this.blacklistDomains = new ArrayList<>();
        this.blacklistExtensions = new ArrayList<>();
        this.blacklistStatusCodes = new ArrayList<>();
        this.fuzzDictionary = new ArrayList<>();
        
        // 默认配置
        initializeDefaults();
    }

    private void initializeDefaults() {
        // 默认过滤的扩展名
        blacklistExtensions.add("ico");
        blacklistExtensions.add("jpg");
        blacklistExtensions.add("jpeg");
        blacklistExtensions.add("png");
        blacklistExtensions.add("gif");
        blacklistExtensions.add("css");
        blacklistExtensions.add("js");
        blacklistExtensions.add("woff");
        blacklistExtensions.add("woff2");
        blacklistExtensions.add("ttf");
        blacklistExtensions.add("eot");
        blacklistExtensions.add("svg");

        // 默认过滤的状态码
        blacklistStatusCodes.add(403);
        blacklistStatusCodes.add(404);
        blacklistStatusCodes.add(501);
        blacklistStatusCodes.add(502);
        blacklistStatusCodes.add(503);

        // 默认Fuzz字典
        fuzzDictionary.add("admin");
        fuzzDictionary.add("test");
        fuzzDictionary.add("backup");
        fuzzDictionary.add("config");
        fuzzDictionary.add("login");
        fuzzDictionary.add("api");
        fuzzDictionary.add("upload");
        fuzzDictionary.add("debug");

        // 短链接爆破默认配置
        autoFuzzEnabled = false;
        shortLinkBruteEnabled = false;
        shortLinkCharset = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        shortLinkMinLength = 1;
        shortLinkMaxLength = 4;
    }

    // Getters and Setters
    public List<String> getBlacklistDomains() {
        return blacklistDomains;
    }

    public void setBlacklistDomains(List<String> blacklistDomains) {
        this.blacklistDomains = blacklistDomains;
    }

    public List<String> getDomainBlacklist() {
        return blacklistDomains;
    }

    public void setDomainBlacklist(List<String> domainBlacklist) {
        this.blacklistDomains = domainBlacklist;
    }

    public List<String> getBlacklistExtensions() {
        return blacklistExtensions;
    }

    public void setBlacklistExtensions(List<String> blacklistExtensions) {
        this.blacklistExtensions = blacklistExtensions;
    }

    public List<Integer> getBlacklistStatusCodes() {
        return blacklistStatusCodes;
    }

    public void setBlacklistStatusCodes(List<Integer> blacklistStatusCodes) {
        this.blacklistStatusCodes = blacklistStatusCodes;
    }

    public List<String> getFuzzDictionary() {
        return fuzzDictionary;
    }

    public void setFuzzDictionary(List<String> fuzzDictionary) {
        this.fuzzDictionary = fuzzDictionary;
    }

    public boolean isAutoFuzzEnabled() {
        return autoFuzzEnabled;
    }

    public void setAutoFuzzEnabled(boolean autoFuzzEnabled) {
        this.autoFuzzEnabled = autoFuzzEnabled;
    }

    public boolean isShortLinkBruteEnabled() {
        return shortLinkBruteEnabled;
    }

    public void setShortLinkBruteEnabled(boolean shortLinkBruteEnabled) {
        this.shortLinkBruteEnabled = shortLinkBruteEnabled;
    }

    public String getShortLinkCharset() {
        return shortLinkCharset;
    }

    public void setShortLinkCharset(String shortLinkCharset) {
        this.shortLinkCharset = shortLinkCharset;
    }

    public int getShortLinkMinLength() {
        return shortLinkMinLength;
    }

    public void setShortLinkMinLength(int shortLinkMinLength) {
        this.shortLinkMinLength = shortLinkMinLength;
    }

    public int getShortLinkMaxLength() {
        return shortLinkMaxLength;
    }

    public void setShortLinkMaxLength(int shortLinkMaxLength) {
        this.shortLinkMaxLength = shortLinkMaxLength;
    }
} 