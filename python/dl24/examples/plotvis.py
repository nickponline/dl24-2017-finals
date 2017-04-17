#!/usr/bin/env python3

import math
import asyncio
import dl24.visualization
import gi
gi.require_version('Gtk', '3.0')
from gi.repository import Gtk
import gbulb


class Dot(object):
    def __init__(self, phase):
        self.phase = float(phase)
        self.direction = 1

    @property
    def x(self):
        return math.cos(self.phase)

    @property
    def y(self):
        return math.sin(self.phase)

    def advance(self, incr):
        self.phase += self.direction * incr
        self.phase %= 2 * math.pi


class Window(dl24.visualization.Window):
    def __init__(self, *args, **kwargs):
        super(Window, self).__init__(*args, **kwargs)
        N = 5
        self.dots = [Dot(i * 2 * math.pi / N) for i in range(N)]
        self.dots_artist = self.axes.plot([dot.x for dot in self.dots],
                                          [dot.y for dot in self.dots], 'o',
                                          markersize=20, picker=30)[0]
        self.canvas.mpl_connect('pick_event', self._pick_handler)
        self.add_artists([self.dots_artist])

    def _reverse(self, button):
        for dot in self.dots:
            dot.direction = -dot.direction

    def _pick_handler(self, event):
        if event.artist is self.dots_artist and len(event.ind) == 1:
            ind = event.ind[0]
            try:
                dot = self.dots[event.ind[0]]
            except IndexError:
                pass
            else:
                dot.direction = -dot.direction

    def make_widgets(self, canvas):
        buttons = Gtk.Box.new(Gtk.Orientation.HORIZONTAL, 4)
        reverse_button = Gtk.Button.new_with_label("Reverse")
        reverse_button.connect('clicked', self._reverse)
        buttons.pack_start(reverse_button, False, False, 0)

        box = Gtk.Box.new(Gtk.Orientation.VERTICAL, 4)
        box.pack_start(buttons, False, True, 0)
        box.pack_start(canvas, True, True, 0)
        self.add(box)

    async def animate(self):
        text = self.axes.text(
            0.02, 0.98, '',
            horizontalalignment='left', verticalalignment='top',
            transform=self.axes.transAxes)
        self.add_artists([text])
        t = 0.0
        while True:
            self.dots_artist.set_data([dot.x for dot in self.dots],
                                     [dot.y for dot in self.dots])
            text.set_text('{:.2f}'.format(t))
            self.event_source()
            await asyncio.sleep(0.01)
            t += 0.02
            for dot in self.dots:
                dot.advance(0.02)


def main():
    gbulb.install(gtk=True)
    loop = asyncio.get_event_loop()
    window = Window(title='Demo')
    window.set_default_size(1024, 768)
    window.show_all()
    loop.create_task(window.animate())
    loop.run_forever()


if __name__ == '__main__':
    main()
