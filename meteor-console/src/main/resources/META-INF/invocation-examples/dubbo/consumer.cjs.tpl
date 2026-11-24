const { DirectlyDubbo, java } = require('dubbo2.js');
async function main() {
  const client = DirectlyDubbo.from({ dubboAddress: {{authority}}, dubboVersion: '2.0.2', dubboInvokeTimeout: 5 });
  const service = client.proxyService({
    dubboInterface: {{service}}, group: {{group}}, version: {{version}},
    methods: {
      [{{method}}]() { return [{{arguments}}]; }
    }
  });
  const result = await service[{{method}}]();
  if (result.err) throw result.err;
  process.stdout.write(JSON.stringify(result.res) + '\n', () => process.exit(0));
}
main().catch(error => {
  process.stderr.write(String(error.stack || error) + '\n', () => process.exit(1));
});
