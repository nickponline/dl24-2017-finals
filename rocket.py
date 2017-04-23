#!/usr/bin/env python3
import argparse
import asyncio
import networkx as nx
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
import graph_util

DEBUG = False

global cache_path
cache_path = None


def convert(string, typefunc=str):
    return typefunc(string)

class Game(object):
    def __init__(self):
        self.cars = []
        self.name = None
        self.score = 0.0
        self.upgrade_engine = False
        self.rocket = None
        self.rocket_site = None
        self.far_rockets = set()

    def load_world(self):
        self.cities, self.roads, self.bases, self.base_locations, self.bonuses, self.G = graph_util.load_world(self.name)

game = Game()

class Car(object):

    def __init__(self):
        #print("creating car")
        self.has_route = False
        self.route = []
        self.route_index = 0
        self.until_move = 0
        self.engine_upgraded = False
        self.trunk_upgraded = False
        self.mode = None
        #self.rocket = None
        self.hq = None
        self.previous_fuel = 0


        self.caches = {
            ('world-A.data', 842) : [842,302,715,[466],638,599,402,15,278,260,262,1126,36,319,986]
        }

    def fuel_forward(self, game):
        #print("FORW", self.until_move, self.route[self.route_index], self.location)
        fuel = 0
        for i in range(self.route_index, len(self.route)):
            src = self.route[i]
            dst = self.route[(i + 1) % len(self.route)]
            fuel += game.G[src][dst]['weight']
        if DEBUG: print("fuel forward:", fuel)
        return fuel

    def fuel_backward(self, game):
        #print("BACK", self.until_move, self.route[self.route_index], self.location)
        fuel = 0
        for i in range(0, self.route_index):
            src = self.route[i]
            dst = self.route[(i + 1) % len(self.route)]
            fuel += game.G[src][dst]['weight']
        if DEBUG:print("fuel backward:", fuel)
        return fuel

    def send_car_home(self, game):


        DEBUG = True
        if DEBUG:print("low fuel going home")
        print(self.route)
        print(self.route_index)
        self.route = self.route[:self.route_index+1][::-1]
        print(self.route)
        # self.route = graph_util.route_to_home(game, self)
        if DEBUG: print("route home=", self.route)
        if DEBUG: print("location: ", self.location)
        self.has_route = True
        self.mode = "RESOURCE" + ':HOMING'

        self.route_index = 0

    def get_direcions(self, game, mode='RESOURCE', source=None, destination=None):
        global cache_path
        # shuttle money from base to rocket
        # if mode == 'FUND':
        #     self.hq = source
        #     self.rocket_location = destination
        #
        #     if self.location == self.hq:
        #         print("car is at hq, sending to rocket")
        #         self.route = graph_util.get_route(game, self.hq, self.rocket_location)
        #         self.mode = mode + ":ROCKET"
        #         self.has_route = True
        #     elif self.location == self.rocket_location:
        #         print("car is at rocket, sending to hq")
        #         self.route = graph_util.get_route(game, self.rocket_location, self.hq)
        #         self.mode = mode + ":HQ"
        #         self.has_route = True

        if mode == 'RESOURCE':
            if self.location == game.hq:
                print("car is at home, creating cycle, previous_fuel={}".format(self.previous_fuel))
                # if (game.name, game.hq) in self.caches:
                #     print("MAP CACHE HIT")
                #     self.route = self.caches[(game.name, game.hq)]

                #
                if game.name == 'world-F.data' and game.hq == 1205:
                    print("CACHE F")
                    self.route = [1205, 1135, 192, 1116, 976, 1439, 758, 1852, 559, 1220, 743, 315, 635, 1423]
                elif game.name == 'world-I.data' and game.hq == 1429:
                    print("CACHE I")
                    self.route = [1429, 402, 2041, 1431, 1694, 1759, 1152, 230, 701, 1128, 1771, 2021, 572, 1203, 505]
                # elif game.name == 'world-M.data' and game.hq == 728:
                #     print("CACHE *")
                #     self.route = [728, 1998, 1817, 1035, 1221, 790, 172, 1830, 330]
                elif game.name == 'world-F.data' and game.hq == 830:
                    print("CACHE F")
                    self.route = [830, 1653, 1605, 1793, 1610, 229, 1243, 490, 1494, 444]
                elif game.name == 'world-I.data' and game.hq == 924:
                    print("CACHE I")
                    self.route = [924, 1579, 1424, 138, 1537, 709, 2013, 370]
                elif game.name == 'world-K.data' and game.hq == 56:
                    print("CACHE K")
                    self.route = [56, 1093, 201, 6, 1473, 1530, 303, 1872, 1536, 503, 201, 1830]
                elif game.name == 'world-C.data' and game.hq == 79:
                    print("CACHE C")
                    self.route = [79, 1025, 916, 213, 1040, 571, 1108, 516, 1012, 673, 477, 1192]
                elif cache_path is not None:
                    self.route = cache_path
                else:
                    #print("HERE??????????")
                    #self.route = graph_util.ldc(game.G, game.hq, bonuses=game.bonuses)
                    #self.route= graph_util.linear_path(game.G, game.hq)
                    #self.route = graph_util.get_a_disjoint(game.G, game.hq, previous=self.route)
                    self.route = graph_util.nick(game.G, game.hq, game.bonuses)
                    cache_path = self.route

                best_value = len(self.route)
                fuel_cost = len(self.route)
                self.has_route = True
                self.mode = mode + ':CYCLING'
                print("route will cost fuel={}, we have={} value={}, length={}".format(fuel_cost, self.fuel, best_value, len(self.route)))

            else:
                print("car is not at home, sending home from={} to hq={}".format(self.location, game.hq))
                self.route = graph_util.route_to_home(game, self)
                print("route home=", self.route)
                self.has_route = True
                self.mode = mode + ':HOMING'


        self.route_index = 0


    def get_next_city(self):
        self.route_index += 1
        next_city = (self.route[(self.route_index) % len(self.route) ])
        #print("we are location={}, next={}".format(self.location, next_city))
        #print(self.route)
        return next_city

    def show_car(self, game):
        fmt = 'CAR id={} loc={} HQ={} F={},{}, engine={}, R={}, wait={}, $={}, mode={}'
        fmt = fmt.format(self.id, self.location, game.hq, self.fuel, self.previous_fuel, self.engine, self.repeated, self.until_move, self.money, self.mode)
        print(fmt)
        route_line = []
        for index, city in enumerate(self.route):
            if self.route_index % len(self.route) == index:
                route_line.append("["+str(city)+"]")
            else:
                route_line.append(str(city))
        if self.mode == "RESOURCE:CYCLING":
            print(" ".join(route_line), "F=", self.fuel_forward(game), "B=", self.fuel_backward(game))
        else:
            print(" ".join(route_line))
        print(" ")

class Client(dl24.client.ClientBase):
    @command('DESCRIBE_WORLD')
    async def describe_world(self):
        line = await self.readline()
        line = line.strip().split()
        game.M = convert(line[0], int)
        game.Sr = convert(line[1], int)
        game.Sf = convert(line[2], int)
        game.Sue = convert(line[3], int)
        game.Sut = convert(line[4], int)
        game.Nmax = convert(line[5], int)
        game.Umax = convert(line[6], int)
        game.Sg = convert(line[7], int)
        game.R = convert(line[8], float)
        game.Q = convert(line[9], int)
        game.Slmin = convert(line[10], int)
        game.Slmax = convert(line[11], int)
        game.T = convert(line[12], int)
        game.L = convert(line[13], int)
        game.K = convert(line[14], float)

        name = convert(line[15], str)

        if name != game.name:
            game.name = name
            game.cars = []
            game.load_world()

        line = await self.readline()
        line = line.strip().split()
        game.Mh = convert(line[0], int)

        line = await self.readline()
        line = line.strip().split()
        game.home_bases_ids = list(map(int, line))

        game.hq = game.home_bases_ids[1]

    @command('ROADS_STATUS')
    async def roads_status(self):
        #print("ROADS_STATUS")
        Rc = int(await self.readline())

        for _ in range(Rc):
            line = await self.readline()
            line = line.strip().split()

            source = int(line[0])
            destination = int(line[1])
            bonus = float(line[2])
            cost = float(line[3])

            # print("before", game.G[source][destination]['weight'], cost, bonus)
            game.G[source][destination]['weight'] = max(3, cost - bonus)
            game.G[destination][source]['weight'] = max(3, cost - bonus)
            # print("after", game.G[source][destination]['weight'])
            #print(line)
            #Cr = game.G[source][destination]['weight']
            #print(cost, Cr, bonus)

    @command('ROCKET_INFO')
    async def rocket_info(self):
        print("ROCKET_INFO")
        Cr = int(await self.readline())
        print(Cr)
        for _ in range(Cr):
            line = await self.readline()
            line = line.strip().split()

            if game.rocket_site is None and int(line[0]) not in game.far_rockets:
                game.rocket_site = int(line[0])
                print("ROCKET LOCATED AT: ", game.rocket_site)

            print(line)

        print("FAR ROCKET SITES: ", game.far_rockets)
        print("ACTIVE ROCKET: ", game.rocket)

    @command('TIME_TO_END')
    async def time_to_end(self):
        print("TIME_TO_END")
        print(await self.readline())

    @command('TRANSFERS')
    async def transfers(self):
        print("TRANSFERS")
        Lc = int(await self.readline())
        print(Lc)
        for _ in range(Lc):
            line = await self.readline()
            line = line.strip().split()
            print(line)

    @command('CARS')
    async def cars(self):
        C = int(await self.readline())

        for i in range(C):
            line = (await self.readline()).strip().split()

            if len(game.cars) == C:
                # if i == 0: print("retrieving a car")
                car = game.cars[i]
            else:
                # if i == 0: print("creating a new car")
                car = Car()
                game.cars.append(car)
                car = game.cars[-1]

            car.id = convert(line[0], int)
            car.location = convert(line[1], int)

            if car.mode == 'RESOURCE:CYCLING':
                car.previous_fuel = car.fuel
            else:
                car.previous = 0

            car.fuel = convert(line[2], int)

            car.engine = convert(line[3], str)
            car.capacity = convert(line[4], str)
            car.repeated = convert(line[5], str)

            car.visited = convert(line[6], int)

            print("VISITED: $=", round(max(0, car.visited * 10 * 1.04**car.visited * 1.0 - (100.0 - car.fuel)*2)))
            car.until_move = convert(line[7], int)
            car.money = convert(line[8], int)

            if car.location == game.hq and car.until_move == 0:
                if car.mode != "ROCKET":
                    car.has_route = False


    @command('MOVE')
    async def move(self, id, target_city):
        #print("    MOVE " + str(id) + " " + str(target_city))
        pass

    @command('TAKE')
    async def take(self, id, amount):
        pass

    @command('GIVE')
    async def give(self, id, amount):
        pass

    @command('UPGRADE_CAR')
    async def upgrade_car(self, id, upgrade_type):
        print("UPGRADED ENGINE")

    @command('FOUND_ROCKET')
    async def found_rocket(self, id, Sg=50, St=10):
        print("FOUND_ROCKET", id, Sg, St)
        game.rocket = id
        #print("upgrading engine for car #{}".format(car.id))

    @command('LAUNCH_ROCKET')
    async def launch_rocket(self, id):
        print("LAUNCH_ROCKET!!!!!!!!")


    @command('HOME')
    async def home(self):
        line = await self.readline()
        line = line.strip().split()

        game.IDh = convert(line[0], int)
        game.Ch = convert(line[1], int)
        game.IDl = convert(line[2], str)
        game.Lm = convert(line[3], int)


# first car
def should_fund(index, car, game):
    return index in [0] and game.rocket_site

async def play_game(client):
    print("playing game")
    previous_excess = None

    while True:

        await client.describe_world()
        print("\n\n\n")

        await client.home()
        await client.roads_status()
        await client.cars()
        await client.time_to_end()
        await client.rocket_info()
        await client.transfers()
        await client.roads_status()
        print("")
        print("TURN: ${} world={}, Sr={}, Sf={}, revenue={} fuel_cost={} car_capacity={}/{} upgrade={}/{}".format(game.Ch, game.name, game.Sr, game.Sf,  game.R, game.Sg, game.Nmax, game.Umax, game.Sue, game.Sut))


        updated = False
        for index, car in enumerate(game.cars):

            car.show_car(game)


            #If we can build a rocket:
                # location = find location nearest to base
                # build it
                # game.rocket = True
                # game.rocket_location = location
                # game.mode = FUND

            #print("BEFORE FOUND", game.rocket, car.location, game.hq, car.until_move)
            # if game.rocket is None and game.Ch > game.Sf and car.location != game.hq and car.until_move == 0:
            #     print("FOUNDING ROCKET AT: ", car.location)
            #     await client.found_rocket(car.id)
            #     game.Ch -= game.Sf
            #     continue

            if game.Ch > game.Sr:
                await client.launch_rocket(634)

            #if we have enough money to upgrade engine
            if game.Ch > game.Sue and car.has_route == False and car.location == game.hq and car.until_move == 0 and car.engine == "NORMAL":
                await client.upgrade_car(car.id, "ENGINE")
                game.Ch -= game.Sue
                continue

            if should_fund(index, car, game) and car.has_route == False and car.until_move == 0 and car.location == game.hq:

                car.route = graph_util.get_loop(game, game.hq, game.rocket_site)
                distance = graph_util.distance_in_fuel(game, car.route)
                print("CAN WE GO TO ROCKET:", distance)
                if  distance < 150.0:
                    car.has_route = True
                    car.route_index = 0
                    car.mode = 'ROCKET'
                    print("SET ROUTE TO ROCKET:", car.route, car.has_route, car.route_index)
                else:
                    print("ROCKET IS TOO FAR AWAY")
                    game.far_rockets.add(game.rocket_site)
                    game.rocket_site = None

            #print("BEFORE PICK", car.location, game.hq, car.money)
            if should_fund(index, car, game) and car.location == game.hq and car.until_move == 0 and car.money == 0:


                car.money =  min(game.Nmax, game.Ch)
                if car.money != 0:
                    game.Ch -= car.money
                    print("CAR PICKING UP: ", car.id, car.money)
                    await client.take(car.id, car.money)
                    continue

            if should_fund(index, car, game) and car.location == game.rocket_site and car.until_move == 0 and car.money > 0:
                print("CAR GIVING UP: ", car.id, car.money)
                await client.give(car.id, car.money)
                car.money = 0

                continue


            # if should_fund and on rocket location: dump
            #
            # if should fun and on hq location: get

                    # elif upgrade_type == 'TRUNK':
                    #     car.trunk_upgraded = True
            # # if game.Ch > game.Sut and car.has_route == False and car.until_move == 0:
            #     await client.upgrade_car(car.id, "TRUNK", car=car)

            # if (we are in FUND mode and car is IDLE at rocket PUT)
            # if (we are in FUND mode and car at rocket TAKE)

            #
            # if car.until_move == 0 and car.has_route == True and car.mode == "RESOURCE:CYCLING" and car.fuel_forward(game) > car.fuel + 8  and car.fuel_backward(game) > car.fuel - 8:
            #     car.send_car_home(game)
            if car.has_route == False and car.until_move == 0 and car.mode != 'ROCKET':
                if not updated:
                    global cache_path
                    cache_path = None
                    updated = True

                car.get_direcions(game)



            if car.has_route == True and car.until_move == 0:
                next_city = car.get_next_city()
                # print("    moving car: #", car.id, car.location, next_city)
                # print(game.name)
                # print(car.route)
                # print(car.route_index)
                await client.move(car.id, next_city)

            if car.has_route == True and car.until_move > 0:
                pass
                #print("    car is travelling until_move={}".format(car.until_move))




        await client.wait()


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
    await play_game(client)

if __name__ == '__main__':
    # gbulb.install(gtk=True)
    # window = Window(title='Demo')
    # window.set_default_size(1024, 768)
    # window.show_all()

    loop = asyncio.get_event_loop()
    # loop.create_task(window.animate())
    # loop.run_forever()
    loop.run_until_complete(main())
