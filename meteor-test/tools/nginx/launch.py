"""Allocate one Nginx listener under a service-wide startup lock; exec keeps process ownership."""
import fcntl
from pathlib import Path
import socket
import subprocess
import sys
import time

directory = Path(sys.argv[1])
# All instances share this service, but own separate configuration, PID and logs.
with open('/tmp/meteor-nginx-start.lock', 'w') as lock:
    fcntl.flock(lock, fcntl.LOCK_EX)
    with socket.socket() as listener:
        listener.bind(('0.0.0.0', 0))
        port = listener.getsockname()[1]
    template = (directory / 'nginx.template').read_text()
    (directory / 'nginx.conf').write_text(template.replace('@PORT@', str(port)))
    process = subprocess.Popen(['nginx', '-p', str(directory) + '/', '-c', str(directory / 'nginx.conf')])
    try:
        deadline = time.monotonic() + 15
        while True:
            if process.poll() is not None:
                raise RuntimeError('Nginx exited before opening its listener')
            try:
                with socket.create_connection(('127.0.0.1', port), timeout=0.2):
                    break
            except OSError:
                if time.monotonic() >= deadline:
                    raise TimeoutError('Nginx listener startup timed out')
                time.sleep(0.02)
        (directory / 'port.tmp').write_text(str(port))
        (directory / 'port.tmp').replace(directory / 'port')
    except BaseException:
        process.terminate()
        process.wait(timeout=5)
        raise
# The native process supervisor owns this launcher and the Nginx descendant.
sys.exit(process.wait())
