const path = require('node:path');

// Keep source-relative Markdown useful both in the repository and on the website.
module.exports = function remarkRepositoryLinks({repositoryUrl, baseUrl}) {
    const repositoryRoot = path.resolve(__dirname, '../..');
    const docsRoot = path.resolve(__dirname, '../docs');
    return (tree, file) => {
        function visit(node) {
            if (['link', 'definition'].includes(node.type) && node.url &&
                !/^(?:[a-z][a-z\d+.-]*:|\/|#)/i.test(node.url)) {
                const [relativePath, suffix = ''] = node.url.split(/(?=[?#])/s, 2);
                const target = path.resolve(path.dirname(file.path), decodeURI(relativePath));
                const sourcePath = path.relative(repositoryRoot, target);
                const docsPath = path.relative(docsRoot, target);
                const sourceInDocs = !path.relative(docsRoot, file.path).startsWith('..');
                // docs/other contains repository-only maintenance material.
                const targetIsDoc = !docsPath.startsWith('..') &&
                    !docsPath.startsWith(`other${path.sep}`) && /\.mdx?$/i.test(target);
                if (!sourceInDocs && targetIsDoc) {
                    node.url = `${baseUrl}docs/${docsPath.split(path.sep).join('/').replace(/\.mdx?$/i, '')}${suffix}`;
                } else if (!sourcePath.startsWith('..') && !path.isAbsolute(sourcePath) &&
                    !targetIsDoc) {
                    node.url = `${repositoryUrl}/blob/main/${sourcePath.split(path.sep).map(encodeURIComponent).join('/')}${suffix}`;
                }
            }
            node.children?.forEach(visit);
        }
        visit(tree);
    };
};
