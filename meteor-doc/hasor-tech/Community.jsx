// Licensed under the Apache License, Version 2.0. See LICENSE.txt.
import React, {useEffect, useId, useRef, useState} from 'react';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import useBaseUrl from '@docusaurus/useBaseUrl';
import GiteeIcon from '@site/static/img/projects/icons/gitee.svg';
import GitHubIcon from '@site/static/img/projects/icons/github.svg';
import WeChatIcon from '@site/static/img/projects/icons/weixin.svg';
import HasorTechIcon from '@site/static/img/projects/hasor-tech.svg';
import MailIcon from '@site/static/img/projects/icons/envelope.svg';
import QQIcon from '@site/static/img/projects/icons/qq-penguin.svg';
import DiscussionsIcon from '@site/static/img/projects/icons/discussions.svg';
import QQGroupOneIcon from '@site/static/img/projects/icons/qq-penguin-1.svg';
import QQGroupTwoIcon from '@site/static/img/projects/icons/qq-penguin-2.svg';
import QQGroupThreeIcon from '@site/static/img/projects/icons/qq-penguin-3.svg';
import projects from './projects.json';
import styles from './community.module.css';

const qqGroups = [
    {number: '193943114',
        href: 'https://qm.qq.com/cgi-bin/qm/qr?k=o4Ue0lHqdr7oLq8ga0vvauXuw41nudbo&jump_from=webapi'},
    {number: '641341864',
        href: 'https://qm.qq.com/cgi-bin/qm/qr?k=wMahYnxpVZPjrJp0ghQQLJmwM2Lmpmjl&jump_from=webapi'},
    {number: '948706820',
        href: 'https://qm.qq.com/cgi-bin/qm/qr?k=-ha3lrkHrAddrZMINYbmxj4W7ZrrWW2b&jump_from=webapi&authKey=BtyfWIjWF7uhOf/ZPur+pr5p1efOZyjGESLynkPzbJ9IMd/j/T/pR1SDLcJKC972'},
];
const supportEmail = 'mailto:zyc@hasor.net';
const qqGroupIcons = [QQGroupOneIcon, QQGroupTwoIcon, QQGroupThreeIcon];

function CommunityLink({label, href, children, external}) {
    return <a className={styles.control} href={href} title={label} aria-label={label}
        target={external ? '_blank' : undefined} rel={external ? 'noopener noreferrer' : undefined}>
        {children}
    </a>;
}

export default function Community({children}) {
    const {i18n, siteConfig} = useDocusaurusContext();
    const {ecosystemSite} = siteConfig.customFields;
    const english = i18n.currentLocale === 'en';
    const labels = english
        ? {group: 'Community links', qq: 'QQ groups', wechat: 'WeChat QR code', email: 'Email support',
            officialAccount: 'Hasor Tech Official Account', contact: 'WeChat Contact'}
        : {group: '社区入口', qq: 'QQ 交流群', wechat: '微信二维码', email: '联系邮箱',
            officialAccount: 'Hasor Tech 公众号', contact: '微信联系人'};
    const gitee = projects.find(project => project.site === ecosystemSite).source;
    const github = `https://github.com/HasorTech/${ecosystemSite}`;
    const qrBaseUrl = useBaseUrl('/img/projects/community/');
    const wechatCodes = [
        {file: 'wechat.svg', label: labels.officialAccount, Icon: HasorTechIcon, badge: styles.officialAccount},
        {file: 'wechat-contact.png', label: labels.contact, Icon: WeChatIcon, badge: styles.contact},
    ];
    const id = useId();
    const actions = useRef(null);
    const qqButton = useRef(null);
    const wechatButton = useRef(null);
    const [active, setActive] = useState(null);
    const openPanel = active?.name;

    useEffect(() => {
        if (!active) return;

        function closeOutside(event) {
            if (!actions.current?.contains(event.target)) setActive(null);
        }

        function closeOnEscape(event) {
            if (event.key !== 'Escape') return;
            if (actions.current?.contains(document.activeElement)) {
                const trigger = active.name === 'qq' ? qqButton : wechatButton;
                trigger.current?.focus();
            }
            setActive(null);
        }

        document.addEventListener('pointerdown', closeOutside);
        document.addEventListener('keydown', closeOnEscape);
        return () => {
            document.removeEventListener('pointerdown', closeOutside);
            document.removeEventListener('keydown', closeOnEscape);
        };
    }, [active]);

    const toggle = name => setActive(current =>
        current?.name === name && current.pinned ? null : {name, pinned: true});

    const preview = name => ({
        onPointerEnter(event) {
            if (event.pointerType === 'mouse') {
                setActive(current => current?.name === name ? current : {name, pinned: false});
            }
        },
        onPointerLeave(event) {
            if (event.pointerType !== 'mouse') return;
            const focusInPanel = event.currentTarget.querySelector('[id]')?.contains(document.activeElement);
            if (!focusInPanel) {
                setActive(current => current?.name === name && !current.pinned ? null : current);
            }
        },
    });

    return <div className={styles.row}>
        <div>{children}</div>
        <div className={styles.actions} role="group" aria-label={labels.group} ref={actions}
            onBlur={event => {
                if (!event.currentTarget.contains(event.relatedTarget)) setActive(null);
            }}>
            <CommunityLink label={english ? 'Gitee Issues' : 'Gitee 问题反馈'} href={gitee + '/issues'} external>
                <GiteeIcon className={styles.gitee} aria-hidden="true" focusable="false"/>
            </CommunityLink>
            <CommunityLink label={english ? 'GitHub Issues' : 'GitHub 问题反馈'} href={github + '/issues'} external>
                <GitHubIcon aria-hidden="true" focusable="false"/>
            </CommunityLink>
            <CommunityLink label={english ? 'GitHub Discussions' : 'GitHub 讨论'} href="https://github.com/HasorTech/dbvisitor/discussions" external>
                <DiscussionsIcon aria-hidden="true" focusable="false" />
            </CommunityLink>
            <div {...preview('qq')}>
                <button ref={qqButton} type="button" className={styles.control}
                    aria-label={labels.qq} aria-controls={id + '-qq'} aria-expanded={openPanel === 'qq'}
                    onClick={() => toggle('qq')}>
                    <QQIcon aria-hidden="true" focusable="false" />
                </button>
                <ul className={`${styles.popover} ${styles.groups}`} id={id + '-qq'} hidden={openPanel !== 'qq'}>
                    {qqGroups.map((group, index) => {
                        const GroupIcon = qqGroupIcons[index];
                        const label = english ? `Group ${index + 1}` : `${index + 1}群`;
                        return <li key={group.href}>
                            <a href={group.href} target="_blank" rel="noopener noreferrer"
                                aria-label={`QQ ${label} (${group.number})`}>
                                <img src={`${qrBaseUrl}qq-${index + 1}.svg`} alt="" width="128" height="128"/>
                                <span className={styles.groupCaption}>
                                    <span className={styles.groupLabel}><GroupIcon aria-hidden="true" focusable="false" />{label}</span>
                                    <span>{group.number}</span>
                                </span>
                            </a>
                        </li>;
                    })}
                </ul>
            </div>
            <div {...preview('wechat')}>
                <button ref={wechatButton} type="button" className={styles.control}
                    aria-label={labels.wechat} aria-controls={id + '-wechat'} aria-expanded={openPanel === 'wechat'}
                    onClick={() => toggle('wechat')}>
                    <WeChatIcon className={styles.wechat} aria-hidden="true" focusable="false"/>
                </button>
                <div className={`${styles.popover} ${styles.qr}`} id={id + '-wechat'} hidden={openPanel !== 'wechat'}>
                    {wechatCodes.map(({file, label, Icon, badge}) => (
                        <figure className={styles.wechatCard} key={file}>
                            <div className={styles.wechatCode}>
                                <img src={qrBaseUrl + file} alt="" width="168" height="168" />
                                <span className={`${styles.wechatMark} ${badge}`} aria-hidden="true">
                                    <Icon focusable="false" />
                                </span>
                            </div>
                            <figcaption>{label}</figcaption>
                        </figure>
                    ))}
                </div>
            </div>
            <CommunityLink label={labels.email} href={supportEmail}>
                <MailIcon aria-hidden="true" focusable="false"/>
            </CommunityLink>
        </div>
    </div>;
}
