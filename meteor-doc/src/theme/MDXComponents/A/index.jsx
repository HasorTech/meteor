// Licensed under the Apache License, Version 2.0. See LICENSE.txt.
import React from 'react';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import MDXAnchor from '@theme-original/MDXComponents/A';
import {ecosystemUrl} from '@site/hasor-tech/routes';

export default function EcosystemAnchor({href, ...props}) {
    const {siteConfig} = useDocusaurusContext();
    return <MDXAnchor {...props} href={ecosystemUrl(href, siteConfig.customFields.ecosystemOrigins)} />;
}
