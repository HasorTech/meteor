// @ts-check
const {themes} = require('prism-react-renderer');
const projectVars = require('./plugins/projectVars');
const remarkProjectVars = require('./plugins/remark-project-vars');
const {GlobExcludeDefault} = require('@docusaurus/utils');
const baseUrl = process.env.DOCS_BASE_URL || '/rsf/';

/** @type {import('@docusaurus/types').Config} */
module.exports = {
    title: 'Meteor',
    tagline: '面向 Java 的多协议 RPC 框架，让服务连接更多语言与生态。',
    // Keep the website location declared in the repository's pom.json; override for other hosts.
    url: process.env.DOCS_URL || 'https://www.hasor.net',
    baseUrl,
    trailingSlash: true,
    favicon: 'img/meteor.svg',
    organizationName: 'zycgit',
    projectName: 'meteor',
    customFields: projectVars,
    onBrokenLinks: 'throw',
    onBrokenAnchors: 'throw',
    i18n: {defaultLocale: 'zh-cn', locales: ['zh-cn']},
    markdown: {
        format: 'detect',
        hooks: {onBrokenMarkdownLinks: 'throw', onBrokenMarkdownImages: 'throw'},
        preprocessor: ({fileContent}) => remarkProjectVars.replaceVariables(fileContent, projectVars),
        parseFrontMatter: ({filePath, fileContent, defaultParseFrontMatter}) => defaultParseFrontMatter({
            filePath,
            fileContent: remarkProjectVars.replaceVariables(fileContent, projectVars),
        }),
    },
    presets: [['classic', {
        docs: {
            remarkPlugins: [[remarkProjectVars, projectVars]],
            sidebarPath: require.resolve('./sidebars.js'),
            // Keep major-version directories such as 4.x in document IDs and URLs.
            numberPrefixParser: false,
            exclude: [...GlobExcludeDefault, 'other/**'],
            editUrl: `${projectVars.repositoryUrl}/edit/main/meteor-doc/`,
            beforeDefaultRemarkPlugins: [[require('./plugins/remark-repository-links'), {...projectVars, baseUrl}]],
        },
        blog: {
            remarkPlugins: [[remarkProjectVars, projectVars]],
            blogTitle: 'Meteor 博客',
            blogDescription: '多协议 RPC、服务开发与注册发现实践。',
            exclude: [...GlobExcludeDefault, '**/assets/**'],
            showReadingTime: true,
            blogSidebarCount: 25,
            postsPerPage: 10,
            editUrl: `${projectVars.repositoryUrl}/edit/main/meteor-doc/`,
            beforeDefaultRemarkPlugins: [[require('./plugins/remark-repository-links'), {...projectVars, baseUrl}]],
            feedOptions: {type: ['rss', 'atom'], xslt: true},
            onInlineTags: 'throw',
            onInlineAuthors: 'throw',
            onUntruncatedBlogPosts: 'throw',
        },
        theme: {customCss: require.resolve('./src/css/custom.css')},
    }]],
    plugins: [
        require.resolve('./plugins/blog-topics'),
        [require.resolve('./plugins/llms'), {
            siteTitle: 'Meteor',
            overview: 'guides/start/overview.md',
            descriptions: {
                'zh-cn': `Meteor ${projectVars.lastReleaseVer} 文档：多协议 RPC、服务开发、注册发现和服务治理。开发版本在版本说明中单独标注。`,
            },
            depth: 2,
            onRouteError: 'throw',
            content: {
                enableMarkdownFiles: false,
                enableLlmsFullTxt: false,
                includeDocs: true,
                includeBlog: true,
                includePages: false,
                includeGeneratedIndex: false,
                excludeRoutes: [
                    '**/tags{,/**}', '**/search', '**/404.html',
                    '**/blog', '**/blog/{archive,authors,page,topics}{,/**}',
                ],
                contentSelectors: ['.theme-doc-markdown', 'article'],
            },
        }],
    ],
    themes: [[require.resolve('@easyops-cn/docusaurus-search-local'), {
        // A filename hash also works with the subpath redirects used by docusaurus serve.
        hashed: 'filename',
        language: ['en', 'zh'],
        indexBlog: true,
        indexPages: false,
        highlightSearchTermsOnTargetPage: true,
        searchResultLimits: 8,
        docsRouteBasePath: '/docs',
    }]],
    themeConfig: /** @type {import('@docusaurus/preset-classic').ThemeConfig} */ ({
        metadata: [{name: 'keywords', content: 'Meteor,Java,RPC,gRPC,Dubbo,Thrift,JSON-RPC,SOAP,多协议,服务发现'}],
        colorMode: {defaultMode: 'light', disableSwitch: true, respectPrefersColorScheme: false},
        navbar: {
            title: 'Meteor',
            logo: {alt: 'Meteor', src: 'img/meteor.svg'},
            items: [
                {type: 'docSidebar', sidebarId: 'guides', label: '技术文档', position: 'left'},
                {type: 'docSidebar', sidebarId: 'releases', label: '版本记录', position: 'left'},
                {to: '/blog/archive', activeBasePath: '/blog', label: '博客', position: 'left'},
                {href: projectVars.repositoryUrl, label: 'Gitee', position: 'right'},
            ],
        },
        docs: {sidebar: {hideable: true, autoCollapseCategories: true}},
        tableOfContents: {minHeadingLevel: 2, maxHeadingLevel: 3},
        prism: {theme: themes.github, additionalLanguages: ['java', 'bash', 'json', 'groovy', 'protobuf']},
        footer: {
            style: 'light',
            links: [
                {title: '开始使用', items: [
                    {label: '快速开始', to: '/docs/guides/start/quick-start'},
                    {label: '选择协议', to: '/docs/guides/protocols/about'},
                    {label: '阅读博客', to: '/blog/archive'},
                ]},
                {title: '开发与维护', items: [
                    {label: '源码仓库', href: projectVars.repositoryUrl},
                    {label: '问题反馈', href: `${projectVars.repositoryUrl}/issues`},
                    {label: '框架扩展', to: '/docs/guides/extensions/about'},
                ]},
                {title: 'Hasor 生态', items: [
                    {label: 'dbVisitor', href: 'https://www.dbvisitor.net/'},
                    {label: 'Hasor', href: 'https://www.hasor.net/'},
                    {label: 'Neta', href: 'https://gitee.com/zycgit/neta'},
                ]},
            ],
            copyright: `Copyright © ${new Date().getFullYear()} Meteor · Apache License 2.0 · Built with Docusaurus`,
        },
    }),
};
