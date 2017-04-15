#!/usr/bin/env python3

import asyncio
import logging
import argparse
import sys
import time
from . import client as _client


LIMIT = 2 ** 20    # Maximum line length that can be handled without crashing
_logger = logging.getLogger(__name__)


class UsageError(RuntimeError):
    pass


def parse_args():
    parser = argparse.ArgumentParser()
    _client.add_arguments(parser)
    parser.add_argument('port', metavar='PORT', type=int, help='Local port')
    parser.add_argument('--log', metavar='FILE', help='Log all communication to file')
    return parser.parse_args()


class Client(object):
    def __init__(self, reader, writer):
        self.reader = reader
        self.writer = writer

    async def get_record(self):
        """Read a single record from the client.

        In the default implementation, a record is simply a line. When there
        are multiple transmitting clients, records are sent to the server
        atomically.

        Returns an empty bytes object on end-of-file.
        """
        return await self.reader.readline()

    async def run(self, proxy):
        peer = self.writer.get_extra_info('peername')
        _logger.info('Client %s connected', peer)
        proxy.clients.add(self)
        self.writer.write(b'PROXY-NOLOGIN\n')
        await self.writer.drain()
        try:
            while True:
                record = await self.get_record()
                if record == b'' and self.reader.at_eof():
                    break
                proxy.writer.write(record)
                proxy.append_to_log('>', record)
                await proxy.writer.drain()
        finally:
            _logger.info('Client %s disconnected', peer)
            self.writer.close()
            proxy.clients.remove(self)


class Proxy(_client.ClientBase):
    def __init__(self, host, port, user, password, local_port, log_filename):
        super(Proxy, self).__init__(host, port, user, password)
        self.local_port = local_port
        self.clients = set()
        if log_filename is None:
            self.log = None
        else:
            self.log = open(log_filename, 'wb', buffering=4096)

    def append_to_log(self, direction, data):
        if self.log and data:
            header = '{} {} '.format(time.asctime(), direction).encode('utf-8')
            self.log.write(header)
            self.log.write(data)
            if data[-1:] != b'\n':
                self.log.write(b'\n')
            self.log.flush()

    async def server_cb(self, reader, writer):
        client = Client(reader, writer)
        await client.run(self)

    async def get_record(self):
        """Read a single record from the server.

        In the default implementation, a record is a line. Clients are always
        sent whole records.

        Returns an empty bytes object on EOF.
        """
        return await self.reader.readline()

    async def run(self):
        server = None
        try:
            _logger.info('Connecting to %s:%s', self.host, self.port)
            await self.connect()
            _logger.info('Connected')
            _logger.info('Listening on port %d', self.local_port)
            server = await asyncio.start_server(self.server_cb, port=self.local_port,
                                                limit=LIMIT)
            while True:
                record = await self.get_record()
                if record == b'' and self.reader.at_eof():
                    break
                self.append_to_log('<', record)
                for client in self.clients:
                    client.writer.write(record)
                    # We don't drain, since then an unresponsive client could jam
                    # it all up. TODO: kick off unresponsive clients.
            _logger.info('Server disconnected, shutting down')
        finally:
            if server is not None:
                server.close()
            if self.log:
                self.log.close()
            self.close()


async def async_main():
    args = parse_args()
    connect_args = Proxy.connect_args(args)
    proxy = Proxy(*connect_args, args.port, args.log)
    await proxy.run()
    return 0


def main():
    logging.basicConfig(
        format='%(asctime)s %(levelname)s %(filename)s:%(lineno)d: %(message)s',
        level='INFO')
    try:
        ret = asyncio.get_event_loop().run_until_complete(async_main())
        sys.exit(ret)
    except (RuntimeError, ConnectionRefusedError) as e:
        print(e)
        sys.exit(1)
