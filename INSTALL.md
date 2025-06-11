# URL Hunter - 安装指南

## 快速安装

### 方法一：使用构建脚本（推荐）

1. **运行构建脚本**
   ```bash
   # Windows
   build_and_install.bat
   
   # Linux/macOS  
   chmod +x gradlew
   ./gradlew build
   ```

2. **加载到Burp Suite**
   - 打开Burp Suite
   - 进入 Extensions → Installed
   - 点击 "Add" 按钮
   - Extension type 选择 "Java"
   - 选择 `build/libs/extension-template-project.jar`
   - 点击 "Next" 完成安装

### 方法二：手动构建

1. **构建JAR文件**
   ```bash
   ./gradlew jar
   ```

2. **查找生成的文件**
   - JAR文件位置：`build/libs/extension-template-project.jar`

3. **加载到Burp**
   - 按照方法一的步骤2加载

## 首次使用配置

1. **启动URL Hunter**
   - 安装成功后，Burp界面会出现 "URL Hunter" 标签页

2. **配置目标域名**
   - 在主界面顶部的"目标域名管理"区域
   - 添加要监控的域名（例如：example.com）
   - 点击"添加"按钮

3. **开始使用**
   - 使用Burp进行正常的Web测试
   - URL会自动被收集并显示在界面中

## 系统要求

- **Burp Suite**: Professional 2021.10+ 或 Community Edition
- **Java**: 11 或更高版本
- **操作系统**: Windows、macOS、Linux

## 常见问题

### Q: 扩展加载失败
**A**: 检查Java版本，确保为Java 11+

### Q: 没有收集到URL
**A**: 确保已在"目标域名管理"中添加了要监控的域名

### Q: 数据库错误
**A**: 确保Burp有写入工作目录的权限

## 验证安装

安装成功后，您应该看到：
- Burp界面出现"URL Hunter"标签页
- Console输出："URL Hunter extension loading..."
- Extensions列表中显示扩展已加载

---

**需要帮助？** 请查看主README.md文档获取详细使用说明。 