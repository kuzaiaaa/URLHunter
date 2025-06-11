package com.urlhunter.model;

public class URLEntry {
    
    // 枚举定义检查状态
    public enum CheckStatus {
        UNCHECKED("UnChecked"),
        CHECKING("Checking"), 
        DONE("Done");
        
        private final String displayName;
        
        CheckStatus(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        @Override
        public String toString() {
            return displayName;
        }
    }
    
    private long id;
    private String url;
    private String method;
    private String host;
    private String path;
    private String query;
    private int statusCode;
    private int length;
    private String title;
    private String ip;
    private boolean isInternal;
    private String subdomain;
    private CheckStatus checkStatus = CheckStatus.UNCHECKED;
    private String notes;
    private long timestamp;

    // 新增：存储原始 HTTP 数据
    private byte[] requestData;
    private byte[] responseData;

    public URLEntry() {
        this.timestamp = System.currentTimeMillis();
    }

    public URLEntry(String url, String method, String host) {
        this.url = url;
        this.method = method;
        this.host = host;
        this.timestamp = System.currentTimeMillis();
        this.checkStatus = CheckStatus.UNCHECKED; // 默认状态
    }

    // Getters and Setters
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public boolean isInternal() {
        return isInternal;
    }

    public void setInternal(boolean internal) {
        isInternal = internal;
    }

    public String getSubdomain() {
        return subdomain;
    }

    public void setSubdomain(String subdomain) {
        this.subdomain = subdomain;
    }

    // 新的状态相关方法
    public CheckStatus getCheckStatus() {
        return checkStatus;
    }

    public void setCheckStatus(CheckStatus checkStatus) {
        this.checkStatus = checkStatus;
        if (checkStatus == CheckStatus.DONE) {
            this.timestamp = System.currentTimeMillis(); // 标记为完成时更新时间戳
        }
    }

    // 兼容性方法，保持向后兼容
    @Deprecated
    public boolean isChecked() {
        return checkStatus == CheckStatus.DONE;
    }

    @Deprecated
    public void setChecked(boolean checked) {
        this.checkStatus = checked ? CheckStatus.DONE : CheckStatus.UNCHECKED;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public void setTimestamp(java.util.Date date) {
        this.timestamp = date.getTime();
    }

    public boolean getIsInternal() {
        return isInternal;
    }

    public void setIsInternal(boolean isInternal) {
        this.isInternal = isInternal;
    }

    // 兼容性方法
    @Deprecated
    public boolean getIsChecked() {
        return isChecked();
    }

    @Deprecated
    public void setIsChecked(boolean isChecked) {
        setChecked(isChecked);
    }

    @Override
    public String toString() {
        return "URLEntry{" +
                "id=" + id +
                ", url='" + url + '\'' +
                ", method='" + method + '\'' +
                ", host='" + host + '\'' +
                ", statusCode=" + statusCode +
                ", title='" + title + '\'' +
                ", checkStatus=" + checkStatus +
                '}';
    }

    // 新增：request/response 数据的 getter/setter 方法
    public byte[] getRequestData() {
        return requestData;
    }

    public void setRequestData(byte[] requestData) {
        this.requestData = requestData;
    }

    public byte[] getResponseData() {
        return responseData;
    }

    public void setResponseData(byte[] responseData) {
        this.responseData = responseData;
    }
} 