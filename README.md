# 瞬影科技刷量助手 - LSPatch模组

## 📱 功能
针对 `瞬影科技_4.1.0.77 (com.yxrjshun.yingkeji)` 的LSPatch/Xposed刷量模块，自动执行广告刷量任务获取金币。

## 🚀 使用GitHub Action编译

### 步骤1: 上传到GitHub
```bash
# 1. 在GitHub创建新仓库，例如 YingkejiAdHelper
# 2. 在本地或手机上执行：
git clone https://github.com/你的用户名/YingkejiAdHelper.git
# 将项目文件放入该目录
git add .
git commit -m "init"
git push
```

### 步骤2: 触发编译
- 推送代码到GitHub后，Action自动触发
- 或者手动进入 Actions → Build LSPatch Module → Run workflow
- 等待编译完成（约3-5分钟）

### 步骤3: 下载APK
- 编译完成后，在Action运行结果页面
- 点击 Artifacts → 下载 `YingkejiAdHelper-release.zip`
- 解压得到 `app-release.apk`

## 📦 安装使用
1. 安装 **LSPatch** (https://github.com/LSPosed/LSPatch)
2. 使用LSPatch修补瞬影科技APK，选择「集成模块」模式
3. 选择本模块apk一起打包
4. 安装修补后的APK
5. 打开瞬影科技App → 刷量模块自动运行

或使用LSPosed：
1. 安装LSPosed框架
2. 安装本模块apk
3. 在LSPosed中启用模块，作用域勾选 `com.yxrjshun.yingkeji`
4. 重启目标应用

## ⚙️ 配置账号
打开刷量助手App，输入账号：
```
token1#oaid1#deviceid1&token2#oaid2#deviceid2
```
点击「开始刷量」即可。

## 📁 项目结构
```
YingkejiAdHelper/
├── .github/workflows/build.yml  # GitHub Action自动编译
├── build.gradle.kts             # Gradle构建配置
├── settings.gradle.kts
├── gradle.properties
├── proguard-rules.pro
└── src/main/
    ├── AndroidManifest.xml
    ├── assets/xposed_init
    └── java/com/operit/yingkeji/adhelper/
        ├── MainHook.kt           # Xposed Hook入口
        ├── AdTaskService.kt      # 刷量核心服务
        └── MainActivity.kt       # 配置界面
```
