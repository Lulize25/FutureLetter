# 日记

一款极简的**本地日记** Android 应用。所有数据只存在你自己的手机上，不联网、不申请任何权限，完全离线可用。

## 功能

- **写日记**：Markdown 编辑（所见即所得渲染），支持插入图片（系统相册选择，原图本地保存）
- **日历**：月视图按日期打点，一眼看到哪天写了日记；支持📅跳转到任意日期
- **搜索**：全文搜索日记标题与正文
- **备份与恢复**：支持导出为 JSON（单文件，图片内嵌）或 ZIP（含原图），可随时导入还原
- **隐私**：零权限、零账号、零服务器

## 下载安装

本仓库通过 GitHub Actions 云端构建，无需本地 Android 环境：

1. 打开仓库的 [Actions](https://github.com/Lulize25/FutureLetter/actions) 页面；
2. 点进最新一次成功的构建（绿色✓）；
3. 页面底部的 **Artifacts** 中下载「日记-release」；
4. 解压 zip 得到 `app-release.apk`，传到手机上安装即可。

## 技术栈

- Kotlin + Jetpack Compose (Material 3)
- Room（SQLite，FTS4 全文搜索）
- Hilt（依赖注入）+ DataStore（偏好设置）
- Coil（图片加载）、CommonMark（Markdown 解析）

## 本地构建

需要 JDK 17 与 Android SDK：

```bash
gradle assembleRelease
```

APK 输出在 `app/build/outputs/apk/release/`。

## 签名说明

`release.keystore` 随仓库提交，用于 CI 统一签名，**签名密钥公开，请勿用于其他项目**。同一签名的版本之间可以覆盖安装、数据保留。

## 备份格式

- **JSON**：`{"version":1,"entries":[{id,title,content,dateMillis,imagePaths:[base64]...}]}`，适合长期存档；
- **ZIP**：`entries.json` + `images/` 目录（原图文件），体积更小。

导入时按原 ID 覆盖合并，图片自动还原到应用私有目录。

## 许可证

MIT
