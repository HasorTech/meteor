const path = require('node:path');

// Keep source-relative Markdown useful both in the repository and on the website.
module.exports = function remarkRepositoryLinks({repositoryUrl, repositoryRef, baseUrl}) {
    const repositoryRoot = path.resolve(__dirname, '../..');
    const docsRoot = path.resolve(__dirname, '../docs');
    return (tree, file) => {
        const relativeSource = path.relative(path.resolve(__dirname, '../docs'), file.path);
        const localizedSource = /^[^/]+\/docusaurus-plugin-content-docs\/current\/(.+)$/.exec(relativeSource.split(path.sep).join('/'));
        // Localized documents use the same source-relative links as their originals.
        const source = localizedSource ? path.join(docsRoot, localizedSource[1]) : file.path;
        function visit(node) {
            if (['link', 'definition'].includes(node.type) && node.url &&
                !/^(?:[a-z][a-z\d+.-]*:|\/|#)/i.test(node.url)) {
                const [relativePath, suffix = ''] = node.url.split(/(?=[?#])/s, 2);
                const target = path.resolve(path.dirname(source), decodeURI(relativePath));
                const sourcePath = path.relative(repositoryRoot, target);
                const docsPath = path.relative(docsRoot, target);
                const sourceInDocs = !path.relative(docsRoot, source).startsWith('..');
                const targetIsDoc = !docsPath.startsWith('..') && /\.mdx?$/i.test(target);
                if (targetIsDoc) {
                    const docPath = docsPath.split(path.sep).map(encodeURIComponent).join('/');
                    // Docusaurus resolves content-root links against both the current locale and its fallback.
                    node.url = sourceInDocs ? `/${docPath}${suffix}`
                        : `${baseUrl}docs/${docPath.replace(/\.mdx?$/i, '')}${suffix}`;
                } else if (!sourcePath.startsWith('..') && !path.isAbsolute(sourcePath)) {
                    node.url = `${repositoryUrl}/blob/${repositoryRef}/${sourcePath.split(path.sep).map(encodeURIComponent).join('/')}${suffix}`;
                }
            }
            node.children?.forEach(visit);
        }
        visit(tree);
    };
};
