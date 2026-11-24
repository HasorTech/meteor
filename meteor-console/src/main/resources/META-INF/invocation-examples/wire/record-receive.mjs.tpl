    while (pending.length >= 4) {
      const marker = pending.readUInt32BE(0), size = marker & 0x7fffffff;
      if (pending.length < 4 + size) break;
      process.stdout.write(pending.subarray(4, 4 + size).toString('hex'));
      pending = pending.subarray(4 + size);
      if (marker >>> 31) {
        console.log();
        complete = true;
        break;
      }
    }
    if (complete) break;
