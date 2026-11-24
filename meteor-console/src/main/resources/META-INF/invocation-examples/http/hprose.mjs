import hprose from 'hprose';

const client = hprose.Client.create({{url}});
client.timeout = 15000;
const service = await client.ready();
console.log(await service[{{method}}](...{{arguments}}));
