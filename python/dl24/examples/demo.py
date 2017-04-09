#!/usr/bin/env python3
import argparse
import asyncio
import os
import dl24.client
from dl24.client import command


class Client(dl24.client.ClientBase):
    @command('TEST')
    async def test(self, foo, bar=3, *, baz=None):
        return int(await self.readline())


async def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('host', help='Remote host')
    parser.add_argument('port', help='Remote port')
    parser.add_argument('--user', help='Login name [$DL24_USER]')
    parser.add_argument('--pass', dest='password', help='Password [$DL24_PASS]')
    args = parser.parse_args()

    host = args.host
    port = args.port
    if args.user is not None:
        user = args.user
    else:
        user = os.environ.get('DL24_USER')
    if user is None:
        parser.error('User not set')
    if args.password is not None:
        password = args.password
    else:
        password = os.environ.get('DL24_PASS')
    if password is None:
        parser.error('Password not set')

    client = await Client.create(host, port, user, password)
    print(await client.test(foo='foo', baz=2))
    await client.wait()


if __name__ == '__main__':
    loop = asyncio.get_event_loop()
    loop.run_until_complete(main())
