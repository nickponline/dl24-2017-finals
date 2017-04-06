from OpenGL.GL import *
from OpenGL.GLUT import *
from OpenGL.GLU import *

class Shape(object):

    def __init__(self, xs, ys, color=(1.0, 1.0, 1.0)):
        self.xs = xs
        self.ys = ys
        self.color = color

    def get_bound(self):
        return max(
            max([ abs(x) for x in self.xs]),
            max([ abs(y) for y in self.ys]),
        )

    def draw(self):
        glColor3f(*self.color)

        if len(self.xs) == 1:
            glEnable( GL_POINT_SMOOTH );
            glPointSize(10)
            glBegin(GL_POINTS)
            glVertex2f(self.xs[0], self.ys[0])
            glEnd()
            glDisable( GL_POINT_SMOOTH );
        else:
            glBegin(GL_LINE_LOOP)
            for x, y in zip(self.xs, self.ys):
                glVertex2f(x, y)
            glEnd()
