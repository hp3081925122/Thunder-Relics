# 雷霆遗迹 · 1.20.1 Forge

这是一个加入雷霆君王生物的 Forge 工作区，使用 Forge 47.4.23、Java 17 与 GeckoLib 4.8.2。

实体 ID：`thunderrelics:thunder_king`。使用刷怪蛋或 `/summon thunderrelics:thunder_king` 召唤。

雷霆君王包含行走、近战连招、双手重劈、投掷王戟与引雷仪式。动画使用 GeckoLib，并按命中帧结算伤害。

## 引雷仪式

`storm_ritual` 持续 6 秒：持械手起举的同时，另一只手同步抬起，在 0.5 秒内合握同一把王戟，再连续向上托举。2.1 秒时切换为持续 5 分钟的雷雨天气；2.2、3.2、4.2 秒各生成一波落雷预警。

每波在 Boss 周围半径 22 格内布置最多 32 个落点，其中一个固定在当前攻击目标脚下。落点生成后不再追踪目标，蓝色预警持续收缩 1.5 秒，再落雷并在同一帧显示完整电弧。落雷伤害为攻击力 × 0.9，半径 1.55 格；随后电弧伤害为攻击力 × 0.25，半径 2.8 格。同一波同一阶段不会叠加多个重叠落点的伤害。地面电弧由 12 条独立随机轨迹组成；落雷阶段同时在 Boss 上方的大范围空域瞬间绘制 18 条随机电弧，仅整体淡出，不播放蔓延动画。特效采用自定义网格渲染，不堆叠原版粒子，也不烧毁建筑。

技能距离上限为 26 格，需要视线，冷却 18 秒，新生成的 Boss 最早 5 秒后可使用。施法期间保持朝向攻击目标并停止寻路。

## 雷霆王庭

标准结构文件：`data/thunderrelics/structures/thunder_court.nbt`，范围 65 × 36 × 73 格。包含四座塔楼、回廊、露天战斗场、王座大厅、祭台、长凳和一名持久存在的雷霆君王。

自然生成配置位于 `data/thunderrelics/worldgen/`，群系包括平原、向日葵平原、草甸、森林和桦木森林；结构候选区域间隔 40 区块、最小间隔参数 20 区块。既有存档需要探索新生成的区块。

- 定位：`/locate structure thunderrelics:thunder_court`
- 按地形放置：`/place structure thunderrelics:thunder_court`
- 按原点放置模板：`/place template thunderrelics:thunder_court`

`ThunderCourtBuilder` 保存了先框架、后内饰的制作步骤。开发测试命令仅在 `-ProyalDebug` 启动且使用专用测试存档时开放；正式运行不启用。实机截图保存在 `run/royal-debug/`，Blockbench 动画连续帧位于 `model_source/storm_ritual/review_storm_ritual/`。

```powershell
$env:JAVA_HOME = 'C:\Users\30819\.jdks\ms-17.0.18'
.\gradlew.bat build
.\gradlew.bat runClient
```

复制项目和批量改名请在上一级目录运行 `new-mod-from-template.ps1`。
