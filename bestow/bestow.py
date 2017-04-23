#!/usr/bin/env python3

import argparse
import asyncio
import logging
import shelve
import math
import gc
import copy
from collections import namedtuple
from enum import Enum
import numpy as np
import dl24.client
from dl24.client import command, ProtocolError, Failure
import dl24.visualization
import gbulb
from prometheus_client import Counter, Gauge, Histogram


class Turn(Enum):
    ME = 0
    YOU = 1


WORLD_FIELDS = [
    ('effort_end', int),
    ('effort_period', int),
    ('colors', int),
    ('size_shared', int),
    ('size_own', int),
    ('piece_range', int),
    ('cube_bonus', float),
    ('start_cash', int),
    ('shared_coeff', float),
    ('quality_min', int),
    ('good_bonus', float),
    ('bad_penalty', float),
    ('shared_exponent', float),
    ('skip_cash', int),
    ('turn_time', int),
    ('command_limit', int),
    ('K', float)
]

WORLD_GAUGES = [Gauge('bestow_world_' + name[0], 'World value of ' + name[0]) for name in WORLD_FIELDS]


PLAYER_FIELDS = [
    ('score', float),
    ('score_own', int),
    ('score_shared', int),
    ('budget', int),
    ('profit_own', int),
    ('effort', int),
    ('multipliers', list),
    ('single_cube', int),
    ('single_square', int)
]

PLAYER_GAUGES = [Gauge('bestow_player_' + name[0], 'Player value of ' + name[0], labelnames=['player']) for name in PLAYER_FIELDS]
NUM_PIECES_GAUGE = Gauge('bestow_num_pieces', 'Number of pieces in game')
BIGGEST_CUBE_GAUGE = Gauge('bestow_biggest_cube', 'Biggest cube in space', labelnames=['player'])
BIGGEST_POTENTIAL_CUBE_GAUGE = Gauge('bestow_biggest_potential_cube', 'Biggest cube if all single cubes added', labelnames=['player'])
OPERATION_TIME = Histogram('bestow_operation_time', 'Function call timings', labelnames=['function'])
_do_assertions = False

ROTATE_X = np.array(
    [[1, 0, 0],
     [0, 0, -1],
     [0, 1, 0]], dtype=np.int8)
ROTATE_Y = np.array(
    [[0, 0, 1],
     [0, 1, 0],
     [-1, 0, 0]], dtype=np.int8)
ROTATE_Z = np.array(
    [[0, -1, 0],
     [1, 0, 0],
     [0, 0, 1]])


def parse(descr, value):
    try:
        return descr[1](value)
    except ValueError as error:
        raise ProtocolError('Failed to parse {}'.format(descr[0]), value) from error


class World(object):
    def __init__(self, line):
        fields = line.split()
        if len(fields) != len(WORLD_FIELDS):
            raise ProtocolError('Expected {} fields, received {}'.format(
                len(WORLD_FIELDS), len(fields)), line)
        for descr, gauge, field in zip(WORLD_FIELDS, WORLD_GAUGES, fields):
            value = parse(descr, field)
            setattr(self, descr[0], value)
            gauge.set(value)


class PlayerState(object):
    def __init__(self, line, playername, world):
        fields = line.split()
        C = world.colors
        if len(fields) != len(PLAYER_FIELDS) + C - 1:
            raise ProtocolError('Wrong number of fields for PlayerState', line)
        fields_iter = iter(fields)
        for descr, gauge in zip(PLAYER_FIELDS, PLAYER_GAUGES):
            try:
                if descr[1] is list:
                    value = [0]
                    for i in range(C):
                        value.append(int(next(fields_iter)))
                else:
                    value = parse(descr, next(fields_iter))
                    gauge.labels(playername).set(value)
            except ValueError as error:
                raise ProtocolError('Could not parse {}'.format(descr[0]), line) from error
            setattr(self, descr[0], value)
        self.mcolors = [-1] * 6
        for i, v in enumerate(self.multipliers):
            if v > 0:
                self.mcolors[v] = i


class MatchInfo(object):
    def __init__(self, me, you, turn, world):
        self.me = PlayerState(me, 'me', world)
        self.you = PlayerState(you, 'you', world)
        turn_map = {'None': None, 'Me': Turn.ME, 'Opponent': Turn.YOU}
        if turn not in turn_map:
            raise ProtocolError('Turn is invalid'.format(turn), turn)
        self.turn = turn_map[turn]

    @property
    def my_turn(self):
        return self.turn == Turn.ME

    @property
    def total_effort(self):
        return self.me.effort + self.you.effort


class Orientation(object):
    def __init__(self, xs, ys, zs):
        mat = np.identity(3, np.int8)
        for i in range(xs):
            mat = ROTATE_X @ mat
        for i in range(ys):
            mat = ROTATE_Y @ mat
        for i in range(zs):
            mat = ROTATE_Z @ mat
        self.xs = xs
        self.ys = ys
        self.zs = zs
        self.mat = mat

    def __call__(self, piece):
        piece2 = copy.copy(piece)
        piece2.pos = self.mat @ piece.pos
        if hasattr(piece2, 'lower'):
            delattr(piece2, 'lower')
            delattr(piece2, 'upper')
        return piece2


ORIENTATIONS = []
for xs in range(4):
    for ys in range(4):
        for zs in range(4):
            orient = Orientation(xs, ys, zs)
            if not any(np.all(orient.mat == existing.mat) for existing in ORIENTATIONS):
                ORIENTATIONS.append(orient)


def check_iterator_done(it, line):
    try:
        next(it)
    except StopIteration:
        return
    else:
        raise ProtocolError('More values than expected in line', line)


class Piece(object):
    def __init__(self, line):
        fields = line.split()
        it = iter(fields)
        self.id = int(next(it))
        C = int(next(it))
        self.coords = []
        pos = []
        for i in range(C):
            pos.append([int(next(it)) for _ in range(3)])
        self.pos = np.array(pos, dtype=np.int8).transpose()
        self.color = []
        for i in range(C):
            self.color.append(int(next(it)))
        self.price = int(next(it))
        self.value = int(next(it))
        self.effort = int(next(it))
        check_iterator_done(it, line)


class Piece2D(object):
    def __init__(self, piece3d):
        self.id = piece3d.id
        color = {}
        depth = {}
        for i, c in enumerate(piece3d.color):
            x, y, z = piece3d.pos[:, i]
            c = piece3d.color[i]
            key = (x, y)
            if z < depth.get(key, 10000):
                depth[key] = z
                color[key] = c
        pos = []
        self.color = []
        for key, value in color.items():
            pos.append(key)
            self.color.append(value)
        self.pos = np.array(pos, dtype=np.int8).transpose()


Placement = namedtuple('Placement',
    ['idx', 'own_pos', 'own_orient', 'shared_pos', 'shared_orient', 'value', 'metric'])


class Client(dl24.client.ClientBase):
    def __init__(self, *args, **kwargs):
        super(Client, self).__init__(*args, **kwargs)
        self.world = None

    @command('DESCRIBE_WORLD')
    async def describe_world(self):
        line = await self.readline()
        self.world = World(line)
        return self.world

    @command('GET_MATCH_INFO')
    async def get_match_info(self):
        player = await self.readline()
        opponent = await self.readline()
        turn = await self.readline()
        return MatchInfo(player, opponent, turn, self.world)

    @command('GET_ALL_PIECES')
    async def get_all_pieces(self):
        N = int(await self.readline())
        lines = []
        for i in range(N):
            lines.append(await self.readline())
        pieces = [Piece(line) for line in lines]
        return {piece.id: piece for piece in pieces}

    @command('GET_PIECES_IN_RANGE')
    async def get_pieces_in_range(self):
        fields = (await self.readline()).split()
        return [int(x) for x in fields[1:]]

    @command('BUY_PIECE')
    async def buy_piece(self, idx):
        pass

    @command('SET_MULTIPLIER')
    async def set_multiplier(self, color, mulitplier):
        pass

    async def _get_space(self, out):
        N = int(await self.readline())
        if out is None:
            out = np.zeros((N, N, N), dtype=np.int8)
        for z in range(N):
            for y in range(N):
                line = await self.readline()
                out[z, y, :] = [int(x) for x in line.split()]
        return out

    @command('GET_MY_SPACE')
    async def get_my_space(self, *, out=None):
        return await self._get_space(out)

    @command('GET_OPPONENT_SPACE')
    async def get_opponent_space(self, *, out=None):
        return await self._get_space(out)

    @command('GET_SHARED_SPACE')
    async def get_shared_space(self, *, out=None):
        N = int(await self.readline())
        if out is None:
            out = np.zeros((N, N), dtype=np.int8)
        for i in range(N):
            line = await self.readline()
            out[i, :] = [int(x) for x in line.split()]
        return out

    @command('PLACE_SHARED_PIECE')
    async def place_shared_piece(self, x, y, rx, ry, rz):
        pass

    @command('PLACE_OWN_PIECE')
    async def place_own_piece(self, x, y, z, rx, ry, rz):
        pass

    @command('PLACE_SINGLE_CUBE_PIECE')
    async def place_single_cube_piece(self, x, y, z):
        pass


def fix_piece(piece):
    if not hasattr(piece, 'lower'):
        piece.lower = np.min(piece.pos, axis=1)
        piece.upper = np.max(piece.pos, axis=1)


OPERATION_TIME_fits2d = OPERATION_TIME.labels('fits2d')
@OPERATION_TIME_fits2d.time()
def fits2d(space, piece, out=None):
    N = space.shape[0]
    fix_piece(piece)
    if out is None:
        out = np.empty((N, N), dtype=np.bool_)
    start = np.maximum(0, -piece.lower)
    stop = np.minimum(N, N - piece.upper)
    xl, yl = start
    xh, yh = stop
    pos = piece.pos
    space_bool = space.astype(np.bool_)
    out.fill(True)
    out[yl:yh, xl:xh].fill(False)
    for i in range(pos.shape[1]):
        x, y = pos[:, i]
        out[yl:yh, xl:xh] |= space_bool[yl+y:yh+y, xl+x:xh+x]
    # Convert from conflict to fit
    out = np.logical_not(out, out=out)
    return out


OPERATION_TIME_prefix_sum = OPERATION_TIME.labels('prefix_sum')
@OPERATION_TIME_prefix_sum.time()
def prefix_sum(space):
    N = space.shape[0]
    out = np.zeros((N + 1, N + 1, N + 1), dtype=np.int32)
    for z in range(N):
        for y in range(N):
            for x in range(N):
                out[z + 1, y + 1, x + 1] = space[z, y, x] \
                    + out[z, y + 1, x + 1] + out[z + 1, y, x + 1] + out[z + 1, y + 1, x] \
                    - out[z, y, x + 1] - out[z, y + 1, x] - out[z + 1, y, x] \
                    + out[z, y, x]
    return out


def rectoid_sum(prefixes, lower, upper):
    return prefixes[upper[2], upper[1], upper[0]] \
            - prefixes[upper[2], upper[1], lower[0]] - prefixes[upper[2], lower[1], upper[0]] - prefixes[lower[2], upper[1], upper[0]] \
            + prefixes[upper[2], lower[1], lower[0]] + prefixes[lower[2], upper[1], lower[0]] + prefixes[lower[2], lower[1], upper[0]] \
            - prefixes[lower[2], lower[1], lower[0]]


OPERATION_TIME_fits3d = OPERATION_TIME.labels('fits3d')
@OPERATION_TIME_fits3d.time()
def fits3d(space, prefix, piece, out=None):
    N = space.shape[0]
    fix_piece(piece)
    start = np.maximum(0, -piece.lower)
    stop = np.minimum(N, N - piece.upper)
    if np.any(start >= stop):
        return out
    xl, yl, zl = start
    xh, yh, zh = stop
    pos = piece.pos
    space_bool = space.astype(np.bool_)
    collide = np.ones((N, N, N), dtype=np.bool_)
    collide[zl:zh, yl:yh, xl:xh].fill(False)
    for i in range(pos.shape[1]):
        x, y, z = pos[:, i]
        collide[zl:zh, yl:yh, xl:xh] |= space_bool[zl+z:zh+z, yl+y:yh+y, xl+x:xh+x]

    if out is None:
        out = np.empty((N, N, N), dtype=np.int32)
    out.fill(0)
    for dz in (piece.lower[2], piece.upper[2] + 1):
        for dy in (piece.lower[1], piece.upper[1] + 1):
            for dx in (piece.lower[0], piece.upper[0] + 1):
                sign = -1
                if dz > piece.lower[2]: sign *= -1
                if dy > piece.lower[1]: sign *= -1
                if dx > piece.lower[0]: sign *= -1
                out[zl:zh, yl:yh, xl:xh] += prefix[zl+dz:zh+dz, yl+dy:yh+dy, xl+dx:xh+dx] * sign

    out[collide] = -1
    return out


OPERATION_TIME_biggest_cube = OPERATION_TIME.labels('biggest_cube')
@OPERATION_TIME_biggest_cube.time()
def biggest_cube(space):
    N = space.shape[0]
    planes = np.zeros((2, N + 1, N + 1), np.int32)
    ans = 0
    cur = 0
    nxt = 1
    for z in range(N - 1, -1, -1):
        for y in range(N - 1, -1, -1):
            for x in range(N - 1, -1, -1):
                top = N
                for dz in range(2):
                    for dy in range(2):
                        for dx in range(2):
                            if dx + dy + dz == 0:
                                v = N if space[z, y, x] else 0
                            else:
                                v = planes[cur ^ dz, y + dy, x + dx] + 1
                            top = min(top, v)
                planes[cur, y, x] = top
                ans = max(ans, top)
        cur, nxt = nxt, cur
    return ans


def best_singles3d(space, singles):
    N = space.shape[0]
    best = 0, 0, 0, 0
    for s in range(1, N + 2):
        need = s * s * s
        for z in range(N - s + 1):
            for y in range(N - s + 1):
                for x in range(N - s + 1):
                    have = np.sum(space[z : z + s, y : y + s, x : x + s])
                    if have + singles >= need:
                        best = x, y, z, s
        if best[-1] != s:
            return best


def place3d(space, piece, x, y, z):
    offset = np.array([[x], [y], [z]], dtype=np.int8)
    pos = piece.pos + offset
    for i in range(pos.shape[1]):
        space[pos[2, i], pos[1, i], pos[0, i]] = 1


def place2d(space, piece, x, y):
    offset = np.array([[x], [y]], dtype=np.int8)
    pos = piece.pos + offset
    for i in range(pos.shape[1]):
        space[pos[1, i], pos[0, i]] = piece.color[i]


def color_scores(space, world):
    space = np.copy(space)
    N = space.shape[0]
    scores = [0.0] * (world.colors + 1)
    st = []
    dx = [-1, 0, 1, 0]
    dy = [0, -1, 0, 1]
    for y in range(N):
        for x in range(N):
            color = space[y, x]
            if color > 0:
                size = 1
                space[y, x] = 0
                st.append((x, y))
                while st:
                    x, y = st.pop()
                    for i in range(4):
                        x2 = x + dx[i]
                        y2 = y + dy[i]
                        if x2 >= 0 and x2 < N and y2 >= 0 and y2 < N and space[y2, x2] == color:
                            space[y2, x2] = 0
                            size += 1
                            st.append((x2, y2))
                scores[color] += math.pow(float(size), world.shared_exponent)
    return scores


class Window(dl24.visualization.Window):
    def __init__(self, *args, **kwargs):
        super(Window, self).__init__(*args, **kwargs)
        self.im_artist = None

    def set_world(self, world):
        if self.im_artist is None:
            img = np.zeros((world.size_shared, world.size_shared), np.float32)
            img[3:8, 5:20] = 1.0
            self.im_artist = self.axes.imshow(
                img, aspect='equal', interpolation='nearest',
                origin='upper', vmin=0, vmax=world.colors)
            self.add_artists([self.im_artist])
        self.min_bounds = [(-0.5, -0.5), (world.size_shared - 0.5, world.size_shared - 0.5)]

    def set_shared(self, shared):
        self.im_artist.set_data(shared.astype(np.float32))
        self.event_source()


def assert_equal(expected, actual, name):
    if _do_assertions:
        assert expected == actual, "prediction error on {} ({} != {})".format(name, expected, actual)


async def play_game(shelf, client, window):
    logging.info('Starting game')
    world = await client.describe_world()
    if window:
        window.set_world(world)
    try:
        pieces = await client.get_all_pieces()
        logging.info('Shelving %d pieces', len(pieces))
        shelf['pieces'] = pieces
    except Failure as error:
        if error.errno == 101:
            logging.info('GET_ALL_PIECES hit limit, reading from shelf')
        pieces = shelf['pieces']
        if isinstance(pieces, list):
            pieces = {piece.id: piece for piece in pieces}
    NUM_PIECES_GAUGE.set(len(pieces))
    pieces2d = {piece.id: Piece2D(piece) for piece in pieces.values()}

    state = await client.get_match_info()
    old_effort = 0
    shared = np.empty((world.size_shared,) * 2, np.int8)
    own = np.empty((world.size_own,) * 3, np.int8)
    while True:
        logging.info('Starting turn')
        old_state = state
        state = await client.get_match_info()
        # Check if game ended
        if state.total_effort < old_effort:
            logging.info('effort decreased (%d -> %d), game ended',
                          old_effort, state.total_effort)
            return
        old_effort = state.total_effort
        # Validations
        assert_equal(state.me.effort, old_state.me.effort, 'effort')
        assert_equal(state.me.profit_own, old_state.me.profit_own, 'profit_own')

        used_piece = False
        logging.info('Got state')
        shared = await client.get_shared_space(out=shared)
        if window:
            window.set_shared(shared)
        if state.my_turn:
            logging.info('Got shared')
            own = await client.get_my_space(out=own)
            logging.info('Getting prefix sum')
            prefix = prefix_sum(own)
            logging.info('Got prefix sum')
            opponent = await client.get_opponent_space()
            biggest = biggest_cube(own)
            biggest_possible = best_singles3d(own, state.me.single_cube)[-1]
            BIGGEST_CUBE_GAUGE.labels('me').set(biggest)
            BIGGEST_POTENTIAL_CUBE_GAUGE.labels('me').set(biggest_possible)
            BIGGEST_CUBE_GAUGE.labels('you').set(biggest_cube(opponent))
            logging.info('Got metrics')
            avail_idx = await client.get_pieces_in_range()
            avail = [pieces[idx] for idx in avail_idx]
            best = None
            cash = state.me.budget - state.me.profit_own
            logging.info('Evaluating pieces')
            for i in range(len(avail)):
                if avail[i].price > state.me.budget:
                    continue
                own_pos = None
                own_orient = None
                shared_pos = None
                shared_weight = -1000
                shared_orient = None
                own_value = avail[i].value
                loss = max(0, avail[i].price - cash)

                best_hits = -1
                for orient in ORIENTATIONS:
                    piece = orient(avail[i])
                    fitness = fits3d(own, prefix, piece)
                    hits = np.max(fitness)
                    if hits > best_hits:
                        good = np.array(np.nonzero(fitness == hits))
                        good_close = np.max(np.abs(good - world.size_own / 2), axis=0)
                        good_idx = np.argmin(good_close)
                        own_pos = list(reversed(good[:, good_idx]))
                        own_orient = orient
                        best_hits = hits

                    piece2d = Piece2D(piece)
                    weight = 0.1 * len(piece2d.color)  # TODO: tune
                    weight -= piece2d.color.count(state.you.mcolors[5])
                    weight += piece2d.color.count(state.you.mcolors[3])
                    if weight > shared_weight:
                        collide = fits2d(shared, piece2d)
                        good_y, good_x = collide.nonzero()
                        if len(good_y):
                            shared_weight = weight
                            shared_orient = orient
                            shared_pos = (good_x[0], good_y[0])
                if shared_pos is None:
                    shared_weight = 0

                if own_pos:
                    value = -loss
                    ### Testing
                    if _do_assertions:
                        piece = own_orient(avail[i])
                        fix_piece(piece)
                        start = piece.lower + own_pos
                        stop = piece.upper + own_pos + 1
                        test_hits = np.sum(own[start[2]:stop[2], start[1]:stop[1], start[0]:stop[0]])
                        assert_equal(test_hits, best_hits, 'hits')
                    ### End testing
                    q = best_hits - world.quality_min
                    scaling = world.good_bonus if q > 0 else world.bad_penalty
                    own_value = int(own_value * (1 + scaling * q))
                    value += own_value
                    metric = (value + 0.001 * shared_weight) / avail[i].effort
                    if value > 0:
                        if best is None or metric > best.metric:
                            best = Placement(i, own_pos=own_pos, own_orient=own_orient,
                                             shared_pos=shared_pos, shared_orient=shared_orient,
                                             value=value, metric=metric)
            logging.info('Done evaluating pieces')
            if best is not None:
                await client.buy_piece(best.idx + 1)
                if best.own_pos:
                    await client.place_own_piece(
                        *best.own_pos,
                        best.own_orient.xs, best.own_orient.ys, best.own_orient.zs)
                    state.me.profit_own += best.value
                    piece = best.own_orient(avail[best.idx])
                    place3d(own, piece, *best.own_pos)
                if best.shared_pos:
                    await client.place_shared_piece(
                        *best.shared_pos,
                        best.shared_orient.xs, best.shared_orient.ys, best.shared_orient.zs)
                    piece2d = Piece2D(best.shared_orient(avail[best.idx]))
                    place2d(shared, piece2d, *best.shared_pos)
                used_piece = True
            if used_piece:
                state.me.effort += avail[best.idx].effort
            else:
                state.me.effort = state.you.effort + 1
            if state.me.effort >= world.effort_end:
                # Game is about to end, figure out multipliers to use
                logging.info('Game ending, setting multipliers')
                cscores = color_scores(shared, world)
                for multiplier in [5, 3, 1]:
                    if multiplier in state.me.multipliers:
                        continue     # Already used
                    best = -1
                    best_score = -1.0
                    for i in range(1, world.colors + 1):
                        if state.me.multipliers[i] > 0 or state.you.multipliers[i] == multiplier:
                            continue
                        if cscores[i] > best_score:
                            best_score = cscores[i]
                            best = i
                    if best > 0:
                        await client.set_multiplier(best, multiplier)
                        state.me.multipliers[best] = multiplier
                logging.info('Game ending, setting single cubes')
                x, y, z, size = best_singles3d(own, state.me.single_cube)
                try:
                    for dz in range(size):
                        for dy in range(size):
                            for dx in range(size):
                                px = x + dx
                                py = y + dy
                                pz = z + dz
                                if own[pz, py, px] == 0:
                                    await client.place_single_cube_piece(px, py, pz)
                                    own[pz, py, px] = 1
                except Failure as error:
                    if error.errno != 6:
                        raise
                    else:
                        logging.warn('Could not complete cube due to command limit')
                biggest = biggest_cube(own)
                BIGGEST_CUBE_GAUGE.labels('me').set(biggest)
        gc.collect()
        await client.wait()


async def main():
    global _do_assertions
    parser = argparse.ArgumentParser()
    dl24.client.add_arguments(parser)
    parser.add_argument('persist', help='File for persisting state')
    parser.add_argument('--assert', dest="assert_", action='store_true', help='Do sanity checks')
    parser.add_argument('--vis', action='store_true', help='Run visualiser')
    args = parser.parse_args()
    if args.assert_:
        _do_assertions = True
    if args.vis:
        window = Window(title='B.E.S.T.O.W.')
        window.set_default_size(768, 768)
        window.show_all()
    else:
        window = None

    shelf = shelve.open(args.persist)
    try:
        connect_args = Client.connect_args(args)
    except ValueError as error:
        parser.error(str(error))
    client = Client(*connect_args)
    await client.connect()
    while True:
        await play_game(shelf, client, window)


if __name__ == '__main__':
    gbulb.install(gtk=True)
    # Workaround for vext installing log handlers early
    root_logger = logging.getLogger()
    while root_logger.handlers:
        root_logger.removeHandler(root_logger.handlers[-1])
    logging.basicConfig(
        format='%(asctime)s %(levelname)s %(filename)s:%(lineno)d: %(message)s',
        level=logging.INFO)

    loop = asyncio.get_event_loop()
    loop.run_until_complete(main())
