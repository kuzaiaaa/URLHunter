# URL Hunter - Burp Suite URL安全检测扩展

## 前言
本项目纯AI（CURSOR Claude-4） 打造，自己一行代码也没有写. 特别感谢@qiqing @Jrd 对项目的支持和建议。

写这个项目的初衷其实是想感受一下现在cursor 的能力，在今年二月份的时候cursor 连一个bcheck 的规则都写不出来（村规支持太差），现在居然能完整写一个Burp插件，从UI ，数据管理，和Burp的联动 初步测试下来没有任何问题。不得不感慨AI的进化速度惊人，带来的便利。

本质上这个项目是造了一个轮子，后续计划加入很多个性化的需求，根据自己的用户习惯来继续定制, 没有进行深度测试（应该有BUG），简单测试下来的bug都让AI修复了。

本项目耗时4天，项目雏形搭建1天，改功能1天，改Bug2天。 

收获和经验来说，主要有两点，一个是在给AI 提需求的时候一定要清晰，最好是配图，一个一个功能来做，更容易做到自己想要的功能。
对于改BUG来说，一次改不好，要多改几次，帮助AI 深度分析问题所在。
当然给AI提供合适的文档链接是必要的，比如要给示例github ，官方文档，api文档，和其他项目作为参考。

参考项目：https://github.com/bit4woo/domain_hunter_pro （我也参与了很多功能的设计）
官方文档：
https://portswigger.net/burp/documentation/desktop/extend-burp/extensions/creating
https://github.com/PortSwigger/burp-extensions-montoya-api-examples
https://portswigger.net/burp/extender/api/

除了前言是我自己写的，其他都是AI生成。
## 项目简介

URL Hunter 是一款专为Burp Suite设计的URL收集和管理扩展，帮助安全测试人员高效地收集、筛选和管理测试目标的URL资源。

## 主要功能

### 🎯 核心功能
- **智能URL收集**: 自动从Proxy流量中提取URL并去重
- **域名分组管理**: 按域名自动分组，支持子域名识别
- **多状态管理**: 支持"未检查"、"检查中"、"已完成"三种状态标记
- **高级搜索**: 支持在URL、标题、备注、请求/响应内容中搜索
- **数据持久化**: SQLite数据库存储，数据不丢失

### 🔧 高级功能
- **Burp集成**: 支持发送URL到Repeater和Intruder
- **直接编辑**: 可在界面中直接编辑备注信息  
- **请求/响应查看**: 完整显示HTTP请求和响应内容
- **灵活筛选**: 支持按状态、关键字等多维度筛选
- **表格式展示**: 类似传统表格的树形界面，信息展示更清晰

### 📊 数据管理
- **自动去重**: 智能识别相同URL（忽略参数差异）
- **IP解析**: 自动解析并显示目标IP地址
- **时间戳**: 记录发现时间，支持按时间排序
- **状态保持**: 界面状态（展开/折叠）智能保持

## 安装使用

### 系统要求
- Burp Suite Professional 2021.10+ 或 Community Edition
- Java 11+
- 支持Windows、macOS、Linux

### 安装步骤

1. **获取扩展文件**
   ```bash
   # 使用包含的构建脚本
   ./build_and_install.bat
   ```

2. **加载到Burp Suite**
   - 打开Burp Suite
   - 进入 Extensions → Installed
   - 点击 "Add" 按钮
   - 选择 Extension type: Java
   - 选择构建生成的 .jar 文件
   - 点击 "Next" 完成安装

3. **配置使用**
   - 安装后会在Burp中添加 "URL Hunter" 标签页
   - 在 "目标域名管理" 中添加要监控的域名
   - 开始使用Burp进行测试，URL会自动收集

### 基本使用

1. **域名配置**: 在主界面上方添加目标域名（如：example.com）
2. **自动收集**: 使用Burp进行正常测试，URL会自动出现在列表中
3. **状态管理**: 右键点击URL可以标记为不同状态
4. **搜索筛选**: 使用搜索框和状态过滤器快速定位目标
5. **集成测试**: 右键菜单支持发送到Repeater/Intruder

## 界面说明

### 主要区域
- **工具栏**: 展开/折叠、刷新、搜索功能
- **URL树形列表**: 按域名分组的URL展示区域
- **请求/响应查看器**: 下方分割面板显示详细的HTTP数据

### 列说明
- **#**: 序号
- **URL**: 完整URL地址
- **Status**: HTTP响应状态码
- **Length**: 响应长度
- **Title**: 页面标题
- **Comments**: 可编辑的备注信息
- **IP**: 解析的IP地址
- **isCheck**: 检查状态（○未检查 ◐检查中 ✓已完成）
- **CheckDoneTime**: 时间戳

## 技术架构

### 技术栈
- **Java**: 核心开发语言
- **Swing**: UI界面框架
- **SQLite**: 数据存储
- **Burp Montoya API**: Burp Suite集成

### 项目结构
```
src/main/java/
├── Extension.java              # 主扩展类
└── com/urlhunter/
    ├── database/              # 数据库层
    ├── model/                 # 数据模型
    ├── proxy/                 # Proxy处理
    ├── scanner/               # URL扫描器
    ├── ui/                    # 用户界面
    └── utils/                 # 工具类
```

## 开发信息

### 构建项目
```bash
# 使用Gradle构建
./gradlew build

# 或使用提供的脚本
./build_and_install.bat
```

### 版本信息
- **当前版本**: v1.0 (最终版本)
- **开发语言**: Java 11+
- **构建工具**: Gradle
- **许可证**: MIT License

## 注意事项

- 首次使用需要在"目标域名管理"中配置要监控的域名
- 数据库文件会自动创建在Burp工作目录
- 建议定期清理无用的URL数据以提高性能
- 搜索功能支持正则表达式模式

## 反馈支持

如有问题或建议，请通过以下方式联系：
- 项目Issue追踪
- 技术支持邮箱

---

**URL Hunter** - 让URL管理更高效 🚀
