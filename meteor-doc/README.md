# Meteor 文档

在线文档：https://www.hasor.net/rsf/

- 技术文档：`docs/guides/`
- 版本说明：`docs/releases/`
- 博客：`blog/`
- 维护资料：`docs/other/`，不进入公开页面和搜索

本工程沿用 `dbvisitor-doc` 的 Docusaurus 骨架。文章、首页内容和配色归 Meteor 自身维护。

## 本地预览

使用 `.nvmrc` 指定的 Node.js 版本，在本目录执行：

```sh
npm ci
npm run start
```

默认地址为 `http://localhost:3000/rsf/`。构建和预览生产站点：

```sh
npm run build
npm run serve -- --host 127.0.0.1 --port 3000
```

本目录用于构建文档站；Java 模块仍由仓库根目录的 `build.sh` 构建。文档构建必须通过链接、锚点和 Markdown 检查。

## 工程结构

```text
meteor-doc/
├── docs/                     # 技术文档、版本记录、维护资料
├── blog/                     # 文章、作者、标签和专栏
├── plugins/                  # 版本变量、博客专栏、AI 索引
├── src/
│   ├── components/           # 通用博客组件
│   ├── theme/                # 博客主题覆盖
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

与 dbVisitor 一样，通过 `@signalwire/docusaurus-plugin-llms-txt` 收集现有页面，再由 `plugins/llms.js` 整理项目介绍和目录。生产构建生成一个 `build/llms.txt`，技术文档在前，版本记录和博客在 `Optional` 部分。页面的 `rel="describedby"` 指向该索引。

项目介绍复用 `docs/guides/start/overview.md` 中 `<!-- llms:start -->` 与 `<!-- llms:end -->` 之间的 Markdown。维护介绍时只改正文，不单独维护另一份 AI 简介。索引使用实际发布地址，不包含维护资料、搜索页或聚合页。

## 版本变量

`plugins/projectVars.js` 集中维护版本信息：

- `lastReleaseVer`：当前使用版本，发布前可使用 SNAPSHOT；正文依赖、控制台 JAR 文件名、首页和 AI 索引统一引用。
- `lastReleaseTime`：当前正式版发布日期。
- `docsVersion`：与 `lastReleaseVer` 一致。
- `developmentVersion`：根 `gradle.properties` 的源码版本，只用于开发版说明和源码构建产物。
- `hasorVersion`：根配置中的 Hasor 依赖版本。

正文使用 `@project.lastReleaseVer@` 等占位符。与 dbVisitor 一样，在 Markdown、代码、链接和 front matter 中解析。正式发布时将 SNAPSHOT 改为正式版号并填写发布日期；历史发布记录、测试归档和第三方版本保留原值。

## 博客维护

文件名为 `YYYY-MM-DD-slug.md` 或 `.mdx`。作者、标签和专栏分别维护在 `authors.yml`、`tags.yml` 和 `topics.yml`；图片归文章自己的资源目录。元数据、标题和更新时间要求见 [AGENTS.md](AGENTS.md)。

博客的列表、标签、专栏、作者页和文章页共用 dbVisitor 的组件与主题结构。只维护 Meteor 的正文、分类和配色，不复制 dbVisitor 的产品内容。

## 目录和链接

`sidebars.js` 定义公开目录。顶栏名称为“技术文档”和“版本记录”，博客另有独立入口。

`plugins/remark-repository-links.js` 将正文中指向源码和维护资料的相对链接转换为仓库地址。

## 网站发布

默认站点地址为 `https://www.hasor.net/rsf/`。本地或 CI 可通过 `DOCS_URL` 和 `DOCS_BASE_URL` 覆盖；子路径以 `/` 开始和结束。只部署 `build/` 中的静态文件。

发布入口与 dbVisitor 相同：

```sh
./deploy.sh
```

`deploy_site.py` 将站点固定为 `meteor`。配置默认读取 `~/.hasor-docs-deploy.json`，也可通过 `HASOR_DOCS_DEPLOY_CONFIG` 指定文件；格式见 `oss-config.sample.json`，真实凭据不写入仓库。示例按 `/rsf/` 子路径配置，实际 OSS 前缀和 CDN 回源规则需与站点地址一致。

脚本依次构建、上传和刷新 CDN，任一步失败即停止。Python 依赖安装在 `.deploy-venv/`，缓存和发布锁位于仓库 `.gradle/docs-deploy/`。同一工作副本的发布会串行执行；跨工作副本或机器发布时由 CI 串行化。

也可单独执行：

```sh
.deploy-venv/bin/python deploy_to_oss.py
.deploy-venv/bin/python refresh_website_cdn.py
```

`npm run deploy` 是 Docusaurus 自带入口，与 `./deploy.sh` 的 OSS/CDN 流程不同。本次工程整理不执行网站发布。
