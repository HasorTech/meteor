import hprose from "hprose";

const url = {{url}};
const call = {{call}};
function values(value) {
    if (Array.isArray(value)) return value.map(values);
    if (value && typeof value === "object") {
        if (Object.keys(value).length === 1 && "$binary" in value) return Buffer.from(value.$binary, "base64");
        return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, values(item)]));
    }
    return value;
}
const client = hprose.Client.create(url);
client.timeout = 15000;
const service = await client.ready();
console.log(await service[call.method](...values(call.args)));
