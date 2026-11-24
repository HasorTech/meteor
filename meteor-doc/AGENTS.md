# Meteor 文档工程约定

本文件适用于 `meteor-doc/`。中文文档源码位于 `docs/cn/`，英文文档源码位于 `docs/en/`。

## 内容与链接

- API、模块名称和调用示例以当前源码为准，不把历史验收表述为当前版本全量通过。
- 当前版本引用、Meteor 依赖版本和控制台运行包名使用 `@project.lastReleaseVer@`，由 `plugins/projectVars.js` 统一维护；`docsVersion` 与该值一致。开发版说明和源码构建产物使用 `@project.developmentVersion@`，从根目录 `gradle.properties` 读取。不批量替换历史发布记录、测试归档和第三方依赖版本。
- 文档之间优先使用相对 Markdown 链接。源码、测试与维护资料使用仓库相对链接，由 `remark-repository-links` 在构建时转为仓库网页地址。
- 首页遵循使用指南、协议目录、架构、示例和接入的阅读顺序；使用统一的柔黄色主题与可读的深色正文。

## 检查

执行 `npm run build`，不得关闭链接、锚点与 Markdown 检查来掩盖失败。布局变更检查桌面和窄屏下的首页、文档和博客入口，搜索需要在构建后的静态站点中验证。

本任务的临时脚本、缓存和验证截图放在仓库 `build/`，不提交生成的站点与依赖目录。
