#!/usr/bin/env python3

import math
import asyncio
import gi
gi.require_version('Gtk', '3.0')
from gi.repository import Gtk, GObject
import matplotlib
matplotlib.use('GTK3Agg')
import numpy as np
from matplotlib.backends.backend_gtk3agg import FigureCanvasGTK3Agg as FigureCanvas
import matplotlib.animation
import gbulb


class ManualEventSource(object):
    """Provides the matplotlib event source interface, but does not use a
    timer.

    The user calls the instance to trigger the event.
    """
    def __init__(self, callbacks=None, trigger_on_start=True):
        if callbacks is None:
            self.callbacks = []
        else:
            self.callbacks = list(callbacks)
        self._running = False
        self._trigger_on_start = True

    def start(self):
        if not self._running:
            self._running = True
            if self._trigger_on_start:
                self()

    def stop(self):
        self._running = False

    def add_callback(self, func, *args, **kwargs):
        self.callbacks.append((func, args, kwargs))

    def remove_callback(self, func, *args, **kwargs):
        if args or kwargs:
            self.callbacks.remove((func, args, kwargs))
        else:
            funcs = [c[0] for c in self.callbacks]
            if func in funcs:
                self.callbacks.pop(funcs.index(func))

    def __call__(self):
        if not self._running:
            return
        for func, args, kwargs in self.callbacks:
            ret = func(*args, **kwargs)
            if ret == False:
                self.callbacks.remove((func, args, kwargs))


class VisWindow(Gtk.Window):
    def __init__(self):
        super(VisWindow, self).__init__(title='Demo')
        self.connect('delete-event', lambda *args: asyncio.get_event_loop().stop())
        self.figure = matplotlib.figure.Figure(tight_layout=True)
        self.axes = self.figure.add_subplot(1, 1, 1)
        self.axes.set_xlim(-1, 1)
        self.axes.set_ylim(-1, 1)
        self.artists = []
        self.canvas = FigureCanvas(self.figure)
        box = Gtk.Box.new(Gtk.Orientation.VERTICAL, 4)
        box.pack_start(self.canvas, True, True, 0)
        self.add(box)
        # Set up animation
        self.event_source = ManualEventSource()
        self.add_artists(self.axes.plot([0.5], [0.7], 'o'))
        self.animation = matplotlib.animation.FuncAnimation(
            self.figure, self.animate,
            event_source=self.event_source,
            init_func=lambda: self.artists,
            blit=False)

    def add_artists(self, artists):
        self.artists.extend(artists)
        self.event_source()

    def animate(self, framedata):
        return self.artists


async def animate(window):
    dot = window.artists[0]
    t = 0.0
    while True:
        dot.set_data([math.cos(t)], [math.sin(t)])
        window.event_source()
        await asyncio.sleep(0.01)
        t += 0.02


def main():
    gbulb.install(gtk=True)
    loop = asyncio.get_event_loop()
    window = VisWindow()
    window.set_default_size(1024, 768)
    window.show_all()
    loop.create_task(animate(window))
    loop.run_forever()


if __name__ == '__main__':
    main()
