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

scene = [ shapes.Shape([-0.5 + random.random()], [- 0.5 + random.random()]) for _ in xrange(100) ]

scene.append(
    shapes.Shape(
        [0.5, 0.5, -0.5, -0.5],
        [0.5, -0.5, -0.5, 0.5],
    )
)

def refresh2d(width, height, bound):
    glViewport(0, 0, width, height)
    glMatrixMode(GL_PROJECTION)
    glLoadIdentity()
    glOrtho(-bound, bound, -bound, bound, 0.0, 1.0)
    glMatrixMode (GL_MODELVIEW)
    glLoadIdentity()

def draw():
    global frame

    if time.time() - frame < 1.0 / TARGET_FPS: return False
    frame = time.time()

    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT)
    glLoadIdentity()

    # find the bounds of everything in the scene to keep everythin in view
    bound = max([ shape.get_bound() for shape in scene ])

    refresh2d(width, height, bound)

    for shape in scene:
        shape.draw()


    # move one of the points right
    # scene should resize to keep it in view
    scene[0].xs[0] += 0.01


    glutSwapBuffers()
    return True

def mouseHandler( button, state, x, y ):
    print button, state, x, y

def keyboardHandler(key, x, y):
    print key, x, y

glutInit()
glutInitDisplayMode(GLUT_RGBA | GLUT_DOUBLE | GLUT_ALPHA | GLUT_DEPTH)
glutInitWindowSize(width, height)
glutInitWindowPosition(0, 0)
window = glutCreateWindow("Visualizer")
glutDisplayFunc(draw)
glutKeyboardFunc(keyboardHandler)
glutMouseFunc(mouseHandler)
glutIdleFunc(draw)
glutMainLoop()
