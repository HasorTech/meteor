// Licensed under the Apache License, Version 2.0. See LICENSE.txt.
import {spawn} from 'node:child_process';

const [mode, command, ...args] = process.argv.slice(2);
if (!['alone', 'portal'].includes(mode) || command !== 'build') {
    console.error('Usage: site-mode.mjs <alone|portal> build [options]');
    process.exit(2);
}

const env = {...process.env, ECOSYSTEM_BUILD_MODE: mode};
if (mode === 'portal') {
    delete env.DOCS_URL;
    delete env.DOCS_BASE_URL;
}
const forwarded = args.length ? ['--', ...args] : [];
const child = spawn('npm', ['run', command, ...forwarded], {env, stdio: 'inherit'});
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
