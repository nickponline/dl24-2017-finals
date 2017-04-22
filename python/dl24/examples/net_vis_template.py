#!/usr/bin/env python3
import argparse
import asyncio
import dl24.client
import matplotlib
from dl24.client import command
import math
import asyncio
import dl24.visualization
import gi
gi.require_version('Gtk', '3.0')
from gi.repository import Gtk
import gbulb

shapes = {}

# Represents a pickable coloured square with and number of clicks counter
class Square(object):
    def __init__(self, x, y, size):
        self.x = x
        self.y = y

        self.xy = [
            [ x + size, y + size],
            [ x + size, y - size],
            [ x - size, y - size],
            [ x - size, y + size],
        ]

        self.count = 0

    def draw(self, axes):
        self.shape = matplotlib.patches.Polygon( self.xy, closed=True, fill=False, edgecolor='black', picker=1)
        self.artist = axes.axes.add_patch(self.shape)
        shapes[self.artist] = self
        self.text = axes.text( self.x, self.y,  str(self.count), horizontalalignment='center', verticalalignment='center')
        self.text.set_text('{}'.format(self.count))
        return [self.artist, self.text]

    def onpick(self):
        import random
        color = random.choice(['yellow', 'green', 'blue'])
        self.shape.set_facecolor(color)
        self.shape.set_fill(True)
        self.count += 1
        self.text.set_text('{}'.format(self.count))
        print("Square was picked")

class Window(dl24.visualization.Window):
    def __init__(self, *args, **kwargs):
        super(Window, self).__init__(*args, **kwargs)

        for x in range(20):
            for y in range(20):
                self.artists = Square(x, y, 0.4).draw(self.axes)
                self.add_artists(self.artists)

        self.canvas.mpl_connect('pick_event', self._pick_handler)

    def _reverse(self, button):
        for dot in self.dots:
            dot.direction = -dot.direction

    def _pick_handler(self, event):
        if event.artist in shapes:
            shapes[event.artist].onpick()

    def make_widgets(self, canvas):
        #pass
        # buttons = Gtk.Box.new(Gtk.Orientation.HORIZONTAL, 4)
        # reverse_button = Gtk.Button.new_with_label("Reverse")
        # reverse_button.connect('clicked', self._reverse)
        # buttons.pack_start(reverse_button, False, False, 0)

        box = Gtk.Box.new(Gtk.Orientation.VERTICAL, 4)
        # box.pack_start(buttons, False, True, 0)
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

            text.set_text('{:.2f}'.format(t))
            self.event_source()
            await asyncio.sleep(0.01)
            t += 0.02
            # for dot in self.dots:
            #     dot.advance(0.02)


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
    gbulb.install(gtk=True)
    window = Window(title='Demo')
    window.set_default_size(1024, 768)
    window.show_all()

    loop = asyncio.get_event_loop()
    loop.create_task(window.animate())
    loop.run_forever()
    #loop.run_until_complete(main())
