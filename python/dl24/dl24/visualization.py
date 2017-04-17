import asyncio
import gi
gi.require_version('Gtk', '3.0')
from gi.repository import Gtk, GObject
from matplotlib.backends.backend_gtk3agg import FigureCanvasGTK3Agg as FigureCanvas
import matplotlib.animation


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


class Window(Gtk.Window):
    def __init__(self, *args, **kwargs):
        super(Window, self).__init__(*args, **kwargs)
        self.connect('delete-event', lambda *args: asyncio.get_event_loop().stop())
        self.figure = matplotlib.figure.Figure(tight_layout=True)
        self.axes = self.figure.add_subplot(1, 1, 1)
        self.min_bounds = [(-1, -1), (1, 1)]
        self.artists = []
        self.canvas = FigureCanvas(self.figure)
        # Set up animation
        self.event_source = ManualEventSource()
        self.animation = matplotlib.animation.FuncAnimation(
            self.figure, self._animate,
            event_source=self.event_source,
            init_func=lambda: self.artists,
            blit=False)
        # Create the GUI
        self.make_widgets(self.canvas)

    def make_widgets(self, canvas):
        """Override this to control the GUI layout and to add controls."""
        box = Gtk.Box.new(Gtk.Orientation.VERTICAL, 4)
        box.pack_start(self.canvas, True, True, 0)
        self.add(box)

    def add_artists(self, artists):
        """Register dynamic artists that have been added to the axes.

        This is not needed for static artists.
        """
        self.artists.extend(artists)
        self.event_source()

    def autoscale_view(self):
        """Change the view limits on self.axes to fit the data. This can
        be replaced in derived classes.
        """
        self.axes.relim()
        self.axes.update_datalim(self.min_bounds)
        self.axes.autoscale_view()

    def _animate(self, framedata):
        self.autoscale_view()
        return self.artists

