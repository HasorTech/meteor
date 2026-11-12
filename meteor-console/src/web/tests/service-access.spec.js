import { test, expect } from '@playwright/test';
async function service(page) {
  await page.goto('/');
  await page.getByLabel('账号', { exact: true }).fill('admin');
  await page.getByLabel('密码', { exact: true }).fill('test-password');
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page.getByRole('navigation', { name: '主要导航' })).toBeVisible();
  const clusters = await (await page.request.get('/api/clusters')).json();
  let cluster = clusters.find((item) => item.name === '协议工作区');
  if (!cluster)
    cluster = await (
      await page.request.post('/api/clusters', {
        data: {
          name: '协议工作区',
          type: 'jdbc',
          database: 'h2',
          address: 'jdbc:h2:mem:console-browser;DB_CLOSE_DELAY=-1',
          username: 'sa',
          password: ''
        }
      })
    ).json();
  const services = await (
    await page.request.get(`/api/services?mode=registry&clusterId=${cluster.id}`)
  ).json();
  const serviceId = services.items.find((item) => item.info.name.includes('OrderService')).info.serviceId;
  await page.goto(`/#view=service&clusterId=${cluster.id}&serviceId=${encodeURIComponent(serviceId)}`);
  await page.reload();
  await page.getByRole('button', { name: '提供者', exact: true }).click();
  await page
    .locator('.member-table tbody tr')
    .filter({ hasText: 'east' })
    .locator('.instance-link')
    .first()
    .click();
  await page
    .getByRole('navigation', { name: '服务详情' })
    .getByRole('button', { name: '服务信息', exact: true })
    .click();
  return page.getByRole('region', { name: '服务接入点', exact: true });
}

test('service access opens the shared drawer with the selected language and endpoint', async ({ page }) => {
  const errors = [];
  page.on('pageerror', (error) => errors.push(error.message));
  const region = await service(page);
  await expect(
    page.getByRole('navigation', { name: '服务详情' }).getByRole('button', { name: '示例', exact: true })
  ).toHaveCount(0);
  const protocols = region.getByRole('tablist', { name: '服务协议' });
  await expect(protocols.getByRole('tab').first()).toContainText('默认');
  const links = region.locator('.example-language-links');
  await expect(links.getByRole('button')).toHaveText([
    'Meteor',
    'Java RPC SDK',
    'Python',
    'Node.js',
    'Go',
    'C',
    'C++',
    'JavaScript',
    'Shell'
  ]);
  await expect(region.locator('pre')).toHaveCount(0);
  await expect(region.getByRole('tablist', { name: '调用角色' })).toHaveCount(0);
  const render = page.waitForResponse(
    (response) => new URL(response.url()).pathname === '/api/examples/render'
  );
  await links.getByRole('button', { name: 'Python', exact: true }).click();
  const generated = await render;
  const params = new URL(generated.url()).searchParams;
  expect(params.get('protocol')).toBe('hprose');
  expect(params.get('address')).toMatch(/^hprose:\/\//);
  expect(params.get('instanceId')).toBeTruthy();
  const drawer = page.getByRole('dialog');
  await expect(drawer.locator('pre code.language-python').first()).not.toBeEmpty();
  await expect(drawer.getByRole('tablist', { name: '示例语言' })).toHaveCount(0);
  await expect(drawer.getByRole('tablist', { name: '调用角色' }).getByRole('tab')).toHaveText([
    '提供者',
    '消费者'
  ]);
  await drawer.getByRole('button', { name: '关闭协议示例' }).click();
  await expect(links.getByRole('button', { name: 'Python', exact: true })).toBeFocused();
  await protocols.getByRole('tab', { name: 'rsf', exact: true }).click();
  await expect(links.getByRole('button', { name: 'Python', exact: true })).toBeDisabled();
  await links.getByRole('button', { name: 'Meteor', exact: true }).click();
  await expect(drawer.locator('pre code.language-java').first()).toContainText('MetConfiguration');
  await drawer.getByRole('tab', { name: '提供者', exact: true }).click();
  await expect(drawer.locator('pre code.language-java').first()).toContainText('register()');
  await drawer.getByRole('button', { name: '关闭协议示例' }).click();
  await protocols.getByRole('tab').first().focus();
  await page.keyboard.press('ArrowDown');
  await expect(protocols.getByRole('tab', { name: 'rsf', exact: true })).toBeFocused();
  expect(errors).toEqual([]);
});

test('service access keeps one language link row and isolates protocol addresses across screen sizes', async ({
  page
}, testInfo) => {
  const names = [
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
  await page.route('**/api/service?*', async (route) => {
    const response = await route.fetch();
    const data = await response.json();
    if (new URL(route.request().url()).searchParams.has('instanceId')) {
      data.info.protocols = names;
      data.endpoints = names.map((name, index) => ({
        protocolName: name,
        defaultProtocol: name === 'rsf',
        contextPath: `/${name}`,
        publishedAddress: `${name}://127.0.0.1:${19200 + index}/${name}`,
        endpoints: {
          [name]: {
            endpoint: `${name}://127.0.0.1:${19200 + index}/${name}`,
            http: `http://127.0.0.1:${19200 + index}/${name}/console-debug/1.0.0`,
            ...(name === 'soap' ? { schema: 'http://127.0.0.1:19210/soap/StatusService?wsdl' } : {})
          }
        }
      }));
    }
    await route.fulfill({ response, json: data });
  });
  const region = await service(page);
  const protocols = region.getByRole('tablist', { name: '服务协议' });
  await expect(protocols.getByRole('tab')).toHaveCount(15);
  await protocols.getByRole('tab', { name: 'connect', exact: true }).click();
  await protocols.getByRole('tab', { name: 'soap', exact: true }).click();
  await expect(region.locator('[data-endpoint-type=schema] a')).toHaveAttribute(
    'href',
    /StatusService\?wsdl$/
  );
  await protocols.getByRole('tab', { name: 'jsonrpc', exact: true }).click();
  await expect(region.locator('[data-endpoint-type=schema]')).toHaveCount(0);
  await expect(region.locator('.example-language-links')).toHaveCount(1);
  await expect(region.locator('pre')).toHaveCount(0);
  for (const width of [1482, 1100, 700, 390]) {
    await page.setViewportSize({ width, height: 1062 });
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(width);
    expect(
      await region.locator('.service-access').evaluate((element) => element.scrollWidth - element.clientWidth)
    ).toBeLessThanOrEqual(1);
    await page.screenshot({ path: testInfo.outputPath(`service-access-${width}.png`), fullPage: true });
  }
});
