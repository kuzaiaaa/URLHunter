package com.urlhunter.utils;

import com.urlhunter.model.URLEntry;
import com.urlhunter.database.DatabaseManager;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;

/**
 * 数据导入导出工具类
 */
public class DataImportExport {
    
    private static final Gson gson = new Gson();
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    
    /**
     * 导出数据到CSV文件
     */
    public static void exportToCSV(List<URLEntry> entries, String filePath) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath, java.nio.charset.StandardCharsets.UTF_8))) {
            // 写入CSV头部
            writer.println("ID,URL,方法,主机,路径,查询,状态码,长度,标题,IP,内网,子域名,已检测,备注,时间");
            
            // 写入数据
            for (URLEntry entry : entries) {
                writer.printf("%d,\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",%d,%d,\"%s\",\"%s\",\"%s\",\"%s\",%b,\"%s\",\"%s\"%n",
                    entry.getId(),
                    escapeCSV(entry.getUrl()),
                    escapeCSV(entry.getMethod()),
                    escapeCSV(entry.getHost()),
                    escapeCSV(entry.getPath()),
                    escapeCSV(entry.getQuery()),
                    entry.getStatusCode(),
                    entry.getLength(),
                    escapeCSV(entry.getTitle()),
                    escapeCSV(entry.getIp()),
                    entry.getIsInternal() ? "是" : "否",
                    escapeCSV(entry.getSubdomain()),
                    entry.getIsChecked(),
                    escapeCSV(entry.getNotes()),
                                         entry.getTimestamp() > 0 ? dateFormat.format(new Date(entry.getTimestamp())) : ""
                );
            }
        }
    }
    
    /**
     * 导出数据到JSON文件
     */
    public static void exportToJSON(List<URLEntry> entries, String filePath) throws IOException {
        try (FileWriter writer = new FileWriter(filePath, java.nio.charset.StandardCharsets.UTF_8)) {
            gson.toJson(entries, writer);
        }
    }
    
    /**
     * 导出数据到HTML报告
     */
    public static void exportToHTML(List<URLEntry> entries, String filePath) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath, java.nio.charset.StandardCharsets.UTF_8))) {
            writer.println("<!DOCTYPE html>");
            writer.println("<html>");
            writer.println("<head>");
            writer.println("    <meta charset='UTF-8'>");
            writer.println("    <title>URL Hunter 扫描报告</title>");
            writer.println("    <style>");
            writer.println("        body { font-family: Arial, sans-serif; margin: 20px; }");
            writer.println("        h1 { color: #333; }");
            writer.println("        table { border-collapse: collapse; width: 100%; margin-top: 20px; }");
            writer.println("        th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }");
            writer.println("        th { background-color: #f2f2f2; }");
            writer.println("        .status-2xx { background-color: #d4edda; }");
            writer.println("        .status-3xx { background-color: #fff3cd; }");
            writer.println("        .status-4xx { background-color: #f8d7da; }");
            writer.println("        .status-5xx { background-color: #f5c6cb; }");
            writer.println("        .internal { color: #28a745; }");
            writer.println("        .external { color: #dc3545; }");
            writer.println("        .checked { background-color: #d1ecf1; }");
            writer.println("    </style>");
            writer.println("</head>");
            writer.println("<body>");
            
            writer.println("<h1>URL Hunter 扫描报告</h1>");
            writer.printf("<p>生成时间: %s</p>%n", dateFormat.format(new Date()));
            writer.printf("<p>总计条目: %d</p>%n", entries.size());
            
            // 统计信息
            Map<String, Integer> statusStats = new HashMap<>();
            int internalCount = 0;
            int checkedCount = 0;
            
            for (URLEntry entry : entries) {
                String statusRange = getStatusRange(entry.getStatusCode());
                statusStats.put(statusRange, statusStats.getOrDefault(statusRange, 0) + 1);
                if (entry.getIsInternal()) internalCount++;
                if (entry.getIsChecked()) checkedCount++;
            }
            
            writer.println("<h2>统计信息</h2>");
            writer.println("<ul>");
            for (Map.Entry<String, Integer> stat : statusStats.entrySet()) {
                writer.printf("<li>%s: %d</li>%n", stat.getKey(), stat.getValue());
            }
            writer.printf("<li>内网地址: %d</li>%n", internalCount);
            writer.printf("<li>已检测: %d</li>%n", checkedCount);
            writer.println("</ul>");
            
            // 数据表格
            writer.println("<h2>详细数据</h2>");
            writer.println("<table>");
            writer.println("<tr>");
            writer.println("<th>URL</th><th>状态码</th><th>长度</th><th>标题</th><th>IP</th><th>内网</th><th>已检测</th><th>备注</th>");
            writer.println("</tr>");
            
            for (URLEntry entry : entries) {
                String statusClass = getStatusClass(entry.getStatusCode());
                String internalClass = entry.getIsInternal() ? "internal" : "external";
                String checkedClass = entry.getIsChecked() ? "checked" : "";
                
                writer.printf("<tr class='%s %s'>%n", statusClass, checkedClass);
                writer.printf("<td><a href='%s' target='_blank'>%s</a></td>%n", 
                    escapeHTML(entry.getUrl()), escapeHTML(entry.getUrl()));
                writer.printf("<td>%d</td>%n", entry.getStatusCode());
                writer.printf("<td>%d</td>%n", entry.getLength());
                writer.printf("<td>%s</td>%n", escapeHTML(entry.getTitle()));
                writer.printf("<td class='%s'>%s</td>%n", internalClass, escapeHTML(entry.getIp()));
                writer.printf("<td>%s</td>%n", entry.getIsInternal() ? "是" : "否");
                writer.printf("<td>%s</td>%n", entry.getIsChecked() ? "是" : "否");
                writer.printf("<td>%s</td>%n", escapeHTML(entry.getNotes()));
                writer.println("</tr>");
            }
            
            writer.println("</table>");
            writer.println("</body>");
            writer.println("</html>");
        }
    }
    
    /**
     * 从CSV文件导入数据
     */
    public static List<URLEntry> importFromCSV(String filePath) throws IOException {
        List<URLEntry> entries = new ArrayList<>();
        
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(filePath), java.nio.charset.StandardCharsets.UTF_8)) {
            String line = reader.readLine(); // 跳过标题行
            
            while ((line = reader.readLine()) != null) {
                try {
                    URLEntry entry = parseCSVLine(line);
                    if (entry != null) {
                        entries.add(entry);
                    }
                } catch (Exception e) {
                    System.err.println("解析CSV行失败: " + line + " - " + e.getMessage());
                }
            }
        }
        
        return entries;
    }
    
    /**
     * 从JSON文件导入数据
     */
    public static List<URLEntry> importFromJSON(String filePath) throws IOException {
        try (FileReader reader = new FileReader(filePath, java.nio.charset.StandardCharsets.UTF_8)) {
            Type listType = new TypeToken<List<URLEntry>>(){}.getType();
            return gson.fromJson(reader, listType);
        }
    }
    
    /**
     * 从纯文本URL列表导入
     */
    public static List<String> importURLsFromText(String filePath) throws IOException {
        List<String> urls = new ArrayList<>();
        
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(filePath), java.nio.charset.StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) { // 忽略空行和注释行
                    urls.add(line);
                }
            }
        }
        
        return urls;
    }
    
    /**
     * 导出域名列表
     */
    public static void exportDomainList(List<URLEntry> entries, String filePath) throws IOException {
        Set<String> domains = new HashSet<>();
        
        for (URLEntry entry : entries) {
            if (entry.getHost() != null && !entry.getHost().isEmpty()) {
                domains.add(entry.getHost());
            }
        }
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath, java.nio.charset.StandardCharsets.UTF_8))) {
            for (String domain : domains) {
                writer.println(domain);
            }
        }
    }
    
    /**
     * 导出URL列表
     */
    public static void exportURLList(List<URLEntry> entries, String filePath) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath, java.nio.charset.StandardCharsets.UTF_8))) {
            for (URLEntry entry : entries) {
                writer.println(entry.getUrl());
            }
        }
    }
    
    /**
     * 批量导入数据到数据库
     */
    public static void batchImportToDatabase(List<URLEntry> entries, DatabaseManager dbManager) {
        for (URLEntry entry : entries) {
            try {
                dbManager.insertURL(entry);
            } catch (Exception e) {
                System.err.println("导入URL失败: " + entry.getUrl() + " - " + e.getMessage());
            }
        }
    }
    
    // 辅助方法
    private static String escapeCSV(String value) {
        if (value == null) return "";
        return value.replace("\"", "\"\"");
    }
    
    private static String escapeHTML(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
    
    private static String getStatusRange(int statusCode) {
        if (statusCode >= 200 && statusCode < 300) return "2xx成功";
        if (statusCode >= 300 && statusCode < 400) return "3xx重定向";
        if (statusCode >= 400 && statusCode < 500) return "4xx客户端错误";
        if (statusCode >= 500) return "5xx服务器错误";
        return "其他";
    }
    
    private static String getStatusClass(int statusCode) {
        if (statusCode >= 200 && statusCode < 300) return "status-2xx";
        if (statusCode >= 300 && statusCode < 400) return "status-3xx";
        if (statusCode >= 400 && statusCode < 500) return "status-4xx";
        if (statusCode >= 500) return "status-5xx";
        return "";
    }
    
    private static URLEntry parseCSVLine(String line) {
        // 简单的CSV解析，实际项目中建议使用专业的CSV库
        String[] parts = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
        
        if (parts.length < 14) return null;
        
        URLEntry entry = new URLEntry();
        
        try {
            // 去掉引号
            for (int i = 0; i < parts.length; i++) {
                parts[i] = parts[i].trim();
                if (parts[i].startsWith("\"") && parts[i].endsWith("\"")) {
                    parts[i] = parts[i].substring(1, parts[i].length() - 1);
                }
            }
            
            entry.setId(Long.parseLong(parts[0]));
            entry.setUrl(parts[1]);
            entry.setMethod(parts[2]);
            entry.setHost(parts[3]);
            entry.setPath(parts[4]);
            entry.setQuery(parts[5]);
            entry.setStatusCode(Integer.parseInt(parts[6]));
            entry.setLength(Integer.parseInt(parts[7]));
            entry.setTitle(parts[8]);
            entry.setIp(parts[9]);
            entry.setInternal("是".equals(parts[10]));
            entry.setSubdomain(parts[11]);
            entry.setChecked(Boolean.parseBoolean(parts[12]));
            entry.setNotes(parts[13]);
            
            if (parts.length > 14 && !parts[14].isEmpty()) {
                try {
                    Date date = dateFormat.parse(parts[14]);
                    entry.setTimestamp(date.getTime());
                } catch (Exception e) {
                    // 忽略时间解析错误
                }
            }
            
            return entry;
            
        } catch (Exception e) {
            System.err.println("解析CSV行数据失败: " + e.getMessage());
            return null;
        }
    }
} 