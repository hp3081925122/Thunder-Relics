# 雷霆遗迹 · 1.20.1 Forge

这是一个加入雷霆君王生物的 Forge 工作区，使用 Forge 47.4.23、Java 17 与 GeckoLib 4.8.2。

实体 ID：`thunderrelics:thunder_king`。使用刷怪蛋或 `/summon thunderrelics:thunder_king` 召唤。

模型资源来自已完成的雷霆君王持械模型，包含 GeoLib 动画 `cleave`、`double_cleave` 和 `heavy_slam`；当前版本先接入基础近战和渲染，技能伤害扩展可在此基础上继续添加。

```powershell
$env:JAVA_HOME = 'C:\Users\30819\.jdks\ms-17.0.18'
.\gradlew.bat build
.\gradlew.bat runClient
```

复制项目和批量改名请在上一级目录运行 `new-mod-from-template.ps1`。
