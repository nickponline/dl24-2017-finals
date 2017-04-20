#!/usr/bin/env python3

import asyncio
import logging
import argparse
import sys
import datetime
import enum
import json
from . import client as _client


LIMIT = 2 ** 20    # Maximum line length that can be handled without crashing
_logger = logging.getLogger(__name__)


class UsageError(RuntimeError):
    pass


class Direction(enum.Enum):
    TO_SERVER = 0
    TO_CLIENT = 1


class Record(object):
    """Encapsulates a message sent through the proxy.

    It has a serialisation method for transmission to the log file, as well as
    deserialisation to read back the lines. Each record is written on a single
    line in the file, even if it has embedded newlines.
    """
    TIME_FORMAT = '%Y-%m-%dT%H:%M:%S.%f%z'

    def __init__(self, direction, message, timestamp=None):
        if timestamp is None:
            timestamp = datetime.datetime.now(datetime.timezone.utc)
        self.direction = direction
        self.message = message
        self.timestamp = timestamp.astimezone(datetime.timezone.utc)

    def __str__(self):
        message_str = self.message.decode('utf-8', errors='surrogateescape')
        return '{} {} {}'.format(self.timestamp.strftime(self.TIME_FORMAT),
                                 '>' if self.direction == Direction.TO_SERVER else '<',
                                 json.dumps(message_str))

    @classmethod
    def parse(cls, raw):
        time_str, dir_str, message_str = raw.split(' ', 2)
        dir_map = {'>': Direction.TO_SERVER, '<': Direction.TO_CLIENT}
        if dir_str not in dir_map:
            raise ValueError('Invalid direction character "{}"'.format(dir_str))
        message = json.loads(message_str)
        if not isinstance(message, str):
            raise ValueError('Message is not a JSON str: "{}"'.format(message))
        return Record(dir_map[dir_str],
                      message.encode('utf-8', errors='surrogateescape'),
                      datetime.datetime.strptime(time_str, cls.TIME_FORMAT))


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
                proxy.append_to_log(Record(Direction.TO_SERVER, record))
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
            self.log = open(log_filename, 'w', encoding='utf-8', buffering=4096)

    def append_to_log(self, record):
        if self.log:
            print(record, file=self.log)
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
                self.append_to_log(Record(Direction.TO_CLIENT, record))
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
    # Workaround for vext installing log handlers early
    root_logger = logging.getLogger()
    while root_logger.handlers:
        root_logger.removeHandler(root_logger.handlers[-1])
    logging.basicConfig(
        format='%(asctime)s %(levelname)s %(filename)s:%(lineno)d: %(message)s',
        level=logging.INFO)
    try:
        ret = asyncio.get_event_loop().run_until_complete(async_main())
        sys.exit(ret)
    except (RuntimeError, ConnectionRefusedError) as e:
        print(e)
        sys.exit(1)
