# TballHelper 台球辅助器

基于 xy007man/Tball 增强重构的 Android 台球游戏辅助工具。

## 功能特性

- **延长瞄准线** - 自动识别瞄准圆环，绘制延长瞄准线
- **球道路径预测** - 显示白球、母球、袋口的预测路径
- **多游戏支持** - 支持多款台球游戏，自定义模板
- **模板管理** - 用户可截取和导入不同游戏的瞄准圆环模板
- **GitHub Actions CI/CD** - 自动构建和发布 APK

## 技术架构

```
├── app/                      # Android 应用层
│   └── src/main/java/com/tballhelper/app/
│       ├── MainActivity.kt   # 主界面
│       ├── OverlayService.kt # 透明悬浮服务
│       ├── OverlayView.kt    # 辅助线绘制
│       ├── data/             # 数据层
│       └── permission/       # 权限管理
│
├── Billiards_SDK/            # Native SDK
│   └── src/main/
│       ├── cpp/              # C++ NDK 代码
│       └── java/              # JNI 接口
│
└── .github/workflows/        # CI/CD 配置
```

## 核心算法

1. **瞄准圆环定位** - 模板匹配 (Template Matching)
2. **瞄准线检测** - 白色像素区域 DFS 搜索
3. **球位置识别** - 颜色过滤识别
4. **路径预测** - 几何连线计算

## 权限说明

- `SYSTEM_ALERT_WINDOW` - 悬浮窗权限
- `FOREGROUND_SERVICE` - 前台服务
- `MEDIA_PROJECTION` - 屏幕截图
- `POST_NOTIFICATIONS` - 通知权限

## 使用说明

1. 安装 APK 后授予悬浮窗和截屏权限
2. 点击"启动服务"，然后切换到游戏
3. 点击"截取模板"截取游戏的瞄准圆环
4. 服务运行后会自动显示辅助线

## 开发构建

```bash
./gradlew assembleDebug
```

## License

MIT License
