# pip install pyopengl

# Simple 2D OpenGL visualizer.
# Renders all shapes in the scene
# Resizes ortho to keep everything in view
# Placeholders to mouse and keyboard events

from OpenGL.GL import *
from OpenGL.GLUT import *
from OpenGL.GLU import *

import time
import random
import shapes

window = 0
width, height = 600, 600
TARGET_FPS = 30.0
frame = 0

# some random points
scene = [ shapes.Shape([-0.5 + random.random()], [- 0.5 + random.random()]) for _ in xrange(100) ]

# a quad
scene.append(
    shapes.Shape(
        [0.5, 0.5, -0.5, -0.5],
        [0.5, -0.5, -0.5, 0.5],
    )
)

def mouseHandler( button, state, x, y ):
    print button, state, x, y

def mouseWheelHandler(wheel, direction, x, y):
    print wheel, direction, x, y

def keyboardHandler(key, x, y):
    print key, x, y

def specialKeyboardHandler(key, x, y):
    print key, x, y

def refresh2d(width, height, bound):
    glViewport(0, 0, width, height)
    glMatrixMode(GL_PROJECTION)
    glLoadIdentity()
    glOrtho(-bound, bound, -bound, bound, 0.0, 1.0)
    glMatrixMode (GL_MODELVIEW)
    glLoadIdentity()

def draw():
    global frame

    last_fps = 1 / (time.time() - frame)
    if last_fps > TARGET_FPS: return False
    frame = time.time()

    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT)
    glLoadIdentity()

    # find the bounds of everything in the scene to keep everything in view
    bound = max([ shape.get_bound() for shape in scene ])

    refresh2d(width, height, bound)

    for shape in scene:
        shape.draw()

    # Render FPS counter
    glWindowPos2f(0,0)
    glutBitmapString(GLUT_BITMAP_TIMES_ROMAN_24, 'FPS: %d' % (int(last_fps),))

    # move one of the points right
    # scene should resize to keep it in view
    scene[0].xs[0] += 0.01


    glutSwapBuffers()
    return True


glutInit()
glutInitDisplayMode(GLUT_RGBA | GLUT_DOUBLE | GLUT_ALPHA | GLUT_DEPTH)
glutInitWindowSize(width, height)
glutInitWindowPosition(0, 0)
window = glutCreateWindow("Visualizer")
glutDisplayFunc(draw)
glutKeyboardFunc(keyboardHandler)
glutSpecialFunc(specialKeyboardHandler)
glutMouseFunc(mouseHandler)
glutMouseWheelFunc(mouseWheelHandler)
glutIdleFunc(draw)
glutMainLoop()
