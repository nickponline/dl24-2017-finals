#!/usr/bin/env python3

import argparse
import asyncio
import logging
import shelve
from enum import Enum
import numpy as np
import dl24.client
from dl24.client import command, ProtocolError, Failure
from prometheus_client import Counter, Gauge


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

    @command('GET_SHARED_SPACE')
    async def get_shared_space(self):
        N = int(await self.readline())
        arr = np.zeros((N, N), dtype=np.int8)
        for i in range(N):
            line = await self.readline()
            arr[i, :] = [int(x) for x in line.split()]
        return arr

    @command('PLACE_SHARED_PIECE')
    async def place_shared_piece(self, x, y, rx, ry, rz):
        pass


def fits2d(space, piece, x, y):
    offset = np.array([[x], [y]], dtype=np.int8)
    pos = piece.pos + offset
    if np.min(pos) < 0 or np.max(pos) >= space.shape[0]:
        return False    # Exceeds edges
    for i in range(pos.shape[1]):
        c = space[pos[1, i], pos[0, i]]
        if c:
            return False
    return True


def place2d(space, piece, x, y):
    offset = np.array([[x], [y]], dtype=np.int8)
    pos = piece.pos + offset
    for i in range(pos.shape[1]):
        space[pos[1, i], pos[0, 1]] = piece.color[i]


def color_scores(space, world):
    space = np.copy(space)
    N = space.shape[0]
    scores = [0.0] * (world.colors + 1)
    st = []
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
                        if space[y2, x2] == color:
                            space[y2, x2] = 0
                            size += 1
                            st.push((x2, y2))
                scores[color] += math.pow(float(size), world.shared_exponent)
    return scores


async def play_game(shelf, client):
    logging.info('Starting game')
    world = await client.describe_world()
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
    while True:
        await client.wait()
        # Check if game ended
        old_state = state
        state = await client.get_match_info()
        if state.total_effort < old_state.total_effort:
            used = sum(state.me.multipliers)
            if used < 9:
                logging.warn('not all multipliers used (%d)', used)
            logging.info('effort decreased (%d -> %d), game ended',
                          old_state.total_effort, state.total_effort)
            return
        used_piece = False
        if state.my_turn:
            shared = await client.get_shared_space()
            avail_idx = await client.get_pieces_in_range()
            avail = [pieces[idx] for idx in avail_idx]
            avail2d = [pieces2d[idx] for idx in avail_idx]
            best = None
            for i in range(len(avail)):
                for y in range(world.size_shared):
                    for x in range(world.size_shared):
                        if fits2d(shared, avail2d[i], x, y) and avail[i].price <= state.me.budget:
                            best = i, x, y
            if best is not None:
                i, x, y = best
                await client.buy_piece(i + 1)
                await client.place_shared_piece(x, y, 0, 0, 0)
                place2d(shared, avail2d[i], x, y)
                used_piece = True
            if used_piece:
                state.me.effort += avail[i].effort
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
                        if state.you.multipliers[i] == multiplier:
                            continue
                        if cscores[i] > best_score:
                            best_score = cscores[i]
                            best = i
                    if best > 0:
                        await self.set_multiplier(best, multiplier)


async def main():
    parser = argparse.ArgumentParser()
    dl24.client.add_arguments(parser)
    parser.add_argument('persist', help='File for persisting state')
    args = parser.parse_args()

    shelf = shelve.open(args.persist)
    try:
        connect_args = Client.connect_args(args)
    except ValueError as error:
        parser.error(str(error))
    client = Client(*connect_args)
    await client.connect()
    while True:
        await play_game(shelf, client)


if __name__ == '__main__':
    # Workaround for vext installing log handlers early
    root_logger = logging.getLogger()
    while root_logger.handlers:
        root_logger.removeHandler(root_logger.handlers[-1])
    logging.basicConfig(
        format='%(asctime)s %(levelname)s %(filename)s:%(lineno)d: %(message)s',
        level=logging.INFO)

    loop = asyncio.get_event_loop()
    loop.run_until_complete(main())

