import {expect, test} from '@playwright/test';

const clusterProfile = {
    name: '生产 · 华东',
    type: 'jdbc',
    database: 'h2',
    address: 'jdbc:h2:mem:console-browser;DB_CLOSE_DELAY=-1',
    username: 'sa',
    password: ''
};

async function login(page) {
    await page.goto('/');
    await page.getByLabel('账号', {exact: true}).fill('admin');
    await page.getByLabel('密码', {exact: true}).fill('test-password');
    await page.getByRole('button', {name: '登录', exact: true}).click();
    await expect(page.getByRole('navigation', {name: '主要导航'})).toBeVisible();
}

async function enter(page) {
    await login(page);
    let profiles = await (await page.request.get('/api/clusters')).json();
    let cluster = profiles.find((c) => c.name === clusterProfile.name);
    if (!cluster) cluster = await (await page.request.post('/api/clusters', {data: clusterProfile})).json();
    await page.goto(`/#view=applications&clusterId=${cluster.id}`);
    await page.reload();
    await expect(page.getByRole('navigation', {name: '服务治理'})).toBeVisible();
}

async function order(page) {
    const response = page.waitForResponse((response) => new URL(response.url()).pathname === '/api/services');
    await page
        .getByRole('navigation', {name: '服务治理'})
        .getByRole('link', {name: '服务目录', exact: true})
        .click();
    const services = (await (await response).json()).items;
    await expect(page.locator('tbody tr td:first-child code')).toHaveText(
        services.map((service) => service.info.serviceId)
    );
    const serviceId = services.find((service) => service.info.name.includes('OrderService')).info.serviceId;
    await page.getByLabel('搜索服务 ID', {exact: true}).fill(serviceId);
    await expect(page.getByRole('columnheader', {name: '操作', exact: true})).toHaveCount(0);
    await page.getByRole('button', {name: serviceId, exact: true}).click();
    await expect(page.getByRole('navigation', {name: '服务详情'})).toBeVisible();
    await expect(page.locator('.breadcrumbs strong')).toHaveText(serviceId);
    await expect(page.getByRole('button', {name: '服务信息', exact: true})).toHaveClass('active');
    await expect(page.locator('.details-grid')).toContainText(serviceId);
}

function invokeButton(page) {
    return page.getByRole('button', {name: /^(发起调用|调用 \d+ 个提供者)$/});
}

async function tab(page, name) {
    await page.getByRole('navigation', {name: '服务详情'}).getByRole('button', {name, exact: true}).click();
}

async function addMeta(page, key, value) {
    await page.getByRole('button', {name: '新增', exact: true}).click();
    await page.getByLabel('元信息键', {exact: true}).fill(key);
    await page.getByLabel('元信息值（JSON）', {exact: true}).fill(value);
    await page.locator('.metadata-panel').getByRole('button', {name: '保存', exact: true}).click();
}

test('the public language packs match and do not open authenticated APIs', async ({request}) => {
    const chinese = await request.get('/api/i18n', {headers: {'Accept-Language': 'zh-CN'}});
    const english = await request.get('/api/i18n', {headers: {'Accept-Language': 'fr, en-GB;q=0.8'}});
    expect(chinese.ok()).toBe(true);
    expect(english.ok()).toBe(true);
    expect(english.headers()['content-language']).toBe('en-US');
    const zh = await chinese.json();
    const en = await english.json();
    expect(zh.locale).toBe('zh-CN');
    expect(en.locale).toBe('en-US');
    expect(Object.keys(en.messages).sort()).toEqual(Object.keys(zh.messages).sort());
    expect(en.messages['ui.sign-in-to-the-console']).toBe('Sign in to the console');
    expect(zh.messages['ui.sign-in-to-the-console']).toBe('登录控制台');
    expect((await request.get('/api/settings')).status()).toBe(401);
});

test('language switching keeps form input, localizes server feedback, and survives reload', async ({
                                                                                                       page
                                                                                                   }) => {
    await page.goto('/');
    await page.getByLabel('账号', {exact: true}).fill('admin');
    await page.getByRole('combobox', {name: '语言'}).selectOption('en-US');
    await expect(page.getByRole('heading', {name: 'Sign in to the console'})).toBeVisible();
    await expect(page.getByLabel('Username', {exact: true})).toHaveValue('admin');

    await page.getByLabel('Password', {exact: true}).fill('wrong');
    await page.getByRole('button', {name: 'Sign in', exact: true}).click();
    await expect(page.getByRole('alert')).toHaveText('Incorrect username or password');

    await page.getByLabel('Password', {exact: true}).fill('test-password');
    await page.getByRole('button', {name: 'Sign in', exact: true}).click();
    await expect(page.getByRole('navigation', {name: 'Main navigation'})).toBeVisible();
    await expect(page.getByRole('button', {name: 'Service governance'})).toBeVisible();
    await page.setViewportSize({width: 390, height: 840});
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
    await page.reload();
    await expect(page.getByRole('combobox', {name: 'Language'})).toHaveValue('en-US');
    await page.getByRole('combobox', {name: 'Language'}).selectOption('zh-CN');
    await expect(page.getByRole('navigation', {name: '主要导航'})).toBeVisible();
    await expect(page.getByRole('button', {name: '服务治理'})).toBeVisible();
});

test('authenticated refresh does not flash the login form while restoring session', async ({page}) => {
    await login(page);
    let releaseSession;
    const pendingSession = new Promise((resolve) => {
        releaseSession = resolve;
    });
    await page.route('**/api/auth/session?*', async (route) => {
        await pendingSession;
        await route.continue();
    });
    await page.reload();
    await expect(page.getByRole('status')).toHaveText('正在加载控制台…');
    await expect(page.getByRole('heading', {name: '登录控制台'})).toHaveCount(0);
    releaseSession();
    await expect(page.getByRole('navigation', {name: '主要导航'})).toBeVisible();
    await expect(page.getByRole('heading', {name: '登录控制台'})).toHaveCount(0);
});

test('login waits for session lookup and recovers after timeout or proxy failure', async ({page}) => {
    // 会话检查期间不提前显示登录表单；超时后仍可登录。
    const sessionRoute = '**/api/auth/session?*';
    await page.route(sessionRoute, () => {
    });
    await page.goto('/');
    await expect(page.getByRole('status')).toHaveText('正在加载控制台…');
    await expect(page.getByRole('heading', {name: '登录控制台'})).toHaveCount(0);
    await expect(page.getByRole('alert')).toHaveText('控制台服务不可用。', {timeout: 8000});
    await expect(page.getByRole('button', {name: '登录', exact: true})).toBeEnabled();

    // 开发代理返回非 JSON 错误时，显示可理解的连接错误。
    await page.unroute(sessionRoute);
    await page.route(sessionRoute, (route) => route.fulfill({status: 502, body: 'Bad Gateway'}));
    await page.reload();
    await expect(page.getByRole('alert')).toHaveText('控制台服务不可用。');

    // 路由缺失与服务离线分开提示，不再把 HTML 404 说成后端没有启动。
    await page.unroute(sessionRoute);
    await page.route(sessionRoute, (route) => route.fulfill({status: 404, body: '<html>Not Found</html>'}));
    await page.reload();
    await expect(page.getByRole('alert')).toHaveText('控制台接口不存在（404）。');

    await page.unroute(sessionRoute);
    await page.route(sessionRoute, (route) => route.abort('connectionrefused'));
    await page.reload();
    await expect(page.getByRole('alert')).toHaveText('控制台服务不可用。');

    // 后端恢复后可以直接登录，无需重启前端。
    await page.unroute(sessionRoute);
    await page.getByLabel('账号', {exact: true}).fill('admin');
    await page.getByLabel('密码', {exact: true}).fill('test-password');
    await page.getByRole('button', {name: '登录', exact: true}).click();
    await expect(page.getByRole('navigation', {name: '主要导航'})).toBeVisible();
});

test('login, first connection, persistence and compact cluster switching', async ({page}) => {
    await page.goto('/');
    await page.getByLabel('账号', {exact: true}).fill('admin');
    await page.getByLabel('密码', {exact: true}).fill('wrong');
    await page.getByRole('button', {name: '登录', exact: true}).click();
    await expect(page.getByRole('alert')).toContainText('账号或密码错误');
    await page.getByLabel('密码', {exact: true}).fill('test-password');
    await page.getByRole('button', {name: '登录', exact: true}).click();
    await expect(page.getByRole('heading', {name: '连接到注册中心'})).toBeVisible();

    // 连接配置是页面，修改测试过的参数后须重新测试。
    await page.locator('.tabbar').getByRole('button', {name: '连接注册中心'}).click();
    await page.getByRole('button', {name: 'JDBC', exact: true}).click();
    await page.getByLabel('连接名称').fill(clusterProfile.name);
    await page.getByLabel('数据库', {exact: true}).selectOption('h2');
    await page.getByLabel('JDBC URL').fill(clusterProfile.address);
    await page.getByLabel('用户名', {exact: true}).fill('sa');
    await expect(page.getByText('数据分组', {exact: true})).toHaveCount(0);
    await page.getByRole('button', {name: '测试连接', exact: true}).click();
    await expect(page.getByRole('status')).toContainText('连接成功');
    await page.getByLabel('连接名称').fill('changed');
    await expect(page.getByRole('button', {name: '保存并进入'})).toBeDisabled();
    await page.getByLabel('连接名称').fill(clusterProfile.name);
    await page.getByRole('button', {name: '保存并进入'}).click();
    await expect(page.getByText('order-app', {exact: true})).toBeVisible();
    await page.reload();
    await expect(page.locator('.cluster-select')).toHaveText(/集群 生产 · 华东/);
    await expect(page.locator('.topbar').getByText('jdbc:h2:', {exact: false})).toHaveCount(0);
    await expect(page.getByRole('button', {name: '可观测性'})).toBeDisabled();
    await page.locator('.cluster-select').click();
    await page.locator('.cluster-menu').getByRole('button', {name: '所有集群', exact: true}).click();
    await expect(page.locator('.cluster-select')).toContainText('所有集群');
    await expect(page.locator('.cluster-cards .card')).toHaveCount(1);
    await page.screenshot({path: 'test-results/clusters.png', fullPage: true});

    // 集群卡片直接复制其本机 ID，可用于配置另一台 Console 的 Remote 连接。
    await page.context().grantPermissions(['clipboard-read', 'clipboard-write']);
    const [saved] = await (await page.request.get('/api/clusters')).json();
    await page.getByRole('button', {name: '复制集群 ID'}).click();
    await expect(page.getByRole('status')).toHaveText('集群 ID 已复制');
    await expect.poll(() => page.evaluate(() => navigator.clipboard.readText())).toBe(saved.id);
    await expect(page.getByRole('heading', {name: '集群连接'})).toBeVisible();
});

test('cluster ID copies when the Clipboard API is unavailable', async ({page}) => {
    await enter(page);
    const clusterId = new URLSearchParams(new URL(page.url()).hash.slice(1)).get('clusterId');
    await page.goto('/#view=clusters');
    await page.context().grantPermissions(['clipboard-read', 'clipboard-write']);
    await page.evaluate(() => Object.defineProperty(navigator.clipboard, 'writeText', {value: undefined}));
    const card = page.getByRole('article').filter({hasText: clusterProfile.name});
    await card.getByRole('button', {name: '复制集群 ID'}).click();
    await expect(page.getByRole('status')).toHaveText('集群 ID 已复制');
    await expect.poll(() => page.evaluate(() => navigator.clipboard.readText())).toBe(clusterId);
});

test('cluster cards truncate long addresses on one line and search the complete address', async ({
    page
}, testInfo) => {
    const address = 'jdbc:h2:tcp://127.0.0.1:19192/mem:console-debug';
    const errors = [];
    page.on('pageerror', (error) => errors.push(error.message));
    await page.route((url) => url.pathname === '/api/clusters', (route) =>
        route.fulfill({
            json: [
                {id: 'jdbc', name: '111', type: 'jdbc', address, database: 'h2'},
                {id: 'dev', name: '开发集群', type: 'nacos', address: '10.0.1.8:8848'},
                {id: 'test', name: '测试集群', type: 'zookeeper', address: '10.0.2.8:2181'},
                {id: 'remote', name: '远程集群', type: 'remote', address: '10.0.3.8:19200'}
            ]
        })
    );
    await page.setViewportSize({width: 1280, height: 692});
    await login(page);
    const cards = page.getByRole('article');
    await expect(cards).toHaveCount(4);
    await expect(page.getByText('集群连接', {exact: true})).toHaveCount(1);
    const search = page.getByRole('searchbox', {name: '搜索集群'});
    const connect = page.getByRole('button', {name: '连接注册中心'});
    expect(Math.abs((await search.boundingBox()).y - (await connect.boundingBox()).y)).toBeLessThan(2);
    const longAddress = cards.first().locator('.cluster-address');
    const shortAddress = cards.nth(1).locator('.cluster-address');
    await expect(longAddress).toHaveText(address);
    await expect(longAddress).toHaveAttribute('title', address);
    await expect(longAddress).toHaveCSS('white-space', 'nowrap');
    await expect(longAddress).toHaveCSS('text-overflow', 'ellipsis');
    expect(await longAddress.evaluate((element) => element.scrollWidth > element.clientWidth)).toBe(true);
    expect(await shortAddress.evaluate((element) => element.scrollWidth === element.clientWidth)).toBe(true);
    expect((await longAddress.boundingBox()).height).toBe((await shortAddress.boundingBox()).height);
    expect((await cards.nth(3).boundingBox()).y).toBeGreaterThan((await cards.first().boundingBox()).y);
    await page.screenshot({path: testInfo.outputPath('cluster-cards.png')});

    await page.setViewportSize({width: 2560, height: 900});
    const button = await connect.boundingBox();
    expect(button.x + button.width).toBeCloseTo(2540, 0);
    const firstCard = await cards.first().boundingBox();
    expect((await cards.nth(3).boundingBox()).y).toBeCloseTo(firstCard.y, 0);
    await page.screenshot({path: testInfo.outputPath('cluster-cards-wide.png')});
    await search.fill('console-debug');
    await expect(cards).toHaveCount(1);
    expect((await cards.first().boundingBox()).width).toBeCloseTo(firstCard.width, 0);

    await page.setViewportSize({width: 375, height: 812});
    expect(await longAddress.evaluate((element) => element.scrollWidth > element.clientWidth)).toBe(true);
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(375);
    await page.screenshot({path: testInfo.outputPath('cluster-cards-mobile.png')});
    await search.fill('no-such-cluster');
    await expect(page.getByText('没有匹配的集群', {exact: true})).toBeVisible();
    expect(errors).toEqual([]);
});

test('all console table headers match the rule panel title height and reference styles', async ({
                                                                                                    page
                                                                                                }, testInfo) => {
    await page.setViewportSize({width: 1440, height: 900});
    await enter(page);
    await order(page);
    await tab(page, '提供者');
    await page.locator('.member-table .instance-link').first().click();
    await tab(page, '流量控制');
    await expect(page.getByLabel('规则正文')).toBeEditable();
    const titleHeight = (await page.locator('.rule-script-toolbar').boundingBox()).height;
    expect(titleHeight).toBe(48);

    async function checkHeaders(table, name) {
        await test.step(name, async () => {
            await expect(table.locator('thead th').first()).toBeVisible();
            for (const width of [1440, 390]) {
                await page.setViewportSize({width, height: 900});
                const headers = await table.locator('thead th').evaluateAll((cells) =>
                    cells.map((cell) => {
                        const style = getComputedStyle(cell);
                        return {
                            height: cell.getBoundingClientRect().height,
                            padding: style.padding,
                            fontSize: style.fontSize,
                            fontWeight: style.fontWeight,
                            lineHeight: style.lineHeight,
                            verticalAlign: style.verticalAlign,
                            background: style.backgroundColor,
                            color: style.color
                        };
                    })
                );
                for (const header of headers) {
                    expect(header).toEqual({
                        height: titleHeight,
                        padding: '7px 12px',
                        fontSize: '13px',
                        fontWeight: '600',
                        lineHeight: '20px',
                        verticalAlign: 'middle',
                        background: 'rgb(228, 235, 244)',
                        color: 'rgb(71, 85, 105)'
                    });
                }
                if (width === 1440) {
                    await page.screenshot({path: testInfo.outputPath(`table-header-${name}.png`), fullPage: true});
                }
            }
            await page.setViewportSize({width: 1440, height: 900});
        });
    }

    const navigation = page.getByRole('navigation', {name: '服务治理'});
    await navigation.getByRole('link', {name: '应用列表', exact: true}).click();
    await page.getByRole('button', {name: '切换为列表', exact: true}).click();
    await checkHeaders(page.locator('.table-scroll table'), 'applications');
    await page.getByRole('button', {name: 'order-app', exact: true}).click();
    await checkHeaders(page.locator('.instance-directory-table'), 'instances');
    await page.locator('.instance-directory-table .instance-primary').first().click();
    await checkHeaders(page.locator('.instance-info-panel .endpoint-table'), 'instance-endpoints');
    await page
        .getByRole('navigation', {name: '实例详情'})
        .getByRole('button', {name: '服务清单', exact: true})
        .click();
    await checkHeaders(page.locator('.instance-services-table'), 'instance-services');
    await page
        .locator('.instance-services-table tbody tr')
        .filter({
            has: page.locator('td', {hasText: /^提供者$/})
        })
        .first()
        .locator('td')
        .first()
        .getByRole('button')
        .click();
    await tab(page, '服务信息');
    await expect(page.getByRole('tablist', {name: '服务协议'})).toBeVisible();
    await tab(page, '元信息');
    await checkHeaders(page.locator('.metadata-table'), 'service-metadata');
    await navigation.getByRole('link', {name: '服务目录', exact: true}).click();
    await checkHeaders(page.locator('.service-directory-table'), 'services');
    await order(page);
    await tab(page, '服务信息');
    await expect(page.getByRole('tablist', {name: '服务协议'})).toBeVisible();
    await tab(page, '提供者');
    await checkHeaders(page.locator('.member-table'), 'providers');
    await tab(page, '消费者');
    await checkHeaders(page.locator('.member-table'), 'consumers');
    await navigation.getByRole('link', {name: '全局配置', exact: true}).click();
    await page
        .getByRole('navigation', {name: '全局配置'})
        .getByRole('button', {name: '元信息', exact: true})
        .click();
    await checkHeaders(page.locator('.metadata-table'), 'global-metadata');
});

test('left navigation persists across governance pages and highlights their section', async ({
                                                                                                 page
                                                                                             }, testInfo) => {
    await enter(page);
    const navigation = page.getByRole('navigation', {name: '服务治理'});
    await expect(navigation.getByRole('link')).toHaveText(['应用列表', '服务目录', '全局配置']);
    await expect(navigation.getByRole('link', {name: '应用列表'})).toHaveAttribute('aria-current', 'page');
    await order(page);
    await expect(navigation.getByRole('link', {name: '服务目录'})).toHaveAttribute('aria-current', 'page');
    await page.reload();
    await expect(navigation.getByRole('link', {name: '服务目录'})).toHaveAttribute('aria-current', 'page');
    await navigation.getByRole('link', {name: '全局配置'}).click();
    await expect(navigation.getByRole('link', {name: '全局配置'})).toHaveAttribute('aria-current', 'page');
    await expect(page.getByRole('navigation', {name: '全局配置'})).toBeVisible();
    await expect(page.locator('.workspace > .page-toolbar')).toHaveCount(0);
    await navigation.getByRole('link', {name: '应用列表'}).click();
    for (const width of [1440, 390]) {
        await page.setViewportSize({width, height: 900});
        await expect(navigation.getByRole('link', {name: '应用列表'})).toBeVisible();
        expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(width);
        await page.screenshot({path: testInfo.outputPath(`left-navigation-${width}.png`)});
    }
});

test('application cards, instance unit search and independent detail navigation', async ({page}) => {
    await enter(page);
    await page.getByRole('button', {name: '切换为列表'}).click();
    await expect(page.locator('tbody tr')).toHaveCount(2);
    await page.getByRole('button', {name: '切换为卡片'}).click();
    await expect(page.locator('.card')).toHaveCount(2);
    const applicationCard = page.getByRole('button', {name: 'order-app', exact: true});
    await expect(applicationCard).toHaveText('order-app');
    await expect(applicationCard.locator('.protocols')).toHaveCount(0);
    const consumerCard = page.getByRole('button', {name: 'storefront', exact: true});
    await expect(consumerCard).toHaveText('storefront');
    await page.screenshot({path: 'test-results/application-cards.png', fullPage: true});
    // 点击卡片边缘也可以进入，不依赖标题或单独的操作按钮。
    await applicationCard.click({position: {x: 8, y: 8}});
    const identity = page.locator('.instance-identity').first();
    await expect(page.getByRole('columnheader', {name: 'PID', exact: true})).toBeVisible();
    await expect(identity.locator('.instance-primary')).not.toContainText('PID');
    await expect(page.locator('.instance-pid').first()).toHaveText(/^\d+$/);
    await expect(identity.locator('.instance-id')).toHaveCount(0);
    const ip = (await identity.locator('.instance-primary').textContent()).trim();
    const processId = (await page.locator('.instance-pid').first().textContent()).trim();
    await page.getByLabel('搜索 IP、进程 ID 或实例 ID').fill(ip);
    await expect(page.locator('.instance-identity')).toHaveCount(2);
    await page.getByLabel('搜索 IP、进程 ID 或实例 ID').fill(processId);
    await expect(page.locator('.instance-identity')).toHaveCount(2);
    await page.getByLabel('搜索 IP、进程 ID 或实例 ID').fill('');
    await expect(page.getByLabel('筛选单元')).toBeVisible();
    await page.getByLabel('筛选单元').selectOption('east');
    await expect(page.getByText('本页 1 个实例', {exact: true})).toBeVisible();
    await page.getByRole('button', {name: '切换为卡片'}).click();
    await expect(page.locator('.instance-card')).toHaveCount(1);
    await expect(page.locator('.instance-card .instance-primary')).toHaveText(ip);
    await expect(page.locator('.instance-card .instance-process')).toHaveText(`PID ${processId}`);
    await expect(page.locator('.instance-card .instance-id')).toHaveCount(0);
    await expect(page.locator('.instance-card').getByRole('button', {name: '详情'})).toHaveCount(0);
    await page.screenshot({path: 'test-results/instance-cards.png', fullPage: true});
    await page.locator('.instance-card').click({position: {x: 8, y: 8}});
    await expect(page.getByRole('navigation', {name: '实例详情'})).toBeVisible();
    await expect(page.locator('.breadcrumbs .instance-primary')).toHaveText(`${ip} (${processId})`);
    await expect(page.locator('.breadcrumbs .instance-id')).toHaveCount(0);
    await expect(
        page
            .locator('.details-grid div')
            .filter({has: page.locator('dt', {hasText: '实例 IP'})})
            .locator('dd')
    ).toHaveText(ip);
    await expect(
        page
            .locator('.details-grid div')
            .filter({has: page.locator('dt', {hasText: '进程 ID'})})
            .locator('dd')
    ).toHaveText(processId);
    const onlineStatus = page
        .locator('.details-grid div')
        .filter({has: page.locator('dt', {hasText: '在线状态'})});
    await expect(onlineStatus).toContainText('在线');
    await expect(page.getByRole('switch', {name: '阻断调用'})).toBeVisible();
    await page.getByRole('switch', {name: '阻断调用'}).click();
    await expect(page.getByRole('switch', {name: '阻断调用'})).toBeChecked();
    await expect(onlineStatus).toContainText('在线');
    await page.getByRole('switch', {name: '阻断调用'}).click();
    await expect(page.getByRole('switch', {name: '阻断调用'})).not.toBeChecked();
    await page.getByRole('button', {name: '服务清单', exact: true}).click();
    await expect(page.getByRole('switch', {name: '阻断调用'})).toHaveCount(0);
    const refreshed = page.waitForResponse((response) => new URL(response.url()).pathname === '/api/services');
    await page.getByRole('button', {name: '刷新', exact: true}).click();
    const snapshot = await (await refreshed).json();
    await expect(page.locator('tbody tr td:first-child code')).toHaveText(
        snapshot.items.map((service) => service.serviceId)
    );
    const inventoryId = snapshot.items.find((service) => service.name.includes('Inventory')).serviceId;
    await page.getByLabel('搜索服务', {exact: true}).fill(inventoryId);
    await expect(page.getByRole('columnheader', {name: '操作', exact: true})).toHaveCount(0);
    await expect(page.getByRole('button', {name: inventoryId, exact: true})).toHaveCount(1);
    await page.getByRole('button', {name: inventoryId, exact: true}).click();
    await expect(page.locator('.breadcrumbs .instance-primary')).toHaveText(`${ip} (${processId})`);
    await expect(page.locator('.breadcrumbs strong')).toHaveText(`${ip} (${processId})`);
    await expect(page.locator('.breadcrumbs').getByRole('button')).toHaveText(['服务目录', inventoryId]);
    await expect(
        page.getByRole('navigation', {name: '服务治理'}).getByRole('link', {name: '服务目录', exact: true})
    ).toHaveAttribute('aria-current', 'page');
    await expect(page.getByRole('button', {name: '提供者', exact: true})).toHaveCount(0);
    await expect(page.getByLabel('目标范围', {exact: true})).toHaveCount(0);
    await page.reload();
    await expect(page.locator('.breadcrumbs').getByRole('button')).toHaveText(['服务目录', inventoryId]);
    await expect(page.locator('.breadcrumbs strong')).toHaveText(`${ip} (${processId})`);
    await page.locator('.breadcrumbs').getByRole('button', {name: inventoryId, exact: true}).click();
    await expect(
        page.getByRole('navigation', {name: '服务详情'}).getByRole('button', {name: '提供者', exact: true})
    ).toBeVisible();
    expect(new URLSearchParams(new URL(page.url()).hash.slice(1)).get('instanceId')).toBeNull();
    await expect(page.locator('.breadcrumbs strong')).toHaveText(inventoryId);
    await page.goBack();
    await expect(page.locator('.breadcrumbs strong')).toHaveText(`${ip} (${processId})`);
    await page.goBack();
    await expect(page.getByRole('navigation', {name: '实例详情'})).toBeVisible();
});

for (const role of ['提供者', '消费者']) {
    test(`${role} entry governs only the selected instance and service`, async ({page}, testInfo) => {
        await enter(page);
        await order(page);
        const serviceRoute = new URLSearchParams(new URL(page.url()).hash.slice(1));
        const serviceId = serviceRoute.get('serviceId');
        await tab(page, role);
        const ip = (await page.locator('.member-table .instance-primary').first().textContent()).trim();
        const pid = (await page.locator('.member-table .instance-pid').first().textContent()).trim();
        const link = page.locator('.member-table').getByRole('link', {name: ip, exact: true}).first();
        await expect(link).toHaveCSS('color', 'rgb(37, 99, 235)');
        await expect(link).toHaveCSS('text-decoration-line', 'underline');
        const linkRoute = new URLSearchParams(new URL(await link.getAttribute('href'), page.url()).hash.slice(1));
        expect(linkRoute.get('view')).toBe('service');
        expect(linkRoute.get('clusterId')).toBe(serviceRoute.get('clusterId'));
        expect(linkRoute.get('serviceId')).toBe(serviceId);
        expect(linkRoute.get('instanceId')).toBeTruthy();
        expect(linkRoute.get('target')).toBeNull();
        await page.screenshot({path: testInfo.outputPath('member-instance-links.png'), fullPage: true});
        await link.click();

        // 两个成员入口均进入实例与服务的交集，刷新后仍保留该范围。
        await expect(page.locator('.breadcrumbs .instance-primary')).toHaveText(`${ip} (${pid})`);
        await expect(page.locator('.breadcrumbs strong')).toHaveText(`${ip} (${pid})`);
        await expect(page.locator('.breadcrumbs').getByRole('button')).toHaveText(['服务目录', serviceId]);
        await expect(
            page
                .locator('.service-info-fields > div')
                .filter({has: page.locator('dt', {hasText: /^身份$/})})
                .locator('dd')
        ).toHaveText(role);
        await expect(page.getByRole('button', {name: '提供者', exact: true})).toHaveCount(0);
        await expect(page.getByRole('button', {name: '消费者', exact: true})).toHaveCount(0);
        await expect(page.getByLabel('目标范围', {exact: true})).toHaveCount(0);
        const scopedRoute = new URLSearchParams(new URL(page.url()).hash.slice(1));
        expect(scopedRoute.get('instanceId')).toBe(linkRoute.get('instanceId'));
        expect(scopedRoute.get('serviceId')).toBe(serviceId);
        await page.reload();
        await expect(page.locator('.breadcrumbs .instance-primary')).toHaveText(`${ip} (${pid})`);
        await expect(page.locator('.breadcrumbs').getByRole('button')).toHaveText(['服务目录', serviceId]);
        await page.screenshot({path: testInfo.outputPath('service-instance-breadcrumb.png'), fullPage: true});

        // 治理只写入该实例的服务覆盖，不修改全服务配置或另一个实例。
        await tab(page, '元信息');
        const key = role === '提供者' ? 'provider-entry' : 'consumer-entry';
        await addMeta(page, key, '"scoped"');
        const query = new URLSearchParams({
            mode: 'registry',
            clusterId: scopedRoute.get('clusterId'),
            scope: 'service',
            serviceId
        });
        const global = await (await page.request.get(`/api/metadata?${query}`)).json();
        expect(global[key]).toBeUndefined();
        query.set('instanceId', scopedRoute.get('instanceId'));
        const local = await (await page.request.get(`/api/metadata?${query}`)).json();
        expect(local[key]).toBe('scoped');
        const instanceQuery = new URLSearchParams({mode: 'registry', clusterId: scopedRoute.get('clusterId')});
        const instances = await (await page.request.get(`/api/instances?${instanceQuery}`)).json();
        const other = instances.items.find((instance) => instance.instanceId !== scopedRoute.get('instanceId'));
        query.set('instanceId', other.instanceId);
        const unrelated = await (await page.request.get(`/api/metadata?${query}`)).json();
        expect(unrelated[key]).toBeUndefined();

        // 服务是实例服务页的上级，返回服务时清除实例范围，但保持集群和服务。
        await page.locator('.breadcrumbs').getByRole('button', {name: serviceId, exact: true}).click();
        await expect(page.locator('.breadcrumbs strong')).toHaveText(serviceId);
        const parentRoute = new URLSearchParams(new URL(page.url()).hash.slice(1));
        expect(parentRoute.get('instanceId')).toBeNull();
        expect(parentRoute.get('serviceId')).toBe(serviceId);
        expect(parentRoute.get('clusterId')).toBe(scopedRoute.get('clusterId'));
        await page.locator('.breadcrumbs').getByRole('button', {name: '服务目录', exact: true}).click();
        await expect(page.getByLabel('搜索服务 ID', {exact: true})).toBeVisible();
        await expect(page.locator('.breadcrumbs strong')).toHaveText('服务目录');
    });
}

test('instance cards distinguish provider, consumer and combined roles', async ({page}) => {
    await enter(page);
    await page.getByRole('button', {name: 'order-app', exact: true}).click();
    await page.getByRole('button', {name: '切换为卡片'}).click();
    const roles = page.locator('.instance-card .instance-role');
    await expect(roles).toHaveCount(2);
    await expect(roles.filter({hasText: /^提供者$/})).toHaveCount(1);
    await expect(roles.filter({hasText: /^提供\/消费$/})).toHaveCount(1);
    await expect(roles.first()).toHaveCSS('color', 'rgb(49, 95, 145)');

    const provider = page.locator('.instance-card').filter({hasText: '提供者'});
    const protocols = await provider.locator('.protocol-summary').boundingBox();
    const role = await provider.locator('.instance-role').boundingBox();
    expect(protocols.x + protocols.width).toBeLessThanOrEqual(role.x);
    await page.screenshot({path: 'test-results/instance-roles.png', fullPage: true});

    await page.getByRole('button', {name: '应用列表', exact: true}).click();
    await page.getByRole('button', {name: 'storefront', exact: true}).click();
    await expect(page.locator('.instance-card .instance-role')).toHaveText('消费者');
    await expect(page.locator('.instance-card .protocols')).toHaveCount(0);
});

test('service directory protocols are read-only and information includes protocol examples', async ({
                                                                                                        page
                                                                                                    }, testInfo) => {
    const protocols = [
        'connect',
        'dubbo',
        'grpc',
        'grpc-web',
        'hessian',
        'hprose',
        'jsonrpc',
        'oncrpc',
        'oncrpc-udp',
        'rsf',
        'soap',
        'thrift',
        'thrift-http',
        'triple',
        'xmlrpc'
    ];
    await page.route('**/api/services?*', async (route) => {
        const response = await route.fetch();
        const result = await response.json();
        for (const service of result.items) service.info.protocols = protocols;
        await route.fulfill({response, json: result});
    });
    await page.route('**/api/service?*', async (route) => {
        const response = await route.fetch();
        const result = await response.json();
        result.info.protocols = protocols;
        await route.fulfill({response, json: result});
    });
    await enter(page);
    await page
        .getByRole('navigation', {name: '服务治理'})
        .getByRole('link', {name: '服务目录', exact: true})
        .click();
    const row = page.locator('.service-directory-table tbody tr').first();
    const serviceId = (await row.locator('td').first().textContent()).trim();
    await row.locator('.protocol-summary').hover();
    const panel = row.locator('.protocol-popover');
    await expect(panel.locator('.protocols .badge')).toHaveText(protocols);
    await expect(panel.getByRole('button')).toHaveCount(0);
    await expect(panel.getByRole('link')).toHaveCount(0);
    await panel.locator('.protocols .badge').first().click();
    await expect(page.getByRole('dialog')).toHaveCount(0);
    await page.mouse.move(0, 0);
    await row.getByRole('button', {name: serviceId, exact: true}).click();
    await expect(page.locator('.service-protocol-table')).toHaveCount(0);
    await tab(page, '服务信息');
    await page.reload();
    await expect(
        page.getByRole('navigation', {name: '服务详情'}).getByRole('button', {name: '服务信息', exact: true})
    ).toHaveClass('active');
    await expect(
        page.getByRole('navigation', {name: '服务详情'}).getByRole('button', {name: '示例', exact: true})
    ).toHaveCount(0);
    const details = page.getByRole('region', {name: '服务示例', exact: true});
    await expect(details.getByRole('tablist', {name: '服务协议'}).getByRole('tab')).toHaveText(protocols);
    const protocolRows = await details
        .getByRole('tab')
        .evaluateAll((items) => new Set(items.map((item) => item.getBoundingClientRect().top)).size);
    expect(protocolRows).toBe(4);
    const actionbar = await page.locator('.detail-tabbar').boundingBox();
    const table = await details.locator('.service-access').boundingBox();
    expect(table.x).toBe(actionbar.x);
    expect(table.width).toBe(actionbar.width);
    const facts = await page.locator('.service-info-panel').boundingBox();
    const methods = await page.locator('.service-methods').boundingBox();
    expect(table.y).toBeGreaterThanOrEqual(facts.y + facts.height);
    expect(methods.y).toBeGreaterThanOrEqual(table.y + table.height);
    const capabilities = await (await page.request.get('/api/examples/languages')).json();
    for (const protocol of protocols) {
        await details.getByRole('tab', {name: protocol, exact: true}).click();
        const row = details.locator('.example-language-links');
        await expect(row.getByRole('button')).toHaveText(exampleLanguageLabels);
        await expect(row.locator('button:not(:disabled)')).toHaveText(
            supportedExampleLanguageLabels(capabilities, protocol)
        );
    }
    await expect(details.locator('.access-endpoints')).toHaveCount(0);
    await expect(page.locator('.service-info-panel')).toBeVisible();
    await expect(details.getByRole('switch', {name: '启用服务', exact: true})).toHaveCount(0);
    await expect(page.getByRole('switch', {name: '启用服务', exact: true})).toBeVisible();
    await page.evaluate(() => window.scrollTo(0, 0));
    await page.screenshot({path: testInfo.outputPath('service-protocol-table.png'), fullPage: true});
});

test('cluster service information shows methods without instance descriptions', async ({
                                                                                           page
                                                                                       }, testInfo) => {
    await enter(page);
    const response = page.waitForResponse((response) => new URL(response.url()).pathname === '/api/service');
    await order(page);
    const service = await (await response).json();
    expect(service.info).not.toHaveProperty('type');
    expect(service).not.toHaveProperty('description');
    expect(service).not.toHaveProperty('descriptions');
    expect(service).not.toHaveProperty('endpoints');
    await expect(page.locator('.service-info-fields dt').filter({hasText: /^接口$/})).toHaveCount(0);
    const information = page.locator('.service-info-panel');
    await expect(information.getByRole('switch', {name: '启用服务'})).toBeEnabled();
    await expect(information.getByLabel('目标范围', {exact: true})).toHaveCount(0);
    await expect(page.getByRole('region', {name: '方法', exact: true})).toBeVisible();
    await page.reload();
    await expect(page.locator('.service-info-panel')).toBeVisible();
    await expect(page.getByRole('region', {name: '方法', exact: true})).toBeVisible();
    await page.screenshot({path: testInfo.outputPath('service-information.png'), fullPage: true});
});

test('instance service methods show documented nested types and recursive references', async ({
                                                                                                  page
                                                                                              }, testInfo) => {
    const description = {
        javaType: 'example.OrderService',
        extensions: {description: '订单服务说明'},
        methods: [
            {
                name: 'submit',
                javaSignature: 'submit(example.Order, java.util.List<example.Order>)',
                extensions: {description: '提交订单\n保留每一个明细项。'},
                parameters: [
                    {name: 'order', type: 'example.Order', extensions: {description: '待提交的订单'}},
                    {name: 'history', type: 'java.util.List<example.Order>'}
                ],
                returns: {type: 'example.Order'},
                exceptions: ['example.OrderException']
            },
            {name: 'ping', javaSignature: 'ping()', parameters: [], returns: {type: 'void'}}
        ],
        types: {
            'example.Order': {
                kind: 'object',
                properties: [
                    {name: 'id', type: 'java.lang.String', extensions: {description: '订单编号'}},
                    {name: 'state', type: 'example.State'},
                    {name: 'lines', type: 'example.Line[]'},
                    {name: 'attributes', type: 'java.util.Map<java.lang.String, example.Line>'},
                    {name: 'previous', type: 'example.Order'},
                    {name: 'owner', type: 'example.Owner'}
                ]
            },
            'example.Owner': {kind: 'object', properties: [{name: 'order', type: 'example.Order'}]},
            'example.Line': {kind: 'object', properties: [{name: 'quantity', type: 'int'}]},
            'example.Line[]': {kind: 'array', elementType: 'example.Line'},
            'java.util.List<example.Order>': {kind: 'collection', arguments: ['example.Order']},
            'java.util.Map<java.lang.String, example.Line>': {
                kind: 'map',
                arguments: ['java.lang.String', 'example.Line']
            },
            'example.State': {
                kind: 'enum',
                values: [{name: 'NEW', extensions: {description: '新建'}}, {name: 'DONE'}]
            }
        }
    };
    await page.route('**/api/service?*', async (route) => {
        const response = await route.fetch();
        const service = await response.json();
        if (new URL(route.request().url()).searchParams.has('instanceId')) service.description = description;
        await route.fulfill({response, json: service});
    });
    await enter(page);
    await order(page);
    await tab(page, '提供者');
    await page.locator('.member-table .instance-link').first().click();
    await tab(page, '服务信息');
    const methods = page.getByRole('region', {name: '方法', exact: true});
    const contract = methods.locator('.method-contract');
    await expect(contract).toHaveCount(1);
    await expect(methods).toContainText('订单服务说明');
    const method = contract.locator('.method-detail').first();
    await expect(methods.locator('.method-detail[open]')).toHaveCount(0);
    await expect(method.locator(':scope > summary > code')).toHaveText(
        'submit(example.Order, java.util.List<example.Order>)'
    );
    await method.locator(':scope > summary').click();
    await expect(method).toHaveAttribute('open', '');
    await expect(method).toContainText('提交订单');
    const parameters = method.getByLabel('请求结构');
    const orderFields = parameters.locator(':scope > .method-field').first();
    const lines = orderFields.getByText('lines', {exact: true}).locator('..').locator('..');
    await expect(lines).not.toHaveAttribute('open', '');
    await expect(lines.getByText('quantity', {exact: true})).not.toBeVisible();
    await lines.locator(':scope > summary').click();
    await lines.getByText('元素', {exact: true}).click();
    await expect(lines.getByText('quantity', {exact: true})).toBeVisible();
    const attributes = orderFields.getByText('attributes', {exact: true}).locator('..').locator('..');
    await expect(attributes).not.toHaveAttribute('open', '');
    await attributes.locator(':scope > summary').click();
    await attributes.getByText('值', {exact: true}).click();
    await expect(attributes.getByText('quantity', {exact: true})).toBeVisible();
    await lines.locator(':scope > summary').click();
    await expect(lines.getByText('quantity', {exact: true})).not.toBeVisible();
    await expect(attributes.getByText('quantity', {exact: true})).toBeVisible();
    await lines.locator(':scope > summary').click();
    const state = orderFields.getByText('state', {exact: true}).locator('..').locator('..');
    await expect(state.getByText('NEW', {exact: true})).not.toBeVisible();
    await state.locator(':scope > summary').click();
    await expect(state.getByText('NEW', {exact: true})).toBeVisible();
    // 自引用与相互引用只显示类型及递归标记，不提供继续展开入口。
    const previous = orderFields.getByText('previous', {exact: true}).locator('..');
    await expect(previous).toContainText('example.Order');
    await expect(previous).toContainText('递归引用');
    await expect(previous).toHaveJSProperty('tagName', 'DIV');
    const owner = orderFields.getByText('owner', {exact: true}).locator('..').locator('..');
    await owner.locator(':scope > summary').click();
    await expect(owner.locator('.method-field-name')).toHaveText(['owner', 'order']);
    await expect(owner.locator('.method-field-type')).toHaveText(['example.Owner', 'example.Order']);
    await expect(owner).toContainText('递归引用');
    await expect(owner.locator('details')).toHaveCount(0);
    const history = parameters.locator(':scope > .method-field').nth(1);
    await history.getByText('元素', {exact: true}).first().click();
    await expect(history.getByText('id', {exact: true})).toBeVisible();
    // 请求、响应独立折叠，键盘展开后保留字段自己的展开状态。
    const requests = method.locator('.method-inputs > .method-structure');
    const responses = method.locator('.method-returns > .method-structure');
    await responses.locator(':scope > summary').click();
    await expect(method.getByLabel('响应结构')).not.toBeVisible();
    await expect(lines.getByText('quantity', {exact: true})).toBeVisible();
    await requests.locator(':scope > summary').focus();
    await page.keyboard.press('Enter');
    await expect(parameters).not.toBeVisible();
    await page.keyboard.press('Enter');
    await expect(lines.getByText('quantity', {exact: true})).toBeVisible();
    await responses.locator(':scope > summary').click();
    // 每层结构都有缩进；同层叶子字段和可折叠对象的名称保持对齐。
    const requestLabel = await requests.locator(':scope > summary > span').boundingBox();
    const orderName = await orderFields
        .locator(':scope > .method-field-node > .method-field-heading > .method-field-name')
        .boundingBox();
    const linesName = await lines.locator(':scope > summary > .method-field-name').boundingBox();
    const elementName = await lines.getByText('元素', {exact: true}).boundingBox();
    const quantityName = await lines.getByText('quantity', {exact: true}).boundingBox();
    expect(orderName.x - requestLabel.x).toBeGreaterThanOrEqual(16);
    for (const [parent, child] of [
        [orderName, linesName],
        [linesName, elementName],
        [elementName, quantityName]
    ]) {
        expect(child.x - parent.x).toBeGreaterThanOrEqual(16);
        expect(child.x - parent.x).toBeLessThanOrEqual(24);
    }
    const idName = await orderFields.getByText('id', {exact: true}).boundingBox();
    const stateName = await state.locator(':scope > summary > .method-field-name').boundingBox();
    expect(idName.x).toBe(linesName.x);
    expect(stateName.x).toBe(linesName.x);
    const responseLabel = await responses.locator(':scope > summary > span').boundingBox();
    const responseIdName = await responses.getByText('id', {exact: true}).boundingBox();
    expect(responseIdName.x - responseLabel.x).toBeGreaterThanOrEqual(16);
    expect(responseIdName.x - responseLabel.x).toBeLessThanOrEqual(24);
    await expect(method.locator('.method-inputs .method-io-heading .method-field-type')).toHaveText([
        'order: example.Order',
        'history: java.util.List<example.Order>'
    ]);
    const resultHeading = method.locator('.method-returns .method-io-heading');
    await expect(resultHeading.locator('span')).toHaveText('响应');
    await expect(resultHeading.locator('code')).toHaveText('example.Order');
    const resultLabelBounds = await resultHeading.locator('span').boundingBox();
    const resultTypeBounds = await resultHeading.locator('code').boundingBox();
    expect(resultTypeBounds.x).toBeGreaterThan(resultLabelBounds.x + resultLabelBounds.width);
    expect(Math.abs(resultTypeBounds.y - resultLabelBounds.y)).toBeLessThanOrEqual(2);
    await expect(
        parameters.locator(
            ':scope > .method-field > .method-field-node > .method-field-heading > .method-field-name'
        )
    ).toHaveText(['order', 'history']);
    await expect(
        parameters.locator(
            ':scope > .method-field > .method-field-node > .method-field-heading > .method-field-type'
        )
    ).toHaveCount(0);
    const headerColor = await method
        .locator('.method-returns .method-io-heading .method-field-type')
        .evaluate((element) => getComputedStyle(element).color);
    const nestedColor = await parameters
        .locator('.method-field-children .method-field-type')
        .first()
        .evaluate((element) => getComputedStyle(element).color);
    expect(headerColor).toBe(nestedColor);
    await expect(parameters).toContainText('待提交的订单');
    await expect(parameters).toContainText('订单编号');
    await expect(parameters).toContainText('quantity');
    await expect(parameters).toContainText('新建');
    await expect(parameters).toContainText('键');
    await expect(parameters).toContainText('值');
    await expect(parameters).toContainText('递归引用');
    await expect(method.getByLabel('响应结构')).toContainText('quantity');
    const inputs = method.getByRole('region', {name: '方法请求', exact: true});
    const returns = method.getByRole('region', {name: '方法响应', exact: true});
    const inputBounds = await inputs.boundingBox();
    const returnBounds = await returns.boundingBox();
    expect(returnBounds.x).toBe(inputBounds.x);
    expect(returnBounds.y).toBeGreaterThanOrEqual(inputBounds.y + inputBounds.height);
    const leaf = parameters.locator('.method-field-heading').filter({hasText: 'quantity'}).first();
    expect(
        await leaf.evaluate((element) => element.closest('.method-field').getBoundingClientRect().height)
    ).toBeLessThanOrEqual(22);
    await expect(method).toContainText('example.OrderException');
    const other = contract.locator('.method-detail').nth(1);
    await expect(other).not.toHaveAttribute('open', '');
    await expect(other.locator(':scope > summary > code')).toHaveText('ping()');
    await other.locator(':scope > summary').click();
    await expect(other).toContainText('无参数');
    await expect(other).toContainText('无响应内容');
    await expect(page.locator('.service-info-panel')).toHaveCount(1);
    const actionbarBounds = await page.locator('.detail-tabbar').boundingBox();
    expect((await methods.boundingBox()).y).toBeGreaterThanOrEqual(actionbarBounds.y + actionbarBounds.height);
    await page.evaluate(() => window.scrollTo(0, 0));
    await page.screenshot({path: testInfo.outputPath('service-methods.png'), fullPage: true});
    await page.setViewportSize({width: 390, height: 900});
    const narrowInputs = await inputs.boundingBox();
    const narrowReturns = await returns.boundingBox();
    expect(narrowReturns.x).toBe(narrowInputs.x);
    expect(narrowReturns.y).toBeGreaterThanOrEqual(narrowInputs.y + narrowInputs.height);
    expect(await methods.evaluate((element) => element.scrollWidth - element.clientWidth)).toBeLessThanOrEqual(
        1
    );
    await page.evaluate(() => window.scrollTo(0, 0));
    await page.screenshot({path: testInfo.outputPath('service-methods-narrow.png'), fullPage: true});
});

test('instance service information displays the selected instance Java description', async ({page}) => {
    await enter(page);
    await order(page);
    const membersResponse = page.waitForResponse(
        (response) => new URL(response.url()).pathname === '/api/service/members'
    );
    await tab(page, '提供者');
    const members = (await (await membersResponse).json()).items;
    expect(members).toHaveLength(2);
    const firstJavaType = 'example.orders.PrimaryOrderApi';
    const secondJavaType = 'example.orders.ReplicaOrderApi';
    const descriptions = new Map([
        [members[0].instance.instanceId, firstJavaType],
        [members[1].instance.instanceId, secondJavaType]
    ]);

    // 两个真实成员共享 serviceId，但各自的响应声明不同的 Java 接口。
    await page.route('**/api/service?*', async (route) => {
        const instanceId = new URL(route.request().url()).searchParams.get('instanceId');
        const response = await route.fetch();
        const service = await response.json();
        if (descriptions.has(instanceId)) {
            service.description = {...service.description, javaType: descriptions.get(instanceId)};
        }
        await route.fulfill({response, json: service});
    });
    await page.locator('.member-table .instance-link').first().click();
    await expect(page.locator('.service-info-fields code')).toHaveText(firstJavaType);
    await tab(page, '服务信息');
    await expect(page.getByRole('region', {name: '方法'})).not.toContainText(firstJavaType);
    await expect(page.locator('.method-detail > summary > code')).toHaveText('find(java.lang.String)');
    await page.locator('.method-detail > summary').click();
    await expect(page.locator('.method-returns .method-io-heading .method-field-type')).toHaveText(
        'java.lang.String'
    );
    expect(new URLSearchParams(new URL(page.url()).hash.slice(1)).get('instanceId')).toBe(
        members[0].instance.instanceId
    );

    // 返回成员列表后打开另一个实例，接口随目标切换。
    await page.goBack();
    await page.goBack();
    await expect(page.locator('.member-table .instance-link')).toHaveCount(2);
    await page.locator('.member-table .instance-link').nth(1).click();
    await expect(page.locator('.service-info-fields code')).toHaveText(secondJavaType);
    await tab(page, '服务信息');
    await expect(page.getByRole('region', {name: '方法'})).not.toContainText(secondJavaType);
    await expect(page.locator('.method-detail > summary > code')).toHaveText('find(java.lang.String)');
    await page.locator('.method-detail > summary').click();
    await expect(page.locator('.method-returns .method-io-heading .method-field-type')).toHaveText(
        'java.lang.String'
    );
    expect(new URLSearchParams(new URL(page.url()).hash.slice(1)).get('instanceId')).toBe(
        members[1].instance.instanceId
    );
});

test('instance service information remains usable when its description is missing', async ({page}) => {
    await enter(page);
    await order(page);
    await tab(page, '提供者');

    // 描述缺失不影响已有服务摘要、身份和治理入口。
    await page.route('**/api/service?*', async (route) => {
        const response = await route.fetch();
        const service = await response.json();
        delete service.description;
        await route.fulfill({response, json: service});
    });
    const pageErrors = [];
    page.on('pageerror', (error) => pageErrors.push(error.message));
    await page.locator('.member-table .instance-link').first().click();
    await expect(page.locator('.service-info-fields')).toBeVisible();
    await expect(page.locator('.service-info-fields')).toContainText('提供者');
    await expect(page.locator('.service-info-fields dt').filter({hasText: /^接口$/})).toHaveCount(0);
    await expect(page.getByRole('switch', {name: '启用服务'})).toBeEnabled();
    await expect(page.getByRole('alert')).toHaveCount(0);
    expect(pageErrors).toEqual([]);
});

test('instance protocol tags use protocolName across connector schemes', async ({page}) => {
    await enter(page);
    const instancesResponse = page.waitForResponse(
        (response) => new URL(response.url()).pathname === '/api/instances'
    );
    await page.getByRole('button', {name: 'order-app', exact: true}).click();
    const instances = (await (await instancesResponse).json()).items;
    const provider = instances.find((instance) => instance.unitName === 'east');

    // 同一实例在两个连接器挂载 hprose，自定义地址 scheme 不改变协议身份。
    expect(provider).not.toHaveProperty('listenAddresses');
    expect(provider).not.toHaveProperty('managementAddress');
    expect(provider.endpoints).toHaveLength(3);
    const hproseEndpoints = provider.endpoints.filter((endpoint) => endpoint.protocolName === 'hprose');
    expect(hproseEndpoints).toHaveLength(2);
    expect(new Set(hproseEndpoints.map((endpoint) => new URL(endpoint.endpoints.endpoint).protocol))).toEqual(
        new Set(['hprose:', 'browser-hprose:'])
    );
    await page.getByLabel('筛选单元').selectOption('east');
    await expect(page.locator('tbody tr')).toHaveCount(1);
    await expect(page.locator('tbody .protocol-summary')).toHaveText('+2');
    await page.locator('tbody .protocol-summary').hover();
    await expect(page.locator('tbody .protocols .badge')).toHaveText(['rsf', 'hprose']);

    // 列表和卡片都显示去重后的数量，浮层列出原始协议名。
    await page.getByRole('button', {name: '切换为卡片'}).click();
    await expect(page.locator('.instance-card')).toHaveCount(1);
    await expect(page.locator('.instance-card .protocol-summary')).toHaveText('+2');
    await page.locator('.instance-card .protocol-summary').hover();
    await expect(page.locator('.instance-card .protocols .badge')).toHaveText(['rsf', 'hprose']);
});

test('instance protocol details retain every reported port and context path', async ({page}) => {
    await enter(page);
    await page.getByRole('button', {name: 'order-app', exact: true}).click();
    await page.getByLabel('筛选单元').selectOption('east');
    await expect(page.locator('tbody tr')).toHaveCount(1);
    const instanceResponse = page.waitForResponse(
        (response) => new URL(response.url()).pathname === '/api/instance'
    );
    await page.getByRole('button', {name: '详情', exact: true}).click();
    const {instance} = await (await instanceResponse).json();

    // 详情保留两个 hprose 连接器，不按协议名合并监听地址。
    const endpoints = instance.endpoints;
    expect(endpoints).toHaveLength(3);
    expect(
        endpoints.filter((endpoint) => endpoint.defaultProtocol).map((endpoint) => endpoint.protocolName)
    ).toEqual(['hprose', 'hprose']);
    await expect(page.getByRole('columnheader', {name: '管理 RPC 地址', exact: true})).toHaveCount(0);
    const managementAddresses = [
        ...new Set(endpoints.map((endpoint) => endpoint.endpoints.management).filter(Boolean))
    ];
    const endpointField = page.locator('.details-grid > div').filter({
        has: page.locator('dt', {hasText: /^管理 Endpoint$/})
    });
    await expect(endpointField.locator('dd code')).toHaveText(managementAddresses);
    await expect(page.locator('.endpoint-table thead th')).toHaveText(['协议', '家族', '端口', 'Context Path']);
    await expect(page.locator('.endpoint-table tbody tr')).toHaveCount(endpoints.length);
    for (const endpoint of endpoints) {
        const address = new URL(endpoint.endpoints.endpoint);
        const row = page
            .locator('.endpoint-table tbody tr')
            .filter({has: page.getByRole('cell', {name: address.port, exact: true})})
            .filter({has: page.getByRole('cell', {name: address.pathname || '—', exact: true})});
        await expect(row.locator('.protocols .badge')).toHaveText(endpoint.protocolName);
        const families = endpoint.protocolName === 'rsf' ? ['TCP'] : ['HTTP/1.1', 'HTTP/2'];
        expect(endpoint.protocolFamilies).toEqual(families);
        await expect(row.locator('.endpoint-family')).toHaveText(families.join('、'));
        await expect(row.locator('.protocols button')).toHaveCount(0);
        await row.locator('.protocols .badge').click();
        await expect(page.getByRole('dialog')).toHaveCount(0);
        await expect(row.locator('td').nth(2)).toHaveText(address.port);
        await expect(row.locator('td').nth(3)).toHaveText(address.pathname || '—');
        await expect(row.locator('td').first().locator('.endpoint-default')).toHaveCount(
            endpoint.defaultProtocol ? 1 : 0
        );
        await expect(row.locator('.endpoint-default')).toHaveCount(endpoint.defaultProtocol ? 1 : 0);
        await expect(row.locator('td').nth(2).locator('.badge')).toHaveCount(0);
    }
    const customEndpoint = endpoints.find(
        (endpoint) => new URL(endpoint.endpoints.endpoint).protocol === 'browser-hprose:'
    );
    expect(customEndpoint.protocolName).toBe('hprose');
    expect(customEndpoint.endpoints.endpoint).toMatch(/^browser-hprose:\/\//);
    expect(customEndpoint.endpoints).not.toHaveProperty('management');
    await page.setViewportSize({width: 390, height: 900});
    expect(
        await page
            .locator('.endpoint-section > .table-scroll')
            .evaluate((element) => element.scrollWidth - element.clientWidth)
    ).toBeLessThanOrEqual(1);
});

test('service endpoint default markers come from the selected provider report', async ({page}) => {
    await enter(page);
    await order(page);
    await tab(page, '提供者');
    await page.locator('.member-table tbody tr').filter({hasText: 'east'}).locator('.instance-link').click();
    await tab(page, '服务信息');
    const endpoints = page.getByRole('region', {name: '服务接入点'});
    const protocols = endpoints.getByRole('tablist', {name: '服务协议'});
    await expect(protocols.getByRole('tab')).toHaveText([/hprose.*默认/s, 'rsf']);
    await expect(protocols.getByRole('tab').first()).toHaveAttribute('aria-selected', 'true');
    await expect(protocols.locator('.muted')).toHaveCount(1);
    await expect(endpoints.locator('.access-endpoints [data-endpoint-type=endpoint]')).toHaveCount(2);
    await protocols.getByRole('tab', {name: 'rsf', exact: true}).click();
    await expect(endpoints.locator('.access-endpoints [data-endpoint-type=endpoint]')).toHaveCount(1);
});

test('instance protocols show ports and context paths without service addresses', async ({
                                                                                             page
                                                                                         }, testInfo) => {
    const customPath = '/api/%E4%B8%AD%E6%96%87/' + 'nested-path-'.repeat(8);
    await page.route('**/api/instance?*', async (route) => {
        const response = await route.fetch();
        const result = await response.json();
        result.instance.endpoints = [
            {
                protocolName: 'soap',
                protocolFamilies: ['HTTP/1.1', 'HTTP/2'],
                endpoints: {
                    wsdl: '',
                    http: 'https://example.test/soap',
                    xsd: '',
                    endpoint: 'soap://127.0.0.1:19210/soap'
                }
            },
            {
                protocolName: 'jsonrpc',
                protocolFamilies: ['HTTP/1.1', 'HTTP/2'],
                endpoints: {endpoint: 'jsonrpc://127.0.0.1:19211/jsonrpc', http: 'http://127.0.0.1:19211/jsonrpc'}
            },
            {protocolName: 'rsf', protocolFamilies: ['TCP'], endpoints: {endpoint: 'rsf://127.0.0.1:19200'}},
            {
                protocolName: 'oncrpc-udp',
                protocolFamilies: ['UDP'],
                endpoints: {endpoint: 'oncrpc-udp://127.0.0.1:19214'}
            },
            {
                protocolName: 'custom',
                endpoints: {
                    endpoint: `custom://[::1]:19212${customPath}`,
                    http: 'http://127.0.0.1:19212/api',
                    xsd: '',
                    openapi: ''
                }
            }
        ];
        await route.fulfill({response, json: result});
    });
    await enter(page);
    await page.getByRole('button', {name: 'order-app', exact: true}).click();
    await page.getByLabel('筛选单元').selectOption('east');
    await expect(page.locator('tbody tr')).toHaveCount(1);
    await page.getByRole('button', {name: '详情', exact: true}).click();
    const rows = page.locator('.endpoint-table tbody tr');
    await expect(rows).toHaveCount(5);
    await expect(rows.locator('.protocols .badge')).toHaveText([
        'custom',
        'jsonrpc',
        'oncrpc-udp',
        'rsf',
        'soap'
    ]);
    await expect(page.locator('.endpoint-table thead th')).toHaveText(['协议', '家族', '端口', 'Context Path']);
    // 缺失家族的旧记录显示占位符，不从协议名或 URI 猜测。
    await expect(rows.locator('.endpoint-family')).toHaveText([
        '—',
        'HTTP/1.1、HTTP/2',
        'UDP',
        'TCP',
        'HTTP/1.1、HTTP/2'
    ]);
    await expect(rows.locator('td:nth-child(3)')).toHaveText(['19212', '19211', '19214', '19200', '19210']);
    await expect(rows.locator('td:nth-child(4)')).toHaveText([customPath, '/jsonrpc', '—', '—', '/soap']);
    await expect(page.locator('.instance-info-panel .endpoint-table')).toBeVisible();
    await expect(page.getByRole('heading', {name: 'Endpoint', exact: true})).toHaveCount(0);
    await expect(rows.locator('td:nth-child(3) .badge')).toHaveCount(0);
    await expect(rows.getByRole('link')).toHaveCount(0);
    await expect(rows.locator('[data-endpoint-type]')).toHaveCount(0);
    await expect(page.locator('.endpoint-table')).not.toContainText('://');
    await expect(page.getByText('Orders?wsdl', {exact: false})).toHaveCount(0);
    await expect(rows.getByRole('button')).toHaveCount(0);
    await expect(rows.first().locator('td').first()).toHaveText('custom');
    const longPath = rows.first().locator('.endpoint-context-path');
    await expect(longPath).toHaveCSS('white-space', 'normal');
    for (const width of [1440, 1100, 390]) {
        await page.setViewportSize({width, height: 900});
        const tableViewport = page.locator('.endpoint-section > .table-scroll');
        expect(
            await tableViewport.evaluate((element) => element.scrollWidth - element.clientWidth)
        ).toBeLessThanOrEqual(1);
        expect(
            await longPath.evaluate((element) => element.scrollWidth - element.clientWidth)
        ).toBeLessThanOrEqual(1);
        const heights = await rows.evaluateAll((items) =>
            items.map((item) => item.getBoundingClientRect().height)
        );
        expect(Math.min(...heights)).toBeGreaterThanOrEqual(36);
        const sidebar = await page.locator('.instance-sidebar').boundingBox();
        const endpoints = await page.locator('.endpoint-section').boundingBox();
        if (width > 1000) {
            expect(sidebar.x + sidebar.width).toBeLessThan(endpoints.x);
            expect(Math.abs(sidebar.y - endpoints.y)).toBeLessThanOrEqual(1);
            const instanceId = page
                .locator('.instance-facts > div')
                .filter({
                    has: page.locator('dt', {hasText: /^实例 ID$/})
                })
                .locator('dd');
            const lines = await instanceId.evaluate(
                (element) => element.getBoundingClientRect().height / parseFloat(getComputedStyle(element).lineHeight)
            );
            expect(lines).toBeLessThanOrEqual(1.1);
            const spareWidth = await instanceId.evaluate((element) => {
                const text = document.createRange();
                text.selectNodeContents(element);
                return element.clientWidth - text.getBoundingClientRect().width;
            });
            expect(spareWidth).toBeGreaterThanOrEqual(-1);
            expect(spareWidth).toBeLessThanOrEqual(8);
        } else {
            expect(endpoints.y).toBeGreaterThan(sidebar.y + sidebar.height);
        }
        expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(width);
        await page.screenshot({path: testInfo.outputPath(`http-endpoints-${width}.png`), fullPage: true});
    }
});

test('HTTP service endpoints show only the selected service contract addresses', async ({
                                                                                            page
                                                                                        }, testInfo) => {
    await page.context().grantPermissions(['clipboard-read', 'clipboard-write']);
    // 类型名称来自控制台设置中的注册中心枚举定义，未知扩展类型仍按原始 key 展示。
    await page.route('**/api/settings?*', async (route) => {
        const response = await route.fetch();
        const settings = await response.json();
        const types = settings.endpointTypes;
        expect(types.map((type) => type.key)).toEqual(['endpoint', 'http', 'management', 'schema']);
        expect(types.find((type) => type.key === 'endpoint').label).toBe('SDK');
        expect(types.find((type) => type.key === 'schema').label).toBe('SCHEMA');
        types.find((type) => type.key === 'schema').label = '服务契约';
        await route.fulfill({response, json: settings});
    });
    await page.route('**/api/service?*', async (route) => {
        const response = await route.fetch();
        const result = await response.json();
        if (new URL(route.request().url()).searchParams.has('instanceId')) {
            result.endpoints = [
                {
                    protocolName: 'soap',
                    transport: 'http',
                    defaultProtocol: true,
                    contextPath: '/soap/orders/2.0',
                    listenAddress: 'soap://127.0.0.1:19210/soap',
                    endpoints: {
                        soap: {
                            endpoint: 'soap://127.0.0.1:19210/soap',
                            http: 'https://example.test/soap/orders/2.0/Orders',
                            schema: 'https://example.test/soap/orders/2.0/Orders?wsdl',
                            xsd: '',
                            openapi: ''
                        }
                    }
                }
            ];
        }
        await route.fulfill({response, json: result});
    });
    await enter(page);
    await order(page);
    await expect(
        page.getByRole('navigation', {name: '服务详情'}).getByRole('button', {name: '接入点', exact: true})
    ).toHaveCount(0);
    await tab(page, '提供者');
    await page.locator('.member-table .instance-link').first().click();
    const endpoints = page.getByRole('region', {name: '服务接入点'});
    await expect(endpoints).toBeVisible();
    await expect(page.getByRole('navigation', {name: '服务详情'}).getByRole('button')).toHaveText([
        '服务信息',
        '流量控制',
        '路由策略',
        '元信息'
    ]);
    const scope = new URLSearchParams(new URL(page.url()).hash.slice(1));
    await tab(page, '服务信息');
    await page.reload();
    await expect(
        page.getByRole('navigation', {name: '服务详情'}).getByRole('button', {name: '服务信息', exact: true})
    ).toHaveClass('active');
    expect(new URLSearchParams(new URL(page.url()).hash.slice(1)).get('instanceId')).toBe(
        scope.get('instanceId')
    );
    await expect(page.locator('.service-info-panel')).toBeVisible();
    await page.getByRole('button', {name: '刷新', exact: true}).click();
    await endpoints.getByRole('tab', {name: 'soap', exact: false}).click();
    await expect(endpoints).toContainText('https://example.test/soap/orders/2.0/Orders');
    await expect(endpoints.locator('[data-endpoint-type=endpoint] code')).toHaveText(
        'soap://127.0.0.1:19210/soap'
    );
    await expect(endpoints.locator('[data-endpoint-type=schema] .badge')).toHaveText('服务契约');
    await expect(endpoints.locator('.access-protocols .muted')).toHaveText('（默认）');
    await expect(endpoints.locator('.access-protocols .muted')).toHaveCount(1);
    await expect(endpoints.locator('.access-endpoints .badge')).toHaveText([
        'SDK',
        'HTTP',
        '服务契约',
        'openapi',
        'xsd'
    ]);
    const schemaLink = endpoints.locator('[data-endpoint-type=schema]').getByRole('link');
    await expect(endpoints.getByRole('link')).toHaveCount(1);
    await expect(schemaLink).toHaveAttribute('href', 'https://example.test/soap/orders/2.0/Orders?wsdl');
    await expect(schemaLink).toHaveAttribute('target', '_blank');
    await expect(schemaLink).toHaveAttribute('rel', 'noopener noreferrer');
    const http = await endpoints.locator('[data-endpoint-type=http]').boundingBox();
    const schema = await endpoints.locator('[data-endpoint-type=schema]').boundingBox();
    expect(schema.y).toBeGreaterThanOrEqual(http.y + http.height);
    for (const [type, address] of [
        ['endpoint', 'soap://127.0.0.1:19210/soap'],
        ['http', 'https://example.test/soap/orders/2.0/Orders']
    ]) {
        const copy = endpoints.locator(`[data-endpoint-type=${type}]`).getByRole('button');
        await copy.click();
        await expect.poll(() => page.evaluate(() => navigator.clipboard.readText())).toBe(address);
        await expect(copy).toHaveAttribute('title', '已复制地址');
        await expect(copy.locator('code')).toHaveCSS('user-select', 'text');
    }
    await page
        .context()
        .route('https://example.test/soap/orders/2.0/Orders?wsdl', (route) =>
            route.fulfill({contentType: 'text/plain', body: 'schema document'})
        );
    const opened = page.waitForEvent('popup');
    await schemaLink.locator('svg').click();
    const schemaPage = await opened;
    await expect(schemaPage).toHaveURL('https://example.test/soap/orders/2.0/Orders?wsdl');
    await expect(schemaPage.locator('body')).toHaveText('schema document');
    await schemaPage.close();
    for (const width of [1440, 1100, 390]) {
        await page.setViewportSize({width, height: 900});
        const icon = await schemaLink.locator('svg').evaluate((element) => {
            const bounds = element.getBoundingClientRect();
            return {
                width: bounds.width,
                height: bounds.height,
                fontSize: parseFloat(getComputedStyle(element.parentElement).fontSize)
            };
        });
        expect(icon.width).toBe(icon.height);
        expect(icon.height).toBeCloseTo(icon.fontSize, 1);
        const entries = await endpoints.locator('[data-endpoint-type]').evaluateAll((items) =>
            items.map((item) => {
                const row = item.getBoundingClientRect();
                const tag = item.querySelector('.badge').getBoundingClientRect();
                return {y: row.y, height: row.height, tagWidth: tag.width, tagBottom: tag.bottom};
            })
        );
        expect(new Set(entries.map((entry) => entry.tagWidth)).size).toBe(1);
        for (let index = 1; index < entries.length; index++) {
            expect(entries[index].y - entries[index - 1].y).toBe(entries[index - 1].height + 4);
            expect(entries[index].y - entries[index - 1].tagBottom).toBeGreaterThanOrEqual(4);
        }
        expect(
            await endpoints
                .locator('.service-access')
                .evaluate((element) => element.scrollWidth - element.clientWidth)
        ).toBeLessThanOrEqual(1);
        const marker = endpoints.locator('.access-protocols .muted');
        expect(
            await marker.evaluate((element) => {
                const bounds = element.getBoundingClientRect();
                const cell = element.closest('button').getBoundingClientRect();
                return bounds.left >= cell.left && bounds.right <= cell.right;
            })
        ).toBe(true);
        expect(
            await endpoints.locator('[data-endpoint-type=endpoint] code').evaluate((element) => element.clientWidth)
        ).toBeGreaterThan(0);
        for (const address of await endpoints.locator('.endpoint-entry code').all()) {
            await expect(address).toHaveCSS('white-space', 'normal');
            expect(
                await address.evaluate((element) => element.scrollWidth - element.clientWidth)
            ).toBeLessThanOrEqual(1);
        }
        await page.screenshot({path: testInfo.outputPath(`service-endpoints-${width}.png`), fullPage: true});
        if (width === 1440) {
            await endpoints
                .locator('.access-endpoints')
                .screenshot({path: testInfo.outputPath('endpoint-tag-spacing.png')});
        }
    }
    await page.evaluate(() => {
        Object.defineProperty(navigator.clipboard, 'writeText', {
            configurable: true,
            value: async () => {
                throw new Error('clipboard unavailable');
            }
        });
    });
    await endpoints.locator('[data-endpoint-type=http]').getByRole('button').click();
    await expect(endpoints.getByRole('alert')).toHaveText('无法自动复制，请选择地址文本手动复制。');
    await tab(page, '服务信息');
    await expect(page.locator('.service-info-panel')).toBeVisible();
    await expect(endpoints).toBeVisible();
});

const exampleLanguageDefinitions = [
    {id: 'java', label: 'Meteor'},
    {id: 'java-sdk', label: 'Java RPC SDK'},
    {id: 'python', label: 'Python'},
    {id: 'javascript', label: 'Node.js'},
    {id: 'go', label: 'Go'},
    {id: 'c', label: 'C'},
    {id: 'cpp', label: 'C++'},
    {id: 'browser', label: 'JavaScript'},
    {id: 'shell', label: 'Shell'}
];
const exampleLanguageLabels = exampleLanguageDefinitions.map((item) => item.label);
const exampleRoleLabels = ['提供者', '消费者'];

function exampleLanguages(protocols) {
    return exampleLanguageDefinitions.map((item) => ({...item, protocols: protocols[item.id] || []}));
}

function supportedExampleLanguageLabels(capabilities, protocol) {
    const supported = new Set(
        capabilities.filter((item) => item.protocols.includes(protocol)).map((item) => item.id)
    );
    return exampleLanguageDefinitions.filter((item) => supported.has(item.id)).map((item) => item.label);
}

test('language entry links open one Markdown document with only role tabs', async ({page}) => {
    await page.route('**/api/examples/languages?*', (route) =>
        route.fulfill({json: exampleLanguages({java: ['rsf'], python: ['rsf'], javascript: ['rsf']})})
    );
    await page.route('**/api/examples/targets?*', (route) =>
        route.fulfill({
            json: {
                targets: [
                    {
                        id: 'provider-a',
                        serviceId: new URL(route.request().url()).searchParams.get('serviceId'),
                        instanceId: 'provider-a',
                        application: 'order-app',
                        unit: 'east',
                        description: {
                            types: {DebugMessage: {javaType: 'net.hasor.meteor.example.api.DebugMessage'}}
                        },
                        endpoint: {protocolName: 'rsf', contextPath: '/orders'}
                    }
                ],
                warnings: []
            }
        })
    );
    await page.route('**/api/examples/render?*', (route) =>
        route.fulfill({
            json: {
                languages: exampleLanguages({java: ['rsf'], python: ['rsf'], javascript: ['rsf']}),
                documents: [
                    {
                        id: 'java-direct',
                        language: 'Java',
                        role: 'direct',
                        markdown: [
                            '# Java 直连',
                            '## 依赖',
                            '```xml',
                            '<dependency>meteor-sdk</dependency>',
                            '```',
                            '## 代码',
                            '```java',
                            'class ConsumerMain { /* <contract> */ }',
                            '```',
                            '<script>window.exampleInjected = true</script>',
                            '<img src=x onerror="window.exampleInjected = true">',
                            '[危险链接](javascript:window.exampleInjected=true)'
                        ].join('\n')
                    },
                    {
                        id: 'java-provider',
                        language: 'Java',
                        role: 'provider',
                        markdown: [
                            '```java',
                            'import net.hasor.meteor.annotation.*;',
                            '',
                            'import java.util.List;',
                            '',
                            'public interface StatusService {',
                            '    net.hasor.meteor.example.api.DebugMessage status(net.hasor.meteor.example.api.DebugMessage request);',
                            '}',
                            '```',
                            '',
                            '```java',
                            'var arguments = java.util.List.of(new net.hasor.meteor.example.api.DebugMessage());',
                            'var wireName = "java.lang.String";',
                            '```'
                        ].join('\n')
                    },
                    {
                        id: 'java-registry',
                        language: 'Java',
                        role: 'registry',
                        markdown: '```java\nclass RegistryConsumerMain {}\n```'
                    },
                    {
                        id: 'python-direct',
                        language: 'Python',
                        role: 'direct',
                        markdown: '# Python 直连\n\n```python\nprint("orders")\n```'
                    },
                    {
                        id: 'node-direct',
                        language: 'Node.js',
                        role: 'direct',
                        markdown:
                            '# Node.js 直连\n\n```json\n{"dependencies":{"rpc-sdk":"1.0.0"}}\n```\n\n```javascript\nconsole.log("orders");\n```'
                    }
                ],
                warnings: []
            }
        })
    );
    const {trigger, scope} = await protocolExampleEntry(page, {id: 'service-examples'});
    const origin = page.url();
    const request = page.waitForRequest((item) => new URL(item.url()).pathname === '/api/examples/targets');
    await trigger.click();
    expect(new URL((await request).url()).searchParams.get('serviceId')).toBeTruthy();
    expect(page.url()).toBe(origin);
    const drawer = page.getByRole('dialog', {name: /^rsf /});
    await expect(drawer).toBeVisible();
    await expect(drawer.getByRole('tablist', {name: '示例语言'})).toHaveCount(0);
    const roles = drawer.getByRole('tablist', {name: '调用角色'});
    const document = drawer.getByRole('tabpanel', {name: '调用示例文档'});
    await expect(roles.getByRole('tab')).toHaveText([...exampleRoleLabels, '使用注册中心']);
    await expect(document.getByRole('heading', {name: 'Java 直连', exact: true})).toBeVisible();
    await expect(document.locator('pre code.language-java')).toHaveText(
        'class ConsumerMain { /* <contract> */ }\n'
    );
    await expect(document.locator('pre code.language-java .hljs-keyword')).toHaveText('class');
    await expect(document.locator('pre code.language-xml')).toContainText(
        '<dependency>meteor-sdk</dependency>'
    );
    await expect(drawer.getByLabel('示例项目')).toHaveCount(0);
    await expect(drawer.getByLabel('示例文件')).toHaveCount(0);
    await expect(drawer.getByRole('button', {name: /下载|复制文件|复制命令/})).toHaveCount(0);
    await expect(drawer.getByRole('heading', {name: /运行命令|说明/})).toHaveCount(0);

    // 模板不能执行 HTML 或 JavaScript 链接。
    await expect(drawer.getByRole('button', {name: '复制代码', exact: true})).toHaveCount(0);
    await expect(document.locator('script, img, a[href^="javascript:"]')).toHaveCount(0);
    expect(await page.evaluate(() => window.exampleInjected)).toBeUndefined();

    await roles.getByRole('tab', {name: '提供者', exact: true}).click();
    const provider = document.locator('pre code.language-java');
    await expect(provider).toHaveText([
        'public interface StatusService {\n    DebugMessage status(DebugMessage request);\n}\n',
        'var arguments = List.of(new DebugMessage());\nvar wireName = "java.lang.String";\n'
    ]);
    await roles.getByRole('tab', {name: '消费者', exact: true}).click();
    await expect(document.locator('pre code.language-java')).toHaveText(
        'class ConsumerMain { /* <contract> */ }\n'
    );
    // 角色选项卡保留键盘切换，并靠左排列。
    await roles.getByRole('tab', {name: '消费者', exact: true}).focus();
    await page.keyboard.press('Home');
    await expect(roles.getByRole('tab', {name: '提供者', exact: true})).toBeFocused();
    await page.keyboard.press('ArrowRight');
    await expect(roles.getByRole('tab', {name: '消费者', exact: true})).toBeFocused();
    await page.keyboard.press('ArrowRight');
    await expect(roles.getByRole('tab', {name: '使用注册中心', exact: true})).toBeFocused();
    await expect(document.locator('pre code.language-java')).toHaveText('class RegistryConsumerMain {}\n');
    await page.keyboard.press('Escape');
    await expect(drawer).toHaveCount(0);
    // 语言在表格中选择，抽屉不再提供另一套语言切换。
    await expect(trigger).toBeFocused();
    const protocol = page.locator('.access-examples .example-language-links');
    await expect(protocol.getByRole('button')).toHaveText(exampleLanguageLabels);
    await expect(protocol.locator('button:not(:disabled)')).toHaveText(['Meteor', 'Python', 'Node.js']);
    for (const [label, codeLanguage, expected] of [
        ['Python', 'python', 'print("orders")'],
        ['Node.js', 'javascript', 'console.log("orders");']
    ]) {
        const link = protocol.getByRole('button', {name: label, exact: true});
        await link.click();
        await expect(drawer.getByRole('tablist', {name: '示例语言'})).toHaveCount(0);
        await expect(roles.getByRole('tab', {name: '消费者', exact: true})).toHaveAttribute(
            'aria-selected',
            'true'
        );
        await expect(roles.getByRole('tab', {name: '提供者', exact: true})).toBeDisabled();
        await expect(document.locator(`pre code.language-${codeLanguage}`)).toHaveText(`${expected}\n`);
        await page.keyboard.press('Escape');
        await expect(link).toBeFocused();
    }
});

test('protocol examples stay on the requested service without a service selector', async ({page}) => {
    await page.route('**/api/examples/targets?*', (route) => {
        const serviceId = new URL(route.request().url()).searchParams.get('serviceId');
        return route.fulfill({
            json: {
                targets: ['first', 'second'].map((id) => ({
                    id,
                    serviceId,
                    instanceId: id,
                    endpoint: {protocolName: 'rsf'}
                })),
                warnings: []
            }
        });
    });
    await page.route('**/api/examples/render?*', (route) =>
        route.fulfill({
            json: {
                languages: exampleLanguages({java: ['rsf']}),
                documents: [
                    {
                        id: 'java-direct',
                        language: 'Java',
                        role: 'direct',
                        markdown: '```java\nclass ConsumerMain {}\n```'
                    }
                ],
                warnings: []
            }
        })
    );
    const {trigger, scope} = await protocolExampleEntry(page, {id: 'service-examples'});
    const rendered = page.waitForRequest(
        (request) => new URL(request.url()).pathname === '/api/examples/render'
    );
    await trigger.click();
    expect(new URL((await rendered).url()).searchParams.get('serviceId')).toBe(scope.serviceId);
    const drawer = page.getByRole('dialog');
    await expect(drawer.locator('.markdown-document')).toBeVisible();
    await expect(drawer.locator('.examples-service-id')).toHaveText(scope.serviceId);
    await expect(drawer.locator('select')).toHaveCount(0);
    await expect(drawer.locator('.examples-address')).toHaveCount(0);
});

test('example table disables unsupported languages, aligns columns and keeps role tabs left aligned', async ({
                                                                                                                 page
                                                                                                             }, testInfo) => {
    await page.route('**/api/service?*', async (route) => {
        const response = await route.fetch();
        const result = await response.json();
        result.info.protocols = ['dubbo', 'jsonrpc', 'unknown'];
        await route.fulfill({response, json: result});
    });
    const capabilities = exampleLanguages(
        Object.fromEntries(
            exampleLanguageDefinitions.map((item) => [
                item.id,
                ['java', 'java-sdk', 'javascript', 'go'].includes(item.id) ? ['dubbo', 'jsonrpc'] : ['jsonrpc']
            ])
        )
    );
    await page.route('**/api/examples/languages?*', (route) => route.fulfill({json: capabilities}));
    await page.route('**/api/examples/targets?*', (route) =>
        route.fulfill({
            json: {
                targets: [
                    {
                        id: 'dubbo-provider',
                        serviceId: new URL(route.request().url()).searchParams.get('serviceId'),
                        instanceId: 'dubbo-provider',
                        endpoint: {protocolName: 'dubbo'}
                    }
                ],
                warnings: []
            }
        })
    );
    await page.route('**/api/examples/render?*', (route) =>
        route.fulfill({
            json: {
                languages: capabilities,
                documents: [
                    {
                        id: 'java-direct',
                        language: 'Java',
                        role: 'direct',
                        markdown: '```java\nclass ConsumerMain {}\n```'
                    },
                    {
                        id: 'java-sdk-direct',
                        language: 'java-sdk',
                        role: 'direct',
                        markdown: '```java\nJsonRpcHttpClient client;\n```'
                    }
                ],
                warnings: []
            }
        })
    );
    await enter(page);
    await order(page);
    await tab(page, '服务信息');
    const protocolTabs = page.getByRole('tablist', {name: '服务协议'});
    await protocolTabs.getByRole('tab', {name: 'dubbo', exact: true}).click();
    const dubbo = page.locator('.access-examples .example-language-links');
    await expect(dubbo.getByRole('button')).toHaveText(exampleLanguageLabels);
    await expect(dubbo.locator('button:not(:disabled)')).toHaveText([
        'Meteor',
        'Java RPC SDK',
        'Node.js',
        'Go'
    ]);
    for (const label of ['Python', 'C++', 'JavaScript', 'Shell', 'C']) {
        await expect(dubbo.getByRole('button', {name: label, exact: true})).toBeDisabled();
    }
    const bounds = {};
    for (const label of exampleLanguageLabels) {
        bounds[label] = await dubbo.getByRole('button', {name: label, exact: true}).boundingBox();
    }
    await protocolTabs.getByRole('tab', {name: 'jsonrpc', exact: true}).click();
    const links = page.locator('.access-examples .example-language-links');
    await expect(links.getByRole('button')).toHaveText(exampleLanguageLabels);
    await expect(links.locator('button:disabled')).toHaveCount(0);
    for (const label of exampleLanguageLabels) {
        const current = await links.getByRole('button', {name: label, exact: true}).boundingBox();
        expect(current.x).toBe(bounds[label].x);
        expect(current.width).toBe(bounds[label].width);
    }
    await protocolTabs.getByRole('tab', {name: 'unknown', exact: true}).click();
    await expect(links.getByRole('button')).toHaveText(exampleLanguageLabels);
    await expect(links.locator('button:disabled')).toHaveCount(exampleLanguageDefinitions.length);
    await protocolTabs.getByRole('tab', {name: 'dubbo', exact: true}).click();
    const enabledColor = await dubbo
        .getByRole('button', {name: 'Meteor', exact: true})
        .evaluate((item) => getComputedStyle(item).color);
    const disabledColor = await dubbo
        .getByRole('button', {name: 'Python', exact: true})
        .evaluate((item) => getComputedStyle(item).color);
    expect(disabledColor).not.toBe(enabledColor);
    await dubbo.getByRole('button', {name: 'Meteor', exact: true}).focus();
    await page.keyboard.press('Tab');
    await expect(dubbo.getByRole('button', {name: 'Java RPC SDK', exact: true})).toBeFocused();
    await page.screenshot({path: testInfo.outputPath('example-language-links.png'), fullPage: true});
    await dubbo.getByRole('button', {name: 'Meteor', exact: true}).click();
    const drawer = page.getByRole('dialog', {name: /^dubbo /});
    const roles = drawer.getByRole('tablist', {name: '调用角色'});
    for (const width of [1920, 1280, 390]) {
        await page.setViewportSize({width, height: 900});
        await expect(drawer.getByRole('tablist', {name: '示例语言'})).toHaveCount(0);
        await expect(roles.getByRole('tab')).toHaveText(exampleRoleLabels);
        const roleBounds = await roles.boundingBox();
        const body = await drawer.locator('.examples-body').boundingBox();
        expect(roleBounds.x - body.x).toBeLessThanOrEqual(24);
        expect(roleBounds.x + roleBounds.width).toBeLessThanOrEqual(width);
        await page.screenshot({path: testInfo.outputPath(`example-role-tabs-${width}.png`), fullPage: true});
    }
    await page.keyboard.press('Escape');
    await dubbo.getByRole('button', {name: 'Java RPC SDK', exact: true}).click();
    await expect(drawer.locator('pre code')).toContainText('JsonRpcHttpClient');
    await expect(roles.getByRole('tab', {name: '提供者', exact: true})).toBeDisabled();
    await page.keyboard.press('Escape');
    await dubbo.getByRole('button', {name: 'Go', exact: true}).click();
    await expect(drawer.getByText('当前语言的示例待补充', {exact: true})).toBeVisible();
    await expect(drawer.locator('pre code')).toHaveCount(0);
    await expect(roles.getByRole('tab', {name: '提供者', exact: true})).toBeDisabled();
});

test('service protocol examples render one Markdown document from real registry data', async ({
                                                                                                  page
                                                                                              }, testInfo) => {
    await enter(page);
    await order(page);
    await tab(page, '服务信息');
    await page
        .getByRole('tablist', {name: '服务协议'})
        .getByRole('tab', {name: 'rsf', exact: true})
        .click();
    const protocol = page.locator('.access-examples .example-language-links');
    const targetsResponse = page.waitForResponse(
        (response) => new URL(response.url()).pathname === '/api/examples/targets'
    );
    const renderResponse = page.waitForResponse(
        (response) => new URL(response.url()).pathname === '/api/examples/render'
    );
    await protocol.getByRole('button', {name: 'Meteor', exact: true}).click();
    const targets = await (await targetsResponse).json();
    await testInfo.attach('protocol-examples-targets', {
        body: JSON.stringify(targets, null, 2),
        contentType: 'application/json'
    });
    expect(targets.targets.length).toBeGreaterThan(0);
    const generated = await (await renderResponse).json();
    await testInfo.attach('protocol-examples-render', {
        body: JSON.stringify(generated, null, 2),
        contentType: 'application/json'
    });
    expect(generated).not.toHaveProperty('projects');
    const java = generated.documents.find(
        (item) => item.language.toLowerCase() === 'java' && item.role === 'direct'
    );
    expect(java).toBeTruthy();
    const binaryType = 'net.hasor.meteor.test.scenario.ConsoleBrowserFixture$OrderService';
    expect(targets.targets[0].description.javaType).toBe(binaryType);
    expect(java.markdown).toContain('var binding = context.publisher().rsfService(OrderService.class).register()');
    expect(java.markdown).toContain('\npublic interface OrderService {');
    const drawer = page.getByRole('dialog', {name: /^rsf /});
    const document = drawer.getByRole('tabpanel', {name: '调用示例文档'});
    await expect(drawer.getByRole('tablist', {name: '示例语言'})).toHaveCount(0);
    await expect(drawer.getByRole('tab', {name: '消费者', exact: true})).toHaveAttribute(
        'aria-selected',
        'true'
    );
    expect(new URL((await renderResponse).url()).searchParams.get('language')).toBe('java');
    await expect(document.locator('pre code')).toHaveCount(3);
    await expect(document.locator('pre code').first()).toHaveClass(/language-groovy/);
    await expect(document.locator('pre code.language-java').first()).toContainText('public interface');
    await expect(document).toContainText('net.hasor:meteor:');
    expect(new URL((await renderResponse).url()).searchParams.get('address')).toBeNull();
    expect(java.markdown).toContain('SERVICE_ADDRESS');
    await expect(document.locator('pre code.language-java').last()).toContainText('SERVICE_ADDRESS');
    await expect(document.locator('pre code.language-java .hljs-keyword')).not.toHaveCount(0);
    const roles = drawer.getByRole('tablist', {name: '调用角色'});
    await expect(roles.getByRole('tab').first()).toHaveText('接口定义');
    await expect(roles.getByRole('tab', {name: '接口描述符', exact: true})).toHaveCount(0);
    await roles.getByRole('tab', {name: '接口定义', exact: true}).click();
    await expect(document.locator('pre code')).toHaveCount(1);
    await expect(document.locator('pre code')).toContainText('public interface OrderService');
    await expect(document).not.toContainText('MetConfiguration');
    await roles.getByRole('tab', {name: '消费者', exact: true}).click();
    const screenshot = testInfo.outputPath('protocol-examples-drawer.png');
    await page.screenshot({path: screenshot, fullPage: true});
    await testInfo.attach('protocol-examples-drawer', {path: screenshot, contentType: 'image/png'});
    await expect(drawer.getByRole('button', {name: /下载/})).toHaveCount(0);
    await expect(drawer.getByLabel('示例文件')).toHaveCount(0);
    await page.mouse.click(8, 100);
    await expect(drawer).toHaveCount(0);
});

test('Meteor and native Java SDK example entries use separate real documents', async ({page}) => {
    await enter(page);
    await order(page);
    await tab(page, '服务信息');
    await page
        .getByRole('tablist', {name: '服务协议'})
        .getByRole('tab', {name: 'hprose', exact: true})
        .click();
    const protocol = page.locator('.access-examples .example-language-links');
    await expect(protocol.getByRole('button')).toHaveText(exampleLanguageLabels);
    await protocol.getByRole('button', {name: 'Meteor', exact: true}).click();
    const drawer = page.getByRole('dialog', {name: /^hprose /});
    await expect(drawer.locator('pre code.language-java').last()).toContainText('MetConfiguration');
    await expect(drawer.getByRole('tab', {name: '提供者', exact: true})).toBeEnabled();
    await page.keyboard.press('Escape');

    await protocol.getByRole('button', {name: 'Java RPC SDK', exact: true}).click();
    await expect(drawer.locator('pre code.language-java')).toContainText('HproseHttpClient');
    await expect(drawer.locator('pre code.language-java')).not.toContainText('import ');
    await expect(drawer.locator('pre code.language-java')).not.toContainText('net.hasor.meteor');
    await expect(drawer.getByRole('tab', {name: '提供者', exact: true})).toBeDisabled();
    await expect(drawer.getByRole('tablist', {name: '示例语言'})).toHaveCount(0);
    await expect(drawer.getByRole('tablist', {name: '调用角色'}).getByRole('tab')).toHaveText(
        ['接口定义', ...exampleRoleLabels]
    );
});

test('native wire examples show named editable input before the call', async ({page}) => {
    await enter(page);
    await order(page);
    await tab(page, '服务信息');
    await page.getByRole('tablist', {name: '服务协议'}).getByRole('tab', {name: 'hprose', exact: true}).click();
    await page.locator('.access-examples .example-language-links')
        .getByRole('button', {name: 'Shell', exact: true}).click();
    const drawer = page.getByRole('dialog', {name: /^hprose /});
    const blocks = drawer.locator('pre');
    await expect(blocks).toHaveCount(4);
    await expect(blocks.nth(0).locator('.markdown-code-file')).toHaveText('arguments.json');
    await expect(blocks.nth(0)).toContainText('"example"');
    await expect(blocks.nth(1).locator('.markdown-code-file')).toHaveText('request.py');
    await expect(blocks.nth(2)).toContainText('python3 request.py');
    await expect(blocks.nth(3)).toContainText('--data-binary @request.bin');
    await expect(drawer).not.toContainText('Base64');
});

test('native Java SDK shows the request alongside consumer code and in example files', async ({page}) => {
    await enter(page);
    await order(page);
    await tab(page, '服务信息');
    await page.getByRole('tablist', {name: '服务协议'}).getByRole('tab', {name: 'hprose', exact: true}).click();
    const requestJson = '```json request.json\n{"value":"example"}\n```';
    await page.route('**/api/examples/render?*', (route) => route.fulfill({
        json: {
            interfaceDefinition: '```java\ninterface EchoService {\n    String echo(String value);\n    String check();\n}\n```',
            interfaceDescriptor: '```protobuf service.proto\nservice EchoService {}\n```',
            documents: [
                {
                    id: 'java-sdk-direct',
                    language: 'java-sdk',
                    role: 'direct',
                    markdown: requestJson + '\n\n```java\nvar result = client.unaryBlocking(request, Map.of(), method).execute();\n```'
                },
                {
                    id: 'java-sdk-files',
                    language: 'java-sdk',
                    role: 'files',
                    markdown: [
                        '```json service.json',
                        '{"file":[]}',
                        '```',
                        '',
                        requestJson,
                        '',
                        '```java ProtobufCodec.java',
                        'final class ProtobufCodec {}',
                        '```'
                    ].join('\n')
                }
            ]
        }
    }));
    await page.locator('.access-examples .example-language-links')
        .getByRole('button', {name: 'Java RPC SDK', exact: true}).click();
    const drawer = page.getByRole('dialog', {name: /^hprose /});
    const roles = drawer.getByRole('tablist', {name: '调用角色'});
    const consumer = roles.getByRole('tab', {name: '消费者', exact: true});
    const files = roles.getByRole('tab', {name: '示例文件', exact: true});
    const document = drawer.getByRole('tabpanel', {name: '调用示例文档'});

    await expect(roles.getByRole('tab')).toHaveText(['接口定义', '接口描述符', ...exampleRoleLabels, '示例文件']);
    await expect(consumer).toHaveAttribute('aria-selected', 'true');
    await roles.getByRole('tab', {name: '接口定义', exact: true}).click();
    await expect(document.locator('pre code')).toContainText('String check();');
    await page.keyboard.press('ArrowRight');
    await expect(roles.getByRole('tab', {name: '接口描述符', exact: true})).toBeFocused();
    await expect(document.locator('.markdown-code-file')).toHaveText(['service.proto']);
    await expect(document.locator('pre code')).toContainText('service EchoService');
    await consumer.click();
    await expect(document.locator('.markdown-code-file')).toHaveText(['request.json']);
    await expect(document.locator('pre code')).toHaveText([
        '{"value":"example"}', 'var result = client.unaryBlocking(request, Map.of(), method).execute();'
    ]);
    await consumer.focus();
    await page.keyboard.press('ArrowRight');
    await expect(files).toBeFocused();
    await expect(files).toHaveAttribute('aria-selected', 'true');
    await expect(document.locator('.markdown-code-file')).toHaveText([
        'service.json', 'request.json', 'ProtobufCodec.java'
    ]);
    await expect(document).not.toContainText('unaryBlocking');
    await consumer.click();
    await expect(document.locator('.markdown-code-file')).toHaveText(['request.json']);
});

test('instance protocol badges are display only in lists and cards', async ({page}) => {
    await enter(page);
    await page.getByRole('button', {name: 'order-app', exact: true}).click();
    await page.getByLabel('筛选单元').selectOption('east');
    await expect(page.locator('tbody tr')).toHaveCount(1);
    await page.locator('tbody .protocol-summary').hover();
    const protocols = page.locator('tbody .protocols');
    await expect(protocols.locator('.badge')).not.toHaveCount(0);
    await expect(protocols.getByRole('button')).toHaveCount(0);
    await protocols.locator('.badge').first().click();
    await expect(page.getByRole('dialog')).toHaveCount(0);
    await page.getByRole('button', {name: '切换为卡片'}).click();
    await page.locator('.instance-card .protocol-summary').hover();
    await expect(page.locator('.instance-card .protocols .badge')).not.toHaveCount(0);
    await expect(page.locator('.instance-card .protocols button')).toHaveCount(0);
});

const protocolExampleEntries = [{id: 'service-examples', name: '服务信息示例'}];

async function protocolExampleEntry(page, entry) {
    if (entry.id.endsWith('-service')) {
        if (entry.id === 'direct-service') {
            await login(page);
            await page.locator('.cluster-select').click();
            await page.locator('.cluster-menu').getByRole('button', {name: '直连实例', exact: true}).click();
            await page.getByLabel('实例 Endpoint', {exact: true}).fill('rsf://127.0.0.1:18767');
            await page.getByLabel('管理凭据', {exact: true}).fill('browser-direct-令牌+secret');
            await page.getByRole('button', {name: '连接', exact: true}).click();
        } else {
            await enter(page);
            await page.getByRole('button', {name: 'order-app', exact: true}).click();
            await page.getByLabel('筛选单元').selectOption('east');
            await expect(page.locator('tbody tr')).toHaveCount(1);
            await page.getByRole('button', {name: '详情', exact: true}).click();
        }
        await expect(page.getByRole('navigation', {name: '实例详情'})).toBeVisible();
        await page.getByRole('button', {name: '服务清单', exact: true}).click();
        return;
    }
    await enter(page);
    await order(page);
    const route = new URLSearchParams(new URL(page.url()).hash.slice(1));
    const protocol = 'rsf';
    await tab(page, '服务信息');
    await page
        .getByRole('tablist', {name: '服务协议'})
        .getByRole('tab', {name: protocol, exact: true})
        .click();
    const trigger = page
        .locator('.example-language-links')
        .getByRole('button', {name: 'Meteor', exact: true});
    return {
        trigger,
        scope: {
            mode: 'registry',
            clusterId: route.get('clusterId'),
            target: null,
            instanceId: null,
            serviceId: route.get('serviceId'),
            protocol,
            contextPath: null,
            address: null
        }
    };
}

test('instance service method debugging preserves its method and provider on refresh', async ({
                                                                                                  page
                                                                                              }, testInfo) => {
    // 调试契约把另一个重载排在前面，入口必须按签名选择，不能依赖方法下标。
    await page.route('**/api/service/debug?*', async (route) => {
        if (route.request().method() !== 'GET') return route.continue();
        const response = await route.fetch();
        const result = await response.json();
        const descriptions = new Set([result.description, ...result.endpoints.map((item) => item.description)]);
        for (const description of descriptions) {
            if (description) {
                description.methods.unshift({
                    name: 'find',
                    javaSignature: 'find()',
                    parameters: [],
                    returns: {type: 'java.lang.String'}
                });
            }
        }
        await route.fulfill({response, json: result});
    });
    await enter(page);
    const directoryResponse = page.waitForResponse(
        (response) => new URL(response.url()).pathname === '/api/services'
    );
    await page
        .getByRole('navigation', {name: '服务治理'})
        .getByRole('link', {name: '服务目录', exact: true})
        .click();
    const services = (await (await directoryResponse).json()).items;
    const serviceId = services.find((service) => service.info.name.includes('OrderService')).info.serviceId;
    const row = page.locator('.service-directory-table tbody tr').filter({hasText: serviceId});
    await expect(
        page.locator('.service-directory-table').getByRole('button', {name: '调试', exact: true})
    ).toHaveCount(0);
    await expect(page.locator('.service-directory-table').getByRole('columnheader')).toHaveText([
        '服务 ID',
        '支持协议',
        '提供者',
        '消费者',
        '状态'
    ]);
    await row.getByRole('button', {name: serviceId, exact: true}).click();
    await tab(page, '提供者');
    const providerRows = page.locator('.member-table tbody tr');
    const unit = (await providerRows.first().locator('td').nth(3).textContent()).trim();
    await providerRows.first().locator('.instance-link').click();
    const instanceId = new URLSearchParams(new URL(page.url()).hash.slice(1)).get('instanceId');
    await tab(page, '服务信息');
    const summary = page.locator('.service-methods .method-detail > summary');
    const debugButton = summary.getByRole('button', {name: '调试', exact: true});
    const summaryBounds = await summary.boundingBox();
    const buttonBounds = await debugButton.boundingBox();
    expect(summaryBounds.x + summaryBounds.width - buttonBounds.x - buttonBounds.width).toBeLessThanOrEqual(14);
    await page.screenshot({path: testInfo.outputPath('method-debug-entry.png'), fullPage: true});
    await debugButton.focus();
    await page.keyboard.press('Enter');
    expect(new URLSearchParams(new URL(page.url()).hash.slice(1)).get('method')).toBe('find(java.lang.String)');
    await expect(
        page.getByRole('navigation', {name: '服务详情'}).getByRole('button', {name: '调试', exact: true})
    ).toHaveCount(0);
    await expect(page.getByRole('dialog')).toHaveCount(0);
    await expect(page.getByRole('combobox', {name: '选择提供者'})).toHaveCount(0);
    const request = page.waitForResponse(
        (response) =>
            new URL(response.url()).pathname === '/api/service/debug' && response.request().method() === 'GET'
    );
    await page.reload();
    const described = await request;
    expect(new URL(described.url()).searchParams.get('instanceId')).toBe(instanceId);
    expect(new URLSearchParams(new URL(page.url()).hash.slice(1)).get('instanceId')).toBe(instanceId);
    await expect(page.locator('.debug-request-title')).toContainText('find(java.lang.String)');
    await expect(page.getByLabel('调试方法')).toHaveCount(0);
    await expect(page.locator('.debug-argument-control')).toHaveValue('');
    const protocols = page.getByRole('group', {name: '调试协议'});
    const choices = protocols.getByRole('button');
    await page.locator('.debug-argument-control').fill('keep-input');
    if ((await choices.count()) > 1) {
        await choices.nth(1).click();
        await expect(page.locator('.debug-request-title')).toContainText('find(java.lang.String)');
        await expect(page.locator('.debug-argument-control')).toHaveValue('keep-input');
        await choices.first().click();
    }
    await page.locator('.debug-argument-control').fill('directory-debug');
    const called = page.waitForResponse(
        (response) =>
            new URL(response.url()).pathname === '/api/service/debug' && response.request().method() === 'POST'
    );
    await invokeButton(page).click();
    const response = await called;
    expect(response.request().postDataJSON().method).toBe('find');
    expect(new URL(response.url()).searchParams.get('instanceId')).toBe(instanceId);
    const result = await response.json();
    expect(result.success, JSON.stringify(result)).toBe(true);
    expect(result.value).toBe(unit === 'east' ? 'order:directory-debug' : 'replica:directory-debug');
    await expect(page.locator('.debug-response')).toContainText(result.value);
    for (const width of [1360, 390]) {
        await page.setViewportSize({width, height: 900});
        await page.evaluate(() => window.scrollTo(0, 0));
        await expect
            .poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth))
            .toBe(true);
        await page.screenshot({
            path: testInfo.outputPath(`directory-provider-debug-${width}.png`),
            fullPage: true
        });
    }
});

test('service method debugging never substitutes an unsupported method', async ({page}) => {
    await enter(page);
    await order(page);
    const route = new URLSearchParams(new URL(page.url()).hash.slice(1));
    await tab(page, '提供者');
    await page.locator('.member-table tbody tr').first().locator('.instance-link').click();
    const instanceId = new URLSearchParams(new URL(page.url()).hash.slice(1)).get('instanceId');
    route.set('instanceId', instanceId);
    route.set('tab', 'debug');
    route.set('method', 'find(long)');
    await page.goto(`/#${route}`);
    await expect(page.getByRole('alert')).toContainText('不支持方法 find(long)');
    await expect(invokeButton(page)).toBeDisabled();
    await expect(page.getByLabel('调试方法')).toHaveCount(0);
    // 同一服务内只改变方法签名，前进和后退也必须切换工作区，不能继续使用旧方法。
    route.set('method', 'find(java.lang.String)');
    await page.goto(`/#${route}`);
    await expect(page.locator('.debug-request-title')).toContainText('find(java.lang.String)');
    await expect(page.getByRole('alert')).toHaveCount(0);
    await expect(invokeButton(page)).toBeEnabled();
    await page.goBack();
    await expect(page.getByRole('alert')).toContainText('不支持方法 find(long)');
    await expect(invokeButton(page)).toBeDisabled();
    await page.goForward();
    await expect(page.locator('.debug-request-title')).toContainText('find(java.lang.String)');
    await expect(page.getByRole('alert')).toHaveCount(0);
    await expect(invokeButton(page)).toBeEnabled();
});

test('direct service method debugging retains its fixed target', async ({page}) => {
    await protocolExampleEntry(page, {id: 'direct-service'});
    const row = page.locator('.instance-services-table tbody tr').first();
    await row.locator('td').first().getByRole('button').click();
    const target = new URLSearchParams(new URL(page.url()).hash.slice(1)).get('target');
    await tab(page, '服务信息');
    await page.locator('.service-methods').getByRole('button', {name: '调试', exact: true}).click();
    await expect(
        page.getByRole('navigation', {name: '服务详情'}).getByRole('button', {name: '调试', exact: true})
    ).toHaveCount(0);
    expect(new URLSearchParams(new URL(page.url()).hash.slice(1)).get('target')).toBe(target);
    await expect(page.getByRole('combobox', {name: '选择提供者'})).toHaveCount(0);
    await page.locator('.debug-argument-control').fill('direct-method-debug');
    await invokeButton(page).click();
    await expect(page.getByRole('region', {name: '响应结果'})).toContainText('成功');
    await expect(page.locator('.debug-response')).toHaveText('8');
    await expect(page.locator('.debug-result-heading')).toContainText('成功');
    await expect(page.locator('.debug-single-result .debug-result-detail')).toHaveCount(1);
    await tab(page, '服务信息');
    await expect(page.locator('.service-methods')).toBeVisible();
    expect(new URLSearchParams(new URL(page.url()).hash.slice(1)).get('target')).toBe(target);
});

test('method debugging has no tab entry and adds a child breadcrumb', async ({page}) => {
    await enter(page);
    await order(page);
    const tabs = page.getByRole('navigation', {name: '服务详情'});
    await expect(tabs.getByRole('button', {name: '调试', exact: true})).toHaveCount(0);
    await tab(page, '提供者');
    await expect(
        page.locator('.member-table').getByRole('columnheader', {name: '操作', exact: true})
    ).toHaveCount(0);
    await expect(page.locator('.member-table').getByRole('button', {name: '调试', exact: true})).toHaveCount(
        0
    );
    await page.locator('.member-table tbody tr').first().locator('.instance-link').click();
    await expect(tabs.getByRole('button', {name: '调试', exact: true})).toHaveCount(0);
    await expect(page.locator('.service-methods')).toBeVisible();
    const breadcrumbs = page.getByRole('navigation', {name: '面包屑导航'});
    const parent = await breadcrumbs.textContent();
    await page.locator('.service-methods').getByRole('button', {name: '调试', exact: true}).click();
    await expect(breadcrumbs).toContainText('调试(find)');
    await expect(page.locator('.debug-argument-control')).toBeVisible();
    await page.reload();
    await expect(breadcrumbs).toContainText('调试(find)');
    await breadcrumbs.locator('.instance-primary').click();
    await expect(breadcrumbs).toHaveText(parent);
    await expect(page.locator('.service-methods')).toBeVisible();
});

for (const entry of ['catalogue', 'instance-service', 'direct-service']) {
    test(`debug links without a method return to the method list: ${entry}`, async ({page}) => {
        if (entry === 'catalogue') {
            await enter(page);
            await order(page);
        } else {
            await protocolExampleEntry(page, {id: entry});
            await page
                .locator('.instance-services-table tbody tr')
                .first()
                .locator('td')
                .first()
                .getByRole('button')
                .click();
        }
        const scope = new URLSearchParams(new URL(page.url()).hash.slice(1));
        scope.set('tab', 'debug');
        scope.delete('method');
        const debugRequests = [];
        page.on('request', (request) => {
            if (new URL(request.url()).pathname === '/api/service/debug') debugRequests.push(request.url());
        });
        await page.goto(`/#${scope}`);
        // 直连凭据只在当前页面会话中保留；注册中心链接可直接刷新。
        if (entry !== 'direct-service') await page.reload();
        const methods = page.getByRole('region', {name: '方法', exact: true});
        const method = methods.locator('.method-detail').first();
        const button = method.getByRole('button', {name: '调试', exact: true});
        await expect(button).toBeVisible();
        await expect(page.getByRole('button', {name: '服务信息', exact: true})).toHaveClass('active');
        await expect(page.getByRole('button', {name: '选择方法', exact: true})).toHaveCount(0);
        await expect(page.getByText('请选择要调试的方法。', {exact: true})).toHaveCount(0);
        await expect(page.locator('.debug-workbench')).toHaveCount(0);
        expect(debugRequests).toEqual([]);
        const signature = await method.locator('.method-signature').getAttribute('title');
        await button.click();
        await expect(page.locator('.debug-request-title')).toContainText(signature);
        await expect(invokeButton(page)).toBeEnabled();
        const debugRoute = new URLSearchParams(new URL(page.url()).hash.slice(1));
        expect(debugRoute.get('method')).toBe(signature);
        for (const key of ['clusterId', 'instanceId', 'serviceId', 'target']) {
            expect(debugRoute.get(key)).toBe(scope.get(key));
        }
        if (entry === 'direct-service') {
            await page.reload();
            await expect(page.getByRole('alert')).toHaveText('请返回直连页面填写目标实例的管理凭据。');
            await expect(page.getByRole('button', {name: '选择方法', exact: true})).toHaveCount(0);
            await expect(page.locator('.debug-workbench')).toHaveCount(0);
        }
    });
}

test('instance method debugging can retry an unavailable contract', async ({page}) => {
    await enter(page);
    await order(page);
    await tab(page, '提供者');
    await page.locator('.member-table tbody tr').first().locator('.instance-link').click();
    await page.route('**/api/service/debug?*', (route) =>
        route.fulfill({
            status: 503,
            json: {error: '提供者暂时不可用'}
        })
    );
    await tab(page, '服务信息');
    await page.locator('.service-methods').getByRole('button', {name: '调试', exact: true}).click();
    await expect(page.getByRole('alert')).toContainText('提供者暂时不可用');
    await expect(invokeButton(page)).toHaveCount(0);
    await page.unroute('**/api/service/debug?*');
    await page.getByRole('button', {name: '刷新', exact: true}).click();
    await expect(page.getByRole('alert')).toHaveCount(0);
    await expect(invokeButton(page)).toBeEnabled();
});

test('provider debugging calls only the selected provider and survives refresh', async ({
                                                                                            page
                                                                                        }, testInfo) => {
    const browserErrors = [];
    const memberRequests = [];
    page.on('request', (request) => {
        if (new URL(request.url()).pathname === '/api/service/members') memberRequests.push(request.url());
    });
    page.on('pageerror', (error) => browserErrors.push(error.message));
    await page.setViewportSize({width: 1488, height: 1056});
    await enter(page);
    await order(page);
    const serviceId = new URLSearchParams(new URL(page.url()).hash.slice(1)).get('serviceId');
    await tab(page, '提供者');
    await expect(page.getByRole('columnheader', {name: '操作', exact: true})).toHaveCount(0);
    const rows = page.locator('.member-table tbody tr');
    for (const index of [0, 1]) {
        const row = rows.nth(index);
        const unit = (await row.locator('td').nth(3).textContent()).trim();
        await row.locator('.instance-link').click();
        await tab(page, '服务信息');
        memberRequests.length = 0;
        await page.locator('.service-methods').getByRole('button', {name: '调试', exact: true}).click();
        await expect(
            page.getByRole('navigation', {name: '服务详情'}).getByRole('button', {name: '调试', exact: true})
        ).toHaveCount(0);
        await expect(page.getByRole('combobox', {name: '选择提供者'})).toHaveCount(0);
        await expect(page.getByRole('region', {name: '基本信息'})).toContainText(serviceId);
        await expect(invokeButton(page)).toBeEnabled();
        const debugResponse = page.waitForResponse(
            (response) =>
                new URL(response.url()).pathname === '/api/service/debug' && response.request().method() === 'GET'
        );
        await page.reload();
        const debug = await (await debugResponse).json();
        expect(JSON.stringify(debug)).not.toContain('"connectorName"');
        expect(JSON.stringify(debug)).not.toContain('"configuredAddress"');
        expect(JSON.stringify(debug)).not.toContain('"bindEnabled"');
        const fixedProvider = page.getByRole('table', {name: '调试提供者', exact: true}).getByRole('radio');
        await expect(fixedProvider).toHaveCount(1);
        await expect(fixedProvider).toBeChecked();
        await expect(fixedProvider).toBeDisabled();
        await expect(page.getByLabel('搜索调试提供者')).toHaveCount(0);
        await expect(page.getByRole('region', {name: '基本信息'})).toContainText('调用方法');
        expect(memberRequests).toEqual([]);
        const protocols = page.getByRole('group', {name: '调试协议', exact: true});
        const choices = protocols.getByRole('button');
        await expect(choices).toHaveText([...new Set(debug.endpoints.map((item) => item.endpoint.protocolName))]);
        await expect(choices.first()).toHaveAttribute('aria-pressed', 'true');
        if ((await choices.count()) > 1) {
            await choices.nth(1).click();
            await expect(choices.nth(1)).toHaveAttribute('aria-pressed', 'true');
            await choices.first().click();
        }
        await expect(page.getByLabel('调试方法')).toHaveCount(0);
        await expect(page.locator('.debug-request-title')).toContainText('find(java.lang.String)');
        const settings = await page.getByRole('complementary', {name: '调用设置'}).boundingBox();
        const editor = await page.getByRole('region', {name: '请求编辑'}).boundingBox();
        const resultPanel = await page.getByRole('region', {name: '响应结果'}).boundingBox();
        const protocolList = await protocols.boundingBox();
        const timeoutInput = await page.getByLabel('调试超时').boundingBox();
        expect(timeoutInput.y).toBeGreaterThan(protocolList.y + protocolList.height);
        expect(editor.x).toBeGreaterThan(settings.x + settings.width);
        expect(resultPanel.y).toBeGreaterThan(editor.y + editor.height);

        await page.locator('.debug-argument-control').fill('debug-42');
        const called = page.waitForResponse(
            (response) =>
                new URL(response.url()).pathname === '/api/service/debug' && response.request().method() === 'POST'
        );
        await invokeButton(page).click();
        const response = await called;
        expect(response.status()).toBe(200);
        const result = await response.json();
        expect(result.success, JSON.stringify(result)).toBe(true);
        expect(result.value).toBe(unit === 'east' ? 'order:debug-42' : 'replica:debug-42');
        await expect(page.locator('.debug-response')).toContainText(result.value);
        await page.evaluate(() => window.scrollTo(0, 0));
        expect(browserErrors).toEqual([]);
        await page.screenshot({path: testInfo.outputPath(`debug-${index}.png`), fullPage: true});
        if (unit === 'east') {
            await page.locator('.debug-argument-control').fill('debug-fail');
            await invokeButton(page).click();
            await expect(page.locator('.debug-response')).toContainText('debug-fail');
            await expect(page.getByRole('region', {name: '响应结果'})).toContainText('失败');
        }

        await page.locator('.breadcrumbs').getByRole('button', {name: serviceId, exact: true}).click();
        await tab(page, '提供者');
        await expect(
            page.getByRole('navigation', {name: '服务详情'}).getByRole('button', {name: '提供者', exact: true})
        ).toHaveClass('active');
    }
    await tab(page, '消费者');
    await expect(page.locator('.member-table').getByRole('button', {name: '调试', exact: true})).toHaveCount(
        0
    );
});

test('instance service list has no debug actions and retains scoped service links', async ({page}) => {
    // 同一实例既发布订单服务又订阅库存服务，两类记录均只保留服务详情入口。
    await enter(page);
    await page.getByRole('button', {name: 'order-app', exact: true}).click();
    await page.getByLabel('筛选单元').selectOption('west');
    await expect(page.locator('tbody tr')).toHaveCount(1);
    await page.getByRole('button', {name: '详情', exact: true}).click();
    await expect(page.getByRole('navigation', {name: '实例详情'})).toBeVisible();
    const instanceRoute = new URLSearchParams(new URL(page.url()).hash.slice(1));
    await page.getByRole('button', {name: '服务清单', exact: true}).click();
    const table = page.locator('.instance-services-table');
    await expect(table.getByRole('columnheader')).toHaveText(['服务 ID', '支持协议', '身份']);
    await expect(table.getByRole('button', {name: '调试', exact: true})).toHaveCount(0);
    await expect(table.locator('tbody tr')).toHaveCount(2);
    for (const role of ['提供者', '消费者']) {
        const row = table
            .locator('tbody tr')
            .filter({has: page.locator('td', {hasText: new RegExp(`^${role}$`)})});
        await expect(row.locator('td').nth(2)).toHaveText(role);
        const serviceId = (await row.locator('td').first().textContent()).trim();
        await row.getByRole('button', {name: serviceId, exact: true}).click();
        await expect(page.locator('.service-info-fields')).toContainText(role);
        const route = new URLSearchParams(new URL(page.url()).hash.slice(1));
        expect(route.get('view')).toBe('service');
        expect(route.get('instanceId')).toBe(instanceRoute.get('instanceId'));
        expect(route.get('clusterId')).toBe(instanceRoute.get('clusterId'));
        expect(route.get('serviceId')).toBe(serviceId);
        await tab(page, '服务信息');
        const methodDebug = page.locator('.service-methods').getByRole('button', {name: '调试', exact: true});
        if (role === '提供者') {
            await methodDebug.click();
            await expect(
                page.getByRole('navigation', {name: '服务详情'}).getByRole('button', {name: '调试', exact: true})
            ).toHaveCount(0);
            await expect(page.getByRole('combobox', {name: '选择提供者'})).toHaveCount(0);
            const debugRoute = new URLSearchParams(new URL(page.url()).hash.slice(1));
            expect(debugRoute.get('instanceId')).toBe(instanceRoute.get('instanceId'));
            expect(debugRoute.get('serviceId')).toBe(serviceId);
            expect(debugRoute.get('method')).toBe('find(java.lang.String)');
            await page.goBack();
            await expect(methodDebug).toBeVisible();
        } else {
            await expect(methodDebug).toHaveCount(0);
            await expect(
                page.getByRole('navigation', {name: '服务详情'}).getByRole('button', {name: '调试', exact: true})
            ).toHaveCount(0);
        }
        await page.goBack();
        await expect(page.locator('.service-info-fields')).toBeVisible();
        await page.goBack();
        await expect(table.locator('tbody tr')).toHaveCount(2);
        await expect(table.getByRole('columnheader', {name: '操作', exact: true})).toHaveCount(0);
    }
});

test('direct instance service list has no debug actions and retains its service target', async ({page}) => {
    await protocolExampleEntry(page, {id: 'direct-service'});
    await page.mouse.move(0, 0);
    const before = new URLSearchParams(new URL(page.url()).hash.slice(1));
    const table = page.locator('.instance-services-table');
    await expect(table.getByRole('columnheader')).toHaveText(['服务 ID', '支持协议', '身份']);
    await expect(table.getByRole('button', {name: '调试', exact: true})).toHaveCount(0);
    const row = table.locator('tbody tr').first();
    await expect(row.locator('td').nth(2)).toHaveText('提供者');
    const serviceId = (await row.locator('td').first().textContent()).trim();
    await row.getByRole('button', {name: serviceId, exact: true}).click();
    await expect(page.locator('.service-info-fields')).toContainText('提供者');
    await expect(page.locator('.breadcrumbs').getByRole('button').first()).toHaveText('直连实例');
    await expect(page.locator('.breadcrumbs strong')).toHaveText(serviceId);
    const route = new URLSearchParams(new URL(page.url()).hash.slice(1));
    expect(route.get('view')).toBe('service');
    expect(route.get('target')).toBe(before.get('target'));
    expect(route.get('serviceId')).toBe(serviceId);
    await expect(page.getByRole('region', {name: '服务接入点'})).toBeVisible();
    await tab(page, '服务信息');
    await expect(
        page.getByRole('region', {name: '服务接入点'}).locator('.access-protocols button')
    ).not.toHaveCount(0);
    expect(new URLSearchParams(new URL(page.url()).hash.slice(1)).get('target')).toBe(before.get('target'));
    await tab(page, '服务信息');
    await expect(page.getByRole('region', {name: '服务接入点'})).toBeVisible();
});

for (const entry of [
    {id: 'instance-service', name: '注册中心实例'},
    {id: 'direct-service', name: '直连实例'}
]) {
    test(`instance protocol summary is read-only: ${entry.name}`, async ({page}, testInfo) => {
        const protocols = [
            'connect',
            'dubbo',
            'grpc',
            'grpc-web',
            'hessian',
            'hprose',
            'jsonrpc',
            'oncrpc',
            'oncrpc-udp',
            'rsf',
            'soap',
            'thrift',
            'thrift-http',
            'triple',
            'xmlrpc'
        ];
        await page.route('**/api/services?*', async (route) => {
            const response = await route.fetch();
            const result = await response.json();
            for (const item of result.items) item.protocols = protocols;
            await route.fulfill({response, json: result});
        });
        await protocolExampleEntry(page, entry);
        const row = page.locator('.instance-services-table tbody tr').first();
        const summary = row.locator('.protocol-summary');
        const panel = row.locator('.protocol-popover');
        await expect(summary).toHaveText('+15');
        for (const width of [1920, 1360]) {
            await page.setViewportSize({width, height: 900});
            const cellBounds = await row.locator('td').nth(1).boundingBox();
            const summaryBounds = await summary.boundingBox();
            expect(cellBounds.x + cellBounds.width - summaryBounds.x - summaryBounds.width).toBeLessThanOrEqual(24);
        }
        await summary.hover();
        await expect(panel).toBeVisible();
        await expect(panel.locator('.protocols .badge')).toHaveText(protocols);
        await expect(panel.getByRole('button')).toHaveCount(0);
        await expect(panel.getByRole('link')).toHaveCount(0);
        const before = await row.boundingBox();
        const soap = panel.locator('.protocols .badge').filter({hasText: /^soap$/});
        await soap.hover();
        await soap.click();
        await expect(page.getByRole('dialog')).toHaveCount(0);
        await expect(panel).toBeVisible();
        await page.screenshot({path: testInfo.outputPath('protocol-popover.png')});
        await page.mouse.move(0, 0);
        await expect(panel).not.toBeVisible();
        expect((await row.boundingBox()).height).toBe(before.height);
        await summary.focus();
        await expect(panel).toBeVisible();
        await page.keyboard.press('Escape');
        await expect(panel).not.toBeVisible();
        await summary.click();
        await expect(panel).toBeVisible();
        await page.getByRole('button', {name: '刷新', exact: true}).click();
        await expect(panel).not.toBeVisible();
    });
}

for (const entry of protocolExampleEntries) {
    test(`all protocol example entries use Markdown: ${entry.name}`, async ({page}) => {
        await page.setViewportSize({width: 1920, height: 1080});
        const {trigger, consumerId, scope} = await protocolExampleEntry(page, entry);
        const originalUrl = page.url();
        const targetsResponse = page.waitForResponse(
            (response) => new URL(response.url()).pathname === '/api/examples/targets'
        );
        const renderResponse = page.waitForResponse(
            (response) => new URL(response.url()).pathname === '/api/examples/render'
        );
        await trigger.click();
        const targetResponse = await targetsResponse;
        expect(targetResponse.status()).toBe(200);
        const requested = new URL(targetResponse.url()).searchParams;
        for (const [key, value] of Object.entries(scope)) expect(requested.get(key), key).toBe(value);
        const {targets} = await targetResponse.json();
        expect(targets.length).toBeGreaterThan(0);
        for (const target of targets) {
            if (scope.instanceId) expect(target.instanceId).toBe(scope.instanceId);
            if (scope.serviceId) expect(target.serviceId).toBe(scope.serviceId);
            if (consumerId) expect(target.instanceId).not.toBe(consumerId);
            expect(target.endpoint.protocolName).toBe(scope.protocol);
            expect(target.endpoint).not.toHaveProperty('connectorName');
            if (scope.contextPath) expect(target.endpoint.contextPath).toBe(scope.contextPath);
        }
        const generatedResponse = await renderResponse;
        expect(generatedResponse.status()).toBe(200);
        const generatedScope = new URL(generatedResponse.url()).searchParams;
        const selected = targets[0];
        expect(generatedScope.get('instanceId')).toBe(selected.instanceId);
        expect(generatedScope.get('serviceId')).toBe(selected.serviceId);
        expect(generatedScope.get('endpointAddress')).toBe(selected.address);
        expect(generatedScope.has('connectorName')).toBe(false);
        expect(generatedScope.get('contextPath')).toBe(selected.endpoint.contextPath || null);
        expect(generatedScope.get('address')).toBe(scope.address);
        const generated = await generatedResponse.json();
        expect(generated).not.toHaveProperty('projects');
        expect(generated.documents.length).toBeGreaterThan(0);
        const directDocument = generated.documents.find(
            (item) => item.language.toLowerCase() === 'java' && item.role === 'direct'
        );
        expect(directDocument).toBeTruthy();
        expect(directDocument.markdown).toContain(scope.address || 'SERVICE_ADDRESS');
        expect(page.url()).toBe(originalUrl);

        const drawer = page.getByRole('dialog', {name: `${scope.protocol} ${selected.serviceId}`, exact: true});
        await expect(page.getByRole('dialog')).toHaveCount(1);
        await expect(drawer).toBeVisible();
        await expect(drawer.locator('#examples-title .badge')).toHaveText(scope.protocol);
        await expect(drawer.locator('#examples-title .examples-service-id')).toHaveText(selected.serviceId);
        await expect(drawer.locator('.examples-body .examples-service-id')).toHaveCount(0);
        await expect(drawer.locator('select')).toHaveCount(0);
        await expect(drawer.locator('.examples-address')).toHaveCount(0);
        await expect(drawer.getByRole('tablist', {name: '示例语言'})).toHaveCount(0);
        const roles = drawer.getByRole('tablist', {name: '调用角色'});
        const expectedTabs = ['接口定义'];
        if (generated.interfaceDescriptor) expectedTabs.push('接口描述符');
        await expect(roles.getByRole('tab')).toHaveText([...expectedTabs, ...exampleRoleLabels]);
        const roleBounds = await roles.boundingBox();
        const bodyBounds = await drawer.locator('.examples-body').boundingBox();
        expect(roleBounds.x - bodyBounds.x).toBeLessThanOrEqual(24);
        const document = drawer.getByRole('tabpanel', {name: '调用示例文档'});
        await expect(document.locator('pre code.language-java').last()).toContainText(
            scope.address || 'SERVICE_ADDRESS'
        );
        for (const label of exampleRoleLabels) {
            await roles.getByRole('tab', {name: label, exact: true}).click();
            await expect(roles.getByRole('tab', {name: label, exact: true})).toHaveAttribute(
                'aria-selected',
                'true'
            );
            await expect(document.locator('.markdown-document')).toBeVisible();
            await expect(document.locator('pre code.language-java').first()).not.toBeEmpty();
        }
        await expect(drawer.getByLabel('示例项目')).toHaveCount(0);
        await expect(drawer.getByLabel('示例文件')).toHaveCount(0);
        await expect(drawer.getByRole('button', {name: /下载|复制文件|复制命令/})).toHaveCount(0);
        await expect(drawer.getByRole('heading', {name: /运行命令|说明/})).toHaveCount(0);
        await drawer.getByRole('button', {name: '关闭协议示例', exact: true}).click();
        await expect(drawer).toHaveCount(0);
        await expect(trigger).toBeFocused();
        const row = page.locator('.access-examples .example-language-links');
        await expect(row.getByRole('button')).toHaveText(exampleLanguageLabels);
        await expect(row.locator('button:not(:disabled)')).toHaveText(
            supportedExampleLanguageLabels(generated.languages, scope.protocol)
        );
    });
}

test('provider protocol labels use endpoint protocolName without protocol filtering', async ({page}) => {
    await enter(page);
    await order(page);
    const membersResponse = page.waitForResponse(
        (response) => new URL(response.url()).pathname === '/api/service/members'
    );
    await tab(page, '提供者');
    const members = (await (await membersResponse).json()).items;
    await expect(page.locator('.member-table tbody tr')).toHaveCount(2);
    for (const member of members) {
        for (const endpoint of member.endpoints) {
            expect(endpoint.protocolName).toBeTruthy();
            expect(endpoint).not.toHaveProperty('protocol');
        }
    }
    await expect(page.locator('.member-table .protocols .badge').filter({hasText: /^hprose$/})).toHaveCount(
        1
    );
    await expect(page.locator('.member-table .protocols .badge').filter({hasText: /^rsf$/})).toHaveCount(2);

    await expect(page.getByLabel('筛选协议')).toHaveCount(0);
    await expect(page.getByLabel('搜索条件')).toHaveCount(0);
});

test('service members search IP only and retain identity units and protocol tags', async ({
                                                                                              page
                                                                                          }, testInfo) => {
    await enter(page);
    await order(page);
    const request = page.waitForRequest((request) => {
        const url = new URL(request.url());
        return url.pathname === '/api/service/members' && url.searchParams.get('role') === 'providers';
    });
    await tab(page, '提供者');
    const query = new URL((await request).url());
    expect(query.searchParams.get('field')).toBe('ip');
    expect(query.searchParams.has('protocol')).toBe(false);
    await expect(page.getByLabel('搜索条件')).toHaveCount(0);
    await expect(page.getByLabel('筛选协议')).toHaveCount(0);
    await expect(page.getByLabel('搜索成员')).toHaveAttribute('placeholder', '搜索 IP');
    await expect(page.getByRole('columnheader', {name: '单元', exact: true})).toBeVisible();
    await expect(page.getByRole('columnheader', {name: '进程', exact: true})).toBeVisible();
    const identity = page.locator('.member-table .instance-primary').first();
    const pid = page.locator('.member-table .instance-pid').first();
    await expect(identity).toHaveText('127.0.0.1');
    await expect(pid).toHaveText(/^\d+$/);
    await expect(page.locator('.member-table .instance-id')).toHaveCount(0);
    const primary = (await identity.textContent()).trim();
    const processId = (await pid.textContent()).trim();
    await page.getByLabel('搜索成员').fill(processId);
    await expect(page.getByText('本页 0 个提供者', {exact: true})).toBeVisible();
    await expect(page.getByText('没有匹配的成员', {exact: true})).toBeVisible();
    await page.getByLabel('搜索成员').fill('127.0.0.');
    await expect(page.getByText('本页 2 个提供者', {exact: true})).toBeVisible();
    await page.getByLabel('搜索成员').fill('east');
    await expect(page.getByText('本页 0 个提供者', {exact: true})).toBeVisible();
    await page.getByLabel('搜索成员').fill('   ');
    await expect(page.getByText('本页 2 个提供者', {exact: true})).toBeVisible();
    await page.getByLabel('搜索成员').fill('');
    await expect(page.getByText('本页 2 个提供者', {exact: true})).toBeVisible();
    await expect(page.getByRole('columnheader', {name: '发布入口', exact: true})).toHaveCount(0);
    await expect(page.getByRole('columnheader', {name: '操作', exact: true})).toHaveCount(0);
    await expect(page.getByRole('button', {name: '治理服务', exact: true})).toHaveCount(0);
    await expect(page.locator('.endpoint-table')).toHaveCount(0);
    await expect(page.locator('.member-table .protocols .badge').filter({hasText: /^rsf$/})).toHaveCount(2);
    await expect(page.locator('.member-table .protocols .badge').filter({hasText: /^hprose$/})).toHaveCount(
        1
    );
    await expect(page.locator('.member-table .protocols button')).toHaveCount(0);
    await page.screenshot({path: testInfo.outputPath('service-endpoints.png'), fullPage: true});
    await tab(page, '消费者');
    await expect(page.getByRole('columnheader', {name: '使用协议', exact: true})).toBeVisible();
    await expect(page.getByRole('columnheader', {name: '支持协议', exact: true})).toHaveCount(0);
    await expect(page.locator('.member-table .instance-primary')).toHaveText(primary);
    await expect(page.locator('.member-table .instance-pid')).toHaveText(processId);
    await expect(page.locator('.member-table .instance-id')).toHaveCount(0);
    await expect(page.getByRole('columnheader', {name: '订阅端点', exact: true})).toHaveCount(0);
    await expect(page.getByRole('columnheader', {name: '操作', exact: true})).toHaveCount(0);
    await expect(page.locator('.endpoint-table')).toHaveCount(0);
    await expect(page.locator('.member-table .protocols')).toContainText('rsf');
    await expect(page.locator('.member-table .protocols button')).toHaveCount(0);
    await expect(page.getByLabel('搜索条件')).toHaveCount(0);
    await expect(page.getByLabel('筛选协议')).toHaveCount(0);
    await expect(page.getByLabel('搜索成员')).toHaveAttribute('placeholder', '搜索 IP');
    await page.getByLabel('搜索成员').fill(primary);
    await expect(page.getByText('本页 1 个消费者', {exact: true})).toBeVisible();
    await page.getByLabel('搜索成员').fill(processId);
    await expect(page.getByText('本页 0 个消费者', {exact: true})).toBeVisible();
    await page.getByLabel('搜索成员').fill('   ');
    await expect(page.getByText('本页 1 个消费者', {exact: true})).toBeVisible();
    await page.getByLabel('搜索成员').fill('');
    await expect(page.getByText('本页 1 个消费者', {exact: true})).toBeVisible();
    await page.screenshot({path: testInfo.outputPath('consumer-ip-search.png'), fullPage: true});
});

for (const ruleType of [
    {tab: '流量控制', type: 'FLOW_CONTROL', rule: '<controlSet><!-- service --></controlSet>'},
    {tab: '路由策略', level: '服务级', type: 'SERVICE_ROUTE'},
    {tab: '路由策略', level: '方法级', type: 'METHOD_ROUTE'},
    {tab: '路由策略', level: '参数级', type: 'ARGS_ROUTE'}
]) {
    test(`service governance uses its current service for ${ruleType.type}`, async ({page}) => {
        await enter(page);
        await order(page);
        const current = new URLSearchParams(new URL(page.url()).hash.slice(1));
        const query = new URLSearchParams({
            mode: 'registry',
            clusterId: current.get('clusterId'),
            serviceId: current.get('serviceId'),
            scope: 'service',
            type: ruleType.type
        });

        async function read(parameters) {
            const response = await page.request.get(`/api/rule?${parameters}`);
            expect(response.ok()).toBeTruthy();
            return response.json();
        }

        const original = await read(query);
        const members = await page.request.get(`/api/service/members?${query}&role=providers`);
        expect(members.ok()).toBeTruthy();
        const overrides = [];
        for (const member of (await members.json()).items) {
            const parameters = new URLSearchParams(query);
            parameters.set('instanceId', member.instance.instanceId);
            overrides.push({parameters, original: await read(parameters)});
        }
        expect(overrides.length).toBeGreaterThan(1);

        async function mutation(path, method, action) {
            const pending = page.waitForResponse(
                (response) =>
                    new URL(response.url()).pathname === `/api/${path}` && response.request().method() === method
            );
            await action();
            const response = await pending;
            expect(response.ok()).toBeTruthy();
            const parameters = new URL(response.url()).searchParams;
            expect(parameters.get('clusterId')).toBe(query.get('clusterId'));
            expect(parameters.get('serviceId')).toBe(query.get('serviceId'));
            expect(parameters.get('scope')).toBe('service');
            expect(parameters.has('instanceId')).toBeFalsy();
            expect(parameters.has('targetUnit')).toBeFalsy();
            expect(parameters.has('localUnit')).toBeFalsy();
            expect(await response.json()).toEqual({success: true});
            for (const override of overrides) {
                expect(await read(override.parameters)).toEqual(override.original);
            }
        }

        try {
            await expect(page.getByLabel('目标范围')).toHaveCount(0);
            await tab(page, ruleType.tab);
            if (ruleType.level) await page.getByRole('button', {name: ruleType.level, exact: true}).click();
            await expect(page.getByLabel('目标单元')).toHaveCount(0);
            const editor = page.getByLabel('规则正文');
            await expect(editor).toBeEditable();
            const rule = ruleType.rule || '<route><!-- service --><return value="addresses"/></route>';
            await editor.fill(rule);
            await mutation('rule', 'PUT', () => page.getByRole('button', {name: '保存', exact: true}).click());
            expect((await read(query)).rule).toBe(rule);
            await expect(page.getByRole('status')).toContainText('已保存');
            const enabled = (await read(query)).enabled;
            await mutation('rule/enabled', 'PUT', () => page.getByRole('switch', {name: '启用规则'}).click());
            expect((await read(query)).enabled).toBe(!enabled);
            page.once('dialog', (dialog) => dialog.accept());
            await mutation('rule', 'DELETE', () => page.getByRole('button', {name: '删除', exact: true}).click());
            await expect(editor).toHaveValue('');
            expect((await read(query)).rule || '').toBe('');
        } finally {
            const restored = original.rule
                ? await page.request.put(`/api/rule?${query}`, {data: {rule: original.rule}})
                : await page.request.delete(`/api/rule?${query}`);
            expect(restored.ok()).toBeTruthy();
            const enabled = await page.request.put(`/api/rule/enabled?${query}`, {
                data: {enabled: original.enabled}
            });
            expect(enabled.ok()).toBeTruthy();
        }
    });
}

test('service enabled switch submits its current service without a target range', async ({page}) => {
    await enter(page);
    await order(page);
    const current = new URLSearchParams(new URL(page.url()).hash.slice(1));
    const query = new URLSearchParams({
        mode: 'registry',
        clusterId: current.get('clusterId'),
        serviceId: current.get('serviceId')
    });
    const original = await (await page.request.get(`/api/service?${query}`)).json();
    const control = page.getByRole('switch', {name: '启用服务'});
    await expect(page.getByLabel('目标范围')).toHaveCount(0);
    try {
        const pending = page.waitForResponse(
            (response) =>
                new URL(response.url()).pathname === '/api/service/enabled' && response.request().method() === 'PUT'
        );
        await control.click();
        const response = await pending;
        expect(response.ok()).toBeTruthy();
        const parameters = new URL(response.url()).searchParams;
        expect(parameters.get('serviceId')).toBe(query.get('serviceId'));
        expect(parameters.get('clusterId')).toBe(query.get('clusterId'));
        expect(parameters.has('instanceId')).toBeFalsy();
        expect(parameters.has('targetUnit')).toBeFalsy();
        expect(parameters.has('localUnit')).toBeFalsy();
        expect(await response.json()).toEqual({success: true});
        await expect(control).toHaveAttribute('aria-checked', String(!original.enabled));
        const updated = await (await page.request.get(`/api/service?${query}`)).json();
        expect(updated.enabled).toBe(!original.enabled);
    } finally {
        const restored = await page.request.put(`/api/service/enabled?${query}`, {
            data: {enabled: original.enabled}
        });
        expect(restored.ok()).toBeTruthy();
    }
});

for (const scenario of [
    {
        tab: 'flow',
        name: '流量控制',
        type: 'FLOW_CONTROL',
        rule: '<controlSet><!-- instance-only --></controlSet>'
    },
    {
        tab: 'route',
        name: '路由策略',
        type: 'SERVICE_ROUTE',
        rule: '<route><!-- instance-only --><return value="addresses"/></route>'
    }
]) {
    test(`instance ${scenario.name} saves, toggles and deletes only the selected instance`, async ({
                                                                                                       page
                                                                                                   }) => {
        await enter(page);
        const clusterId = new URLSearchParams(new URL(page.url()).hash.slice(1)).get('clusterId');
        const instancesQuery = new URLSearchParams({mode: 'registry', clusterId, applicationName: 'order-app'});
        const response = await page.request.get(`/api/instances?${instancesQuery}`);
        expect(response.ok()).toBeTruthy();
        const instances = (await response.json()).items;
        const selected = instances.find((instance) => instance.unitName === 'east');
        const sibling = instances.find((instance) => instance.unitName === 'west');
        expect(selected).toBeTruthy();
        expect(sibling).toBeTruthy();
        const query = new URLSearchParams({
            mode: 'registry',
            clusterId,
            scope: 'instance',
            instanceId: selected.instanceId,
            type: scenario.type
        });
        const siblingQuery = new URLSearchParams(query);
        siblingQuery.set('instanceId', sibling.instanceId);

        async function read(target) {
            const response = await page.request.get(`/api/rule?${target}`);
            expect(response.ok()).toBeTruthy();
            return response.json();
        }

        const original = await read(query);
        const siblingOriginal = await read(siblingQuery);

        async function mutation(path, method, action) {
            const pending = page.waitForResponse(
                (response) =>
                    new URL(response.url()).pathname === `/api/${path}` && response.request().method() === method
            );
            await action();
            const response = await pending;
            expect(response.ok()).toBeTruthy();
            const parameters = new URL(response.url()).searchParams;
            expect(parameters.get('instanceId')).toBe(selected.instanceId);
            expect(parameters.get('scope')).toBe('instance');
            expect(parameters.has('targetUnit')).toBeFalsy();
            expect(parameters.has('localUnit')).toBeFalsy();
            expect(await read(siblingQuery)).toEqual(siblingOriginal);
        }

        try {
            await page.goto(
                `/#view=instance&clusterId=${clusterId}&instanceId=${selected.instanceId}&application=order-app&tab=${scenario.tab}`
            );
            const editor = page.getByLabel('规则正文');
            await expect(editor).toBeEditable();
            await expect(page.getByLabel('目标单元')).toHaveCount(0);
            if (scenario.tab === 'route') {
                for (const level of ['方法级', '参数级', '服务级']) {
                    await page.getByRole('button', {name: level, exact: true}).click();
                    await expect(editor).toBeEditable();
                    await expect(page.getByLabel('目标单元')).toHaveCount(0);
                }
            }
            await editor.fill(scenario.rule);
            await mutation('rule', 'PUT', () => page.getByRole('button', {name: '保存', exact: true}).click());
            expect((await read(query)).rule).toBe(scenario.rule);
            const enabled = (await read(query)).enabled;
            await mutation('rule/enabled', 'PUT', () => page.getByRole('switch', {name: '启用规则'}).click());
            expect((await read(query)).enabled).toBe(!enabled);
            page.once('dialog', (dialog) => dialog.accept());
            await mutation('rule', 'DELETE', () => page.getByRole('button', {name: '删除', exact: true}).click());
            await expect(editor).toHaveValue('');
            expect((await read(query)).rule || '').toBe('');
        } finally {
            const restore = original.rule
                ? await page.request.put(`/api/rule?${query}`, {data: {rule: original.rule}})
                : await page.request.delete(`/api/rule?${query}`);
            expect(restore.ok()).toBeTruthy();
            const restoreEnabled = await page.request.put(`/api/rule/enabled?${query}`, {
                data: {enabled: original.enabled}
            });
            expect(restoreEnabled.ok()).toBeTruthy();
        }
    });
}

test('rule editing, current service submission, dirty navigation and global configuration tabs', async ({
                                                                                                            page
                                                                                                        }) => {
    await enter(page);
    await order(page);
    await tab(page, '路由策略');
    const editor = page.getByLabel('规则正文');
    await editor.fill('<route><return value="addresses"/></route>');
    await expect(page.getByLabel('目标单元')).toHaveCount(0);
    const saved = page.waitForResponse(
        (response) => new URL(response.url()).pathname === '/api/rule' && response.request().method() === 'PUT'
    );
    await page.getByRole('button', {name: '保存', exact: true}).click();
    const response = await saved;
    expect(response.ok()).toBeTruthy();
    const parameters = new URL(response.url()).searchParams;
    expect(parameters.get('scope')).toBe('service');
    expect(parameters.has('instanceId')).toBeFalsy();
    expect(parameters.has('targetUnit')).toBeFalsy();
    expect(parameters.has('localUnit')).toBeFalsy();
    expect(await response.json()).toEqual({success: true});
    await expect(page.getByRole('status')).toContainText('已保存');
    await editor.fill('<route><!-- unsaved --></route>');
    page.once('dialog', (dialog) => dialog.dismiss());
    await page.getByRole('button', {name: '方法级', exact: true}).click();
    await expect(editor).toHaveValue('<route><!-- unsaved --></route>');
    page.once('dialog', (dialog) => dialog.accept());
    await page.getByRole('button', {name: '方法级', exact: true}).click();
    await expect(editor).toHaveValue('');
    await expect(page.getByText('方法级路由', {exact: true})).toHaveCount(0);
    await editor.fill('<route/>');
    await page.getByRole('button', {name: '保存', exact: true}).click();
    await expect(page.getByRole('alert')).toBeVisible();
    page.once('dialog', (dialog) => dialog.accept());
    await page.getByRole('button', {name: '服务目录', exact: true}).click();
    await page.getByRole('navigation', {name: '服务治理'}).getByRole('link', {name: '全局配置'}).click();
    await expect(page.getByLabel('规则正文')).toBeVisible();
    await page.getByRole('navigation', {name: '全局配置'}).getByRole('button', {name: '路由策略'}).click();
    for (const level of ['服务级', '方法级', '参数级']) {
        await page.getByRole('button', {name: level, exact: true}).click();
        await expect(page.getByLabel('规则正文')).toBeEditable();
    }
});

test('route levels keep the panel mounted and ignore an older response after rapid switching', async ({
                                                                                                          page
                                                                                                      }) => {
    let releaseMethod;
    let releaseArgs;
    const methodGate = new Promise((resolve) => {
        releaseMethod = resolve;
    });
    const argsGate = new Promise((resolve) => {
        releaseArgs = resolve;
    });
    await page.route('**/api/rule?*', async (route) => {
        const type = new URL(route.request().url()).searchParams.get('type');
        if (type === 'METHOD_ROUTE') await methodGate;
        if (type === 'ARGS_ROUTE') await argsGate;
        await route.fulfill({
            json: {rule: `<route><!-- ${type} --></route>`, enabled: true, switchSupported: true}
        });
    });
    try {
        await enter(page);
        await order(page);
        await tab(page, '路由策略');
        const panel = page.locator('.rule-script');
        const editor = page.getByLabel('规则正文');
        await expect(editor).toHaveValue('<route><!-- SERVICE_ROUTE --></route>');
        await editor.evaluate((element) => {
            element.style.height = '440px';
        });
        const panelNode = await panel.elementHandle();
        const editorNode = await editor.elementHandle();
        const before = await panel.boundingBox();
        const methodRequest = page.waitForRequest(
            (request) => new URL(request.url()).searchParams.get('type') === 'METHOD_ROUTE'
        );
        await page.getByRole('button', {name: '方法级', exact: true}).click();
        await methodRequest;
        await expect(panel).toHaveAttribute('aria-busy', 'true');
        expect(await panelNode.evaluate((element) => element.isConnected)).toBe(true);
        expect(await editorNode.evaluate((element) => element.isConnected)).toBe(true);
        await expect(page.getByRole('navigation', {name: '路由级别'})).toBeVisible();
        await expect(editor).toHaveValue('');
        await expect(editor).toHaveJSProperty('readOnly', true);
        await expect(page.getByRole('button', {name: '保存', exact: true})).toBeDisabled();
        expect((await panel.boundingBox()).height).toBe(before.height);
        const argsRequest = page.waitForRequest(
            (request) => new URL(request.url()).searchParams.get('type') === 'ARGS_ROUTE'
        );
        await page.getByRole('button', {name: '参数级', exact: true}).click();
        await argsRequest;
        releaseArgs();
        await expect(editor).toHaveValue('<route><!-- ARGS_ROUTE --></route>');
        await expect(editor).toBeEditable();
        const methodResponse = page.waitForResponse(
            (response) => new URL(response.url()).searchParams.get('type') === 'METHOD_ROUTE'
        );
        releaseMethod();
        await (await methodResponse).finished();
        await page.evaluate(() => new Promise(requestAnimationFrame));
        await expect(panel).toHaveAttribute('aria-busy', 'false');
        await expect(editor).toHaveValue('<route><!-- ARGS_ROUTE --></route>');
        await expect(page.getByRole('button', {name: '参数级', exact: true})).toHaveClass('active');
        expect(await editorNode.evaluate((element) => element.style.height)).toBe('440px');
    } finally {
        releaseMethod();
        releaseArgs();
    }
});

test('a failed route-level load leaves the panel available for retry without exposing the old rule', async ({
                                                                                                                page
                                                                                                            }) => {
    let attempts = 0;
    await page.route('**/api/rule?*', async (route) => {
        const type = new URL(route.request().url()).searchParams.get('type');
        if (type === 'METHOD_ROUTE' && ++attempts === 1) {
            await route.fulfill({status: 500, json: {message: '读取方法级规则失败'}});
            return;
        }
        await route.fulfill({
            json: {rule: `<route><!-- ${type} --></route>`, enabled: true, switchSupported: true}
        });
    });
    await enter(page);
    await order(page);
    await tab(page, '路由策略');
    const panel = page.locator('.rule-script');
    const editor = page.getByLabel('规则正文');
    await expect(editor).toHaveValue('<route><!-- SERVICE_ROUTE --></route>');
    const node = await panel.elementHandle();
    await page.getByRole('button', {name: '方法级', exact: true}).click();
    await expect(page.getByRole('alert')).toBeVisible();
    expect(await node.evaluate((element) => element.isConnected)).toBe(true);
    await expect(editor).toHaveValue('');
    await expect(editor).toHaveJSProperty('readOnly', true);
    await expect(page.getByRole('button', {name: '保存', exact: true})).toBeDisabled();
    await page.getByRole('button', {name: '重新读取', exact: true}).click();
    await expect(editor).toHaveValue('<route><!-- METHOD_ROUTE --></route>');
    await expect(editor).toBeEditable();
    await expect(page.getByRole('alert')).toHaveCount(0);
});

test('metadata preserves JSON types, rejects invalid drafts and surfaces network failures', async ({
                                                                                                       page
                                                                                                   }) => {
    const writes = [];
    page.on('request', (request) => {
        if (new URL(request.url()).pathname === '/api/metadata' && ['PUT', 'DELETE'].includes(request.method())) {
            writes.push(new URL(request.url()).searchParams);
        }
    });
    await enter(page);
    await order(page);
    await tab(page, '元信息');
    await addMeta(page, 'policy', '{"enabled":true,"zones":["east","west"],"weight":10}');
    await expect(page.locator('.metadata-table')).toContainText('"weight":10');
    await addMeta(page, 'team', '"orders"');
    await page.getByRole('button', {name: '刷新', exact: true}).click();
    await expect(page.locator('.metadata-table')).toContainText('"orders"');
    await page.screenshot({path: 'test-results/service-metadata.png', fullPage: true});
    await page
        .locator('.metadata-table tr')
        .filter({hasText: 'team'})
        .getByRole('button', {name: '编辑'})
        .click();
    await page.getByLabel('元信息值（JSON）').fill('{invalid');
    await page.locator('.metadata-panel').getByRole('button', {name: '保存', exact: true}).click();
    await expect(page.getByRole('alert')).toContainText('有效 JSON');
    await page.getByRole('button', {name: '取消', exact: true}).click();
    page.once('dialog', (dialog) => dialog.accept());
    await page
        .locator('.metadata-table tr')
        .filter({hasText: 'team'})
        .getByRole('button', {name: '删除'})
        .click();
    await expect(page.locator('.metadata-table')).not.toContainText('"orders"');
    await page.route('**/api/metadata?*', (route) =>
        route.fulfill({
            status: 502,
            contentType: 'application/json',
            body: JSON.stringify({error: '存储连接中断'})
        })
    );
    await page.getByRole('button', {name: '刷新', exact: true}).click();
    await expect(page.getByRole('alert')).toContainText('存储连接中断');
    await page.unroute('**/api/metadata?*');
    expect(writes).toHaveLength(3);
    const current = new URLSearchParams(new URL(page.url()).hash.slice(1));
    for (const parameters of writes) {
        expect(parameters.get('scope')).toBe('service');
        expect(parameters.get('serviceId')).toBe(current.get('serviceId'));
        expect(parameters.get('clusterId')).toBe(current.get('clusterId'));
        expect(parameters.has('targetUnit')).toBeFalsy();
        expect(parameters.has('localUnit')).toBeFalsy();
    }
});

test('direct management uses real RPC without a selected registry', async ({page}, testInfo) => {
    await login(page);
    await page.locator('.cluster-select').click();
    await page.locator('.cluster-menu').getByRole('button', {name: '直连实例', exact: true}).click();
    const heading = page.getByRole('heading', {name: '直连实例', exact: true});
    const panel = page.locator('.direct-layout');
    const formRegion = panel.getByRole('form', {name: '连接信息', exact: true});
    const helpRegion = panel.getByRole('complementary', {name: '连接准备', exact: true});
    await expect(heading).toHaveCount(1);
    await expect(page.getByRole('navigation', {name: '面包屑导航', exact: true})).toHaveCount(0);
    await expect(formRegion).toBeVisible();
    await expect(helpRegion).toBeVisible();
    await expect(page.getByLabel('实例 Endpoint', {exact: true})).toBeVisible();
    await expect(page.getByLabel('管理凭据', {exact: true})).toHaveAttribute('type', 'password');
    const originalViewport = page.viewportSize();
    for (const {width, height} of [{width: 1780, height: 884}, {width: 390, height: 844}]) {
        await page.setViewportSize({width, height});
        const form = await formRegion.boundingBox();
        const help = await helpRegion.boundingBox();
        expect((await panel.boundingBox()).x).toBeCloseTo((await heading.boundingBox()).x, 0);
        if (width === 390) {
            expect(help.y).toBeGreaterThanOrEqual(form.y + form.height);
            await expect(helpRegion).toHaveCSS('border-top-width', '1px');
        } else {
            expect(help.x).toBeGreaterThanOrEqual(form.x + form.width);
            expect(help.y).toBeCloseTo(form.y, 0);
            await expect(helpRegion).toHaveCSS('border-left-width', '1px');
        }
        expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(width);
        const screenshot = testInfo.outputPath(`direct-connect-${width}.png`);
        await page.screenshot({path: screenshot, fullPage: true});
        await testInfo.attach(`direct-connect-${width}`, {path: screenshot, contentType: 'image/png'});
    }
    if (originalViewport) await page.setViewportSize(originalViewport);
    await page.getByLabel('管理凭据', {exact: true}).fill('wrong-direct-secret');
    await page.getByLabel('实例 Endpoint', {exact: true}).fill('bad-address');
    await page.getByRole('button', {name: '连接', exact: true}).click();
    await expect(page.getByRole('alert')).toContainText('管理地址格式');
    await page.getByLabel('实例 Endpoint', {exact: true}).fill('rsf://127.0.0.1:18767');
    await page.getByRole('button', {name: '连接', exact: true}).click();
    await expect(page.getByRole('alert')).toContainText('管理凭据无效');
    await page.getByLabel('管理凭据', {exact: true}).fill('browser-direct-令牌+secret');
    const request = page.waitForRequest(
        (request) => request.url().includes('/api/instance?') && request.url().includes('mode=direct')
    );
    await page.getByRole('button', {name: '连接', exact: true}).click();
    expect((await request).headers()['x-meteor-management-token']).toBe(
        Buffer.from('browser-direct-令牌+secret').toString('base64')
    );
    await expect(page.getByText('direct-order-app', {exact: true})).toBeVisible();
    expect(decodeURIComponent(page.url())).not.toContain('browser-direct-令牌+secret');
    const storage = await page.evaluate(() => JSON.stringify({...localStorage, ...sessionStorage}));
    expect(storage).not.toContain('browser-direct-令牌+secret');
    await page.getByRole('button', {name: '服务清单', exact: true}).click();
    await page.getByRole('button', {name: /InventoryService/}).click();
    await expect(page.locator('.service-info-fields code')).toHaveText(
        'net.hasor.meteor.test.scenario.ConsoleBrowserFixture$InventoryService'
    );
    await tab(page, '流量控制');
    await expect(page.getByLabel('规则正文')).toBeEditable();
    await expect(page.getByRole('switch', {name: '启用规则'})).toHaveCount(0);
    await tab(page, '元信息');
    await addMeta(page, 'owner', '"direct"');
    await expect(page.locator('.metadata-table')).toContainText('"direct"');
});

test('dense layouts fit 1080p and 1360-wide screens, narrow screens scroll only inside tables', async ({
                                                                                                           page
                                                                                                       }) => {
    await page.setViewportSize({width: 1920, height: 960});
    await enter(page);
    await expect(page.getByText('order-app', {exact: true})).toBeVisible();
    await page.screenshot({path: 'test-results/applications-1080p.png', fullPage: true});
    await order(page);
    await tab(page, '路由策略');
    for (const width of [1920, 1360]) {
        await page.setViewportSize({width, height: width === 1920 ? 960 : 768});
        await expect(page.getByRole('button', {name: '保存', exact: true})).toBeInViewport();
        const dimensions = await page.evaluate(() => ({
            width: innerWidth,
            actual: document.documentElement.scrollWidth
        }));
        expect(dimensions.actual).toBeLessThanOrEqual(dimensions.width);
    }
    await page.screenshot({path: 'test-results/route-1360.png', fullPage: true});
    await tab(page, '提供者');
    await page.setViewportSize({width: 390, height: 844});
    const dimensions = await page.evaluate(() => ({
        width: innerWidth,
        actual: document.documentElement.scrollWidth
    }));
    expect(dimensions.actual).toBeLessThanOrEqual(dimensions.width);
});

test('connection pages cover provider fields, failed connection, second cluster and removal', async ({
                                                                                                         page
                                                                                                     }) => {
    await login(page);
    await page.locator('.tabbar').getByRole('button', {name: '连接注册中心'}).click();
    await expect(page.getByText('命名空间', {exact: true})).toBeVisible();
    await page.getByRole('button', {name: 'ZooKeeper', exact: true}).click();
    await expect(page.getByLabel('根路径')).toHaveValue('/meteor');
    await expect(page.getByText('命名空间', {exact: true})).toHaveCount(0);
    await page.getByRole('button', {name: 'JDBC', exact: true}).click();
    await page.getByLabel('连接名称').fill('独立集群');
    await page.getByLabel('数据库', {exact: true}).selectOption('h2');
    await page.getByLabel('JDBC URL').fill('jdbc:h2:mem:no-schema;DB_CLOSE_DELAY=-1');
    await page.getByLabel('用户名', {exact: true}).fill('sa');
    await page.getByRole('button', {name: '测试连接', exact: true}).click();
    await expect(page.getByRole('alert')).toContainText('Table');
    await expect(page.getByRole('button', {name: '保存并进入'})).toBeDisabled();
    await page.getByLabel('JDBC URL').fill('jdbc:h2:mem:console-other;DB_CLOSE_DELAY=-1');
    await page.getByRole('button', {name: '测试连接', exact: true}).click();
    await expect(page.getByRole('status')).toContainText('连接成功');
    await page.getByRole('button', {name: '保存并进入'}).click();
    await expect(page.getByText('没有匹配的应用', {exact: true})).toBeVisible();
    await page.locator('.cluster-select').click();
    await page.locator('.cluster-menu').getByRole('button', {name: '所有集群', exact: true}).click();
    const card = page.locator('.cluster-cards .card').filter({hasText: '独立集群'});
    await card.getByRole('button', {name: '编辑', exact: true}).click();
    await expect(page.getByLabel('连接名称')).toHaveValue('独立集群');
    await page.getByRole('button', {name: '取消', exact: true}).click();
    const actions = card.getByText('操作', {exact: true});
    await actions.press('Enter');
    await expect(card.getByRole('button', {name: '移除', exact: true})).toBeVisible();
    await actions.press('Escape');
    await expect(card.getByRole('button', {name: '移除', exact: true})).toBeHidden();
    await actions.click();
    await card.getByRole('button', {name: '移除', exact: true}).click();
    const dialog = page.getByRole('dialog');
    await expect(dialog.getByRole('button', {name: '取消', exact: true})).toBeFocused();
    await dialog.getByRole('button', {name: '取消', exact: true}).click();
    await expect(actions).toBeFocused();
    await actions.click();
    await card.getByRole('button', {name: '移除', exact: true}).click();
    await dialog.getByRole('button', {name: '移除', exact: true}).click();
    await expect(card).toHaveCount(0);
});

test('Remote connects through authenticated RPC and hides storage credentials', async ({page}) => {
    await login(page);
    await page.locator('.tabbar').getByRole('button', {name: '连接注册中心'}).click();
    await page.getByRole('button', {name: 'Remote', exact: true}).click();
    await expect(page.getByLabel('用户名', {exact: true})).toHaveCount(0);
    await expect(page.getByLabel('密码', {exact: true})).toHaveCount(0);
    await expect(page.getByLabel('管理凭据', {exact: true})).toBeVisible();
    await page.getByLabel('连接名称').fill('Remote 管理');
    await page.getByLabel('RPC 地址').fill('rsf://127.0.0.1:18768');
    await page.getByLabel('远端集群 ID').fill('saved-registry');
    await page.getByLabel('管理凭据', {exact: true}).fill('wrong-secret');
    await page.getByRole('button', {name: '测试连接', exact: true}).click();
    await expect(page.getByRole('alert')).toContainText('远端控制台管理凭据无效');
    await expect(page.getByRole('button', {name: '保存并进入'})).toBeDisabled();

    await page.getByLabel('管理凭据', {exact: true}).fill('browser-remote-secret');
    await page.getByRole('button', {name: '测试连接', exact: true}).click();
    await expect(page.getByRole('status')).toContainText('连接成功');
    await page.getByRole('button', {name: '保存并进入'}).click();
    await expect(page.getByText('order-app', {exact: true})).toBeVisible();
    const profiles = await (await page.request.get('/api/clusters')).json();
    const remote = profiles.find((profile) => profile.name === 'Remote 管理');
    expect(remote.hasManagementToken).toBe(true);
    expect(remote.managementToken).toBeUndefined();
    expect(remote.hasPassword).toBe(false);
    await page.reload();
    await expect(page.locator('.cluster-select')).toContainText('Remote 管理');
    await page.request.delete(`/api/clusters?id=${remote.id}`);
});

test('service schema links open protocol documents and other address types only copy', async ({page}) => {
    await page.context().grantPermissions(['clipboard-read', 'clipboard-write']);
    await page.route('**/api/service?*', async (route) => {
        const response = await route.fetch();
        const result = await response.json();
        if (new URL(route.request().url()).searchParams.has('instanceId')) {
            result.endpoints = [
                {
                    protocolName: 'grpc',
                    endpoints: {
                        grpc: {
                            endpoint: 'grpc://127.0.0.1:19209/grpc',
                            schema: 'http://127.0.0.1:19209/grpc/orders.OrderService?proto',
                            proto: 'http://127.0.0.1:19209/grpc/orders.OrderService?proto'
                        }
                    }
                },
                {
                    protocolName: 'thrift-http',
                    endpoints: {
                        'thrift-http': {
                            endpoint: 'thrift-http://127.0.0.1:19213/thrift',
                            schema: 'http://127.0.0.1:19213/thrift/OrderService?thrift',
                            thrift: 'http://127.0.0.1:19213/thrift/OrderService?thrift'
                        }
                    }
                },
                {
                    protocolName: 'triple',
                    endpoints: {
                        triple: {
                            endpoint: 'triple://127.0.0.1:19212/triple',
                            schema: 'http://127.0.0.1:19212/triple/orders.OrderService?proto&group=orders&version=2.0',
                            proto: 'http://127.0.0.1:19212/triple/orders.OrderService?proto&group=orders&version=2.0'
                        }
                    }
                }
            ];
        }
        await route.fulfill({response, json: result});
    });
    await enter(page);
    await order(page);
    await tab(page, '提供者');
    await page.locator('.member-table .instance-link').first().click();
    await tab(page, '服务信息');
    const endpoints = page.getByRole('region', {name: '服务接入点'});
    const protocols = endpoints.getByRole('tablist', {name: '服务协议'});
    for (const [protocol, type, address] of [
        ['grpc', 'proto', 'http://127.0.0.1:19209/grpc/orders.OrderService?proto'],
        ['triple', 'proto', 'http://127.0.0.1:19212/triple/orders.OrderService?proto&group=orders&version=2.0'],
        ['thrift-http', 'thrift', 'http://127.0.0.1:19213/thrift/OrderService?thrift']
    ]) {
        await protocols.getByRole('tab', {name: protocol, exact: true}).click();
        const entries = endpoints.locator('.access-endpoints');
        await expect(entries.getByRole('link')).toHaveCount(1);
        await expect(entries.locator('[data-endpoint-type=schema] a')).toHaveAttribute('href', address);
        const copy = entries.locator(`[data-endpoint-type=${type}]`).getByRole('button');
        await expect(copy.locator('code')).toHaveText(address);
        await copy.click();
        await expect.poll(() => page.evaluate(() => navigator.clipboard.readText())).toBe(address);
        await expect(entries.locator('[data-endpoint-type]').first()).toHaveAttribute(
            'data-endpoint-type',
            'endpoint'
        );
    }
});

test('instance service shows endpoints before methods below facts', async ({page}, testInfo) => {
    const errors = [];
    page.on('pageerror', (error) => errors.push(error.message));
    await enter(page);
    await order(page);
    await tab(page, '提供者');
    await page.route('**/api/service?*', async (route) => {
        const response = await route.fetch();
        const service = await response.json();
        if (new URL(route.request().url()).searchParams.has('instanceId')) {
            const protocols = [
                'rsf',
                'connect',
                'dubbo',
                'grpc',
                'grpc-web',
                'hessian',
                'hprose',
                'jsonrpc',
                'oncrpc',
                'oncrpc-udp',
                'soap',
                'thrift',
                'thrift-http',
                'triple',
                'xmlrpc'
            ];
            service.info.protocols = protocols;
            service.endpoints = protocols.map((protocolName, index) => ({
                protocolName,
                defaultProtocol: protocolName === 'rsf',
                endpoints: {
                    [protocolName]: {
                        endpoint: `${protocolName}://127.0.0.1:${19200 + index}/${protocolName}`,
                        ...(protocolName === 'grpc'
                            ? {
                                http: 'http://127.0.0.1:19203/grpc/commerce/v1',
                                proto: 'http://127.0.0.1:19203/grpc/commerce/v1/OrderService?proto'
                            }
                            : {})
                    }
                }
            }));
        }
        await route.fulfill({response, json: service});
    });
    await page.locator('.member-table .instance-link').first().click();
    const tabs = page.getByRole('navigation', {name: '服务详情'});
    await expect(tabs.getByRole('button')).toHaveText(['服务信息', '流量控制', '路由策略', '元信息']);
    const summary = page.locator('.instance-service-summary');
    const access = page.getByRole('region', {name: '服务接入点'});
    await expect(summary).toContainText('提供者');
    await expect(tabs.getByRole('button', {name: '方法', exact: true})).toHaveCount(0);
    const methods = page.locator('.service-methods');
    await expect(methods).toBeVisible();
    const summaryBox = await summary.boundingBox();
    const methodsBox = await methods.boundingBox();
    expect(methodsBox.y).toBeGreaterThanOrEqual(summaryBox.y + summaryBox.height);
    await expect(summary.locator('dt')).toHaveText([
        '服务 ID',
        '接口',
        '分组',
        '名称',
        '版本',
        '身份',
        '状态',
        '启用服务'
    ]);
    const accessBox = await access.boundingBox();
    expect(accessBox.y).toBeGreaterThanOrEqual(summaryBox.y + summaryBox.height);
    expect(methodsBox.y).toBeGreaterThanOrEqual(accessBox.y + accessBox.height);
    await expect(tabs.getByRole('button', {name: '接入点', exact: true})).toHaveCount(0);
    await expect(access).toBeVisible();
    await page.setViewportSize({width: 1488, height: 1058});
    await page.screenshot({path: testInfo.outputPath('service-access-rsf.png'), fullPage: true});
    await access.getByRole('tab', {name: 'grpc', exact: true}).click();
    await expect(page.locator('.service-methods')).toBeVisible();
    for (const width of [1920, 1488, 1280, 1100, 800, 390]) {
        await page.setViewportSize({width, height: 1058});
        const protocols = access.getByRole('tab');
        const protocolRows = await protocols.evaluateAll(
            (items) => new Set(items.map((item) => item.getBoundingClientRect().top)).size
        );
        expect(protocolRows).toBe(width > 700 ? 4 : 8);
        await protocols.first().focus();
        await page.keyboard.press('ArrowDown');
        await expect(protocols.nth(width > 700 ? 4 : 2)).toHaveAttribute('aria-selected', 'true');
        await page.keyboard.press('ArrowUp');
        await expect(protocols.first()).toHaveAttribute('aria-selected', 'true');
        await access.getByRole('tab', {name: 'grpc', exact: true}).click();
        const endpointTitle = await access.locator('.access-endpoints h2').boundingBox();
        const exampleTitle = await access.locator('.access-examples h2').boundingBox();
        const endpointBody = await access.locator('.access-endpoints .access-section-body').boundingBox();
        const exampleBody = await access.locator('.access-examples .access-section-body').boundingBox();
        const address = await access.locator('[data-endpoint-type=endpoint] code').boundingBox();
        expect(address.width).toBeGreaterThanOrEqual(120);
        expect(Math.abs(endpointTitle.x - exampleTitle.x)).toBeLessThanOrEqual(1);
        expect(Math.abs(endpointBody.x - exampleBody.x)).toBeLessThanOrEqual(1);
        if (width > 700) {
            expect(endpointBody.x).toBeGreaterThan(endpointTitle.x + endpointTitle.width);
            expect(Math.abs(endpointBody.y - endpointTitle.y)).toBeLessThanOrEqual(1);
            expect(Math.abs(exampleBody.y - exampleTitle.y)).toBeLessThanOrEqual(1);
        } else {
            expect(endpointBody.y).toBeGreaterThan(endpointTitle.y);
            expect(exampleBody.y).toBeGreaterThan(exampleTitle.y);
        }
        expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(
            1
        );
        await page.screenshot({path: testInfo.outputPath(`service-overview-${width}.png`), fullPage: true});
        if (width === 1920) {
            await access
                .locator('.access-sidebar')
                .screenshot({path: testInfo.outputPath('protocol-support-four-columns.png')});
        }
    }
    await tab(page, '服务信息');
    await expect(page.locator('.service-methods')).toBeVisible();
    await expect(access).toBeVisible();
    await expect(page.locator('.method-detail[open]')).toHaveCount(0);
    await page.reload();
    await expect(tabs.getByRole('button', {name: '服务信息', exact: true})).toHaveClass('active');
    await tab(page, '服务信息');
    await expect(summary).toBeVisible();
    await expect(access).toBeVisible();
    expect(errors).toEqual([]);
});

test('persistent method catalogue reads definitions without enumerating providers', async ({
                                                                                               page
                                                                                           }, testInfo) => {
    await enter(page);
    // 目录读取不能借助提供者列表或逐实例查询。
    let memberRequests = 0;
    let methodRequests = 0;
    page.on('request', (request) => {
        if (request.url().includes('/api/service/methods?')) methodRequests++;
    });
    await page.route('**/api/service/members?*', async (route) => {
        memberRequests++;
        await route.abort();
    });
    await order(page);
    await expect(
        page.locator('.detail-tabbar').getByRole('button', {name: '方法清单', exact: true})
    ).toHaveCount(0);
    const information = page.locator('.service-info-panel');
    await expect(information).toBeVisible();
    const methods = page.getByRole('region', {name: '方法', exact: true});
    await expect(methods.locator('.method-detail')).toHaveCount(1);
    const informationBounds = await information.boundingBox();
    expect((await methods.boundingBox()).y).toBeGreaterThanOrEqual(
        informationBounds.y + informationBounds.height
    );
    await expect(methods.getByRole('heading', {name: '方法清单', exact: true})).toBeVisible();
    await expect(methods.getByRole('heading', {name: '方法', exact: true})).toHaveCount(0);
    await expect(methods.locator('.method-detail > summary')).toContainText('find(java.lang.String)');
    await expect(methods.locator('.method-detail[open]')).toHaveCount(0);
    await expect(methods.getByRole('button', {name: '调试', exact: true})).toHaveCount(1);
    await methods.locator('.method-detail > summary').click();
    await expect(methods.locator('.method-detail-body')).toContainText('响应');
    await methods.getByLabel('方法名', {exact: true}).fill('missing');
    await expect(methods).toContainText('暂无匹配的方法');
    await methods.getByLabel('方法名', {exact: true}).fill('IN');
    await expect(methods.locator('.method-detail')).toHaveCount(1);
    await expect(methods.getByRole('button', {name: '查询', exact: true})).toHaveCount(0);
    expect(methodRequests).toBe(1);
    expect(memberRequests).toBe(0);
    await page.screenshot({path: testInfo.outputPath('persistent-method-catalogue.png'), fullPage: true});
});

test('method catalogue supports exact-name registries', async ({page}) => {
    await enter(page);
    let queries = [];
    await page.route('**/api/service/methods?*', async (route) => {
        const url = new URL(route.request().url());
        queries.push(url.searchParams.get('methodName'));
        if (!url.searchParams.get('methodName')) {
            return route.fulfill({json: {listingSupported: false, definitions: [], nextCursor: null}});
        }
        const response = await route.fetch();
        const body = await response.json();
        body.listingSupported = false;
        await route.fulfill({response, json: body});
    });
    await order(page);
    const methods = page.getByRole('region', {name: '方法', exact: true});
    await expect(methods).toContainText('请输入完整方法名查询');
    await methods.getByLabel('方法名', {exact: true}).fill('find');
    await methods.getByRole('button', {name: '查询', exact: true}).click();
    await expect(methods.locator('.method-detail')).toHaveCount(1);
    expect(queries).toEqual([null, 'find']);
});

test('persistent method catalogue is available through console RPC cascading', async ({page}) => {
    await login(page);
    const response = await page.request.post('/api/clusters', {
        data: {
            name: '方法目录 Remote',
            type: 'remote',
            address: 'rsf://127.0.0.1:18768',
            remoteClusterId: 'saved-registry',
            managementToken: 'browser-remote-secret'
        }
    });
    expect(response.ok()).toBe(true);
    const cluster = await response.json();
    try {
        await page.goto(`/#view=applications&clusterId=${cluster.id}`);
        await page.reload();
        await order(page);
        const methods = page.getByRole('region', {name: '方法', exact: true});
        await expect(methods.locator('.method-detail')).toHaveCount(1);
        await expect(methods.locator('.method-detail > summary')).toContainText('find(java.lang.String)');
        await expect(page.getByRole('alert')).toHaveCount(0);
    } finally {
        await page.request.delete(`/api/clusters?id=${cluster.id}`);
    }
});

test('method catalogue appends pages and keeps each definition type graph', async ({page}) => {
    await enter(page);
    await page.route('**/api/service/methods?*', async (route) => {
        const cursor = new URL(route.request().url()).searchParams.get('cursor');
        const sourceUrl = new URL(route.request().url());
        sourceUrl.searchParams.delete('cursor');
        const response = await route.fetch({url: sourceUrl.toString()});
        const result = await response.json();
        const definition = structuredClone(result.definitions[0]);
        definition.methods[0].name = cursor ? 'save' : 'find';
        definition.methods[0].javaSignature = `${definition.methods[0].name}(example.Node)`;
        definition.methods[0].parameters = [{name: 'request', type: 'example.Node'}];
        definition.methods[0].returns = {type: 'example.Node'};
        definition.types = {
            'example.Node': {
                kind: 'object',
                properties: [
                    {name: cursor ? 'newField' : 'oldField', type: 'java.lang.String'},
                    {name: 'next', type: 'example.Node'}
                ]
            }
        };
        await route.fulfill({
            response,
            json: {
                listingSupported: true,
                definitions: [definition],
                nextCursor: cursor ? null : 'page-two'
            }
        });
    });
    await order(page);
    const methods = page.getByRole('region', {name: '方法', exact: true});
    await expect(methods.locator('.method-detail')).toHaveCount(2);
    await expect(methods.getByRole('button', {name: '加载更多', exact: true})).toHaveCount(0);
    const first = methods.locator('.method-detail').first();
    const second = methods.locator('.method-detail').last();
    await first.locator(':scope > summary').click();
    await second.locator(':scope > summary').click();
    await expect(first).toContainText('oldField');
    await expect(first).not.toContainText('newField');
    await expect(second).toContainText('newField');
    await expect(first).toContainText('递归引用');
    await methods.getByLabel('方法名', {exact: true}).fill('av');
    await expect(methods.locator('.method-detail')).toHaveCount(1);
    await expect(methods.locator('.method-detail > summary')).toContainText('save');
});

test('method catalogue does not search a partial list after a page fails', async ({page}) => {
    await enter(page);
    let fail = true;
    await page.route('**/api/service/methods?*', async (route) => {
        const url = new URL(route.request().url());
        if (url.searchParams.has('cursor') && fail)
            return route.fulfill({status: 503, json: {error: '方法分页读取失败'}});
        const cursor = url.searchParams.get('cursor');
        url.searchParams.delete('cursor');
        const response = await route.fetch({url: url.toString()});
        const result = await response.json();
        await route.fulfill({
            response,
            json: {...result, nextCursor: cursor ? null : 'second-page'}
        });
    });
    await order(page);
    const methods = page.getByRole('region', {name: '方法', exact: true});
    await expect(methods).toContainText('方法分页读取失败');
    await expect(methods.locator('.method-detail')).toHaveCount(0);
    fail = false;
    await page.getByRole('button', {name: '刷新', exact: true}).click();
    await expect(methods.locator('.method-detail')).toHaveCount(2);
    await expect(methods).not.toContainText('方法分页读取失败');
});

test('method catalogue keeps multiple method signatures and query toolbar compact', async ({
                                                                                               page
                                                                                           }, testInfo) => {
    await enter(page);
    await page.route('**/api/service/methods?*', async (route) => {
        const response = await route.fetch();
        const result = await response.json();
        const definitions = ['find', 'save', 'update', 'remove', 'count', 'exists'].map((name) => {
            const definition = structuredClone(result.definitions[0]);
            definition.methods[0].name = name;
            definition.methods[0].javaSignature = `${name}(java.lang.String)`;
            return definition;
        });
        await route.fulfill({response, json: {listingSupported: true, definitions, nextCursor: null}});
    });
    await order(page);
    const methods = page.getByRole('region', {name: '方法', exact: true});
    await expect(methods.locator('.method-detail')).toHaveCount(6);
    await expect(methods.locator('.method-detail[open]')).toHaveCount(0);
    await expect(methods.locator('.methods-title')).toHaveText('方法清单');
    const heading = await methods.getByRole('heading', {name: '方法清单', exact: true}).boundingBox();
    const query = await methods.getByLabel('方法名', {exact: true}).boundingBox();
    expect(Math.abs(heading.y + heading.height / 2 - query.y - query.height / 2)).toBeLessThan(10);
    await methods.locator('.method-detail > summary').first().click();
    await expect(methods.locator('.method-detail-body').first()).toContainText('请求');
    await expect(methods.locator('.method-result')).toHaveCount(0);
    await expect(methods.getByRole('button', {name: '调试', exact: true})).toHaveCount(6);
    await page.screenshot({path: testInfo.outputPath('method-catalogue-design.png'), fullPage: true});
    await page.setViewportSize({width: 760, height: 980});
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
});

test('catalogue method debugging reuses the workbench and selects a concrete provider', async ({page}) => {
    await enter(page);
    await order(page);
    const methods = page.getByRole('region', {name: '方法', exact: true});
    await methods.getByRole('button', {name: '调试', exact: true}).click();
    await expect(page.getByLabel('调试提供者', {exact: true})).toBeVisible();
    await expect(page.locator('.debug-request-title')).toContainText('find(java.lang.String)');
    await expect(page.getByRole('group', {name: '调试协议', exact: true})).toBeVisible();
    await expect(invokeButton(page)).toBeEnabled();
    await page.locator('.debug-argument-control').fill('catalogue-debug');
    const call = page.waitForResponse(
        (response) =>
            new URL(response.url()).pathname === '/api/service/debug' && response.request().method() === 'POST'
    );
    await invokeButton(page).click();
    const request = (await call).request();
    expect(new URL(request.url()).searchParams.get('instanceId')).toBeTruthy();
    expect(request.postDataJSON().method).toBe('find');
    await expect(page.getByRole('region', {name: '响应结果', exact: true})).toContainText('成功');
    await expect(page.locator('.debug-result-heading')).toContainText('成功');
    await expect(page.locator('.debug-single-result .debug-result-detail')).toHaveCount(1);
});

test('batch debugging selects provider rows and limits frontend concurrency to five', async ({
                                                                                                 page
                                                                                             }, testInfo) => {
    await enter(page);
    let originalId;
    let pending = 0;
    let maximum = 0;
    const calls = [];
    await page.route('**/api/service/members?*', async (route) => {
        const response = await route.fetch();
        const result = await response.json();
        const member = result.items.find((item) => item.enabled && item.instance.online);
        originalId = member.instance.instanceId;
        const items = Array.from({length: 7}, (_, index) => {
            const copy = structuredClone(member);
            copy.instance.instanceId = `batch-${index}`;
            copy.instance.ipAddress = `10.0.0.${index + 1}`;
            copy.instance.processId = 1200 + index;
            return copy;
        });
        await route.fulfill({response, json: {...result, items, nextCursor: null}});
    });
    await page.route('**/api/service/debug?*', async (route) => {
        const url = new URL(route.request().url());
        if (route.request().method() === 'GET') {
            url.searchParams.set('instanceId', originalId);
            const response = await route.fetch({url: url.toString()});
            return route.fulfill({response});
        }
        const id = url.searchParams.get('instanceId');
        calls.push(id);
        pending++;
        maximum = Math.max(maximum, pending);
        await new Promise((resolve) => setTimeout(resolve, 180));
        pending--;
        if (id === 'batch-2') return route.fulfill({status: 503, json: {error: '目标暂时不可用'}});
        await route.fulfill({json: {success: true, value: `result:${id}`, elapsedMillis: 180}});
    });
    await order(page);
    await page
        .getByRole('region', {name: '方法', exact: true})
        .getByRole('button', {name: '调试', exact: true})
        .click();
    const wall = page.getByRole('table', {name: '调试提供者', exact: true});
    await expect(wall.locator('input')).toHaveCount(7);
    await expect(wall.getByLabel('选择 10.0.0.1 (1200)', {exact: true})).toBeChecked();
    const selectionMode = page.getByRole('switch', {name: '多选提供者', exact: true});
    await expect(selectionMode).toHaveAttribute('aria-checked', 'false');
    const second = wall.getByLabel('选择 10.0.0.2 (1201)', {exact: true});
    await second.click();
    await second.click();
    await expect(wall.locator('input:checked')).toHaveCount(1);
    await expect(second).toBeChecked();
    await selectionMode.click();
    await wall.getByLabel('选择 10.0.0.1 (1200)', {exact: true}).click();
    for (let index = 3; index <= 7; index++)
        await wall.getByLabel(`选择 10.0.0.${index} (${1199 + index})`, {exact: true}).click();
    await expect(page.getByRole('region', {name: '提供者选择', exact: true})).toContainText('已选 7 个');
    await expect(page.getByRole('combobox', {name: '调试提供者'})).toHaveCount(0);
    const editor = page.locator('.debug-argument-control');
    await editor.fill('batch');
    await invokeButton(page).click();
    await expect(invokeButton(page)).toBeEnabled();
    expect(calls).toHaveLength(7);
    expect(new Set(calls).size).toBe(7);
    expect(maximum).toBe(5);
    const results = page.getByRole('tablist', {name: '提供者响应', exact: true}).getByRole('tab');
    await expect(results).toHaveCount(7);
    await expect(results.filter({hasText: '失败'})).toHaveCount(1);
    await expect(results.filter({hasText: '成功'})).toHaveCount(6);
    await expect(page.locator('.debug-result-summary')).toContainText('7 个提供者');
    await expect(page.locator('.debug-result-summary')).toContainText('6 成功');
    await expect(page.locator('.debug-result-summary')).toContainText('1 失败');
    const detail = page.getByRole('tabpanel');
    await expect(detail).toContainText('result:batch-1');
    await results.nth(2).click();
    await expect(detail).toContainText('目标暂时不可用');
    await results.first().click();
    await results.first().press('End');
    await expect(results.last()).toBeFocused();
    await expect(results.last()).toHaveAttribute('aria-selected', 'true');
    await expect(detail).toContainText('result:batch-6');
    await results.last().press('ArrowUp');
    await expect(results.nth(5)).toBeFocused();
    await results.nth(5).press('Home');
    await expect(results.first()).toBeFocused();
    await results.first().press('ArrowDown');
    await expect(results.nth(1)).toBeFocused();
    await expect(detail).toContainText('result:batch-0');
    await expect(page.getByRole('button', {name: /^全部(展开|折叠)$/})).toHaveCount(0);
    await expect(page.locator('.debug-result-heading button')).toHaveCount(1);
    await page.getByRole('button', {name: '收起响应结果', exact: true}).click();
    await expect(detail).not.toBeVisible();
    await page.getByRole('button', {name: '展开响应结果', exact: true}).click();
    await expect(detail).toContainText('result:batch-0');
    await expect(results.nth(1)).toHaveAttribute('aria-selected', 'true');
    const scrolling = await page.locator('.debug-result-list').evaluate((element) => ({
        height: element.clientHeight,
        content: element.scrollHeight
    }));
    expect(scrolling.height).toBeLessThanOrEqual(320);
    expect(scrolling.content).toBeGreaterThan(scrolling.height);
    await selectionMode.click();
    await expect(wall.locator('input:checked')).toHaveCount(1);
    await page.screenshot({path: testInfo.outputPath('batch-debugging.png'), fullPage: true});
});

test('batch debugging invokes both real providers and preserves their distinct responses', async ({
                                                                                                      page
                                                                                                  }) => {
    await enter(page);
    await order(page);
    await page
        .getByRole('region', {name: '方法', exact: true})
        .getByRole('button', {name: '调试', exact: true})
        .click();
    const cards = page.getByRole('table', {name: '调试提供者', exact: true}).locator('input');
    await expect(cards).toHaveCount(2);
    await page.getByRole('switch', {name: '多选提供者', exact: true}).click();
    for (let index = 0; index < 2; index++) {
        if (!(await cards.nth(index).isChecked())) await cards.nth(index).click();
    }
    await expect(invokeButton(page)).toBeEnabled();
    await page
        .getByRole('group', {name: '调试协议', exact: true})
        .getByRole('button', {name: 'rsf', exact: true})
        .click();
    await page.locator('.debug-argument-control').fill('batch-real');
    await invokeButton(page).click();
    const results = page.getByRole('tablist', {name: '提供者响应', exact: true}).getByRole('tab');
    await expect(results).toHaveCount(2);
    await expect(results.filter({hasText: '成功'})).toHaveCount(2);
    const values = [];
    for (let index = 0; index < 2; index++) {
        await results.nth(index).click();
        values.push(await page.getByRole('tabpanel').locator('.debug-response').textContent());
    }
    expect(values.some((value) => value.includes('order:batch-real'))).toBe(true);
    expect(values.some((value) => value.includes('replica:batch-real'))).toBe(true);
});

test('debug response folds before calling and stays folded for single and multiple targets', async ({
                                                                                                        page
                                                                                                    }, testInfo) => {
    await enter(page);
    await order(page);
    await page
        .getByRole('region', {name: '方法', exact: true})
        .getByRole('button', {name: '调试', exact: true})
        .click();
    await expect(invokeButton(page)).toBeEnabled();
    const resultToggle = page.locator('.debug-result-toggle');
    const requestToggle = page.locator('.debug-request-toggle');
    const panels = page.locator('#debug-result-panels');
    const placeholder = panels.getByText('展示结果或异常', {exact: true});
    const argument = page.locator('.debug-argument-control');
    const captureStyle = '.topbar, #breadcrumb-bar { visibility: hidden !important; }';
    await expect(requestToggle).toHaveText('请求参数');
    await expect(resultToggle).toHaveText('响应结果');
    await expect(resultToggle).toHaveAttribute('aria-controls', 'debug-result-panels');
    await expect(resultToggle).toHaveAttribute('aria-expanded', 'true');
    await expect(placeholder).toBeVisible();
    await page.locator('.debug-workbench').screenshot({
        path: testInfo.outputPath('before-call-expanded.png'),
        style: captureStyle
    });
    await resultToggle.press('Space');
    await expect(panels).toBeHidden();
    await expect(placeholder).toBeHidden();
    await expect(argument).toBeVisible();
    await expect(resultToggle).toHaveAccessibleName('展开响应结果');
    await page.locator('.debug-workbench').screenshot({
        path: testInfo.outputPath('before-call-collapsed.png'),
        style: captureStyle
    });
    await argument.fill('collapsed-single');
    await invokeButton(page).click();
    await expect(invokeButton(page)).toBeEnabled();
    await expect(page.locator('.debug-result-heading')).toContainText('成功');
    await expect(resultToggle).toHaveAttribute('aria-expanded', 'false');
    await expect(panels).toBeHidden();
    await expect(page.locator('.debug-single-result .debug-response')).toContainText('collapsed-single');
    await page.locator('.debug-workbench').screenshot({
        path: testInfo.outputPath('single-call-collapsed.png'),
        style: captureStyle
    });
    await resultToggle.press('Enter');
    await expect(panels).toBeVisible();
    await expect(page.locator('.debug-single-result .debug-response')).toContainText('collapsed-single');
    await expect(page.locator('.debug-single-result .debug-response')).toBeVisible();
    await page.locator('.debug-workbench').screenshot({
        path: testInfo.outputPath('single-call-expanded.png'),
        style: captureStyle
    });
    await resultToggle.click();
    await page.getByRole('switch', {name: '多选提供者', exact: true}).click();
    await page.getByRole('table', {name: '调试提供者', exact: true}).locator('input').nth(1).check();
    await expect(resultToggle).toHaveAttribute('aria-expanded', 'false');
    await expect(placeholder).toBeHidden();
    await invokeButton(page).click();
    await expect(invokeButton(page)).toBeEnabled();
    await expect(page.locator('.debug-result-summary')).toContainText('2 成功');
    await expect(panels).toBeHidden();
    await expect(resultToggle).toHaveAttribute('aria-expanded', 'false');
    await resultToggle.click();
    await expect(page.getByRole('tablist', {name: '提供者响应', exact: true}).getByRole('tab')).toHaveCount(
        2
    );
    await expect(page.getByRole('tabpanel')).toContainText('collapsed-single');
});

test('debug request editors resize natively to two lines and preserve collapsed arguments', async ({
                                                                                                       page
                                                                                                   }, testInfo) => {
    await enter(page);
    const browserErrors = [];
    page.on('pageerror', (failure) => browserErrors.push(failure.message));
    const calls = [];
    const signature = 'status(net.hasor.meteor.example.api.DebugMessage,boolean)';
    await page.route('**/api/service/debug?*', async (route) => {
        if (route.request().method() === 'POST') {
            calls.push(route.request().postDataJSON());
            return route.fulfill({
                json: {success: true, value: {value: 'status:default:149556:', result: 0}, elapsedMillis: 151}
            });
        }
        const response = await route.fetch();
        const contract = await response.json();
        const reference = 'net.hasor.meteor.example.api.DebugMessage';
        const description = {
            methods: [
                {
                    javaSignature: signature,
                    parameters: [
                        {javaName: 'request', type: reference},
                        {javaName: 'trace', type: 'boolean'}
                    ]
                }
            ],
            types: {
                [reference]: {
                    javaType: reference,
                    kind: 'object',
                    properties: [
                        {javaName: 'value', type: 'java.lang.String'},
                        {javaName: 'left', type: 'int'},
                        {javaName: 'right', type: 'int'},
                        {javaName: 'result', type: 'int'}
                    ]
                },
                'java.lang.String': {javaType: 'java.lang.String', kind: 'scalar'},
                int: {javaType: 'int', kind: 'scalar'},
                boolean: {javaType: 'boolean', kind: 'scalar'}
            }
        };
        contract.description = description;
        for (const endpoint of contract.endpoints) {
            endpoint.description = description;
            endpoint.parameterTypes = {'@object': {component: 'json', type: 'JSON'}};
        }
        await route.fulfill({response, json: contract});
    });
    await order(page);
    const hash = new URLSearchParams(new URL(page.url()).hash.slice(1));
    hash.set('tab', 'debug');
    hash.set('method', signature);
    await page.goto(`/#${hash}`);
    const editor = page.getByLabel('request', {exact: true});
    const collapse = page.getByRole('button', {name: '收起请求参数', exact: true});
    await expect(editor).toBeVisible();
    await expect(collapse).toHaveAttribute('aria-expanded', 'true');
    await expect(invokeButton(page)).toBeEnabled();
    await invokeButton(page).click();
    await expect(page.locator('.debug-result-heading')).toContainText('成功');
    const captureStyle = '.topbar, #breadcrumb-bar { visibility: hidden !important; }';
    await page.locator('.debug-workbench').screenshot({
        path: testInfo.outputPath('request-expanded.png'),
        style: captureStyle
    });

    // 拖动浏览器原生右下角，而非给元素写高度，验证真正的拖拽能力。
    async function resizeEditor(delta) {
        await editor.scrollIntoViewIfNeeded();
        const bounds = await editor.boundingBox();
        await page.mouse.move(bounds.x + bounds.width - 4, bounds.y + bounds.height - 4);
        await page.mouse.down();
        await page.mouse.move(bounds.x + bounds.width - 4, bounds.y + bounds.height - 4 + delta, {steps: 12});
        await page.mouse.up();
        return bounds;
    }

    const initial = await resizeEditor(100);
    await expect.poll(async () => (await editor.boundingBox()).height).toBeGreaterThan(initial.height + 80);
    const expanded = await editor.boundingBox();
    await resizeEditor(-expanded.height + 12);
    const minimum = await editor.evaluate((element) => {
        const style = getComputedStyle(element);
        return {
            lines:
                (element.clientHeight - parseFloat(style.paddingTop) - parseFloat(style.paddingBottom)) /
                parseFloat(style.lineHeight),
            height: element.getBoundingClientRect().height,
            width: element.getBoundingClientRect().width,
            containerWidth: element.parentElement.getBoundingClientRect().width,
            resize: style.resize
        };
    });
    expect(minimum.resize).toBe('vertical');
    expect(minimum.lines).toBeGreaterThanOrEqual(1.95);
    expect(minimum.lines).toBeLessThanOrEqual(2.05);
    expect(minimum.width).toBeCloseTo(minimum.containerWidth, 0);
    const payload = Object.fromEntries(Array.from({length: 35}, (_, index) => [`field${index}`, index]));
    await editor.fill(JSON.stringify(payload, null, 2));
    await expect.poll(async () => (await editor.boundingBox()).height).toBeCloseTo(minimum.height, 0);
    expect(await editor.evaluate((element) => element.scrollHeight > element.clientHeight)).toBe(true);
    await page.getByRole('button', {name: '格式化', exact: true}).click();
    expect((await editor.boundingBox()).height).toBeCloseTo(minimum.height, 0);
    await page.locator('.debug-workbench').screenshot({
        path: testInfo.outputPath('request-two-lines.png'),
        style: captureStyle
    });
    await collapse.focus();
    await page.keyboard.press('Enter');
    await expect(editor).toBeHidden();
    await expect(page.locator('.debug-request-title')).toContainText(signature);
    await invokeButton(page).click();
    await expect(invokeButton(page)).toBeEnabled();
    expect(calls.at(-1).arguments).toEqual([payload, false]);
    await expect(editor).toBeHidden();
    await page.locator('.debug-workbench').screenshot({
        path: testInfo.outputPath('request-collapsed.png'),
        style: captureStyle
    });
    await page.getByRole('button', {name: '展开请求参数', exact: true}).focus();
    await page.keyboard.press('Space');
    await expect(editor).toHaveValue(JSON.stringify(payload, null, 2));
    expect((await editor.boundingBox()).height).toBeCloseTo(minimum.height, 0);

    // CSS 上限约束原生拖拽；超长内容在内部滚动，不把页面无限撑高。
    await editor.evaluate((element) => {
        element.style.height = '10000px';
    });
    expect((await editor.boundingBox()).height).toBeLessThanOrEqual(
        Math.min(480, page.viewportSize().height * 0.6)
    );
    await editor.fill('invalid json');
    await collapse.click();
    const count = calls.length;
    await invokeButton(page).click();
    await expect(page.getByRole('alert')).toContainText('请求参数无效：request：');
    await expect(editor).toBeVisible();
    await expect(collapse).toHaveAttribute('aria-expanded', 'true');
    expect(calls).toHaveLength(count);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    expect(browserErrors).toEqual([]);
});

test('debug results use one compact master detail view for success failure and pending states', async ({
                                                                                                           page
                                                                                                       }, testInfo) => {
    await enter(page);
    const browserErrors = [];
    page.on('pageerror', (error) => browserErrors.push(error.message));
    page.on('console', (message) => {
        if (message.type() === 'error') browserErrors.push(message.text());
    });
    let originalId;
    let longResponse = false;
    let release;
    let waiting = true;
    const gate = new Promise((resolve) => {
        release = resolve;
    });
    const signature = 'status(net.hasor.meteor.example.api.DebugMessage)';
    await page.route('**/api/service/members?*', async (route) => {
        const response = await route.fetch();
        const result = await response.json();
        const member = result.items.find((item) => item.enabled && item.instance.online);
        originalId = member.instance.instanceId;
        const items = [149556, 150629].map((pid, index) => {
            const copy = structuredClone(member);
            copy.instance.instanceId = `result-${index}`;
            copy.instance.ipAddress = '127.0.0.1';
            copy.instance.processId = pid;
            return copy;
        });
        await route.fulfill({response, json: {...result, items, nextCursor: null}});
    });
    await page.route('**/api/service/debug?*', async (route) => {
        const url = new URL(route.request().url());
        if (route.request().method() === 'POST') {
            if (waiting) await gate;
            if (url.searchParams.get('instanceId') === 'result-1')
                return route.fulfill({
                    json: {
                        success: false,
                        errors: {code: 'TIMEOUT', message: '目标连接超时'},
                        elapsedMillis: 5000
                    }
                });
            return route.fulfill({
                json: {
                    success: true,
                    value: longResponse
                        ? Array.from({length: 250}, (_, index) => ({index, value: 'long response'}))
                        : {value: 'status:default:149556:', left: 0, right: 0, result: 0},
                    elapsedMillis: 151
                }
            });
        }
        url.searchParams.set('instanceId', originalId);
        const response = await route.fetch({url: url.toString()});
        const contract = await response.json();
        const reference = 'net.hasor.meteor.example.api.DebugMessage';
        const description = {
            methods: [
                {
                    name: 'status',
                    javaName: 'status',
                    javaSignature: signature,
                    parameters: [{name: 'request', javaName: 'request', type: reference}]
                }
            ],
            types: {
                [reference]: {
                    javaType: reference,
                    kind: 'object',
                    properties: [
                        {name: 'value', type: 'java.lang.String'},
                        {name: 'left', type: 'int'},
                        {name: 'right', type: 'int'},
                        {name: 'result', type: 'int'}
                    ]
                },
                'java.lang.String': {javaType: 'java.lang.String', kind: 'scalar'},
                int: {javaType: 'int', kind: 'scalar'}
            }
        };
        contract.description = description;
        for (const endpoint of contract.endpoints) {
            endpoint.description = description;
            endpoint.parameterTypes = {
                ...endpoint.parameterTypes,
                '@object': {component: 'json', type: 'JSON'}
            };
        }
        await route.fulfill({response, json: contract});
    });
    await order(page);
    const hash = new URLSearchParams(new URL(page.url()).hash.slice(1));
    hash.set('tab', 'debug');
    hash.set('method', signature);
    await page.goto(`/#${hash}`);
    const providers = page.getByRole('table', {name: '调试提供者'}).locator('input');
    await expect(providers).toHaveCount(2);
    await page.getByRole('switch', {name: '多选提供者'}).click();
    await providers.nth(1).check();
    await expect(invokeButton(page)).toBeEnabled();
    await invokeButton(page).click();
    const tabs = page.getByRole('tablist', {name: '提供者响应'}).getByRole('tab');
    const detail = page.getByRole('tabpanel');
    await expect(tabs).toHaveCount(2);
    await expect(page.locator('.debug-result-summary')).toContainText('2 进行中');
    await tabs.nth(1).click();
    await expect(detail).toContainText('正在等待该提供者响应');
    const collapse = page.getByRole('button', {name: '收起响应结果', exact: true});
    await collapse.focus();
    await page.keyboard.press('Enter');
    await expect(detail).toBeHidden();
    await expect(page.getByRole('button', {name: '收起请求参数', exact: true})).toHaveAttribute(
        'aria-expanded',
        'true'
    );
    waiting = false;
    release();
    await expect(invokeButton(page)).toBeEnabled();
    await expect(page.getByRole('button', {name: '展开响应结果', exact: true})).toHaveAttribute(
        'aria-expanded',
        'false'
    );
    await expect(page.locator('.debug-result-summary')).toContainText('1 成功');
    await expect(page.locator('.debug-result-summary')).toContainText('1 失败');
    await page.getByRole('button', {name: '展开响应结果', exact: true}).press('Space');
    await expect(tabs.nth(1)).toHaveAttribute('aria-selected', 'true');
    await expect(detail).toContainText('目标连接超时');
    await expect(detail).toContainText('TIMEOUT');
    await expect(page.locator('.debug-result-summary')).toContainText('1 成功');
    await expect(page.locator('.debug-result-summary')).toContainText('1 失败');
    await tabs.first().click();
    await expect(detail).toContainText('status:default:149556:');
    const listBox = await page.locator('.debug-result-list').boundingBox();
    const detailBox = await detail.boundingBox();
    if (testInfo.project.name === 'mobile')
        expect(detailBox.y).toBeGreaterThanOrEqual(listBox.y + listBox.height);
    else expect(detailBox.x).toBeGreaterThanOrEqual(listBox.x + listBox.width);
    const heading = page.locator('.debug-result-heading');
    const requestToggle = page.locator('.debug-request-toggle');
    const resultToggle = page.locator('.debug-result-toggle');
    expect((await resultToggle.boundingBox()).x).toBeCloseTo((await requestToggle.boundingBox()).x, 0);
    expect((await resultToggle.boundingBox()).width).toBeCloseTo((await requestToggle.boundingBox()).width, 0);
    const toggleStyle = (element) => {
        const style = getComputedStyle(element);
        const icon = getComputedStyle(element.querySelector('img'));
        return [style.fontSize, style.fontWeight, style.color, style.gap, icon.width, icon.height];
    };
    await expect(requestToggle).toHaveText('请求参数');
    await expect(resultToggle).toHaveText('响应结果');
    expect(await resultToggle.evaluate(toggleStyle)).toEqual(await requestToggle.evaluate(toggleStyle));
    if (testInfo.project.name !== 'mobile')
        expect((await heading.boundingBox()).height).toBeLessThanOrEqual(40);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    await page.locator('.debug-workbench').screenshot({
        path: testInfo.outputPath('result-workbench.png'),
        style: '.topbar, #breadcrumb-bar { visibility: hidden !important; }'
    });
    await page
        .getByRole('region', {name: '响应结果', exact: true})
        .screenshot({path: testInfo.outputPath('result-region.png')});
    await page.getByRole('switch', {name: '多选提供者'}).click();
    await invokeButton(page).click();
    await expect(invokeButton(page)).toBeEnabled();
    await expect(page.getByRole('tablist', {name: '提供者响应'})).toHaveCount(0);
    await expect(page.locator('.debug-single-result .debug-result-detail')).toHaveCount(1);
    await expect(heading).toContainText('5000 ms');
    await expect(page.locator('.debug-single-result')).toContainText('TIMEOUT');
    await expect(heading.getByRole('button')).toHaveCount(1);
    await expect(page.getByRole('button', {name: /^全部(展开|折叠)$/})).toHaveCount(0);
    await page
        .getByRole('region', {name: '响应结果', exact: true})
        .screenshot({path: testInfo.outputPath('single-result-error.png')});
    await providers.first().check();
    await invokeButton(page).click();
    await expect(invokeButton(page)).toBeEnabled();
    await expect(heading).toContainText('151 ms');
    await expect(page.locator('.debug-single-result')).toContainText('status:default:149556:');
    await page
        .getByRole('region', {name: '响应结果', exact: true})
        .screenshot({path: testInfo.outputPath('single-result.png')});
    const captureStyle = '.topbar, #breadcrumb-bar { visibility: hidden !important; }';
    await page.locator('.debug-workbench').screenshot({
        path: testInfo.outputPath('single-workbench.png'),
        style: captureStyle
    });
    await resultToggle.click();
    await expect(page.locator('.debug-single-result')).toBeHidden();
    await expect(heading).toContainText('成功');
    await expect(heading).toContainText('151 ms');
    await expect(page.getByLabel('request', {exact: true})).toBeVisible();
    await page.locator('.debug-workbench').screenshot({
        path: testInfo.outputPath('response-collapsed-workbench.png'),
        style: captureStyle
    });
    await requestToggle.click();
    await expect(page.getByLabel('request', {exact: true})).toBeHidden();
    await expect(resultToggle).toHaveAttribute('aria-expanded', 'false');
    await expect(heading).toContainText('151 ms');
    await page.locator('.debug-workbench').screenshot({
        path: testInfo.outputPath('both-collapsed-workbench.png'),
        style: captureStyle
    });
    await requestToggle.press('Enter');
    await resultToggle.press('Space');
    await expect(page.getByLabel('request', {exact: true})).toBeVisible();
    await expect(page.locator('.debug-single-result')).toContainText('status:default:149556:');
    longResponse = true;
    await invokeButton(page).click();
    await expect(invokeButton(page)).toBeEnabled();
    const response = page.locator('.debug-single-result .debug-response');
    await expect(response).toContainText('long response');
    const responseSize = await response.evaluate((element) => ({
        height: element.clientHeight,
        content: element.scrollHeight
    }));
    expect(responseSize.height).toBeLessThanOrEqual(480);
    expect(responseSize.content).toBeGreaterThan(responseSize.height);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    expect(browserErrors).toEqual([]);
});

test('provider selection preserves the debug workbench without reloading its contract', async ({page}) => {
    await enter(page);
    let reads = 0;
    page.on('request', (request) => {
        if (request.method() === 'GET' && request.url().includes('/api/service/debug?')) reads++;
    });
    await order(page);
    await page
        .getByRole('region', {name: '方法', exact: true})
        .getByRole('button', {name: '调试', exact: true})
        .click();
    const cards = page.getByRole('table', {name: '调试提供者', exact: true}).locator('input');
    await expect(cards).toHaveCount(2);
    const protocols = page.getByRole('group', {name: '调试协议', exact: true});
    await expect(protocols.getByRole('button', {name: 'rsf', exact: true})).toBeVisible();
    await protocols.getByRole('button', {name: 'rsf', exact: true}).click();
    await page.locator('.debug-argument-control').fill('keep-selection');
    const workbench = await page.locator('.debug-workbench').elementHandle();
    for (const index of [1, 0, 1, 0]) await cards.nth(index).click();
    await expect(page.locator('.debug-argument-control')).toHaveValue('keep-selection');
    await expect(protocols.getByRole('button', {name: 'rsf', exact: true})).toHaveAttribute(
        'aria-pressed',
        'true'
    );
    expect(await workbench.evaluate((element) => element.isConnected)).toBe(true);
    expect(reads).toBe(1);
    let release;
    const gate = new Promise((resolve) => {
        release = resolve;
    });
    await page.route('**/api/service/debug?*', async (route) => {
        await gate;
        await route.continue();
    });
    const refreshing = page.waitForRequest((request) => request.url().includes('/api/service/debug?'));
    await page.getByRole('button', {name: '刷新', exact: true}).click();
    await refreshing;
    expect(await workbench.evaluate((element) => element.isConnected)).toBe(true);
    await expect(page.locator('.debug-argument-control')).toHaveValue('keep-selection');
    release();
    await expect(page.getByRole('button', {name: '刷新', exact: true})).toBeEnabled();
    await expect(page.locator('.debug-argument-control')).toHaveValue('keep-selection');
    await expect(protocols.getByRole('button', {name: 'rsf', exact: true})).toHaveAttribute(
        'aria-pressed',
        'true'
    );
});

test('provider table bounds 100 targets and retains selections across filtering and pages', async ({
                                                                                                       page
                                                                                                   }, testInfo) => {
    await enter(page);
    let originalId;
    let unavailable = false;
    const calls = [];
    const errors = [];
    page.on('pageerror', (error) => errors.push(error.message));
    page.on('console', (message) => {
        if (message.type() === 'error') errors.push(message.text());
    });
    await page.route('**/api/service/members?*', async (route) => {
        const sourceUrl = new URL(route.request().url());
        sourceUrl.searchParams.delete('cursor');
        sourceUrl.searchParams.delete('filter');
        const response = await route.fetch({url: sourceUrl.toString()});
        const result = await response.json();
        const member = result.items.find((item) => item.enabled && item.instance.online);
        originalId = member.instance.instanceId;
        const url = new URL(route.request().url());
        const secondPage = url.searchParams.get('cursor') === 'provider-page-2';
        let items = Array.from({length: secondPage ? 25 : 100}, (_, offset) => {
            const index = offset + (secondPage ? 100 : 0);
            const copy = structuredClone(member);
            copy.instance.instanceId = `large-${index}`;
            copy.instance.ipAddress = `10.0.0.${index + 1}`;
            copy.instance.processId = 1200 + index;
            copy.instance.unitName = index % 3 ? 'default' : 'canary';
            copy.instance.online = index !== 45 && !(unavailable && index === 43);
            copy.enabled = index !== 46;
            return copy;
        });
        const filter = url.searchParams.get('filter') || '';
        if (filter)
            items = items.filter(({instance}) => `${instance.ipAddress} ${instance.processId}`.includes(filter));
        await route.fulfill({
            response,
            json: {...result, items, nextCursor: !secondPage && !filter ? 'provider-page-2' : null}
        });
    });
    await page.route('**/api/service/debug?*', async (route) => {
        const url = new URL(route.request().url());
        if (route.request().method() === 'GET') {
            url.searchParams.set('instanceId', originalId);
            return route.fulfill({response: await route.fetch({url: url.toString()})});
        }
        calls.push(url.searchParams.get('instanceId'));
        return route.fulfill({json: {success: true, value: 'ok', elapsedMillis: 2}});
    });
    await order(page);
    await page
        .getByRole('region', {name: '方法', exact: true})
        .getByRole('button', {name: '调试', exact: true})
        .click();
    const table = page.getByRole('table', {name: '调试提供者', exact: true});
    const panel = page.getByRole('region', {name: '提供者选择', exact: true});
    const list = page.getByRole('region', {name: '提供者列表', exact: true});
    const input = (index) => table.getByLabel(`选择 10.0.0.${index + 1} (${1200 + index})`, {exact: true});
    await expect(table.locator('tbody tr')).toHaveCount(100);
    await expect(input(0)).toBeChecked();
    await expect(input(45)).toBeDisabled();
    await expect(input(46)).toBeDisabled();
    await expect(input(45).locator('..').locator('..')).toContainText('离线');
    await expect(input(46).locator('..').locator('..')).toContainText('禁用');
    await page.getByRole('switch', {name: '多选提供者', exact: true}).click();
    await input(42).focus();
    await page.keyboard.press('Space');
    await expect(input(42)).toBeChecked();
    await input(43).click();
    await expect(panel).toContainText('本页 100 个提供者，已选 3 个');
    await expect(invokeButton(page)).toHaveText('调用 3 个提供者');
    const titleBox = await page.locator('.debug-request-title').boundingBox();
    expect(titleBox.width).toBeGreaterThan(180);
    const concurrencyBox = await page.locator('.debug-concurrency').boundingBox();
    expect(concurrencyBox.height).toBeLessThanOrEqual(20);
    await list.evaluate((element) => {
        element.scrollTop = 40 * element.querySelector('tbody tr').getBoundingClientRect().height;
    });
    const metrics = await list.evaluate((element) => ({
        height: element.clientHeight,
        contentHeight: element.scrollHeight,
        scroll: element.scrollTop,
        top: element.getBoundingClientRect().top,
        head: element.querySelector('th').getBoundingClientRect().top
    }));
    expect(metrics.height).toBeLessThanOrEqual(224);
    expect(metrics.contentHeight).toBeGreaterThan(3100);
    expect(metrics.scroll).toBeGreaterThan(1000);
    expect(Math.abs(metrics.head - metrics.top)).toBeLessThan(2);
    const workbench = await page.locator('.debug-workbench').boundingBox();
    if (testInfo.project.name === 'desktop') expect(workbench.y).toBeLessThan(600);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    await page.evaluate(() => window.scrollTo(0, 0));
    await page.screenshot({path: testInfo.outputPath('providers-100-selected-3.png')});
    await panel.screenshot({path: testInfo.outputPath('providers-100-panel.png')});
    await page.screenshot({path: testInfo.outputPath('providers-100-full.png'), fullPage: true});

    await page.locator('.debug-argument-control').fill('keep-across-pages');
    await panel.getByRole('button', {name: '下一页', exact: true}).click();
    await expect(table.locator('tbody tr')).toHaveCount(25);
    await input(100).click();
    await panel.getByRole('button', {name: '仅看已选', exact: true}).click();
    await expect(table.locator('input:checked')).toHaveCount(4);
    await expect(panel.getByRole('button', {name: '上一页', exact: true})).toHaveCount(0);
    const search = page.getByRole('textbox', {name: '搜索调试提供者', exact: true});
    await search.fill('1300');
    await expect(table.locator('input')).toHaveCount(1);
    await expect(input(100)).toBeChecked();
    await expect(panel).toContainText('当前显示 1 个提供者，已选 4 个');
    await search.fill('no-such-provider');
    await expect(table).toContainText('没有匹配的已选提供者。');
    await expect(invokeButton(page)).toHaveText('调用 4 个提供者');
    await panel.getByRole('button', {name: '返回全部', exact: true}).click();
    await expect(search).toHaveValue('');
    await expect(table.locator('input')).toHaveCount(25);
    await panel.getByRole('button', {name: '上一页', exact: true}).click();
    await expect(table.locator('input')).toHaveCount(100);
    await expect(input(43)).toBeChecked();
    await search.fill('1242');
    await expect(table.locator('input')).toHaveCount(1);
    await expect(input(42)).toBeChecked();
    await expect(panel).toContainText('已选 4 个');
    await search.fill('');
    await expect(table.locator('input')).toHaveCount(100);
    await expect(page.locator('.debug-argument-control')).toHaveValue('keep-across-pages');
    await invokeButton(page).click();
    await expect(invokeButton(page)).toBeEnabled();
    expect(calls.sort()).toEqual(['large-0', 'large-100', 'large-42', 'large-43'].sort());
    await page.getByRole('switch', {name: '多选提供者', exact: true}).click();
    await expect(panel).toContainText('已选 1 个');
    await panel.getByRole('button', {name: '仅看已选', exact: true}).click();
    await search.fill('');
    await expect(input(100)).toBeChecked();
    await panel.getByRole('button', {name: '返回全部', exact: true}).click();
    await page.getByRole('switch', {name: '多选提供者', exact: true}).click();
    await input(43).click();
    unavailable = true;
    await page.getByRole('button', {name: '刷新', exact: true}).click();
    await expect(panel).toContainText('已选提供者中有离线或禁用实例');
    await expect(invokeButton(page)).toBeDisabled();
    await panel.getByRole('button', {name: '仅看已选', exact: true}).click();
    await input(43).click();
    await expect(input(43)).toHaveCount(0);
    await expect(invokeButton(page)).toHaveText('调用 1 个提供者');
    await expect(invokeButton(page)).toBeEnabled();
    await panel.getByRole('button', {name: '清空', exact: true}).click();
    await expect(table).toContainText('尚未选择提供者。');
    await expect(invokeButton(page)).toBeDisabled();
    await expect(panel.getByRole('button', {name: '清空', exact: true})).toBeDisabled();
    await panel.getByRole('button', {name: '返回全部', exact: true}).click();
    await page.getByRole('button', {name: '刷新', exact: true}).click();
    await expect(table.locator('input')).toHaveCount(100);
    await expect(table.locator('input:checked')).toHaveCount(0);
    await expect(invokeButton(page)).toBeDisabled();
    await expect(page.locator('.debug-argument-control')).toHaveValue('keep-across-pages');
    await search.fill('no-such-provider');
    await expect(table).toContainText('暂无匹配的提供者。');
    expect(errors).toEqual([]);
});

test('debug parameters use named scalar and JSON fields and support no arguments', async ({
                                                                                              page
                                                                                          }, testInfo) => {
    await enter(page);
    await order(page);
    await page
        .getByRole('region', {name: '方法', exact: true})
        .getByRole('button', {name: '调试', exact: true})
        .click();
    await expect(page.locator('.debug-argument-control')).toBeVisible();
    const original = new URL(page.url());
    const signature = 'mixed(java.lang.String, int, boolean, example.Payload)';
    const captured = [];
    let withPlugin = true;
    await page.route('**/api/service/debug?*', async (route) => {
        if (route.request().method() === 'POST') {
            captured.push(route.request().postDataJSON());
            return route.fulfill({json: {success: true, value: 'ok', elapsedMillis: 35}});
        }
        const response = await route.fetch();
        const contract = await response.json();
        const description = {
            methods: [
                {
                    javaSignature: signature,
                    parameters: [
                        {javaName: 'name', type: 'java.lang.String'},
                        {javaName: 'count', type: 'int'},
                        {javaName: 'enabled', type: 'boolean'},
                        {javaName: 'payload', type: 'example.Payload'}
                    ]
                },
                {javaSignature: 'ping()', parameters: []}
            ],
            types: {
                'example.Payload': {
                    javaType: 'example.Payload',
                    kind: 'object',
                    properties: [{javaName: 'value', type: 'java.lang.String'}]
                }
            }
        };
        contract.description = description;
        for (const endpoint of contract.endpoints) {
            endpoint.description = description;
            if (!withPlugin) delete endpoint.parameterTypes;
        }
        await route.fulfill({response, json: contract});
    });
    const hash = new URLSearchParams(original.hash.slice(1));
    hash.set('method', signature);
    await page.goto(`/#${hash}`);
    await page.reload();
    await expect(page.locator('.debug-argument-control')).toHaveCount(4);
    await expect(page.getByLabel('name', {exact: true})).toHaveValue('');
    await expect(page.getByLabel('payload', {exact: true})).toHaveValue('{\n  "value": ""\n}');
    await expect(page.locator('.debug-argument-type')).toHaveText(['String', 'int', 'boolean', 'JSON']);
    await expect(page.locator('textarea#debug-argument-0')).toBeVisible();
    await expect(page.locator('input#debug-argument-1')).toHaveValue('0');
    await expect(page.locator('input#debug-argument-2')).toHaveValue('false');
    await expect(page.getByRole('button', {name: '格式化', exact: true})).toHaveCount(1);
    const formatButton = page.getByRole('button', {name: '格式化', exact: true});
    await expect(formatButton.locator('svg')).toBeVisible();
    await expect(formatButton).toHaveText('');
    const editorBounds = await page.getByLabel('payload', {exact: true}).boundingBox();
    const formatBounds = await formatButton.boundingBox();
    expect(formatBounds.x).toBeGreaterThan(editorBounds.x);
    expect(formatBounds.y).toBeGreaterThan(editorBounds.y);
    expect(formatBounds.x + formatBounds.width).toBeLessThan(editorBounds.x + editorBounds.width);
    expect(formatBounds.y + formatBounds.height).toBeLessThan(editorBounds.y + editorBounds.height);
    await page.getByLabel('payload', {exact: true}).fill('{"value":"formatted"}');
    await page.getByRole('button', {name: '格式化', exact: true}).click();
    await expect(page.getByLabel('payload', {exact: true})).toHaveValue('{\n  "value": "formatted"\n}');
    await page.getByLabel('name', {exact: true}).fill('null "quoted"');
    await page.getByLabel('count', {exact: true}).fill('not a number');
    await invokeButton(page).click();
    await expect(page.getByRole('alert')).toContainText('count：');
    await page.getByLabel('count', {exact: true}).fill('42');
    await page.getByLabel('enabled', {exact: true}).fill('invalid');
    await invokeButton(page).click();
    await expect(page.getByRole('alert')).toContainText('enabled：');
    await page.getByLabel('enabled', {exact: true}).fill('false');
    await page.getByLabel('payload', {exact: true}).fill('broken json');
    await invokeButton(page).click();
    await expect(page.getByRole('alert')).toContainText('payload：');
    expect(captured).toHaveLength(0);
    const payload = Object.fromEntries(Array.from({length: 30}, (_, i) => [`field${i}`, i]));
    await page.getByLabel('payload', {exact: true}).fill(JSON.stringify(payload, null, 2));
    await expect
        .poll(() =>
            page.getByLabel('payload', {exact: true}).evaluate((el) => el.scrollHeight - el.clientHeight)
        )
        .toBeGreaterThan(0);
    expect((await page.getByLabel('payload', {exact: true}).boundingBox()).height).toBeLessThanOrEqual(480);
    await invokeButton(page).click();
    await expect(page.locator('.debug-result-heading')).toContainText('成功 · 35 ms');
    expect(captured[0].arguments).toEqual(['null "quoted"', 42, false, payload]);
    await page.screenshot({path: testInfo.outputPath('named-debug-parameters.png'), fullPage: true});
    const headingBox = await page.locator('.debug-result-heading').boundingBox();
    const stateBox = await page.locator('.debug-result-toggle').boundingBox();
    expect(stateBox.x).toBeCloseTo(headingBox.x, 0);
    hash.set('method', 'ping()');
    await page.goto(`/#${hash}`);
    await page.reload();
    await expect(page.getByText('此方法无需参数。')).toBeVisible();
    await expect(page.locator('.debug-argument-control')).toHaveCount(0);
    await invokeButton(page).click();
    await expect(page.locator('.debug-result-heading')).toContainText('成功');
    expect(captured.at(-1).arguments).toEqual([]);
    withPlugin = false;
    hash.set('method', signature);
    await page.goto(`/#${hash}`);
    await page.reload();
    await expect(page.locator('.debug-argument-control')).toHaveCount(4);
    await expect(page.locator('textarea#debug-argument-0')).toBeVisible();
    await expect(page.locator('textarea#debug-argument-3')).toBeVisible();
    await expect(page.locator('input#debug-argument-1')).toHaveValue('0');
    await expect(page.getByRole('button', {name: '格式化', exact: true})).toHaveCount(0);
});

test('console settings disable missing JDBC drivers and choose an available default', async ({page}) => {
    // 浏览器单独验证能力结果的消费；真实缺包启动及写接口拒绝由 Java 场景覆盖。
    await page.route('**/api/settings?*', async (route) => {
        const response = await route.fetch();
        const settings = await response.json();
        for (const database of settings.features.jdbcDatabases) {
            if (['mysql', 'postgresql'].includes(database.key)) {
                database.available = false;
                database.reason = `当前控制台未安装可用的 ${database.label} 驱动`;
            }
        }
        await route.fulfill({response, json: settings});
    });
    await login(page);
    await page.locator('.tabbar').getByRole('button', {name: '连接注册中心'}).click();
    await page.getByRole('button', {name: 'JDBC', exact: true}).click();
    const databases = page.getByLabel('数据库', {exact: true});
    await expect(databases).toHaveValue('h2');
    await expect(databases.locator('option[value="mysql"]')).toHaveJSProperty('disabled', true);
    await expect(databases.locator('option[value="mysql"]')).toHaveText('MySQL（不可用）');
    await expect(databases.locator('option[value="mysql"]')).toHaveAttribute('title', /MySQL 驱动/);
    await expect(databases.locator('option[value="h2"]')).toHaveJSProperty('disabled', false);
    await expect(page.getByRole('alert')).toHaveCount(0);
});

test('console settings preserve an existing connection whose driver is unavailable', async ({page}) => {
    await enter(page);
    const profiles = await (await page.request.get('/api/clusters')).json();
    const cluster = profiles.find((item) => item.name === clusterProfile.name);
    await page.route('**/api/settings?*', async (route) => {
        const response = await route.fetch();
        const settings = await response.json();
        const h2 = settings.features.jdbcDatabases.find((database) => database.key === 'h2');
        h2.available = false;
        h2.reason = '当前控制台未安装可用的 H2 驱动';
        await route.fulfill({response, json: settings});
    });
    await page.goto(`/#view=connection&id=${cluster.id}`);
    await page.reload();
    await expect(page.getByLabel('数据库', {exact: true})).toHaveValue('h2');
    await expect(page.getByRole('alert')).toContainText('当前控制台未安装可用的 H2 驱动');
    await expect(page.getByRole('button', {name: '测试连接', exact: true})).toBeDisabled();
    await expect(page.getByRole('button', {name: '保存并进入', exact: true})).toBeDisabled();
});

test('console settings keep missing debug plugins disabled while installed protocols still call', async ({
                                                                                                             page
                                                                                                         }) => {
    await page.route('**/api/settings?*', async (route) => {
        const response = await route.fetch();
        const settings = await response.json();
        settings.features.debugProtocols = settings.features.debugProtocols.filter((name) => name !== 'hprose');
        await route.fulfill({response, json: settings});
    });
    await enter(page);
    await order(page);
    // 服务上报的协议仍然展示；只有本机缺插件的调试操作被禁用。
    await expect(
        page.getByRole('tablist', {name: '服务协议'}).getByRole('tab', {name: 'hprose', exact: true})
    ).toBeEnabled();
    // 固定进入发布了 Hprose 的 east 实例，避免目录预览随机选到仅发布 RSF 的副本。
    await tab(page, '提供者');
    await page.locator('.member-table tbody tr').filter({hasText: 'east'}).locator('.instance-link').click();
    await page
        .getByRole('region', {name: '方法', exact: true})
        .getByRole('button', {name: '调试', exact: true})
        .click();
    const protocols = page.getByRole('group', {name: '调试协议', exact: true});
    await expect(protocols.getByRole('button', {name: 'hprose', exact: true})).toBeDisabled();
    await expect(protocols.getByRole('button', {name: 'hprose', exact: true})).toHaveAttribute(
        'title',
        /未安装 hprose/
    );
    await expect(protocols.getByRole('button', {name: 'rsf', exact: true})).toHaveAttribute(
        'aria-pressed',
        'true'
    );
    await page.locator('.debug-argument-control').fill('capability-rsf');
    await invokeButton(page).click();
    await expect(page.locator('.debug-response')).toContainText('order:capability-rsf');
});

test('console settings prevent calls when no advertised debug protocol is installed', async ({page}) => {
    await page.route('**/api/settings?*', async (route) => {
        const response = await route.fetch();
        const settings = await response.json();
        settings.features.debugProtocols = [];
        await route.fulfill({response, json: settings});
    });
    await enter(page);
    await order(page);
    await page
        .getByRole('region', {name: '方法', exact: true})
        .getByRole('button', {name: '调试', exact: true})
        .click();
    await expect(invokeButton(page)).toBeDisabled();
    await expect(page.getByRole('alert')).toContainText('当前控制台未安装');
    const protocols = page.getByRole('group', {name: '调试协议', exact: true}).getByRole('button');
    await expect(protocols).not.toHaveCount(0);
    for (const button of await protocols.all()) await expect(button).toBeDisabled();
});
