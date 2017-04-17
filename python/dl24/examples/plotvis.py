#!/usr/bin/env python3

import math
import asyncio
import dl24.visualization
import gi
gi.require_version('Gtk', '3.0')
from gi.repository import Gtk
import gbulb


async def animate(window):
    dot = window.axes.plot([0.0], [0.0], 'o')[0]
    text = window.axes.text(
        0.02, 0.98, '',
        horizontalalignment='left', verticalalignment='top',
        transform=window.axes.transAxes)
    window.add_artists([dot, text])
    t = 0.0
    while True:
        dot.set_data([math.cos(t)], [math.sin(t)])
        text.set_text(t)
        window.event_source()
        await asyncio.sleep(0.01)
        t += 0.02


def main():
    gbulb.install(gtk=True)
    loop = asyncio.get_event_loop()
    window = dl24.visualization.Window()
    window.set_default_size(1024, 768)
    window.show_all()
    loop.create_task(animate(window))
    loop.run_forever()


if __name__ == '__main__':
    main()
