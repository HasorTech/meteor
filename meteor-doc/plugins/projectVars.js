const fs = require('node:fs');
const path = require('node:path');

// Update these two values when publishing a release.
const lastReleaseVer = '5.0.0-SNAPSHOT';
const lastReleaseTime = '尚未发布';
const lastReleaseArtifact = Number(lastReleaseVer.split('.')[0]) >= 5 ? 'meteor' : 'hasor-rsf';

// Development notes and source build artifacts follow the checkout version.
const properties = fs.readFileSync(path.resolve(__dirname, '../../gradle.properties'), 'utf8');
const version = properties.match(/^version\s*=\s*(\S+)\s*$/m)?.[1];
if (!version) throw new Error('Missing Meteor version in gradle.properties');
const hasorVersion = properties.match(/^lib\.hasor\s*=\s*(\S+)\s*$/m)?.[1];
if (!hasorVersion) throw new Error('Missing Hasor version in gradle.properties');

module.exports = {
    docsVersion: lastReleaseVer,
    lastReleaseVer,
    lastReleaseTime,
    lastReleaseUrl: lastReleaseVer.endsWith('-SNAPSHOT') ?
        `${lastReleaseVer.split('.')[0]}.x/v${lastReleaseVer.replace(/-SNAPSHOT$/, '')}.md` :
        `https://central.sonatype.com/artifact/net.hasor/${lastReleaseArtifact}/${lastReleaseVer}`,
    developmentVersion: version,
    hasorVersion,
    repositoryUrl: 'https://gitee.com/zycgit/meteor',
};
