#!/usr/bin/env python3
import dl24.listener
from   dl24.visualization import Window
from   dl24.proxy import Direction

import asyncio
import matplotlib
import gi
gi.require_version('Gtk', '3.0')
from gi.repository import Gtk
import gbulb
from watchdog.observers.polling import PollingObserver
import sys

posts = None
workers = None
enemyWorkers = None
A = None
C = None
window = None

class Post(object):
    def __init__(self, x, y, c):
        self.x = x
        self.y = y
        self.c = c

        size = 0.4
        self.xy = [
            [ x + size, y + size],
            [ x + size, y - size],
            [ x - size, y - size],
            [ x - size, y + size],
        ]

        artists = self.draw(window.axes)
        window.add_artists(artists)

    def draw(self, axes):
        self.shape = matplotlib.patches.Polygon( self.xy, closed=True, fill=False, edgecolor='black', picker=1)
        self.set_color(self.c)
        self.artist = axes.axes.add_patch(self.shape)
        return [self.artist]

    def set_color(self, c):
        self.c = c
        self.shape.set_facecolor('green' if self.c == C else 'white' if self.c == '-' else 'black' if self.c == '#' else 'red')
        

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

        #self.canvas.mpl_connect('pick_event', self._pick_handler)

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


async def async_main(log_file):
    global A, C, posts, workers, enemyWorkers
    conn = dl24.listener.open_file_connection(log_file, observer=PollingObserver(), seek_to_end=True)
    last_command = None
    is_ok = None
    line = 0
    while True:
        async for record in dl24.listener.proxy_log_records(conn):
            message = record.message.decode('utf-8')
            if message == 'DESCRIBE_WORLD\n':
                print('Got world description')
            if record.direction == Direction.TO_SERVER:
                last_command = message
                is_ok = None
            else:
                if last_command is not None and is_ok is None:
                    # We are expecting either a OK or FAIL
                    if message == 'OK\n':
                        is_ok = True
                        line = 0

                    else:
                        is_ok = None
                        last_command = None

                elif last_command is not None and is_ok:
                    # This is the reply.
                    if posts is None and last_command == 'DESCRIBE_WORLD\n':
                        # Wait for the DESCRIBE_WORLD record before populating our
                        # initial state.
                        parts = message.split(' ')
                        A = int(parts[0])
                        C = parts[2]
                        posts = [[Post(x, y, '-') for x in range(0, A)] for y in range(0, A)]
                        workers = {}
                        enemyWorkers = {}
        
                    elif posts is not None:
                        # Only process other commands once we have seen a world description.
                        if last_command == 'SHOW_MAP\n' and line >= 1 and line <= A:
                            y = line - 1
                            for i in range(A):
                                posts[i][y].set_color(line[i])


if __name__ == '__main__':
    gbulb.install(gtk=True)
    window = Window(title='Demo')
    window.set_default_size(1024, 768)
    window.show_all()

    loop = asyncio.get_event_loop()
    loop.create_task(window.animate())
    #loop.run_until_complete(consume_proxy_log(sys.argv[1]))
    #loop.run_forever()
    loop.run_until_complete(async_main(sys.argv[1]))
    