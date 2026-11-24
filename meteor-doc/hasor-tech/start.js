// Licensed under the Apache License, Version 2.0. See LICENSE.txt.
const {spawn} = require('node:child_process');
const {existsSync, readFileSync} = require('node:fs');
const {createServer} = require('node:net');
const path = require('node:path');

const workspaceOrigins = path.resolve(__dirname, '../../../www/hasor-tech/origins.json');
const localOrigins = path.join(__dirname, 'origins.json');

function loadDevOrigins(site) {
    const shared = site !== 'www' && existsSync(workspaceOrigins) ? workspaceOrigins : localOrigins;
    const defaults = JSON.parse(readFileSync(shared, 'utf8')).dev;
    const overridePath = process.env.ECOSYSTEM_DEV_CONFIG;
    const override = overridePath ? JSON.parse(readFileSync(overridePath, 'utf8')) : {};
    const configured = override.dev ?? override;
    return Object.fromEntries(Object.entries(defaults).map(([name, fallback]) => {
        const value = configured[name] ?? fallback;
        const url = typeof value === 'string' && URL.canParse(value) ? new URL(value) : null;
        if (!url || !['http:', 'https:'].includes(url.protocol) || !Number(url.port)
            || url.username || url.password || url.pathname !== '/' || url.search || url.hash) {
            throw new Error('Expected an HTTP(S) origin with an explicit port for ' + name + ' in ' + (overridePath || shared));
        }
        return [name, url.origin];
    }));
}

function checkPort(host, port) {
    return new Promise((resolve, reject) => {
        const probe = createServer();
        probe.once('error', reject);
        probe.listen({host, port: Number(port)}, () => probe.close(resolve));
    });
}

async function start() {
    const [site, mode, ...args] = process.argv.slice(2);
    if (!site || site === '--choose-mode') {
        console.log('请选择启动模式：');
        console.log('  npm run start:alone   只调试当前站，其余站点使用线上地址');
        console.log('  npm run start:portal  使用本地地址联调，各站需要分别启动');
        return;
    }
    if (!['alone', 'portal'].includes(mode)) {
        throw new Error('Choose npm run start:alone or npm run start:portal.');
    }
    const origins = loadDevOrigins(site);
    if (!origins[site]) throw new Error('Missing development origin for ' + site);
    const {hostname, port, protocol} = new URL(origins[site]);
    const host = hostname.replace(/^\[|\]$/g, '');
    if (!args.includes('--help')) await checkPort(host, port);
    const cli = path.join(path.dirname(require.resolve('@docusaurus/core/package.json')), 'bin/docusaurus.mjs');
    const child = spawn(process.execPath, [cli, 'start', ...args, '--host', host, '--port', port], {
        stdio: 'inherit',
        env: {
            ...process.env,
            NODE_ENV: 'development',
            ECOSYSTEM_BUILD_MODE: mode,
            HTTPS: String(protocol === 'https:'),
            ...(mode === 'portal' && {DOCS_URL: '', DOCS_BASE_URL: ''}),
            ECOSYSTEM_DEV_ORIGINS: JSON.stringify(mode === 'alone' ? {[site]: origins[site]} : origins),
        },
    });
    for (const signal of ['SIGINT', 'SIGTERM']) {
        process.once(signal, () => child.kill(signal));
    }
    child.on('error', error => {
        console.error(error.message);
        process.exitCode = 1;
    });
    child.on('exit', code => {
        process.exitCode = code ?? 1;
    });
}

start().catch(error => {
    console.error(error.message);
    process.exitCode = 1;
});
