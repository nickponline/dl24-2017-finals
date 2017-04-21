#!/usr/bin/env python3
import asyncio
import dl24.listener

from watchdog.observers.polling import PollingObserver

async def async_main():
    conn = dl24.listener.open_file_connection('proxy.log', observer=PollingObserver())
    while True:
        async for record in dl24.listener.proxy_log_records(conn):
            print('Next record')
            print(record.message)


if __name__ == '__main__':
    asyncio.get_event_loop().run_until_complete(async_main())
