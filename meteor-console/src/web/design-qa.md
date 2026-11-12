# 集群连接表单验收

日期：2026-10-12

final result: passed

## 对照范围

参考用户第二张数据源截图（1374 × 966），采用标签右对齐、输入框逐行对齐的表单布局。按用户补充，保留顶部 Nacos / JDBC / ZooKeeper 切换，不引入左侧分区。参考图的数据库产品字段、绿色主题不属于此次实现范围。

## 视觉核验

已将参考图与浏览器实际截图放在同一次比较中检查。三种表单保留 Meteor 的字体、蓝色主题和原有字段；输入框统一宽度，标签垂直居中，底部操作与输入框左边缘对齐。无需新增图片或图标。表单为普通 HTML 控件，未使用截图覆盖。

保留紧凑行距以适配桌面显示器；小于 700px 时标签置于输入框上方。与参考图页面内容不同，因此按表单对齐关系核验，不作整页像素一致性比较。未发现需修复的 P0/P1/P2 视觉问题。

## 验证记录

1. `./gradlew :meteor-console:webTest --console=plain`：11 项通过，覆盖类型切换、连接失败、测试后修改、保存、编辑、取消及移除。
2. `npm run format:check`：通过。
3. 三种表单分别在 1920 × 1080、1366 × 768、390 × 844 下截图并检查：无横向溢出；桌面标签、输入框和操作按钮对齐，按钮在首屏内可见；无浏览器运行异常。
4. 截图及布局测量结果保存在 `../../build/design-qa/connection/`，包括各类型的三个尺寸截图和 `metrics.json`。浏览器使用真实控制台测试夹具；本轮未额外连接外部 Nacos / ZooKeeper 服务。

## 服务接入点与示例工作区验收

日期：2026-11-09

final result: passed

参考已确认的效果图：`/home/zyc/.codex/generated_images/01a19561-7fe5-7091-acfd-f1913eb805c3/exec-3fe5d0ee-30d6-4813-ae64-b4ecd1de252b.png`。实现范围为实例服务详情的“接入点”选项卡：左侧协议垂直选项卡，右侧顶部接入点，下方语言和角色选项卡及示例正文。

截图及完整、局部并排对照保存在仓库 `.gradle/endpoint-workspace/`：`implementation.png`、`comparison.png`、`comparison-tabs.png`。主对照视口为 1482 × 1062；另检查 1100、700、390 像素宽度。

已核对五个区域：协议导航、接入点地址、语言与角色栏、Markdown 代码正文、外层导航与面板。默认协议在首位，其余按字母排序；SDK 地址先展示；语言靠左，提供者／消费者靠右；不可用语言和角色置灰。保留现有控制台字号、色彩、圆角和紧凑密度，未按效果图放大全局字体。夹具中的服务名称、端口和示例文本与效果图不同，不影响布局验收。

迭代中修复了窄窗口语言栏挤出角色按钮、快速切换协议时旧响应覆盖新内容，以及独立示例抽屉错误回退到其他语言的问题。最终检查无横向页面溢出；协议、语言和角色支持键盘切换；独立示例抽屉仍可正常使用。无待修复的 P0／P1／P2 视觉问题。

验证：`./build.sh web`、`./build.sh package --max-workers=4` 均通过；Playwright 全部 55 项通过，包含真实后端夹具、快速切换、角色切换和多窗口宽度。相关前端文件格式检查、`git diff --check` 通过。打包不代表执行了完整 Java 测试套件。

## 接入点轻量分区与缩进调整

日期：2026-11-09；此节为后续排版调整的最新验收。

final result: passed

源视觉依据为用户最后确认的无边框页面与“重新调整缩进”的要求。本轮同状态基线：仓库 `.gradle/endpoint-workspace/spacing-review/before.png`；实现截图：同目录 `after.png`。两图均为 JSON-RPC 协议、关闭示例抽屉、1482 × 1062 CSS 像素、deviceScaleFactor 1，无密度缩放；PID 随夹具启动变化，不属于设计差异。

完整并排对照：`comparison.png`；局部对照：`comparison-detail.png`，左侧为调整前，右侧为调整后。已打开并检查这两组组合图。

发现与修正：原区块各自叠加 16px 内边距，导致标题左侧留白过宽，区块之间的空隙也被重复放大。本轮去掉区块内边距，标题共用外层的左边线；地址和语言链接相对标题缩进 12px；标题与内容间隔 12px，区块间隔 28px。继续使用白底，无新增边框、圆角或阴影。

五项检查：字体与字号沿用现有控制台，标题层级可辨，地址保留黑色加粗；布局缩进统一，区块距离紧凑且可区分；颜色沿用既有品牌色、禁用色和背景；无新增或替换图片资产；文案及语言顺序保持原样。未发现待修复的 P0／P1／P2 问题。

验证：前端构建、产品打包、格式检查通过；两项相关 Playwright 回归通过，覆盖实际语言示例抽屉、传入实例地址、角色切换、键盘导航以及 1482／1100／700／390px 宽度；无页面横向溢出或浏览器脚本异常。本轮未重新执行完整 Java 或全部浏览器测试。

## 调试工作区：左侧协议下拉框

- source visual truth: `/home/zyc/.codex/generated_images/01a19561-7fe5-7091-acfd-f1913eb805c3/exec-ee7e0e74-75e7-426e-b63c-356d7d7ede7d.png`
- implementation: `.gradle/endpoint-workspace/debug-redesign-review/implementation.png`（相对于仓库根目录）。
- viewport: 1488 × 1056 CSS px，deviceScaleFactor 1；源图 1490 × 1055，实装截图 1488 × 1056。全图比较仅把源图归一到截图尺寸；局部比较按工作区裁切，不据此声称像素一致。
- full-view comparison: `.gradle/endpoint-workspace/debug-redesign-review/comparison.png`。
- focused comparison: `.gradle/endpoint-workspace/debug-redesign-review/detail.png`。
- narrow-screen evidence: `.gradle/endpoint-workspace/debug-redesign-review/mobile.png`，390 px 宽。
- state: 调试选项卡、协议已选中、方法已选中、请求调用成功。真实夹具为 OrderService.find(String)，返回字符串；设计图为 StatusService.status(DebugMessage)，返回对象。方法数、编辑器行数及响应高度依真实契约变化，不填入模拟方法。

### 核对结果

- 字体：沿用控制台现有 14 px 基准与字体回退、15 px 分区标题和等宽代码。设计图的整体字号稍大；保留产品现有字号，避免此页与全局导航不一致。长签名允许换行。
- 布局：基本信息四列摘要；统一白色工作区，260 px 左栏依次为协议下拉框、方法列表、超时；右侧请求在上、响应在下，发起调用按钮位于请求标题右侧。用细分隔线区分区域，无嵌套卡片。
- 色彩：使用已有蓝色选中态、灰蓝背景与绿色成功态；不复制生成图的渐变和阴影。
- 资产：本次无新增图像资产；保留产品标识及原生下拉控件。编辑器沿用真实文本编辑器，未增加仅供装饰的行号。
- 文案：保留真实服务 ID、实例 UUID 与目标地址，不照搬设计图错误的实例编号；增加简短的 JSON 数组输入提示。
- 交互：六项相关浏览器回归通过，覆盖方法入口自动选中重载签名、刷新保留实例与方法、协议切换保留方法、实际请求成功/失败、契约读取失败重试、窄屏无水平溢出。
- 浏览器异常：额外运行真实提供者调用用例，捕获 `pageerror` 并断言为空，通过。
- 构建：本轮 `./build.sh web`、`./build.sh package --max-workers=4` 与浏览器夹具重新构建均成功；package 不代表执行 Java 单元测试。

### Findings / comparison history

首次全图及局部比较未发现需修复的 P0/P1/P2 问题。生成图与真实数据、全局字号、编辑器行号的差异按上述产品约束保留。响应按内容自然增长，不预留模拟对象的高度。未修改此页以外的导航与路由。

final result: passed

## 实例服务信息：上下布局与独立方法列表（2026-11-09）

- Source visual truth：`/home/zyc/.codex/generated_images/01a19561-7fe5-7091-acfd-f1913eb805c3/exec-cf7ce6d5-98b6-4bbe-bd31-ced8f7917d3a.png`。
- Implementation：`.gradle/endpoint-workspace/service-overview-review/implementation.png`（相对 Meteor 根目录）。
- Viewport：1488 × 1058 CSS px，deviceScaleFactor 1；原图 1487 × 1058 px，仅将参考图宽度归一至 1488 px。实现截图 1488 × 1058 px，无密度缩放。
- State：实例服务“服务信息”，下方选择 gRPC；布局夹具声明 15 种协议，提供 SDK、HTTP、PROTO 三种地址。实际服务为浏览器夹具的 OrderService，未替换成设计稿中的 StatusService；服务文本和端口依真实夹具及布局响应展示。
- Full-view comparison：`.gradle/endpoint-workspace/service-overview-review/comparison.png`，参考与实现并排。
- Focused comparison：`.gradle/endpoint-workspace/service-overview-review/detail-comparison.png`，服务摘要、协议栏、接入点和示例区域并排。
- Mobile evidence：`.gradle/endpoint-workspace/service-overview-review/mobile.png`，390 px 宽；另验证 800 px 宽。

### Findings / comparison history

- 合并后下方区域原先继承独立页面的 viewport 最小高度，会额外预留空白；已为概览内工作区取消该最小高度，最终截图中高度由协议列表与内容决定。
- 顶部基本信息三列，800 px 两列，390 px 单列；下方协议栏与接入点、示例位于独立工作区。方法列表移到独立 Tab，概览不再出现方法正文。无独立接入点 Tab。
- 沿用控制台全局 14 px 字体、蓝色状态、灰蓝页面背景和白色内容，未复制生成图的渐变或较大字号。地址保留深色加粗与完整换行。示例使用已有单行文字入口及真实支持状态；不照搬生成图中 C 的灰色状态。
- 首次按 gRPC 状态完成全图与局部对比，未发现需修复的 P0/P1/P2 差异。窄屏地址自然换行，无页面横向溢出。

### Validation

- 本轮 `./build.sh web`、`./build.sh package --max-workers=4` 成功；打包后重新执行 `:meteor-console:prepareBrowserFixture`。
- 14 项关联浏览器回归通过：实例服务摘要、描述缺失、接入点默认协议与完整地址、方法到调试的签名与实例传递、刷新、协议切换、真实提供者调用、直连、前进后退。
- 最终布局复核及示例/方法用例 7 项通过：15 协议、1488/800/390 布局、方法 Tab 刷新、嵌套/自引用描述、语言能力禁用状态、共享 Markdown 抽屉、Meteor 与原生 Java SDK 示例。
- 浏览器 `pageerror` 断言为空；Prettier 和 `git diff --check` 通过。
- 未运行 NXN 或 Java 全量单元测试；本次仅调整控制台前端布局与入口。

final result: passed

## 接入点与示例：共享标题列和内容列（2026-11-09）

- Source visual truth：`/home/zyc/.codex/generated_images/01a19561-7fe5-7091-acfd-f1913eb805c3/exec-ab4ac741-2119-49a3-b5b8-a941940cc633.png`。
- Implementation screenshot：`.gradle/endpoint-workspace/service-access-align-review/implementation.png`，相对 Meteor 根目录。
- Viewport：1488 × 1058 CSS px，deviceScaleFactor 1；实现 1488 × 1058 px，源图 1465 × 1073 px，在比较图中归一至 1488 × 1058。生成稿与真实字体和数据存在差异，未据此进行像素级误差比较。
- State：实例服务信息，默认 rsf 协议；真实夹具使用 OrderService，布局响应提供 15 种协议。另在 gRPC 的 SDK、HTTP、PROTO 多地址状态检查 1488、800、390 px 宽度。
- Full-view comparison：`.gradle/endpoint-workspace/service-access-align-review/comparison.png`，参考与实现并排。
- Focused comparison：`.gradle/endpoint-workspace/service-access-align-review/detail-comparison.png`，接入点与示例区域参考在上、实现在下。
- Mobile evidence：`.gradle/endpoint-workspace/service-access-align-review/mobile.png`。

### Findings / comparison history

首次全图及局部比较无待修复 P0/P1/P2 差异。

- 字体：沿用控制台 14 px 正文和 16 px、600 字重标题；地址保留 13 px 等宽深色加粗。生成稿较大字号不扩散到产品其它页面。
- 间距：两个区域共享 64 px 标题列、20 px 列间距；标题起点一致，SDK 标签与语言入口起点一致。细分隔线与 20 px 上下间距区分内容，无额外内层卡片。700 px 以下改为标题在上、正文在下，完整地址自然换行。
- 色彩：维持白色内容、现有蓝色可用入口和灰色禁用入口；无新增背景强调色。
- 资产：本次为文本和布局调整，无新增图片、图标或模拟资产。
- 文案：保留接入点、示例及真实语言名，保留真实地址和能力状态。未照搬生成稿中具体服务数据。
- 交互：语言链接继续打开统一示例抽屉，协议键盘导航、切换、默认排序和能力禁用规则不变；未在当前面板重新展开代码。

### Validation

- `./build.sh web` 和 `./build.sh package --max-workers=4` 成功；随后重新构建浏览器夹具。
- 8 项相关 Playwright 回归通过，覆盖对齐断言、响应式、多地址、默认标记、语言入口及角色 Tab、Meteor 与原生 Java SDK 文档、目录及实例的示例入口。
- 布局用例捕获并断言浏览器 pageerror 为空；1488、800、390 px 页面无横向溢出。
- Prettier 与 `git diff --check` 通过。本轮未执行 Java 或 NXN 全量测试。

final result: passed
