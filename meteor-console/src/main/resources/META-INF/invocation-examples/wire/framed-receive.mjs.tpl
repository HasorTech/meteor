    if (pending.length < 4) continue;
    const size = pending.readUInt32BE(0);
    if (pending.length < 4 + size) continue;
    console.log(pending.subarray(4, 4 + size).toString('hex'));
    complete = true;
    break;
