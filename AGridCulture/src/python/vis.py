#!/usr/bin/env python3
from __future__ import absolute_import
from __future__ import print_function

import dl24.listener
from   dl24.visualization import Window
from   dl24.proxy import Direction

from OpenGL.GL import *
from OpenGL.GLUT import *
from OpenGL.GLU import *

import time
import random
import shapes
from six.moves import range

import asyncio
from watchdog.observers.polling import PollingObserver
import sys

window = 0
width, height = 600, 600
TARGET_FPS = 30.0
frame = 0

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

        self.set_color(c)

        size = 0.8
        self.xy = [
            [ x + size, y + size],
            [ x + size, y],
            [ x, y],
            [ x, y + size],
        ]


    def draw(self):
        glColor3f(*self.color)

        glBegin(GL_LINE_LOOP)
        for x, y in self.xy:
            glVertex2f(x, y)
        glEnd()
        
        
    def set_color(self, c):
        if c == C:
            self.color = (0., 1., 0.)
        elif c == '-':
            self.color = (1., 1., 1.)
        elif c == '#':
            self.color = (0., 0., 0.)
        else:
            self.color = (1., 0., 0.)
        
    def get_bound(self):
        return max([
            max(k) for k in self.xy
        ])

def refresh2d(width, height, bound):
    glViewport(0, 0, width, height)
    glMatrixMode(GL_PROJECTION)
    glLoadIdentity()
    glOrtho(0, bound, 0, bound, 0.0, 1.0)
    glMatrixMode (GL_MODELVIEW)
    glLoadIdentity()

def draw():
    global frame

    last_fps = 1 / (time.time() - frame)
    if last_fps > TARGET_FPS: return True
    frame = time.time()

    #print('Rendering')
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT)
    glLoadIdentity()

    # find the bounds of everything in the scene to keep everything in view
    if posts is not None:
        bound = max([max([post.get_bound() for post in post_line]) for post_line in posts])
    else:
        bound = 1

    refresh2d(width, height, bound)

    if posts is not None:
        for post_line in posts:
            for post in post_line:
                post.draw()

    # Render FPS counter
    glWindowPos2f(0,0)
    glutBitmapString(GLUT_BITMAP_TIMES_ROMAN_24, b'FPS: %d' % (int(last_fps),))

    glutSwapBuffers()
    return False

def idle_func():
    print('Sleeping')
    time.sleep(0.01)
    
def async_vis():
    glutInit()
    glutInitDisplayMode(GLUT_RGBA | GLUT_DOUBLE | GLUT_ALPHA | GLUT_DEPTH)
    glutInitWindowSize(width, height)
    glutInitWindowPosition(0, 0)
    window = glutCreateWindow("Visualizer")
    glutDisplayFunc(draw)
    #glutKeyboardFunc(keyboardHandler)
    #glutSpecialFunc(specialKeyboardHandler)
    glutMouseFunc(mouseHandler)
    glutMouseWheelFunc(mouseWheelHandler)
    glutIdleFunc(draw)
    glutMainLoop()

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
                        print('A=%d and C=%s' % (A, C))
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
    from threading import Thread
    glut_thread = Thread(target=async_vis)
    glut_thread.setDaemon(True)
    glut_thread.start()
    loop = asyncio.get_event_loop()
    #loop.create_task(async_main(sys.argv[1]))
    #loop.run_until_complete(async_vis())
    loop.run_until_complete(async_main(sys.argv[1]))
    #loop.run_forever()
    