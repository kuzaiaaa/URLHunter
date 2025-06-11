@echo off
echo ===========================================
echo URL Hunter - Burp插件构建脚本
echo ===========================================

echo 正在清理项目...
if exist build rmdir /s /q build
if exist .gradle rmdir /s /q .gradle

echo 正在构建项目...
call gradlew.bat clean build --no-daemon

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ===========================================
    echo 构建成功！
    echo ===========================================
    echo.
    echo 插件JAR文件位置:
    echo %CD%\build\libs\
    dir build\libs\*.jar
    echo.
    echo 安装说明:
    echo 1. 打开Burp Suite
    echo 2. 进入Extensions标签
    echo 3. 点击Add按钮
    echo 4. 选择JAR文件并加载
    echo 5. 在主界面中找到"URL Hunter"标签
    echo.
    echo ===========================================
) else (
    echo.
    echo ===========================================
    echo 构建失败！请检查错误信息。
    echo ===========================================
)

pause 