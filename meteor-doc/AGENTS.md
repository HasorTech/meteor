# Meteor 文档工程约定

本文件适用于 `meteor-doc/`。本目录沿用 `dbvisitor/dbvisitor-doc` 的 Docusaurus 骨架。公共页面位于 `docs/`，博客位于 `blog/`；`docs/other/` 下的 `technical-design/`、`testing/` 和 `ai/` 保存维护资料，不参与公开页面扫描。

## 内容与链接

- API、模块名称和调用示例以当前源码为准，不把历史验收表述为当前版本全量通过。
- 当前版本引用、Meteor 依赖版本和控制台运行包名使用 `@project.lastReleaseVer@`，由 `plugins/projectVars.js` 统一维护；`docsVersion` 与该值一致。开发版说明和源码构建产物使用 `@project.developmentVersion@`，从根目录 `gradle.properties` 读取。不批量替换历史发布记录、测试归档和第三方依赖版本。
- 文档之间优先使用相对 Markdown 链接。源码、测试与维护资料使用仓库相对链接，由 `remark-repository-links` 在构建时转为仓库网页地址。
- 首页遵循使用指南、协议目录、架构、示例和接入的阅读顺序；使用统一的柔黄色主题与可读的深色正文。

## 博客

- 延续 dbVisitor 的“全部 / 专栏 / 最新”导航、摘要列表、专栏与标签索引、文章头部和右侧目录。
- 文件名为 `YYYY-MM-DD-slug.md` 或 `.mdx`。发布时间取文件名，不额外设置顶层 `date`。
- front matter 依次填写 `slug`、`title`、`description`、`authors`、`tags`、`topics`、`language`，可选 `updated` 放最后。
- 中文大标题建议 12–24 字符，上限 28；小标题建议 4–12 字符，上限 20。保持技术含义准确。
- 文章简介后放 `<!-- truncate -->`。作者、标签和专栏分别在 `authors.yml`、`tags.yml`、`topics.yml` 中声明。
- 首发前编辑与纯格式调整不填写 `updated`；正式发布后的实质性修订才标记真实更新时间。
- 配图与附件归 `blog/assets/<文章文件名>/`。代码示例链接到所属工程，不复制整个运行工程。

## 检查

执行 `npm run build`，不得关闭链接、锚点与 Markdown 检查来掩盖失败。布局变更检查桌面和窄屏下的首页、文档、博客导航与文章页，搜索需要在构建后的静态站点中验证。

本任务的临时脚本、缓存和验证截图放在仓库 `build/`，不提交生成的站点与依赖目录。
