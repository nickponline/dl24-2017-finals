import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Random;
import java.util.Scanner;

import dl24.Client;
import dl24.ProtocolException;

public class AGridCulture
{
    private static final int[] PORTS = new int[] {
        20003, 20004, 20005
    };
    private static final int[] PROMETHEUS_PORTS = new int[] {
        6021, 6022, 6023
    };

    private final Client client;

    private Random random = new Random();

    private int A, E, F, G, I, L;
    private double D, Z, W, T, Sw, Sh, Ss, N, M, K;
    private boolean B, H;
    private char C;
    private int lastU = -1;

    final int[] qx = new int[250000];
    final int[] qy = new int[250000];
    
    private class Worker
    {
        int x, y, id;
        char[] storage;
        int numStored = 0;
        boolean allocatedToMove;
        int[][] distance;
        int[][] dirX, dirY;
        int nextX, nextY;
        Worker(int id, int x, int y)
        {
            this.id = id;
            this.x = x;
            this.y = y;
            storage = new char[G];
            distance = new int[A][A];
            dirX = new int[A][A];
            dirY = new int[A][A];
        }
        void addToStorage(char color)
        {
            storage[numStored++] = color;
        }
        
        void recomputeDistances()
        {
            for (int i = 0; i < A; i++) {
                Arrays.fill(distance[i], -1);
                Arrays.fill(dirX[i],  0);
                Arrays.fill(dirY[i],  0);
            }
            distance[x][y] = 0;
            dirX[x][y] = 0;
            dirY[x][y] = 0;
            qx[0] = x;
            qy[0] = y;
            int qh = 1;
            int qt = 0;
            while (qt < qh) {
                int curx = qx[qt];
                int cury = qy[qt];
                qt++;
                for (int c = 0; c < 4; c++) {
                    int nx = curx + CX[c];
                    int ny = cury + CY[c];
                    if (nx < 0) nx += A;
                    if (nx >= A) nx -= A;
                    if (ny < 0) ny += A;
                    if (ny >= A) ny -= A;
                    if (map[nx][ny] == '#' && !B) {
                        // No hills allowed.
                        continue;
                    }
                    if (distance[nx][ny] == -1) {
                        distance[nx][ny] = distance[curx][cury] + 1;
                        if (curx == x && cury == y) {
                            dirX[nx][ny] = CX[c];
                            dirY[nx][ny] = CY[c];
                        }
                        else {
                            dirX[nx][ny] = dirX[curx][cury];
                            dirY[nx][ny] = dirY[curx][cury];
                        }
                        qx[qh] = nx;
                        qy[qh] = ny;
                        qh++;
                    }
                }
            }
            
            // Sanity check
            for (int i = 0; i < A; i++) {
                for (int j = 0; j < A; j++) {
                    if (dirX[i][j] == 0 && dirY[i][j] == 0) {
                        assert(distance[i][j] == -1 || (i == x && j == y));
                    }
                }
            }
        }
    }
    
    private class EnemyWorker
    {
        int x, y;
        char color;
        EnemyWorker(char color, int x, int y)
        {
            this.color = color;
            this.x = x;
            this.y = y;
        }
    }
    
    //private class 
    static int[] cx = {1, 1, 0, 0}, cy = {0, 1, 1, 0};

    private char[][] map;
    private int[][] markerExpiry;
    private int[][] nWorkers;
    private boolean[][] claimedForMove;
    private boolean[][] harvestCell;
    private boolean[][] harvestPost;
    
    private List<Worker> workers = new ArrayList<>();
    private List<EnemyWorker> enemyWorkers = new ArrayList<>();
    
    public AGridCulture(Client client)
        throws Exception
    {
        this.client = client;
        
        // Run some basic tests on harvesting.
        initMap(new String[] {
            ".........",
            "...AAA...",
            ".AAA.AAA.",
            "...AAA...",
            ".........",
            ".........",
            ".........",
            ".........",
        }, false);
        assertHarvestMatch(
            3, 1,
            new int[][] {
                {3, 1}, {4, 1}, {5, 1}, {3, 2}, {5, 2}, {3, 3}, {4, 3}, {5, 3}
            },
            new int[][] {
                
            }
        );
        
        initMap(new String[] {
            "................",
            ".BBBBBBBBBBBBB..",
            ".B.AAAAAAA...B..",
            ".B.ABBBB.AA..B..",
            ".B.AB..B.AA..B..",
            ".B.ABBBB.AA..B..",
            ".B.AAAAAAA...B..",
            ".B...........B..",
            ".BBBBBBBBBBBBB..",
            "................",
            "................",
            "................",
            "................",
            "................",
            "................",
            "................",
        }, false);
        
        assertHarvestMatch(
            1, 1,
            new int[][] {
                {1, 1}, {2, 1}, {3, 1}, {4, 1}, {5, 1}, {6, 1}, {7, 1}, {8, 1}, {9, 1}, {10, 1}, {11, 1}, {12, 1}, {13, 1},
                {1, 2}, {13, 2},
                {1, 3}, {13, 3},
                {1, 4}, {13, 4},
                {1, 5}, {13, 5},
                {1, 6}, {13, 6},
                {1, 7}, {13, 7},
                {1, 8}, {2, 8}, {3, 8}, {4, 8}, {5, 8}, {6, 8}, {7, 8}, {8, 8}, {9, 8}, {10, 8}, {11, 8}, {12, 8}, {13, 8},
            },
            new int[][] {}
        );
        assertHarvestMatch(
            4, 2,
            new int[][] {
                {3, 2}, {4, 2}, {5, 2}, {6, 2}, {7, 2}, {8, 2}, {9, 2},
                {3, 3}, {9, 3}, {10, 3},
                {3, 4}, {9, 4}, {10, 4},
                {3, 5}, {9, 5}, {10, 5},
                {3, 6}, {4, 6}, {5, 6}, {6, 6}, {7, 6}, {8, 6}, {9, 6},
            },
            new int[][] {}
        );
        assertHarvestMatch(
            6, 5,
            new int[][] {
                {4, 3}, {5, 3}, {6, 3}, {7, 3},
                {4, 4}, {7, 4},
                {4, 5}, {5, 5}, {6, 5}, {7, 5}
            },
            new int[][] {}
        );
        
        H = true;
        assertHarvestMatch(
            1, 1,
            new int[][] {
                {1, 1}, {2, 1}, {3, 1}, {4, 1}, {5, 1}, {6, 1}, {7, 1}, {8, 1}, {9, 1}, {10, 1}, {11, 1}, {12, 1}, {13, 1},
                {1, 2}, {13, 2},
                {1, 3}, {13, 3},
                {1, 4}, {13, 4},
                {1, 5}, {13, 5},
                {1, 6}, {13, 6},
                {1, 7}, {13, 7},
                {1, 8}, {2, 8}, {3, 8}, {4, 8}, {5, 8}, {6, 8}, {7, 8}, {8, 8}, {9, 8}, {10, 8}, {11, 8}, {12, 8}, {13, 8},
            },
            new int[][] {
                {3, 2}, {4, 2}, {5, 2}, {6, 2}, {7, 2}, {8, 2}, {9, 2},
                {3, 3}, {9, 3}, {10, 3},
                {3, 4}, {9, 4}, {10, 4},
                {3, 5}, {9, 5}, {10, 5},
                {3, 6}, {4, 6}, {5, 6}, {6, 6}, {7, 6}, {8, 6}, {9, 6},
                {4, 3}, {5, 3}, {6, 3}, {7, 3},
                {4, 4}, {7, 4},
                {4, 5}, {5, 5}, {6, 5}, {7, 5}
            }
        );
        assertHarvestMatch(
            4, 2,
            new int[][] {
                {3, 2}, {4, 2}, {5, 2}, {6, 2}, {7, 2}, {8, 2}, {9, 2},
                {3, 3}, {9, 3}, {10, 3},
                {3, 4}, {9, 4}, {10, 4},
                {3, 5}, {9, 5}, {10, 5},
                {3, 6}, {4, 6}, {5, 6}, {6, 6}, {7, 6}, {8, 6}, {9, 6},
            },
            new int[][] {
                {4, 3}, {5, 3}, {6, 3}, {7, 3},
                {4, 4}, {7, 4},
                {4, 5}, {5, 5}, {6, 5}, {7, 5}                    
            }
        );
        assertHarvestMatch(
            6, 5,
            new int[][] {
                {4, 3}, {5, 3}, {6, 3}, {7, 3},
                {4, 4}, {7, 4},
                {4, 5}, {5, 5}, {6, 5}, {7, 5}
            },
            new int[][] {}
        );
        
        initMap(new String[] {
            "............",
            ".AAAAAAAAAA.",
            ".A..A..A..A.",
            ".AAAA..AAAA.",
            "............",
            "............",
            "............",
            "............",
            "............",
            "............",
            "............",
            "............",
        }, false);
        assertHarvestMatch(
            1, 1,
            new int[][] {
                {1, 1}, {2, 1}, {3, 1}, {4, 1}, {7, 1}, {8, 1}, {9, 1}, {10, 1},
                {1, 2}, {4, 2}, {7, 2}, {10, 2},
                {1, 3}, {2, 3}, {3, 3}, {4, 3}, {7, 3}, {8, 3}, {9, 3}, {10, 3},                
            },
            new int[][] {}
        );
    }

    private void initMap(String[] s, boolean killInternal)
    {
        A = s.length;
        map = new char[A][A];
        harvestPost = new boolean[A][A];
        harvestCell = new boolean[A][A];
        for (int i = 0; i < A; i++) {
            for (int j = 0; j < A; j++) {
                map[j][i] = s[i].charAt(j);
            }
        }
        
        H = killInternal;
    }

    private void assertHarvestMatch(int x, int y, int[][] perimeter, int[][] internal)
        throws Exception
    {
        Harvest harvest = getHarvest(x, y);
        assert(perimeter.length == harvest.perimeterX.length);
        for (int i = 0; i < perimeter.length; i++) {
            boolean found = false;
            for (int j = 0; j < harvest.perimeterX.length; j++) {
                if (perimeter[i][0] == harvest.perimeterX[j] && perimeter[i][1] == harvest.perimeterY[j]) {
                    found = true;
                    break;
                }
            }
            assert(found);
        }
        for (int j = 0; j < harvest.perimeterX.length; j++) {
            boolean found = false;
            for (int i = 0; i < perimeter.length; i++) {
                if (perimeter[i][0] == harvest.perimeterX[j] && perimeter[i][1] == harvest.perimeterY[j]) {
                    found = true;
                    break;
                }
            }
            assert(found);
        }

        assert(internal.length == harvest.internalX.length);
        for (int i = 0; i < internal.length; i++) {
            boolean found = false;
            for (int j = 0; j < harvest.internalX.length; j++) {
                if (internal[i][0] == harvest.internalX[j] && internal[i][1] == harvest.internalY[j]) {
                    found = true;
                    break;
                }
            }
            assert(found);
        }
        for (int j = 0; j < harvest.internalX.length; j++) {
            boolean found = false;
            for (int i = 0; i < internal.length; i++) {
                if (internal[i][0] == harvest.internalX[j] && internal[i][1] == harvest.internalY[j]) {
                    found = true;
                    break;
                }
            }
            assert(found);
        }
    }

    public void run()
        throws Exception
    {
        try {
            // First wait for the current turn to end.
            client.doWait();

            while (true) {
                lastU = -1;
                   
                // Get basic description of the world.
                client.writeCommand("DESCRIBE_WORLD");
                Scanner sc = new Scanner(client.readLine());
                A = sc.nextInt();
                B = (sc.nextInt() == 1);
                C = sc.next().charAt(0);
                D = sc.nextDouble();
                E = sc.nextInt();
                F = sc.nextInt();
                G = sc.nextInt();
                H = (sc.nextInt() == 1);
                Z = sc.nextDouble();
                W = sc.nextDouble();
                T = sc.nextDouble();
                Sw = sc.nextDouble();
                Sh = sc.nextDouble();
                Ss = sc.nextDouble();
                N = sc.nextDouble();
                M = sc.nextDouble();
                I = sc.nextInt();
                L = sc.nextInt();
                K = sc.nextDouble();

                // Wait until we can see the map.
                //
                // XXX move into proxy?
                while (true) {
                    try {
                        client.writeCommand("SHOW_MAP");
                        
                        // If we got here, then we succeeded.
                        break;
                    }
                    catch (ProtocolException e) {
                        // Pause briefly and try again.
                        Thread.sleep(100);
                    }
                }
                
                map = new char[A][A];
                markerExpiry = new int[A][A];
                nWorkers = new int[A][A];
                claimedForMove = new boolean[A][A];
                harvestCell = new boolean[A][A];
                harvestPost = new boolean[A][A];
                client.readLine(); // map size
                for (int i = 0; i < A; i++) {
                    String line = client.readLine();
                    for (int j = 0; j < A; j++) {
                        map[j][i] = line.charAt(j);
                    }
                }
                for (int i = 0; i < A; i++) {
                    sc = new Scanner(client.readLine());
                    for (int j = 0; j < A; j++) {
                        if (sc.hasNextInt()) {
                            markerExpiry[j][i] = sc.nextInt();
                        }
                        else {
                            markerExpiry[j][i] = -1;
                        }
                    }
                }
                // Get a handle on where our workers are.
                try {
                    client.writeCommand("LIST_MY_WORKERS");
                    int num = Integer.parseInt(client.readLine());
                    workers.clear();
                    for (int i = 0; i < num; i++) {
                        sc = new Scanner(client.readLine());
                        int id = sc.nextInt();
                        int x = sc.nextInt();
                        int y = sc.nextInt();
                        Worker worker = new Worker(id, x, y);
                        int numStored = sc.nextInt();
                        for (int j = 0; j < numStored; j++) {
                            char Cm = sc.next().charAt(0);
                            int Dm = sc.nextInt();
                            for (int k = 0; k < Dm; k++) {
                                worker.addToStorage(Cm);
                            }
                        }
                        worker.recomputeDistances();
                        workers.add(worker);
                    }
                }
                catch (ProtocolException e) {
                    // Should not really happen so early on...
                    throw e;
                }

                // Now begin the main game loop.
                while (true) {
                    // See how many turns are remaining.
                    client.writeCommand("TIME_TO_END");
                    sc = new Scanner(client.readLine());
                    int U = sc.nextInt();
                    int V = sc.nextInt();
                                        
                    // Once we have completed the game then we go around again. We determine game-end
                    // by U changing by more than we expect.
                    if (lastU != -1 && U != lastU - 1) {
                        break;
                    }
                    else {
                        System.err.println(U + " turns to go");
                        lastU = U;
                    }
                    
                    // Figure out where the enemies are.
                    client.writeCommand("LIST_ENEMY_WORKERS");
                    int numEnemies = Integer.parseInt(client.readLine());
                    enemyWorkers.clear();
                    for (int i = 0; i < numEnemies; i++) {
                        sc = new Scanner(client.readLine());
                        char color = sc.next().charAt(0);
                        int x = sc.nextInt();
                        int y = sc.nextInt();
                        enemyWorkers.add(new EnemyWorker(color, x, y));                        
                    }

                    // Make a note of how many workers there are in every grid cell.
                    for (int i = 0; i < A; i++) {
                        Arrays.fill(nWorkers[i], 0);
                        Arrays.fill(claimedForMove[i], false);
                    }
                    for (int i = 0; i < workers.size(); i++) {
                        Worker worker = workers.get(i);
                        nWorkers[worker.x][worker.y]++;
                    }
                    for (int i = 0; i < enemyWorkers.size(); i++) {
                        EnemyWorker worker = enemyWorkers.get(i);
                        nWorkers[worker.x][worker.y]++;
                    }
                    
                    // Score any areas that are ready to go.
                    scoreAreas();
                    
                    // Place any markers where we are ready to do so.
                    placeMarkers();
                    
                    // Move our workers.
                    moveWorkers();

                    // See if our workers should drop or destroy any markers.
                    dropDestroyMarkers();

                    // Update the distance cache for workers that have moved.
                    for (int i = 0; i < workers.size(); i++) {
                        Worker worker = workers.get(i);
                        if (worker.allocatedToMove) {
                            worker.x = worker.nextX;
                            worker.y = worker.nextY;
                            worker.recomputeDistances();
                        }
                    }

                    // Wait until the end of the turn.
                    client.doWait();
                    
                    // Determine any new markers that were placed in the previous turn.
                    client.writeCommand("SHOW_HISTORY");
                    sc = new Scanner(client.readLine());
                    int Md = sc.nextInt();
                    for (int i = 0; i < Md; i++) {
                        int x = sc.nextInt();
                        int y = sc.nextInt();
                        map[x][y] = '.';
                        markerExpiry[x][y] = -1;
                    }
                    sc = new Scanner(client.readLine());
                    int Mc = sc.nextInt();
                    for (int i = 0; i < Mc; i++) {
                        int x = sc.nextInt();
                        int y = sc.nextInt();
                        char c = sc.next().charAt(0);
                        map[x][y] = c;
                        markerExpiry[x][y] = F;
                    }
                    sc = new Scanner(client.readLine());
                    int Sc = sc.nextInt();
                    Harvest[] harvests = new Harvest[Sc];
                    for (int i = 0; i < Sc; i++) {
                        int x = sc.nextInt();
                        int y = sc.nextInt();
                        harvests[i] = getHarvest(x, y);
                    }
                    
                    for (int i = 0; i < Sc; i++) {
                        Harvest harvest = harvests[i];
                        for (int j = 0; j < harvest.perimeterX.length; j++) {
                            int hx = harvest.perimeterX[j];
                            int hy = harvest.perimeterY[j];
                            map[hx][hy] = '.';
                            markerExpiry[hx][hy] = -1;
                        }
                        if (H) {
                            for (int j = 0; j < harvest.internalX.length; j++) {
                                int hx = harvest.internalX[j];
                                int hy = harvest.internalY[j];
                                map[hx][hy] = '.';
                                markerExpiry[hx][hy] = -1;
                            }
                        }
                    }
                    
                    // Get last score from the round.
                    client.writeCommand("LAST_SCORE");
                    sc = new Scanner(client.readLine());
                    int Slast = sc.nextInt();
                    for (int i = 0; i < Slast; i++) {
                        char color = sc.next().charAt(0);
                        double S = sc.nextDouble();
                        if (color == C) {
                            client.logPrometheusSummary("SCORE", S);
                            System.err.println("Got score " + S + " in last round");
                        }
                    }

                    // Update marker expiries.
                    for (int i = 0; i < A; i++) {
                        for (int j = 0; j < A; j++) {
                            if (markerExpiry[j][i] >= 0) {
                                markerExpiry[j][i] -= 1;
                                if (markerExpiry[j][i] < 0) {
                                    map[j][i] = '.';
                                }
                            }
                        }
                    }
                }
            }
        }
        catch (Exception e) {
            throw e;
        }
    }
    
    static class Harvest
    {
        int[] perimeterX, perimeterY;
        int[] internalX, internalY;
        
        Harvest(int numPerimeter, int numInternal)
        {
            perimeterX = new int[numPerimeter];
            perimeterY = new int[numPerimeter];
            internalX = new int[numInternal];
            internalY = new int[numInternal];
        }
    }

    static int[] CX = {-1, 1, 0, 0}, CY = {0, 0, -1, 1};
    static int[] CHX1 = {0, 1, 0, 0}, CHY1 = {0, 0, 0, 1};
    static int[] CHX2 = {0, 1, 1, 1}, CHY2 = {1, 1, 0, 1};

    private Harvest getHarvest(int x, int y)
        throws Exception
    {
        // Need to return:
        //
        // 1) the perimeter, which will include POSTs to be removed from the map
        // 2) any internal POSTs which should be removed from the map.
        if (map[x][y] == '.') {
            throw new Exception("Failed harvest at blank cell " + x + " " + y);
        }
        
        // Figure out which posts are included.
        for (int i = 0; i < A; i++) {
            Arrays.fill(harvestPost[i], false);
            Arrays.fill(harvestCell[i], false);
        }
        harvestPost[x][y] = true;
        Queue<Integer> q = new ArrayDeque<>();
        q.add(y * A + x);
        while (!q.isEmpty()) {
            int curY = q.peek() / A;
            int curX = q.poll() - curY * A;
            for (int c = 0; c < 4; c++) {
                int nx = curX + CX[c];
                int ny = curY + CY[c];
                if (nx < 0) nx += A;
                if (nx >= A) nx -= A;
                if (ny < 0) ny += A;
                if (ny >= A) ny -= A;
                if (map[nx][ny] == map[x][y] && !harvestPost[nx][ny]) {
                    harvestPost[nx][ny] = true;
                    q.add(nx + ny * A);
                }
            }
        }
        
        // Now flood-fill to figure out which cells are in and out.
        harvestCell[0][0] = true;
        q.add(0);
        int count = 1;
        while (!q.isEmpty()) {
            int curY = q.peek() / A;
            int curX = q.poll() - curY * A;
            for (int c = 0; c < 4; c++) {
                int nx = curX + CX[c];
                int ny = curY + CY[c];
                int chk1x = curX + CHX1[c];
                int chk2x = curX + CHX2[c];
                int chk1y = curY + CHY1[c];
                int chk2y = curY + CHY2[c];

                // We can only cross to the next cell if this does not cross a fence.
                if (harvestPost[chk1x][chk1y] && harvestPost[chk2x][chk2y]) {
                    continue;
                }
                
                if (nx < 0) nx += A - 1;
                if (nx >= A - 1) nx -= A - 1;
                if (ny < 0) ny += A - 1;
                if (ny >= A - 1) ny -= A - 1;
                if (!harvestCell[nx][ny]) {
                    harvestCell[nx][ny] = true;
                    q.add(nx + ny * A);
                    count++;
                }
            }
        }
        
        // Figure out whether it was the true or false cells that got filled.
        boolean value = count < (A * A) / 2;
        int numPerimeter = 0, numInternal = 0;
        for (int i = 0; i < A - 1; i++) {
            for (int j = 0; j < A - 1; j++) {
                if (harvestCell[i][j] == value) {
                    for (int c = 0; c < 4; c++) {
                        int nx = i + cx[c];
                        int ny = j + cy[c];
                        if (harvestPost[nx][ny]) {
                            numPerimeter++;
                        }
                        else if (map[nx][ny] != '.' && map[nx][ny] != '#') {
                            numInternal++;
                        }
                    }
                }
            }
        }
        Harvest harvest = new Harvest(numPerimeter, numInternal);
        numPerimeter = 0; numInternal = 0;
        for (int i = 0; i < A - 1; i++) {
            for (int j = 0; j < A - 1; j++) {
                if (harvestCell[i][j] == value) {
                    for (int c = 0; c < 4; c++) {
                        int nx = i + cx[c];
                        int ny = j + cy[c];
                        if (harvestPost[nx][ny]) {
                            harvest.perimeterX[numPerimeter] = nx;
                            harvest.perimeterY[numPerimeter] = ny;
                            numPerimeter++;
                        }
                        else if (map[nx][ny] != '.' && map[nx][ny] != '#') {
                            harvest.internalX[numInternal] = nx;
                            harvest.internalY[numInternal] = ny;
                            numInternal++;
                        }
                    }
                }
            }
        }
        return harvest;
    }

    private void scoreAreas()
        throws Exception
    {
        // Identify any areas that are ready to be scored.
        System.err.println("Checking for scores with " + C);
        for (int i = 1; i < A; i++) {
            for (int j = 1; j < A; j++) {
                if (map[i][j] == C && map[i - 1][j] == C && map[i][j - 1] == C && map[i - 1][j - 1] == C && client.getCommandsUsed() < L - 1) {
                    // Ready!
                    System.err.println("Harvesting at " + i + " " + j);
                    client.writeCommand("SCORE", i, j);
                }
            }
        }
        /*PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter("/tmp/map.out", true)));
        out.println("Checking for scores with " + C);
        for (int i = 0; i < A; i++) {
            for (int j = 0; j < A; j++) {
                out.print(map[j][i]);
            }
            out.println();
        }
        out.close();*/
    }

    private void placeMarkers()
        throws IOException
    {
        for (int i = 0; i < workers.size(); i++) {
            // This worker should drop a marker if it's current position does not have a marker,
            // and if it is part of a square grid around which we can construct a blob.
            Worker worker = workers.get(i);
            if (map[worker.x][worker.y] != '.' || nWorkers[worker.x][worker.y] > 1) {
                // No point in putting a marker down here, since we can't.
                continue;
            }
            boolean ok = false;
            for (int j = 0; j < 2; j++) {
                for (int k = 0; k < 2; k++) {
                    int nx = worker.x + cx[j];
                    int ny = worker.y + cy[k];
                    if (nx < 0) nx += A;
                    if (nx >= A) nx -= A;
                    if (ny < 0) ny += A;
                    if (ny >= A) ny -= A;
                    int mx = nx - 1;
                    int my = ny - 1;
                    if (mx < 0) mx += A;
                    if (my < 0) my += A;
                    if (
                        (map[nx][ny] == '.' || map[nx][ny] == C) &&
                        (map[mx][ny] == '.' || map[mx][ny] == C) &&
                        (map[nx][my] == '.' || map[nx][my] == C) &&
                        (map[mx][my] == '.' || map[my][my] == C))
                    {
                        ok = true;
                        break;
                    }
                }
            }
            if (ok && client.getCommandsUsed() < L - 1) {
                // We can attempt to put a marker down.
                try {
                    System.err.println("Worker " + worker.id + " placing marker at " + worker.x + ";" + worker.y);
                    client.writeCommand("PUT", worker.id, C);
                    
                    // If that succeeded, then make a note on the map.
                    map[worker.x][worker.y] = C;
                    markerExpiry[worker.x][worker.y] = F;
                }
                catch (ProtocolException e) {
                    // Failed for some reason; probably because another team beat us to it.
                }
            }
        }
    }

    static class TargetCell
        implements Comparable<TargetCell>
    {
        int x, y, value, workerIndex;
        TargetCell(int x, int y, int value, int workerIndex)
        {
            this.x = x;
            this.y = y;
            this.value = value;
            this.workerIndex = workerIndex;
        }
        
        public int compareTo(TargetCell that)
        {
            return Integer.compare(value, that.value);
        }
    }
    
    private void moveWorkers()
        throws Exception
    {
        for (int i = 0; i < workers.size(); i++) {
            workers.get(i).allocatedToMove = false;
        }
        
        for (int val = 3; val >= 0; val--) {
            PriorityQueue<TargetCell> q = new PriorityQueue<>();
            for (int x = 0; x < A; x++) {
                for (int y = 0; y < A; y++) {
                    // Skip marked cells and those with foreign or multiple workers in them.
                    if (claimedForMove[x][y] || map[x][y] != '.' || nWorkers[x][y] > 1) {
                        continue;
                    }
                    if (nWorkers[x][y] == 1) {
                        boolean foreign = true;
                        for (int i = 0; i < workers.size(); i++) {
                            if (workers.get(i).x == x && workers.get(i).y == y) {
                                foreign = false;
                                break;
                            }
                        }
                        if (foreign) {
                            continue;
                        }
                    }
                    
                    // Figure out whether we can place a square of markers here, and if so
                    // how many cells are already filled.
                    boolean ok = false;
                    int value = 0;
                    for (int j = 0; j < 2; j++) {
                        for (int k = 0; k < 2; k++) {
                            int nx = x + cx[j];
                            int ny = y + cy[k];
                            if (nx < 0) nx += A;
                            if (nx >= A) nx -= A;
                            if (ny < 0) ny += A;
                            if (ny >= A) ny -= A;
                            int mx = nx - 1;
                            int my = ny - 1;
                            if (mx < 0) mx += A;
                            if (my < 0) my += A;
                            if (
                                (map[nx][ny] == '.' || map[nx][ny] == C) &&
                                (map[mx][ny] == '.' || map[mx][ny] == C) &&
                                (map[nx][my] == '.' || map[nx][my] == C) &&
                                (map[mx][my] == '.' || map[my][my] == C))
                            {
                                ok = true;
                                int curValue = (map[nx][ny] == C ? 1 : 0) + 
                                               (map[mx][ny] == C ? 1 : 0) + 
                                               (map[nx][my] == C ? 1 : 0) + 
                                               (map[mx][my] == C ? 1 : 0);
                                if (curValue > value) {
                                    value = curValue;
                                }
                            }
                        }
                    }
                    if (!ok || value != val) {
                        continue;
                    }
                    
                    // Figure out which worker would be best to assign to this task.
                    int bestDist = -1;
                    int bestWorker = -1;
                    for (int i = 0; i < workers.size(); i++) {
                        Worker worker = workers.get(i);
                        if (worker.allocatedToMove) {
                            continue;
                        }
                        int dist = worker.distance[x][y];
                        if (dist != -1 && (bestDist == -1 || dist < bestDist)) {
                            bestDist = dist;
                            bestWorker = i;
                        }
                    }
                    if (bestWorker > -1) {
                        q.add(new TargetCell(x, y, bestDist, bestWorker));
                    }
                }
            }
            
            // Suck out the best candidates and assign them for moves.
            while (!q.isEmpty()) {
                TargetCell target = q.poll();
                Worker worker = workers.get(target.workerIndex);
                if (worker.allocatedToMove) {
                    continue;
                }
                else if (client.getCommandsUsed() < L - 1) {
                    worker.allocatedToMove = true;
                    claimedForMove[target.x][target.y] = true;
                    System.err.println("Moving worker " + worker.id + " from " + worker.x + ";" + worker.y + " to " + target.x + ";" + target.y + " for value " + val + " with " + worker.dirX[target.x][target.y] + " and " + worker.dirY[target.x][target.y]);
                    client.writeCommand("MOVE", worker.id, worker.dirX[target.x][target.y], worker.dirY[target.x][target.y]);
                    worker.nextX = worker.x + worker.dirX[target.x][target.y];
                    worker.nextY = worker.y + worker.dirY[target.x][target.y];
                    if (worker.nextX < 0) worker.nextX += A;
                    if (worker.nextX >= A) worker.nextX -= A;
                    if (worker.nextY < 0) worker.nextY += A;
                    if (worker.nextY >= A) worker.nextY -= A;
                }
            }
        }
    }
    
    private void dropDestroyMarkers()
        throws Exception
    {
        for (int i = 0; i < workers.size(); i++) {
            Worker worker = workers.get(i);
            if (map[worker.x][worker.y] != '.' && map[worker.x][worker.y] != '#' && map[worker.x][worker.y] != C && worker.numStored < G && nWorkers[worker.x][worker.y] == 1 && canExecuteCommand()) {
                client.writeCommand("PUT", worker.id, C);
                worker.addToStorage(map[worker.x][worker.y]);
                System.err.println("Replaced marker of team " + map[worker.x][worker.y] + " at " + worker.x + " " + worker.y);
                map[worker.x][worker.y] = C;
                markerExpiry[worker.x][worker.y] = F;
            }
            else if (map[worker.x][worker.y] == '.' && worker.numStored > G - 10 && nWorkers[worker.x][worker.y] == 1 && canExecuteCommand()) {
                // Dump a random marker.
                int index = random.nextInt(worker.numStored);
                client.writeCommand("PUT", worker.id, worker.storage[index]);
                System.err.println("Dumped marker " + worker.storage[index] + " at " + worker.x + " " + worker.y);
                worker.numStored--;
                if (worker.numStored > 0) {
                    worker.storage[index] = worker.storage[worker.numStored];
                }
            }
        }
    }

    private boolean canExecuteCommand()
    {
        return client.getCommandsUsed() < L - 1;
    }

    public static void main(String[] args)
        throws Exception
    {
        int index = Integer.parseInt(args[0]);
        try {
            final Client client = new Client("grid" + index, null, null, "localhost", PORTS[index], PROMETHEUS_PORTS[index]);
            final AGridCulture culture = new AGridCulture(client);
            while (true) {
                try {
                    culture.run();
                }
                catch (Exception e) {
                    if (!e.getMessage().contains("Failed harvest at blank cell")) {
                        client.stop();
                        throw new RuntimeException(e);
                    }
                    else {
                        System.err.println("Failed harvest! " + e);
                    }
                }
            }
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to start client " + index, e);
        }
    }
}
