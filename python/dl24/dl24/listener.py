"""Reads a log file written by the proxy, optionally using inotify to
react to new records
"""

import os
import sys
import asyncio
import watchdog
import watchdog.events
import watchdog.observers
from . import proxy as _proxy


class ModifiedHandler(watchdog.events.FileSystemEventHandler):
    """Trigger an asyncio.Event when a watched file is modified."""
    def __init__(self, loop, notify):
        self._loop = loop
        self._notify = notify

    def on_modified(self, event):
        self._loop.call_soon_threadsafe(lambda: self._notify.set())


class FileReadTransport(asyncio.ReadTransport):
    """Transport that reads from a file, using watchdog to check
    for more data being appended.
    """
    def __init__(self, loop, filename, protocol, observer=None, extra=None):
        super(FileReadTransport, self).__init__(extra)
        if loop is None:
            loop = asyncio.get_event_loop()
        self._protocol = protocol
        self._unpaused = asyncio.Event(loop=loop)
        self._modified = asyncio.Event(loop=loop)
        self._task = None
        self._closing = False
        self._file = open(filename, 'rb')
        self._task = loop.create_task(self._reader())
        if observer is None:
            self._observer = watchdog.observers.Observer()
        else:
            self._observer = observer
        handler = ModifiedHandler(loop, self._modified)
        # This is overkill, watching the entire directory instead of just the
        # file we want. However, watchdog doesn't allow watching just one
        # file, and spurious wakeups are harmless.
        self._observer.schedule(handler, os.path.dirname(os.path.abspath(filename)))
        self._observer.start()
        self.resume_reading()

    def pause_reading(self):
        self._unpaused.clear()

    def resume_reading(self):
        self._unpaused.set()

    def close(self):
        if self._task is not None and not self._task.done():
            self._task.cancel()
        if self._observer is not None:
            self._observer.stop()
            self._observer = None
        self._closing = True

    def is_closing(self):
        return self._closing

    async def _reader(self):
        try:
            self._protocol.connection_made(self)
            while True:
                data = self._file.read(4096)
                if not data:
                    self._modified.clear()
                    # Hit EOF. Wait until watchdog releases us
                    await self._modified.wait()
                else:
                    # We have data. If downstream has exerted flow
                    # control, wait for it to unblock.
                    await self._unpaused.wait()
                    self._protocol.data_received(data)
        except asyncio.CancelledError:
            pass
        finally:
            self._protocol.connection_lost(None)

    def __del__(self):
        self.close()


def open_file_connection(filename, observer=None, loop=None):
    """Open a :class:`asyncio.StreamReader` which reads from `filename`."""
    if loop is None:
        loop = asyncio.get_event_loop()
    reader = asyncio.StreamReader(limit=1048576, loop=loop)
    protocol = asyncio.StreamReaderProtocol(reader, loop=loop)
    transport = FileReadTransport(loop, filename, protocol, observer=observer)
    return reader


class ProxyLogRecords(object):
    def __init__(self, reader):
        self._reader = reader

    def __aiter__(self):
        return self

    async def __anext__(self):
        line = await self._reader.readline()
        if line == b'':
            raise StopAsyncIteration
        else:
            line = line.decode('utf-8', errors='strict')
            return _proxy.Record.parse(line)


def proxy_log_records(reader):
    """Asynchronous generator that yields records from a proxy log."""
    return ProxyLogRecords(reader)
