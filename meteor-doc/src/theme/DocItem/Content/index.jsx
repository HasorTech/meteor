// Licensed under the Apache License, Version 2.0. See LICENSE.txt.
import React from 'react';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import {useDoc} from '@docusaurus/plugin-content-docs/client';
import DocItemContent from '@theme-original/DocItem/Content';

export default function MeteorDocContent(props) {
    const {i18n: {currentLocale}} = useDocusaurusContext();
    const {metadata: {source}} = useDoc();
    if (currentLocale !== 'en' || !source.startsWith('@site/docs/')) {
        return <DocItemContent {...props} />;
    }
    return (
        <>
            <p className="alert alert--info">This page is available in Simplified Chinese.</p>
            <div lang="zh-CN"><DocItemContent {...props} /></div>
        </>
    );
}
