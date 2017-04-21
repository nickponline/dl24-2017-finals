#!/usr/bin/env python3
import argparse
import asyncio
import dl24.client
from dl24.client import command


class Client(dl24.client.ClientBase):
    @command('TEST')
    async def test(self, foo, bar=3, *, baz=None):
        return int(await self.readline())


async def main():
    parser = argparse.ArgumentParser()
    dl24.client.add_arguments(parser)
    args = parser.parse_args()

    try:
        connect_args = Client.connect_args(args)
    except ValueError as error:
        parser.error(str(error))
    client = Client(*connect_args)
    await client.connect()
    print(await client.test(foo='foo', baz=2))
    await client.wait()


if __name__ == '__main__':
    loop = asyncio.get_event_loop()
    loop.run_until_complete(main())
