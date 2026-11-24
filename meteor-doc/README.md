# Meteor 文档

在线文档：https://www.hasor.net/meteor/

- 技术文档：`docs/cn/docusaurus-plugin-content-docs/current/guides/`
- 版本说明：`docs/cn/docusaurus-plugin-content-docs/current/releases/`
- [Meteor 博客](https://www.hasor.net/cn/blog/meteor/)
- 维护资料：[article/meteor](../../article/meteor/)

本工程沿用 `dbvisitor-doc` 的 Docusaurus 骨架。使用指南、版本说明、首页内容和配色由 Meteor 维护。

## 本地预览

使用 `.nvmrc` 指定的 Node.js 版本，在本目录执行：

```sh
npm ci
npm run start:alone
```

默认地址为 `http://localhost:4304/`。构建和预览生产站点：

```sh
npm run build
npm run serve -- --host 127.0.0.1 --port 3000
```

顶部的语言图标可切换中文和 English。开发时用 `npm run start:alone -- --locale en` 启动英文站，首页为 `http://localhost:4304/en/`。同时预览两种语言时，使用上面的构建及预览命令。

本目录用于构建文档站；Java 模块仍由仓库根目录的 `build.sh` 构建。文档构建必须通过链接、锚点和 Markdown 检查。

## 工程结构

```text
meteor-doc/
├── docs/cn/                  # 中文文档、版本记录
├── docs/en/                  # 英文文档和界面译稿
├── plugins/                  # 版本变量、AI 索引
├── src/
│   ├── pages/                # Meteor 首页
│   └── css/                  # Meteor 配色和文档样式
├── static/                   # 图片等静态资源
├── docusaurus.config.js      # 站点配置
├── sidebars.js               # 技术文档和版本目录
├── package.json              # Node.js 依赖和标准命令
├── deploy.py                 # 构建、上传和 CDN 刷新
├── deploy_config.py          # 发布配置校验和锁
├── deploy_site.py            # 当前站点标识
├── deploy_to_oss.py           # OSS 上传
├── refresh_website_cdn.py     # CDN 刷新
└── oss-config.sample.json    # 发布配置示例，不包含凭据
```

`node_modules/`、`.docusaurus/`、`build/` 和 `.deploy-venv/` 为本地生成目录，不提交。

## AI 文档索引

与 dbVisitor 一样，通过 `@signalwire/docusaurus-plugin-llms-txt` 收集现有页面，再由 `plugins/llms.js` 整理项目介绍和目录。门户模式生成 `build/cn/meteor/llms.txt` 和 `build/en/meteor/llms.txt`，技术文档在前，版本记录在 `Optional` 部分。页面的 `rel="describedby"` 指向该索引。

项目介绍复用 各文档语言目录下的 `guides/start/overview.md` 中 `<!-- llms:start -->` 与 `<!-- llms:end -->` 之间的 Markdown。维护介绍时只改正文，不单独维护另一份 AI 简介。索引使用实际发布地址，不包含维护资料、搜索页或聚合页。

## 版本变量

`plugins/projectVars.js` 集中维护版本信息：

- `lastReleaseVer`：当前使用版本，发布前可使用 SNAPSHOT；正文依赖、控制台 JAR 文件名、首页和 AI 索引统一引用。
- `lastReleaseTime`：当前正式版发布日期。
- `docsVersion`：与 `lastReleaseVer` 一致。
- `developmentVersion`：根 `gradle.properties` 的源码版本，只用于开发版说明和源码构建产物。
- `hasorVersion`：根配置中的 Hasor 依赖版本。

正文使用 `@project.lastReleaseVer@` 等占位符。与 dbVisitor 一样，在 Markdown、代码、链接和 front matter 中解析。正式发布时将 SNAPSHOT 改为正式版号并填写发布日期；历史发布记录、测试归档和第三方版本保留原值。

## 博客维护

博客文章和配图在门户工程 `www/docs/cn/docusaurus-plugin-content-blog/meteor/` 维护，配套可运行示例由 Meteor 的 `met-example/` 提供。阅读入口为 [Meteor 博客](https://www.hasor.net/cn/blog/meteor/)。

## 目录和链接

`sidebars.js` 定义公开目录。顶栏名称为“技术文档”和“版本记录”，博客另有独立入口。

`plugins/remark-repository-links.js` 将正文中指向源码和维护资料的相对链接转换为仓库地址。

## 网站发布

在本目录验证门户发布：

```sh
./deploy.sh --mode portal --dry-run
```

门户模式读取 `sites.portal`，产物包含 `cn/meteor/`、`en/meteor/` 及自动选语言的 `meteor/` 入口。独立模式使用 `--mode alone`，读取 `sites.meteor`，产物包含 `cn/`、`en/` 和根入口。配置默认位于 `~/.hasor-docs-deploy.json`，也可通过 `HASOR_DOCS_DEPLOY_CONFIG` 指定。

实际发布时去掉 `--dry-run`。脚本依次构建、上传和刷新 CDN，任一步失败即停止。Python 依赖位于 `.deploy-venv/`，发布锁位于 `~/.cache/hasor-docs-deploy/`。

也可单独执行：

```sh
.deploy-venv/bin/python deploy_to_oss.py
.deploy-venv/bin/python refresh_website_cdn.py
```

`npm run deploy:docusaurus` 是 Docusaurus 自带入口，与 `./deploy.sh` 的 OSS/CDN 流程不同。本次工程整理不执行网站发布。
