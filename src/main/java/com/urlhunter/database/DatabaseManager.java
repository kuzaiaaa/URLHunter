package com.urlhunter.database;

import com.urlhunter.model.URLEntry;
import com.urlhunter.model.DomainConfig;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class DatabaseManager {
    private static final Logger logger = Logger.getLogger(DatabaseManager.class.getName());
    private static final String DEFAULT_DB_NAME = "urlhunter.db";
    private String currentDbPath;
    private Connection connection;
    private Gson gson;

    public DatabaseManager() {
        this.gson = new Gson();
        this.currentDbPath = DEFAULT_DB_NAME;
        initializeDatabase();
    }
    
    public DatabaseManager(String dbPath) {
        this.gson = new Gson();
        this.currentDbPath = dbPath;
        initializeDatabase();
    }

    private void initializeDatabase() {
        try {
            // 确保加载SQLite驱动
            Class.forName("org.sqlite.JDBC");
            
            // 使用绝对路径创建数据库文件
            String dbPath = currentDbPath;
            if (!dbPath.contains("/") && !dbPath.contains("\\")) {
                // 如果只是文件名，则使用系统临时目录
                String tempDir = System.getProperty("java.io.tmpdir");
                dbPath = tempDir + System.getProperty("file.separator") + currentDbPath;
            }
            
            String url = "jdbc:sqlite:" + dbPath;
            logger.info("尝试连接数据库: " + url);
            
            connection = DriverManager.getConnection(url);
            
            // 设置数据库参数
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON");
                stmt.execute("PRAGMA journal_mode = WAL");
            }
            
            createTables();
            logger.info("数据库初始化成功: " + dbPath);
            
        } catch (ClassNotFoundException e) {
            logger.severe("SQLite驱动未找到: " + e.getMessage());
            throw new RuntimeException("SQLite驱动未找到", e);
        } catch (SQLException e) {
            logger.severe("数据库初始化失败: " + e.getMessage());
            throw new RuntimeException("数据库初始化失败", e);
        }
    }

    private void createTables() throws SQLException {
        if (connection == null) {
            throw new SQLException("数据库连接为null，无法创建表");
        }
        
        String createURLsTable = """
            CREATE TABLE IF NOT EXISTS urls (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                url TEXT UNIQUE NOT NULL,
                method TEXT,
                host TEXT,
                path TEXT,
                query TEXT,
                status_code INTEGER,
                length INTEGER,
                title TEXT,
                ip TEXT,
                is_internal BOOLEAN,
                subdomain TEXT,
                is_checked BOOLEAN DEFAULT FALSE,
                check_status TEXT DEFAULT 'UNCHECKED',
                notes TEXT,
                timestamp INTEGER,
                request_data BLOB,
                response_data BLOB
            )
        """;

        String createConfigTable = """
            CREATE TABLE IF NOT EXISTS config (
                id INTEGER PRIMARY KEY,
                config_data TEXT
            )
        """;

        String createIndexes = """
            CREATE INDEX IF NOT EXISTS idx_host ON urls(host);
            CREATE INDEX IF NOT EXISTS idx_subdomain ON urls(subdomain);
            CREATE INDEX IF NOT EXISTS idx_status_code ON urls(status_code);
            CREATE INDEX IF NOT EXISTS idx_is_checked ON urls(is_checked);
            CREATE INDEX IF NOT EXISTS idx_check_status ON urls(check_status);
        """;

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createURLsTable);
            stmt.execute(createConfigTable);
            stmt.execute(createIndexes);
            
            // 检查现有表是否需要添加新字段
            addMissingColumns();
        }
    }

    /**
     * 添加缺失的列到现有表中
     */
    private void addMissingColumns() throws SQLException {
        // 检查 request_data 字段是否存在
        if (!columnExists("urls", "request_data")) {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("ALTER TABLE urls ADD COLUMN request_data BLOB");
                logger.info("添加 request_data 字段到 urls 表");
            }
        }
        
        // 检查 response_data 字段是否存在
        if (!columnExists("urls", "response_data")) {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("ALTER TABLE urls ADD COLUMN response_data BLOB");
                logger.info("添加 response_data 字段到 urls 表");
            }
        }
        
        // 检查 check_status 字段是否存在（兼容v7.0）
        if (!columnExists("urls", "check_status")) {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("ALTER TABLE urls ADD COLUMN check_status TEXT DEFAULT 'UNCHECKED'");
                logger.info("添加 check_status 字段到 urls 表");
                
                // 迁移现有数据：如果 is_checked=true 则设为 DONE，否则为 UNCHECKED
                stmt.execute("""
                    UPDATE urls 
                    SET check_status = CASE 
                        WHEN is_checked = 1 THEN 'DONE' 
                        ELSE 'UNCHECKED' 
                    END 
                    WHERE check_status IS NULL OR check_status = ''
                """);
                logger.info("完成 check_status 字段数据迁移");
            }
        }
    }

    /**
     * 检查表中是否存在指定列
     */
    private boolean columnExists(String tableName, String columnName) throws SQLException {
        String sql = "PRAGMA table_info(" + tableName + ")";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                String name = rs.getString("name");
                if (columnName.equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void insertURL(URLEntry entry) {
        if (connection == null) {
            logger.severe("数据库连接为null，无法插入URL");
            return;
        }
        
        String sql = """
            INSERT OR REPLACE INTO urls 
            (url, method, host, path, query, status_code, length, title, ip, is_internal, subdomain, is_checked, check_status, notes, timestamp, request_data, response_data)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, entry.getUrl());
            pstmt.setString(2, entry.getMethod());
            pstmt.setString(3, entry.getHost());
            pstmt.setString(4, entry.getPath());
            pstmt.setString(5, entry.getQuery());
            pstmt.setInt(6, entry.getStatusCode());
            pstmt.setInt(7, entry.getLength());
            pstmt.setString(8, entry.getTitle());
            pstmt.setString(9, entry.getIp());
            pstmt.setBoolean(10, entry.isInternal());
            pstmt.setString(11, entry.getSubdomain());
            pstmt.setBoolean(12, entry.isChecked());
            pstmt.setString(13, entry.getCheckStatus() != null ? entry.getCheckStatus().name() : "UNCHECKED");
            pstmt.setString(14, entry.getNotes());
            pstmt.setLong(15, entry.getTimestamp());
            
            // 存储原始 request 数据
            if (entry.getRequestData() != null) {
                pstmt.setBytes(16, entry.getRequestData());
            } else {
                pstmt.setNull(16, java.sql.Types.BLOB);
            }
            
            // 存储原始 response 数据
            if (entry.getResponseData() != null) {
                pstmt.setBytes(17, entry.getResponseData());
            } else {
                pstmt.setNull(17, java.sql.Types.BLOB);
            }
            
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.severe("插入URL失败: " + e.getMessage());
        }
    }

    public List<URLEntry> getAllURLs() {
        List<URLEntry> urls = new ArrayList<>();
        if (connection == null) {
            logger.severe("数据库连接为null，无法获取URL列表");
            return urls;
        }
        
        String sql = "SELECT * FROM urls ORDER BY timestamp DESC";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                URLEntry entry = new URLEntry();
                entry.setId(rs.getLong("id"));
                entry.setUrl(rs.getString("url"));
                entry.setMethod(rs.getString("method"));
                entry.setHost(rs.getString("host"));
                entry.setPath(rs.getString("path"));
                entry.setQuery(rs.getString("query"));
                entry.setStatusCode(rs.getInt("status_code"));
                entry.setLength(rs.getInt("length"));
                entry.setTitle(rs.getString("title"));
                entry.setIp(rs.getString("ip"));
                entry.setInternal(rs.getBoolean("is_internal"));
                entry.setSubdomain(rs.getString("subdomain"));
                entry.setChecked(rs.getBoolean("is_checked"));
                
                // 处理 check_status 字段
                String checkStatusStr = rs.getString("check_status");
                if (checkStatusStr != null && !checkStatusStr.isEmpty()) {
                    try {
                        entry.setCheckStatus(URLEntry.CheckStatus.valueOf(checkStatusStr));
                    } catch (IllegalArgumentException e) {
                        // 如果状态值无效，设为默认值
                        entry.setCheckStatus(URLEntry.CheckStatus.UNCHECKED);
                    }
                } else {
                    // 从旧的 is_checked 字段迁移
                    entry.setCheckStatus(entry.isChecked() ? URLEntry.CheckStatus.DONE : URLEntry.CheckStatus.UNCHECKED);
                }
                
                entry.setNotes(rs.getString("notes"));
                entry.setTimestamp(rs.getLong("timestamp"));
                
                // 读取原始 request/response 数据
                byte[] requestData = rs.getBytes("request_data");
                if (requestData != null) {
                    entry.setRequestData(requestData);
                }
                
                byte[] responseData = rs.getBytes("response_data");
                if (responseData != null) {
                    entry.setResponseData(responseData);
                }
                
                urls.add(entry);
            }
        } catch (SQLException e) {
            logger.severe("获取URL列表失败: " + e.getMessage());
        }

        return urls;
    }

    public List<URLEntry> getURLsByHost(String host) {
        List<URLEntry> urls = new ArrayList<>();
        String sql = "SELECT * FROM urls WHERE host = ? ORDER BY timestamp DESC";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, host);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                URLEntry entry = new URLEntry();
                entry.setId(rs.getLong("id"));
                entry.setUrl(rs.getString("url"));
                entry.setMethod(rs.getString("method"));
                entry.setHost(rs.getString("host"));
                entry.setPath(rs.getString("path"));
                entry.setQuery(rs.getString("query"));
                entry.setStatusCode(rs.getInt("status_code"));
                entry.setLength(rs.getInt("length"));
                entry.setTitle(rs.getString("title"));
                entry.setIp(rs.getString("ip"));
                entry.setInternal(rs.getBoolean("is_internal"));
                entry.setSubdomain(rs.getString("subdomain"));
                entry.setChecked(rs.getBoolean("is_checked"));
                
                // 处理 check_status 字段
                String checkStatusStr = rs.getString("check_status");
                if (checkStatusStr != null && !checkStatusStr.isEmpty()) {
                    try {
                        entry.setCheckStatus(URLEntry.CheckStatus.valueOf(checkStatusStr));
                    } catch (IllegalArgumentException e) {
                        // 如果状态值无效，设为默认值
                        entry.setCheckStatus(URLEntry.CheckStatus.UNCHECKED);
                    }
                } else {
                    // 从旧的 is_checked 字段迁移
                    entry.setCheckStatus(entry.isChecked() ? URLEntry.CheckStatus.DONE : URLEntry.CheckStatus.UNCHECKED);
                }
                
                entry.setNotes(rs.getString("notes"));
                entry.setTimestamp(rs.getLong("timestamp"));
                
                // 读取原始 request/response 数据
                byte[] requestData = rs.getBytes("request_data");
                if (requestData != null) {
                    entry.setRequestData(requestData);
                }
                
                byte[] responseData = rs.getBytes("response_data");
                if (responseData != null) {
                    entry.setResponseData(responseData);
                }
                
                urls.add(entry);
            }
        } catch (SQLException e) {
            logger.severe("按主机获取URL失败: " + e.getMessage());
        }

        return urls;
    }

    public void updateURL(URLEntry entry) {
        String sql = """
            UPDATE urls SET 
            method=?, host=?, path=?, query=?, status_code=?, length=?, title=?, ip=?, 
            is_internal=?, subdomain=?, is_checked=?, check_status=?, notes=?, request_data=?, response_data=?
            WHERE id=?
        """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, entry.getMethod());
            pstmt.setString(2, entry.getHost());
            pstmt.setString(3, entry.getPath());
            pstmt.setString(4, entry.getQuery());
            pstmt.setInt(5, entry.getStatusCode());
            pstmt.setInt(6, entry.getLength());
            pstmt.setString(7, entry.getTitle());
            pstmt.setString(8, entry.getIp());
            pstmt.setBoolean(9, entry.isInternal());
            pstmt.setString(10, entry.getSubdomain());
            pstmt.setBoolean(11, entry.isChecked());
            pstmt.setString(12, entry.getCheckStatus() != null ? entry.getCheckStatus().name() : "UNCHECKED");
            pstmt.setString(13, entry.getNotes());
            
            // 更新原始 request 数据
            if (entry.getRequestData() != null) {
                pstmt.setBytes(14, entry.getRequestData());
            } else {
                pstmt.setNull(14, java.sql.Types.BLOB);
            }
            
            // 更新原始 response 数据
            if (entry.getResponseData() != null) {
                pstmt.setBytes(15, entry.getResponseData());
            } else {
                pstmt.setNull(15, java.sql.Types.BLOB);
            }
            
            pstmt.setLong(16, entry.getId());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.severe("更新URL失败: " + e.getMessage());
        }
    }

    public void deleteURL(long id) {
        String sql = "DELETE FROM urls WHERE id = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.severe("删除URL失败: " + e.getMessage());
        }
    }

    public void saveConfig(DomainConfig config) {
        String sql = "INSERT OR REPLACE INTO config (id, config_data) VALUES (1, ?)";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            String configJson = gson.toJson(config);
            pstmt.setString(1, configJson);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.severe("保存配置失败: " + e.getMessage());
        }
    }

    public DomainConfig loadConfig() {
        String sql = "SELECT config_data FROM config WHERE id = 1";
        
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            if (rs.next()) {
                String configJson = rs.getString("config_data");
                return gson.fromJson(configJson, DomainConfig.class);
            }
        } catch (SQLException e) {
            logger.severe("加载配置失败: " + e.getMessage());
        }
        
        return new DomainConfig(); // 返回默认配置
    }

    public List<String> getDistinctHosts() {
        List<String> hosts = new ArrayList<>();
        String sql = "SELECT DISTINCT host FROM urls ORDER BY host";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                hosts.add(rs.getString("host"));
            }
        } catch (SQLException e) {
            logger.severe("获取主机列表失败: " + e.getMessage());
        }

        return hosts;
    }

    public void clearDatabase() {
        String sql = "DELETE FROM urls";
        
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(sql);
            logger.info("数据库清空成功");
        } catch (SQLException e) {
            logger.severe("清空数据库失败: " + e.getMessage());
        }
    }

    public void clearAllData() {
        clearDatabase();
    }
    
    /**
     * 切换到新的数据库文件
     */
    public void switchDatabase(String dbPath) throws SQLException {
        // 关闭当前连接
        close();
        
        // 切换到新路径
        this.currentDbPath = dbPath;
        
        // 重新初始化数据库
        initializeDatabase();
        
        logger.info("已切换到数据库: " + dbPath);
    }
    
    /**
     * 获取当前数据库路径
     */
    public String getCurrentDbPath() {
        return currentDbPath;
    }
    
    /**
     * 创建新的数据库文件
     */
    public void createNewDatabase(String dbPath) throws SQLException {
        switchDatabase(dbPath);
    }
    
    /**
     * 导入另一个数据库的数据
     */
    public void importFromDatabase(String sourceDbPath) throws SQLException {
        String tempUrl = "jdbc:sqlite:" + sourceDbPath;
        
        try (Connection sourceConn = DriverManager.getConnection(tempUrl);
             Statement stmt = sourceConn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM urls")) {
            
            while (rs.next()) {
                URLEntry entry = new URLEntry();
                entry.setUrl(rs.getString("url"));
                entry.setMethod(rs.getString("method"));
                entry.setHost(rs.getString("host"));
                entry.setPath(rs.getString("path"));
                entry.setQuery(rs.getString("query"));
                entry.setStatusCode(rs.getInt("status_code"));
                entry.setLength(rs.getInt("length"));
                entry.setTitle(rs.getString("title"));
                entry.setIp(rs.getString("ip"));
                entry.setInternal(rs.getBoolean("is_internal"));
                entry.setSubdomain(rs.getString("subdomain"));
                entry.setChecked(rs.getBoolean("is_checked"));
                entry.setNotes(rs.getString("notes"));
                entry.setTimestamp(rs.getLong("timestamp"));
                
                insertURL(entry);
            }
            
            logger.info("从数据库导入完成: " + sourceDbPath);
            
        } catch (SQLException e) {
            logger.severe("从数据库导入失败: " + e.getMessage());
            throw e;
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            logger.severe("关闭数据库连接失败: " + e.getMessage());
        }
    }
} 