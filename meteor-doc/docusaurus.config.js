// @ts-check
const {onlineOrigins} = require('./hasor-tech/routes');
const {themes} = require('prism-react-renderer');
const projectVars = require('./plugins/projectVars');
const remarkProjectVars = require('./plugins/remark-project-vars');
const baseUrl = process.env.DOCS_BASE_URL || '/';

/** @type {import('@docusaurus/types').Config} */
module.exports = require('./hasor-tech/config')({
    title: 'Meteor',
    tagline: '面向 Java 的多协议 RPC 框架，让服务连接更多语言与生态。',
    // Keep the website location declared in the repository's pom.json; override for other hosts.
    url: process.env.DOCS_URL || onlineOrigins.meteor,
    baseUrl,
    trailingSlash: true,
    favicon: 'img/favicon/meteor.svg',
    organizationName: 'HasorTech',
    projectName: 'meteor',
    customFields: projectVars,
    onBrokenLinks: 'throw',
    onBrokenAnchors: 'throw',
    i18n: {defaultLocale: 'zh-cn', locales: ['zh-cn', 'en']},
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
            path: 'docs/cn/docusaurus-plugin-content-docs/current',
            remarkPlugins: [[remarkProjectVars, projectVars]],
            sidebarPath: require.resolve('./sidebars.js'),
            // Keep major-version directories such as 4.x in document IDs and URLs.
            numberPrefixParser: false,
            editLocalizedFiles: true,
            editUrl: `${projectVars.repositoryUrl}/edit/${projectVars.repositoryRef}/meteor-doc/`,
            editLocalizedFiles: true,
            beforeDefaultRemarkPlugins: [[require('./plugins/remark-repository-links'), {...projectVars, baseUrl}]],
        },
        blog: false,
        theme: {customCss: require.resolve('./src/css/custom.css')},
    }]],
    plugins: [
        [require.resolve('./plugins/llms'), {
            siteTitle: 'Meteor',
            overview: 'guides/start/overview.md',
            descriptions: {
                'zh-cn': `Meteor ${projectVars.lastReleaseVer} 文档：多协议 RPC、服务开发、注册发现和服务治理。开发版本在版本说明中单独标注。`,
                en: `Meteor ${projectVars.lastReleaseVer} documentation for multi-protocol RPC, service development, discovery, and governance.`,
            },
            depth: 2,
            onRouteError: 'throw',
            content: {
                enableMarkdownFiles: false,
                enableLlmsFullTxt: false,
                includeDocs: true,
                includeBlog: false,
                includePages: false,
                includeGeneratedIndex: false,
                excludeRoutes: [
                    '**/tags{,/**}', '**/search', '**/404.html',
                ],
                contentSelectors: ['.theme-doc-markdown', 'article'],
            },
        }],
    ],
    themes: [[require.resolve('@easyops-cn/docusaurus-search-local'), {
        // A filename hash also works with the subpath redirects used by docusaurus serve.
        hashed: 'filename',
        language: ['en', 'zh'],
        indexBlog: false,
        indexPages: false,
        highlightSearchTermsOnTargetPage: true,
        searchResultLimits: 8,
        docsRouteBasePath: '/docs',
    }]],
    themeConfig: /** @type {import('@docusaurus/preset-classic').ThemeConfig} */ ({
        metadata: [{name: 'keywords', content: 'Meteor,Java,RPC,gRPC,Dubbo,Thrift,JSON-RPC,SOAP,多协议,服务发现'}],
        colorMode: {defaultMode: 'light', disableSwitch: true, respectPrefersColorScheme: false},
        docs: {sidebar: {hideable: true, autoCollapseCategories: true}},
        tableOfContents: {minHeadingLevel: 2, maxHeadingLevel: 3},
        prism: {theme: themes.github, additionalLanguages: ['java', 'bash', 'json', 'groovy', 'protobuf']},
        footer: {
            style: 'light',
            copyright: `Copyright © ${new Date().getFullYear()} Meteor · Apache License 2.0 · Built with Docusaurus`,
        },
    }),
}, 'meteor');
