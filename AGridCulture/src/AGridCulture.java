import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
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
    
    private PrintWriter specialWriter;
    
    private int wrap(int k)
    {
        if (k < 0) return k + A;
        else if (k >= A) return k - A;
        else return k;
        
    }

    private class Worker
    {
        int x, y, id;
        char[] storage;
        int numStored = 0;
        boolean allocatedToMove;
        int[][] distance;
        int[][] dirX, dirY;
        int nextX, nextY;
        boolean special = false;
        int tx1, ty1, tx2, ty2, txl;
        int lastTx, lastTy;
        int specialInd;
        int evilCounter;
        Worker cooperator;
        boolean cooperative;
        boolean primaryCoop;
        int hiveX = -1, hiveY = -1;
        
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
        
        void makeSpecialCooperator(Worker cooperator)
        {
            special = true;
            this.cooperator = cooperator; 
            cooperative = cooperator.cooperative = true;
            primaryCoop = false;
            cooperator.primaryCoop = true;
            lastTx = lastTy = -1;
            specialInd = -1;
        }

        void makeSpecial(List<Worker> workers)
        {
            MAX = Math.max((int) Math.sqrt(A * A / ((workers.size() + enemyWorkers.size()) / 2 + 1)), 7);
            special = true;
            cooperative = false;
            
            // If our worker is too close to some of our other workers, then move it away to a random location.
            for (int i = 0; i < workers.size(); i++) {
                Worker worker = workers.get(i);
                if (worker.id == id) break;
                if (worker.distance[x][y] < 10) {
                    txl = -1;
                    do {
                        lastTx = wrap(x + random.nextInt(10));
                        lastTy = wrap(y + random.nextInt(10));
                    } while (map[lastTx][lastTy] == '#' || distance[lastTx][lastTy] < 10);
                    specialWriter.println("Worker " + id + " relocating to " + lastTx + " " + lastTy + " to get away from " + worker.id + " at " + worker.x + " " + worker.y);
                    return;
                }
            }

            // Look around us to determine the biggest square area that we can place markers on.
            int neg = 0, best = -1, bestNeg = -1, bestPos = -1;
            while (true) {
                neg++;
                boolean ok = map[wrap(x - neg)][wrap(y - neg)] != '#';
                for (int i = 0; i < neg && ok; i++) {
                    ok &= map[wrap(x - neg)][wrap(y - i)] != '#';
                    ok &= map[wrap(x - i)][wrap(y - neg)] != '#';
                }
                if (!ok) {
                    neg--;
                    break;
                }

                if (neg >= A) {
                    break;
                }
                // Now increment in the positive direction and see how far we can go.
                int pos = 0;
                while (true) {
                    int total = pos + neg;
                    if (total > best) {
                        best = total;
                        bestNeg = neg;
                        bestPos = pos;
                    }
                    
                    if (total >= A) {
                        break;
                    }
                    pos++;
                    ok = map[wrap(x + pos)][wrap(y + pos)] != '#';
                    for (int i = -neg; i < pos && ok; i++) {
                        ok &= map[wrap(x + pos)][wrap(y + i)] != '#';
                        ok &= map[wrap(x + i)][wrap(y + pos)] != '#';
                    }
                    if (!ok) {
                        break;
                    }
                }
            }
            
            int best2 = -1, best2Neg = -1, best2Pos = -1;
            neg = 0;
            while (true) {
                neg++;
                boolean ok = map[wrap(x - neg)][wrap(y + neg)] != '#';
                for (int i = 0; i < neg && ok; i++) {
                    ok &= map[wrap(x - neg)][wrap(y + i)] != '#';
                    ok &= map[wrap(x - i)][wrap(y + neg)] != '#';
                }
                if (!ok) {
                    neg--;
                    break;
                }
                
                if (neg >= A) {
                    break;
                }
                // Now increment in the positive direction and see how far we can go.
                int pos = 0;
                while (true) {
                    int total = pos + neg;
                    if (total > best2) {
                        best2 = total;
                        best2Neg = neg;
                        best2Pos = pos;
                    }
                    
                    if (total >= A) {
                        break;
                    }
                    
                    pos++;
                    ok = map[wrap(x + pos)][wrap(y - pos)] != '#';
                    for (int i = -neg; i < pos && ok; i++) {
                        ok &= map[wrap(x + pos)][wrap(y - i)] != '#';
                        ok &= map[wrap(x + i)][wrap(y - pos)] != '#';
                    }
                    if (!ok) {
                        break;
                    }
                }
            }
            
            if (best2 > best) {
                specialWriter.println("Best square for worker " + id + " has size " + best2);
                if (best2 > MAX) {
                    // Cap at MAX for now.
                    specialWriter.println("Capping to " + MAX);
                    best2 = MAX;
                }
                specialWriter.flush();
                tx1 = x - best2Neg;
                ty1 = y - best2Pos;
                tx2 = tx1 + best2;
                ty2 = ty1 + best2;
                txl = best2;
            }
            else {
                specialWriter.println("Best square for worker at " + x + " " + y + " with ID " + id + " has size " + best);
                if (best > MAX) {
                    // Cap at MAX for now.
                    specialWriter.println("Capping to " + MAX);
                    best = MAX;
                }
                specialWriter.flush();
                tx1 = x - bestNeg;
                ty1 = y - bestNeg;
                tx2 = tx1 + best;
                ty2 = ty1 + best;
                txl = best;
            }
            lastTx = -1; lastTy = -1;
            specialInd = -1;
            evilCounter = 0;
        }

        int MAX;
        int MIN = 4;
        
        boolean isSuitable(int tx, int ty, boolean nonEdge)
        {
            char ch = map[tx][ty];
            return (ch != C && ch != '#' && nWorkers[tx][ty] == 0 && (ch == '.' || ((nonEdge && numStored < G - 3) || (!nonEdge && numStored < G))));            
        }
        
        void specialMove(List<Worker> workers)
            throws Exception
        {
            nextX = x;
            nextY = y;
            
            if ((turns % 200) == 0) {
                // Move to a new location to spice things up & stop traps from developing.
                txl = -1;
                lastTx = -1;
            }
            
            //if ((!cooperative || primaryCoop) && 
            if (cooperator == null && txl < MIN) {
                // If we could not get a local square with a useful size, then we should go searching for one.
                if (lastTx != -1) {
                    // We already have a target in mind.
                    if (x == lastTx && y == lastTy) {
                        // We've made it! Re-evaluate our options.
                        makeSpecial(workers);
                    }
                    else {
                        // Otherwise, move toward the new target.
                        if (canExecuteCommand()) {
                            specialWriter.println("Moving from " + x + " " + y + " towards new area at " + lastTx + " " + lastTy);
                            specialWriter.flush();
                            client.writeCommand("MOVE", id, dirX[lastTx][lastTy], dirY[lastTx][lastTy]);
                            nextX = wrap(x + dirX[lastTx][lastTy]);
                            nextY = wrap(y + dirY[lastTx][lastTy]);
                            specialWriter.println("Next is " + nextX + " " + nextY);
                            specialWriter.flush();
                        }
                    }
                }
                else {
                    // Decide on a new target to find a space for us to make a square.
                    specialWriter.println("Deciding on new target for special worker " + id);
                    specialWriter.flush();
                    do {
                        lastTx = wrap(x + random.nextInt(20) - 10);
                        lastTy = wrap(y + random.nextInt(20) - 10);
                    } while (map[lastTx][lastTy] == '#');
                    specialWriter.println("Targeting " + lastTx + " " + lastTy + " for better pastures");
                    specialWriter.flush();
                }
                return;
            }
            
            // Check whether we are besieged by evil forces.
            if (!cooperative || primaryCoop) {
                boolean evilLurks = false;
                for (int i = 0; i < enemyWorkers.size(); i++) {
                    EnemyWorker worker = enemyWorkers.get(i);
                    if (distance[worker.x][worker.y] < txl) {
                        evilLurks = true;
                        break;
                    }
                }
                if (evilLurks) {
                    evilCounter++;
                    if (evilCounter >= 3 * txl) {
                        // Relocate. Do a flood fill from all enemies to find somewhere that is sufficiently far
                        // away from them and close to us.
                        specialWriter.println("We're under attack!");
                        specialWriter.flush();
                        int qt = 0;
                        int qh = 0;
                        for (int i = 0; i < A; i++) {
                            Arrays.fill(evilDist[i], -1);
                        }
                        for (int i = 0; i < enemyWorkers.size(); i++) {
                            int ex = enemyWorkers.get(i).x;
                            int ey = enemyWorkers.get(i).y;
                            if (evilDist[ex][ey] < 0) {
                                qx[qh] = ex;
                                qy[qh] = ey;
                                evilDist[ex][ey] = 0;
                                qh++;
                            }
                        }
                        int close = -1, closeX = -1, closeY = -1;
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
                                if (evilDist[nx][ny] < 0) {
                                    evilDist[nx][ny] = evilDist[curx][cury] + 1;
                                    qx[qh] = nx;
                                    qy[qh] = ny;
                                    qh++;
                                    if ((evilDist[nx][ny] > 2 * txl) && (close < 0 || distance[nx][ny] < close)) {
                                        close = distance[nx][ny];
                                        closeX = nx;
                                        closeY = ny;
                                    }
                                }
                            }
                        }
                        
                        if (close > 0) {
                            // We have somewhere to flee to, so let's do it.
                            specialWriter.println("Worker " + id + " fleeing from evil at " + x + " " + y + " to " + closeX + " " + closeY);
                            txl = -1;
                            lastTx = closeX;
                            lastTy = closeY;
                            evilCounter = 0;
                            return;
                        }
                    }
                }
                else {
                    evilCounter--;
                    if (evilCounter < 0) {
                        evilCounter = 0;
                    }
                }
            }
            
            int best = -1;
            int bestX = -1, bestY = -1;
            if (specialInd != -1) {
                // We have an index in our square that we should be targeting, so do that.
                if (specialInd < txl) {
                    bestX = wrap(tx1 + specialInd);
                    bestY = wrap(ty1);
                }
                else if (specialInd < 2 * txl) {
                    bestX = wrap(tx1 + txl);
                    bestY = wrap(ty1 + specialInd - txl);
                }
                else if (specialInd < 3 * txl) {
                    bestX = wrap(tx1 + 3 * txl - specialInd);
                    bestY = wrap(ty1 + txl);
                }
                else {
                    bestX = wrap(tx1);
                    bestY = wrap(ty1 + 4 * txl - specialInd);
                }
                if (x == bestX && y == bestY) {
                    
                }
                best = distance[bestX][bestY];
            }
            else {
                int bestSpecial = -1;
                // Find the cell on our square border that is closest to us and which needs to be marked.
                if (lastTx != -1 && lastTy != -1 && isSuitable(lastTx, lastTy, lastTx != wrap(tx1) && lastTx != wrap(tx1 + txl) && lastTy != wrap(ty1) && lastTy != wrap(ty1 + txl)) && (x != lastTx || y != lastTy)) {
                    best = distance[lastTx][lastTy];
                    bestX = lastTx;
                    bestY = lastTy;
                }
                else {
                    if (cooperative && !primaryCoop) {
                        // Copy parameters from our primary co-op partner.
                        tx1 = cooperator.tx1;
                        ty1 = cooperator.ty1;
                        tx2 = cooperator.tx2;
                        ty2 = cooperator.ty2;
                        txl = cooperator.txl;
                        if (txl < 0) {
                            // Looks like our co-op partner is being relocated... let's follow them.
                            if ((x != cooperator.x || y != cooperator.y) && canExecuteCommand()) {
                                specialWriter.println("Worker " + id + " following cooperator from " + x + " " + y + " to " + cooperator.x + " " + cooperator.y);
                                client.writeCommand("MOVE", id, dirX[cooperator.x][cooperator.y], dirY[cooperator.x][cooperator.y]);
                                nextX = wrap(x + dirX[cooperator.x][cooperator.y]);
                                nextY = wrap(y + dirY[cooperator.x][cooperator.y]);
                                specialWriter.println("Next is " + nextX + " " + nextY);
                                return;
                            }
                        }
                    }
                    for (int i = 0; i < txl; i++) {
                        int tx = wrap(tx1);
                        int ty = wrap(ty1 + txl - i);
                        if (!(cooperative || primaryCoop) && isSuitable(tx, ty, false)) {
                            int d = distance[tx][ty];
                            if (best == -1 || d < best) {
                                best = d;
                                bestX = tx;
                                bestY = ty;
                                bestSpecial = 3 * txl + i;
                            }
                        }
                        
                        tx = wrap(tx2);
                        ty = wrap(ty1 + i);
                        if ((!cooperative || !primaryCoop) && isSuitable(tx, ty, false)) {
                            int d = distance[tx][ty];
                            if (best == -1 || d < best) {
                                best = d;
                                bestX = tx;
                                bestY = ty;
                                bestSpecial = i + txl;
                            }
                        }
        
                        tx = wrap(tx1 + i);
                        ty = wrap(ty1);
                        if ((!cooperative || primaryCoop) && isSuitable(tx, ty, false)) {
                            int d = distance[tx][ty];
                            if (best == -1 || d < best) {
                                best = d;
                                bestX = tx;
                                bestY = ty;
                                bestSpecial = i;
                            }
                        }
        
                        tx = wrap(tx1 + txl - i);
                        ty = wrap(ty2);
                        if ((!cooperative || !primaryCoop) && isSuitable(tx, ty, false)) {
                            int d = distance[tx][ty];
                            if (best == -1 || d < best) {
                                best = d;
                                bestX = tx;
                                bestY = ty;
                                bestSpecial = 2 * txl + i;
                            }
                        }
                    }
                }
            }
            
            specialWriter.println("T: " + tx1 + " " + ty1 + " " + txl);
            for (int i = 0; i <= txl; i++) {
                for (int j = 0; j <= txl; j++) {
                    int tx = wrap(tx1 + i);
                    int ty = wrap(ty1 + j);
                    char ch = map[tx][ty];
                    if (ch == C) {
                        specialWriter.print('C');
                    }
                    else if (ch == '#') {
                        specialWriter.print('#');
                    }
                    else if (ch == '.') {
                        specialWriter.print('.');
                    }
                    else {
                        specialWriter.print('X');
                    }
                    if (tx == x && ty == y) {
                        specialWriter.print('*');
                    }
                    else if (tx == bestX && ty == bestY) {
                        specialWriter.print('$');
                    }
                    else {
                        specialWriter.print(' ');
                    }
                    specialWriter.print(' ');
                }
                specialWriter.println();
            }
            if (best > 0 && canExecuteCommand()) {
                specialWriter.println("Closest point to " + id + " at " + x + " " + y + " is " + bestX + " " + bestY + " with distance of " + best);
                client.writeCommand("MOVE", id, dirX[bestX][bestY], dirY[bestX][bestY]);
                nextX = wrap(x + dirX[bestX][bestY]);
                nextY = wrap(y + dirY[bestX][bestY]);
                specialWriter.println("Next is " + nextX + " " + nextY);
                lastTx = bestX; lastTy = bestY;
            }
            else {
                lastTx = -1; lastTy = -1;
                if (canExecuteCommand()) {
                    // Pick the closest valid cell within our special area.
                    best = -1;
                    for (int i = 0; i <= txl; i++) {
                        for (int j = 0; j <= txl; j++) {
                            int tx = wrap(tx1 + i);
                            int ty = wrap(ty1 + j);
                            if ((!cooperative || ((i + j <= txl) == primaryCoop)) && isSuitable(tx, ty, true)) {
                                int d = distance[tx][ty];
                                if (best == -1 || d < best) {
                                    best = d;
                                    bestX = tx;
                                    bestY = ty;
                                }
                            }
                        }
                    }
                    if (best > 0) {
                        specialWriter.println("Closest arbitrary point to " + x + " " + y + " is " + bestX + " " + bestY + " with distance of " + best);
                        client.writeCommand("MOVE", id, dirX[bestX][bestY], dirY[bestX][bestY]);
                        nextX = wrap(x + dirX[bestX][bestY]);
                        nextY = wrap(y + dirY[bestX][bestY]);
                        specialWriter.println("Next is " + nextX + " " + nextY);
                    }
                    else {
                        // If we get here, then we probably need to relocate.
                        if (!cooperative || primaryCoop) {
                            txl = -1;
                            lastTx = lastTy = -1;
                        }
                    }
                }
            }
            specialWriter.flush();
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
                        _assert(distance[i][j] == -1 || (i == x && j == y));
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
    
    private char[][] map;
    private int[][] markerExpiry;
    private int[][] nWorkers;
    private boolean[][] claimedForMove;
    private boolean[][] harvestCell;
    private boolean[][] harvestPost;
    private boolean[][] scored;
    private int[][] evilDist;
    private int[][] workerDist;
    private int[][][] bigSquare;
    private int[][][] bigSquareW;
    private int turns;
    private int hiveX, hiveY, hiveL;
    
    private List<Worker> workers = new ArrayList<>();
    private List<EnemyWorker> enemyWorkers = new ArrayList<>();
    
    int index;
    public AGridCulture(Client client, int index)
        throws Exception
    {
        this.client = client;
        this.index = index;
        specialWriter = new PrintWriter(new FileWriter("/Users/carl/contests/deadline24/2017/final/special." + index));
        
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
        });
        assertHarvestMatch(
            3, 1,
            new int[][] {
                {3, 1}, {4, 1}, {5, 1}, {3, 2}, {5, 2}, {3, 3}, {4, 3}, {5, 3}
            },
            new int[][] {
                
            }
        );
        
        C = 'A';
        assertFindHarvest(new int[][][] {
            {
                {3, 1}, {4, 1}, {3, 2}, {4, 2}
            }
        });

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
        });
        
        C = 'B';
        assertFindHarvest(new int[][][] {
            {
                {1, 1}, {2, 1}, {3, 1}, {4, 1}, {5, 1}, {6, 1}, {7, 1}, {8, 1}, {9, 1}, {10, 1}, {11, 1}, {12, 1},
                {1, 2}, {2, 2}, {3, 2}, {4, 2}, {5, 2}, {6, 2}, {7, 2}, {8, 2}, {9, 2}, {10, 2}, {11, 2}, {12, 2},
                {1, 3}, {2, 3}, {3, 3}, {4, 3}, {5, 3}, {6, 3}, {7, 3}, {8, 3}, {9, 3}, {10, 3}, {11, 3}, {12, 3},
                {1, 4}, {2, 4}, {3, 4}, {4, 4}, {5, 4}, {6, 4}, {7, 4}, {8, 4}, {9, 4}, {10, 4}, {11, 4}, {12, 4},
                {1, 5}, {2, 5}, {3, 5}, {4, 5}, {5, 5}, {6, 5}, {7, 5}, {8, 5}, {9, 5}, {10, 5}, {11, 5}, {12, 5},
                {1, 6}, {2, 6}, {3, 6}, {4, 6}, {5, 6}, {6, 6}, {7, 6}, {8, 6}, {9, 6}, {10, 6}, {11, 6}, {12, 6},
                {1, 7}, {2, 7}, {3, 7}, {4, 7}, {5, 7}, {6, 7}, {7, 7}, {8, 7}, {9, 7}, {10, 7}, {11, 7}, {12, 7},
            }
        });

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
                {3, 4}, {10, 4},
                {3, 5}, {9, 5}, {10, 5},
                {3, 6}, {4, 6}, {5, 6}, {6, 6}, {7, 6}, {8, 6}, {9, 6},
            },
            new int[][] {
                {4, 3}, {5, 3}, {6, 3}, {7, 3},
                {4, 4}, {7, 4}, {9, 4},
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
        });
        assertHarvestMatch(
            1, 1,
            new int[][] {
                {1, 1}, {2, 1}, {3, 1}, {4, 1}, {7, 1}, {8, 1}, {9, 1}, {10, 1},
                {1, 2}, {4, 2}, {7, 2}, {10, 2},
                {1, 3}, {2, 3}, {3, 3}, {4, 3}, {7, 3}, {8, 3}, {9, 3}, {10, 3},                
            },
            new int[][] {}
        );
        
        initMap(new String[] {
            ".......",
            ".AAAAA.",
            ".A.A.A.",
            ".A...A.",
            ".AAAAA.",
            ".......",
            ".......",
        });
        assertHarvestMatch(
            1, 1,
            new int[][] {
                {1, 1}, {2, 1}, {3, 1}, {4, 1}, {5, 1},
                {1, 2}, {5, 2},
                {1, 3}, {5, 3},
                {1, 4}, {2, 4}, {3, 4}, {4, 4}, {5, 4}
            },
            new int[][] {
                {3, 2}
            }
        );
    }

    private void initMap(String[] s)
    {
        A = s.length;
        map = new char[A][A];
        harvestPost = new boolean[A][A];
        harvestCell = new boolean[A][A];
        scored = new boolean[A][A];
        for (int i = 0; i < A; i++) {
            for (int j = 0; j < A; j++) {
                map[j][i] = s[i].charAt(j);
            }
        }
    }

    private void _assert(boolean condition)
    {
        if (!condition) {
            throw new RuntimeException();
        }
    }
    
    private void assertHarvestMatch(int x, int y, int[][] perimeter, int[][] internal)
        throws Exception
    {
        Harvest harvest = getHarvest(x, y);
        _assert(perimeter.length == harvest.perimeterX.length);
        for (int i = 0; i < perimeter.length; i++) {
            boolean found = false;
            for (int j = 0; j < harvest.perimeterX.length; j++) {
                if (perimeter[i][0] == harvest.perimeterX[j] && perimeter[i][1] == harvest.perimeterY[j]) {
                    found = true;
                    break;
                }
            }
            _assert(found);
        }
        for (int j = 0; j < harvest.perimeterX.length; j++) {
            boolean found = false;
            for (int i = 0; i < perimeter.length; i++) {
                if (perimeter[i][0] == harvest.perimeterX[j] && perimeter[i][1] == harvest.perimeterY[j]) {
                    found = true;
                    break;
                }
            }
            _assert(found);
        }

        _assert(internal.length == harvest.internalX.length);
        for (int i = 0; i < internal.length; i++) {
            boolean found = false;
            for (int j = 0; j < harvest.internalX.length; j++) {
                if (internal[i][0] == harvest.internalX[j] && internal[i][1] == harvest.internalY[j]) {
                    found = true;
                    break;
                }
            }
            _assert(found);
        }
        for (int j = 0; j < harvest.internalX.length; j++) {
            boolean found = false;
            for (int i = 0; i < internal.length; i++) {
                if (internal[i][0] == harvest.internalX[j] && internal[i][1] == harvest.internalY[j]) {
                    found = true;
                    break;
                }
            }
            _assert(found);
        }
    }

    private void assertFindHarvest(int[][][] harvests)
    {
        List<Area> areas = getAreasToScore();
        _assert(harvests.length == areas.size());
        for (int i = 0; i < harvests.length; i++) {
            int[][] harvest = harvests[i];
            
            // Find an area which matches this harvest.
            boolean found = false;
            for (int a = 0; a < areas.size(); a++) {
                Area area = areas.get(a);
                if (area.x.length != harvest.length) {
                    continue;
                }
                boolean ok = true;
                for (int j = 0; j < harvest.length; j++) {
                    boolean foundEl = false;
                    for (int k = 0; k < area.x.length; k++) {
                        if (area.x.get(k) == harvest[j][0] && area.y.get(k) == harvest[j][1]) {
                            foundEl = true;
                            break;
                        }
                    }
                    if (!foundEl) {
                        ok = false;
                        break;
                    }
                }
                for (int k = 0; k < area.x.length; k++) {
                    boolean foundEl = false;
                    for (int j = 0; j < harvest.length; j++) {
                        if (area.x.get(k) == harvest[j][0] && area.y.get(k) == harvest[j][1]) {
                            foundEl = true;
                            break;
                        }
                    }
                    if (!foundEl) {
                        ok = false;
                        break;
                    }
                }
                if (ok) {
                    found = true;
                }
            }
            _assert(found);
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
                scored = new boolean[A][A];
                evilDist = new int[A][A];
                workerDist = new int[A][A];
                bigSquare = new int[A][A][A];
                bigSquareW = new int[A][A][A];
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
                    int numSpecial = num;//Math.max(num / 2, num - 2);
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
                        if (i < numSpecial) {
                            // Make this a special worker.
                            if ((i % 2) == 0) {
                                worker.makeSpecial(workers);
                            }
                            else {
                                // Make this a cooperator.
                                worker.makeSpecialCooperator(workers.get(i - 1));
                            }
                        }
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
                        turns = 0;
                        hiveX = hiveY = hiveL = -1;
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
                    
                    runStrategy();

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
                    System.err.println("Commands left: " + (L - client.getCommandsUsed()));
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
                        try {
                            harvests[i] = getHarvest(x, y);
                        }
                        catch (Exception e) {
                            // Ignore failure to harvest errors!
                            //
                            // XXX
                            harvests[i] = null;
                        }
                    }
                    
                    for (int i = 0; i < Sc; i++) {
                        Harvest harvest = harvests[i];
                        if (harvest == null) continue;
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
                    
                    turns++;
                }
            }
        }
        catch (Exception e) {
            throw e;
        }
    }
    
    void runStrategy()
        throws Exception
    {
        if (index == -1) {
            runHeroStrategy();
        }
        else {
            runHiveMindStrategy();
        }
    }
    
    class GoalWorker implements Comparable<GoalWorker>
    {
        int x, y, w, d;

        public GoalWorker(int x, int y, int w, int d)
        {
            this.x = x; this.y = y; this.w = w; this.d = d;
        }
        
        public int compareTo(GoalWorker that)
        {
            return Integer.compare(d,  that.d);
        }
    }
    void runHiveMindStrategy()
        throws Exception
    {
        if (hiveX == -1 || (turns % 1000) == 0) {
            determineHivemindLocation();
        }
        
        // Execute claim operations.
        for (int i = 0; i < workers.size(); i++) {
            Worker w = workers.get(i);
            final char ch = map[w.x][w.y];
            if (w.hiveX != -1 && w.x == w.hiveX && w.y == w.hiveY) {
                _assert(ch != C);
                if (nWorkers[w.hiveX][w.hiveY] == 1 && canExecuteCommand()) {
                    // We're the only worker and so can go ahead with claiming this spot. 
                    if (ch == '.') {
                        // Easy.
                        client.writeCommand("PUT", w.id, C);
                        map[w.x][w.y] = C;
                        markerExpiry[w.x][w.y] = F;
                    }
                    else if (w.numStored < G) {
                        client.writeCommand("PUT", w.id, C);
                        w.addToStorage(ch);
                        map[w.x][w.y] = C;
                        markerExpiry[w.x][w.y] = F;
                    }
                }
            }
            else if (ch == '.' && w.numStored > 0 && canExecuteCommand() && nWorkers[w.x][w.y] == 1) {
                // Prepare to dump. Make sure that this point is not inside our hive!
                int distX = w.x - hiveX;
                int distY = w.y - hiveY;
                if (distX < 0) distX += A;
                if (distY < 0) distY += A;
                if (distX >= hiveL && distY >= hiveL) {
                    // Dump something random from our storage.
                    int index = random.nextInt(w.numStored);
                    char dump = w.storage[index];
                    client.writeCommand("PUT", w.id, dump);
                    w.numStored--;
                    w.storage[index] = w.storage[w.numStored];
                }
            }
        }
        
        // Clear each worker's previous state about where it's going, if the cell is now occupied by us
        // (or by an opponent's POST and we do not have capacity to replace it).
        for (int i = 0; i < workers.size(); i++) {
            Worker w = workers.get(i);
            //if (w.hiveX != -1 && (map[w.hiveX][w.hiveY] == C || (map[w.hiveX][w.hiveY] != '.' && w.numStored >= G))) {
                w.hiveX = w.hiveY = -1;
            //}
            w.allocatedToMove = false;
        }

        // Make a note of the hive cells that are already claimed by a worker.
        for (int i = 0; i < A; i++) {
            Arrays.fill(claimedForMove[i], false);
        }
        for (int i = 0; i < workers.size(); i++) {
            Worker w = workers.get(i);
            if (w.hiveX != -1) {
                claimedForMove[w.hiveX][w.hiveY] = true;
            }
        }
        
        // For each worker that does not currently have a goal, assign it one.
        //
        // Note, rather than assigning each worker to a goal, we instead create a list of
        // all the goal/worker pairs that could be assigned, and then allocate them in
        // order of proximity.
        List<GoalWorker> options = new ArrayList<>();
        for (int i = 0; i < workers.size(); i++) {
            Worker w = workers.get(i);
            if (w.hiveX == -1) {
                // See if there is a part of our hive which we can mark using this worker.
                int best = -1, bestX = -1, bestY = -1;
                for (int x = 0; x < hiveL && w.hiveX == -1; x++) {
                    for (int y = 0; y < hiveL && w.hiveX == -1; y++) {
                        int nx = wrap(hiveX + x);
                        int ny = wrap(hiveY + y);
                        if (!claimedForMove[nx][ny] && (map[nx][ny] == '.' || (map[nx][ny] != C && w.numStored < G))) {
                            int d = w.distance[nx][ny];
                            options.add(new GoalWorker(nx, ny, i, d));
                        }
                    }
                }
            }
        }
        Collections.sort(options);
        for (int i = 0; i < options.size(); i++) {
            GoalWorker g = options.get(i);
            Worker w = workers.get(g.w);
            if (w.hiveX == -1 && !claimedForMove[g.x][g.y]) {
                // We can allocate this cell to this worker.
                w.hiveX = g.x;
                w.hiveY = g.y;
                claimedForMove[g.x][g.y] = true;
            }
        }
        
        // For any workers that have nothing to do and have full storage, send them out to dump stuff.
        for (int i = 0; i < workers.size(); i++) {
            Worker w = workers.get(i);
            if (w.hiveX == -1 && w.numStored >= G) {
                // Find the closest blank square that we can dump onto.
                for (int j = 0; j < A; j++) {
                    Arrays.fill(scored[j], false);
                }
                int qt = 0;
                int qh = 1;
                qx[0] = w.x;
                qy[0] = w.y;
                scored[w.x][w.y] = true;
                while (qt < qh) {
                    int curx = qx[qt];
                    int cury = qy[qt];
                    if (map[curx][cury] == '.' && !claimedForMove[curx][cury]) {
                        break;
                    }
                    qt++;
                    for (int c = 0; c < 4; c++) {
                        int nx = curx + CX[c];
                        int ny = cury + CY[c];
                        if (nx < 0) nx += A;
                        if (nx >= A) nx -= A;
                        if (ny < 0) ny += A;
                        if (ny >= A) ny -= A;
                        if (!scored[nx][ny]) {
                            qx[qh] = nx;
                            qy[qh] = ny;
                            scored[nx][ny] = true;
                            qh++;
                        }
                    }
                }
                
                // Move towards the empty cell.
                int tx = qx[qt];
                int ty = qy[qt];
                if ((tx != w.x || ty != w.y) && canExecuteCommand()) {
                    specialWriter.println("Worker " + w.id + " at " + w.x + " " + w.y + " moving towards empty cell " + tx + " " + ty + " to dump");
                    int dx = w.dirX[tx][ty];
                    int dy = w.dirY[tx][ty];
                    client.writeCommand("MOVE", w.id, dx, dy);
                    w.nextX = w.x + dx;
                    w.nextY = w.y + dy;
                    w.allocatedToMove = true;
                    claimedForMove[tx][ty] = true;
                }
            }
        }
        
        // Now execute moves.
        for (int i = 0; i < workers.size(); i++) {
            Worker w = workers.get(i);
            if (canExecuteCommand()) {
                if (w.hiveX != -1 && (w.x != w.hiveX || w.y != w.hiveY)) {
                    // Move this worker towards the hive.
                    int dx = w.dirX[w.hiveX][w.hiveY];
                    int dy = w.dirY[w.hiveX][w.hiveY];
                    try {
                        client.writeCommand("MOVE", w.id, dx, dy);
                    }
                    catch (ProtocolException e) {
                        throw e;
                    }
                    w.nextX = wrap(w.x + dx);
                    w.nextY = wrap(w.y + dy);
                    w.allocatedToMove = true;
                }
                else if (w.hiveX == -1 && !w.allocatedToMove) {
                    // Get out of dodge.
                    for (int j = -1; j <= hiveL && !w.allocatedToMove; j++) {
                        for (int k = 0; k < 4 && !w.allocatedToMove; k++) {
                            int nx = -1, ny = -1;
                            switch (k) {
                            case 0: nx = hiveX - 1; ny = hiveY + j; break;
                            case 1: nx = hiveX + hiveL; ny = hiveY + j; break;
                            case 2: nx = hiveX + j; ny = hiveY - 1; break;
                            case 3: nx = hiveX + j; ny = hiveY + hiveL; break;
                            }
                            nx = wrap(nx);
                            ny = wrap(ny);
                            if (map[nx][ny] != '#' || B) {
                                if (w.x != nx || w.y != ny) {
                                    int dx = w.dirX[nx][ny];
                                    int dy = w.dirY[nx][ny];
                                    specialWriter.println("Worker " + w.id + " at " + w.x + " " + w.y + " aiming for " + nx + " " + ny + " to get out");
                                    client.writeCommand("MOVE", w.id, dx, dy);
                                    w.nextX = wrap(w.x + dx);
                                    w.nextY = wrap(w.y + dy);
                                    w.allocatedToMove = true;
                                }
                                else {
                                    w.allocatedToMove = true;
                                    w.nextX = w.x;
                                    w.nextY = w.y;
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Finally, do scoring. We score the area when the total area consumed is equal to the area of the hive
        // minus the number of enemy workers in our hive space.
        List<Area> areas = getAreasToScore();
        int totalScore = 0;
        for (int i = 0; i < areas.size(); i++) {
            totalScore += areas.get(i).x.length;
        }
        int expectedScore = (hiveL - 1) * (hiveL - 1);
        int enemies = 0;
        for (EnemyWorker worker : enemyWorkers) {
            int distX = worker.x - hiveX;
            int distY = worker.y - hiveY;
            if (distX < 0) distX += A;
            if (distY < 0) distY += A;
            if (distX < hiveL || distY < hiveL) {
                enemies++;
            }
        }
        //expectedScore -= enemies / 4;
        
        // If none of our workers have spare storage and if they're all idle, then score.
        boolean force = true;
        for (int i = 0; i < workers.size(); i++) {
            Worker w = workers.get(i);
            if (w.hiveX != -1 || w.numStored < G) {
                force = false;
            }
        }
        
        // If many of our POSTs in the hive are about to expire, then score now.
        int expire = 0;
        for (int i = 0; i < hiveL; i++) {
            for (int j = 0; j < hiveL; j++) {
                int x = wrap(hiveX + i);
                int y = wrap(hiveY + j);
                if (map[x][y] == C && 0 <= markerExpiry[x][y] && markerExpiry[x][y] <= 1) {
                    expire++;
                }
            }
        }
        expectedScore -= expire;
        if (force || totalScore >= expectedScore) {
            // Hit it!
            for (Area area : areas) {
                if (canExecuteCommand()) {
                    client.writeCommand("SCORE", area.sx, area.sy);
                }
            }
        }
        else {
            specialWriter.println("Total score was " + totalScore + " but we expected at least " + expectedScore + " to hit");
        }
        
        // Last of all, to aid with debugging we dump the state of the hive and the distance of our workers from
        // their targets.
        for (int i = -1; i <= hiveL; i++) {
            for (int j = -1; j <= hiveL; j++) {
                int x = wrap(hiveX + i);
                int y = wrap(hiveY + j);
                specialWriter.print(map[x][y]);
                boolean hasWorker = false;
                for (Worker w : workers) {
                    if (w.x == x && w.y == y) {
                        hasWorker = true;
                        specialWriter.print('*');
                        break;
                    }
                }
                if (!hasWorker) {
                    specialWriter.print(' ');
                }
            }
            specialWriter.println();
        }
        for (Worker w : workers) {
            if (w.hiveX != -1) {
                specialWriter.println("Worker " + w.id + " at " + w.x + " " + w.y + " aiming for " + w.hiveX + " " + w.hiveY);
            }
        }
        specialWriter.flush();
    }

    void determineHivemindLocation()
    {
        // Determine the distance of each grid point from the enemies.
        System.err.println("Computing distance of all grid points from enemies");
        int qt = 0;
        int qh = 0;
        for (int i = 0; i < A; i++) {
            Arrays.fill(evilDist[i], -1);
        }
        for (int i = 0; i < enemyWorkers.size(); i++) {
            int ex = enemyWorkers.get(i).x;
            int ey = enemyWorkers.get(i).y;
            if (evilDist[ex][ey] < 0) {
                qx[qh] = ex;
                qy[qh] = ey;
                evilDist[ex][ey] = 0;
                qh++;
            }
        }
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
                if (evilDist[nx][ny] < 0) {
                    evilDist[nx][ny] = evilDist[curx][cury] + 1;
                    qx[qh] = nx;
                    qy[qh] = ny;
                    qh++;
                }
            }
        }
        
        // And do the same for our workers.
        qt = 0;
        qh = 0;
        for (int i = 0; i < A; i++) {
            Arrays.fill(workerDist[i], -1);
        }
        for (int i = 0; i < workers.size(); i++) {
            int ex = workers.get(i).x;
            int ey = workers.get(i).y;
            if (workerDist[ex][ey] < 0) {
                qx[qh] = ex;
                qy[qh] = ey;
                workerDist[ex][ey] = 0;
                qh++;
            }
        }
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
                if (workerDist[nx][ny] < 0) {
                    workerDist[nx][ny] = workerDist[curx][cury] + 1;
                    qx[qh] = nx;
                    qy[qh] = ny;
                    qh++;
                }
            }
        }
        
        // Determine the average distance from evil for all squares that can be formed in the grid.
        double best = -1;
        int bestX = -1, bestY = -1, bestSize = -1;
        System.err.println("Determining best grid location & square size");
        int maxSize = Math.min(A,  F / (workers.size() * 2));
        for (int size = 0; size < maxSize; size++) {
            int num = (size + 1) * (size + 1);
            for (int i = 0; i < A; i++) {
                for (int j = 0; j < A; j++) {
                    if (map[i][j] == '#') {
                        bigSquare[i][j][size] = -1;
                        bigSquareW[i][j][size] = -1;
                    }
                    else if (size == 0) {
                        bigSquare[i][j][size] = evilDist[i][j];
                        bigSquareW[i][j][size] = workerDist[i][j];
                    }
                    else {
                        int left = i - 1;
                        if (left < 0) left += A;
                        int up = j - 1;
                        if (up < 0) up += A;
                        int cornerX = i - size;
                        if (cornerX < 0) cornerX += A;
                        int cornerY = j - size;
                        if (cornerY < 0) cornerY += A;
                        if (bigSquare[left][j][size - 1] == -1 || bigSquare[i][up][size - 1] == -1 || bigSquare[cornerX][cornerY][0] == -1) {
                            bigSquare[i][j][size] = -1;
                            bigSquareW[i][j][size] = -1;
                        }
                        else {
                            int result = bigSquare[left][j][size - 1] + bigSquare[i][up][size - 1] + bigSquare[cornerX][cornerY][0];
                            int resultW = bigSquareW[left][j][size - 1] + bigSquareW[i][up][size - 1] + bigSquareW[cornerX][cornerY][0];
                            if (size > 1) {
                                result -= bigSquare[left][up][size - 2];
                                resultW -= bigSquareW[left][up][size - 2];
                            }
                            bigSquare[i][j][size] = result;
                            bigSquareW[i][j][size] = resultW;
                            
                            double averageDistanceFromEvil = ((double) result) / ((double) num);
                            //double averageDistanceFromWorkers = ((double) result) / ((double) num);
                            double averageDistanceFromWorkers = 0;
                            for (int w = 0; w < workers.size(); w++) {
                                Worker W = workers.get(w);
                                int distX = W.x - (i - size / 2);
                                int distY = W.y - (j - size / 2);
                                if (distX < 0) distX += A;
                                if (distX >= A) distX -= A;
                                if (distY < 0) distY += A;
                                if (distY >= A) distY -= A;
                                averageDistanceFromWorkers += distX + distY;
                            }
                            averageDistanceFromWorkers /= workers.size();
                            if (averageDistanceFromEvil > size / 2.0 && (best < 0 || size > bestSize || averageDistanceFromWorkers < best)) {
                                best = averageDistanceFromWorkers;
                                bestSize = size;
                                bestX = i;
                                bestY = j;
                            }
                        }
                    }
                }
            }
        }
                
        _assert(best > 0);
        
        hiveX = bestX - bestSize;
        hiveY = bestY - bestSize;
        hiveL = bestSize + 1;
        System.err.println("Hive determined as " + hiveX + " " + hiveY + " " + hiveL + " with distance from enemies of " + best);
    }

    void runHeroStrategy()
        throws Exception
    {
        // Place any markers where we are ready to do so.
        placeMarkers();
        
        // Score any areas that are ready to go.
        scoreAreas();
        
        // Move our workers.
        moveWorkers();

        // See if our workers should drop or destroy any markers.
        dropDestroyMarkers();
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
    static int[] cx = {1, 1, 0, 0}, cy = {0, 1, 1, 0};

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
            Arrays.fill(scored[i],  false);
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
                        
                        int mx1 = i + (cx[c] == 0 ? -1 : 1);
                        int my1 = j;
                        int mx2 = i;
                        int my2 = j + (cy[c] == 0 ? -1 : 1);

                        if (mx1 < 0) mx1 += A - 1;
                        if (mx1 >= A - 1) mx1 -= A - 1;
                        if (my2 < 0) my2 += A - 1;
                        if (my2 >= A - 1) my2 -= A - 1;
                        int mx3 = mx1;
                        int my3 = my2;
                        
                        if (harvestPost[nx][ny] && !scored[nx][ny] && (harvestCell[mx1][my1] != value || harvestCell[mx2][my2] != value || harvestCell[mx3][my3] != value)) {
                            scored[nx][ny] = true;
                            numPerimeter++;
                        }
                        else if (map[nx][ny] != '.' && map[nx][ny] != '#' && !scored[nx][ny]) {
                            scored[nx][ny] = true;
                            numInternal++;
                        }
                    }
                }
            }
        }
        for (int i = 0; i < A; i++) {
            Arrays.fill(scored[i], false);
        }
        Harvest harvest = new Harvest(numPerimeter, numInternal);
        numPerimeter = 0; numInternal = 0;
        for (int i = 0; i < A - 1; i++) {
            for (int j = 0; j < A - 1; j++) {
                if (harvestCell[i][j] == value) {
                    for (int c = 0; c < 4; c++) {
                        int nx = i + cx[c];
                        int ny = j + cy[c];

                        int mx1 = i + (cx[c] == 0 ? -1 : 1);
                        int my1 = j;
                        int mx2 = i;
                        int my2 = j + (cy[c] == 0 ? -1 : 1);

                        if (mx1 < 0) mx1 += A - 1;
                        if (mx1 >= A - 1) mx1 -= A - 1;
                        if (my2 < 0) my2 += A - 1;
                        if (my2 >= A - 1) my2 -= A - 1;
                        int mx3 = mx1;
                        int my3 = my2;
                        
                        if (harvestPost[nx][ny] && !scored[nx][ny] && (harvestCell[mx1][my1] != value || harvestCell[mx2][my2] != value || harvestCell[mx3][my3] != value)) {
                            harvest.perimeterX[numPerimeter] = nx;
                            harvest.perimeterY[numPerimeter] = ny;
                            scored[nx][ny] = true;
                            numPerimeter++;
                        }
                        else if (map[nx][ny] != '.' && map[nx][ny] != '#' && !scored[nx][ny]) {
                            harvest.internalX[numInternal] = nx;
                            harvest.internalY[numInternal] = ny;
                            scored[nx][ny] = true;
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
        List<Area> areas = getAreasToScore();
        Collections.sort(areas);
        for (Area area : areas) {
            // How big a size do we want before we harvest?
            if (area.x.length > 5 && canExecuteCommand()) {
                // Yum!
                System.err.println("Harvesting at " + area.sx + " " + area.sy + " with size of " + area.x.length);
                client.writeCommand("SCORE", area.sx, area.sy);
            }
            else {
                //System.err.println("Skipping area of size " + area.x.length);
            }
        }        
    }
    
    class IntList
    {
        int[] list = new int[1];
        int length = 0;
        public void add(int x)
        {
            if (length >= list.length) {
                list = Arrays.copyOf(list, list.length * 2);
            }
            list[length++] = x;
        }
        public int get(int i)
        {
            if (i >= length) {
                throw new ArrayIndexOutOfBoundsException(i);
            }
            else {
                return list[i];
            }
        }
        public void clear()
        {
            length = 0;
        }
    }
    class Area implements Comparable<Area> {
        IntList x = new IntList(), y = new IntList();
        int sx, sy;
        
        public int compareTo(Area that)
        {
            return Integer.compare(that.x.length, x.length);
        }
    }

    private List<Area> getAreasToScore()
    {
        for (int i = 0; i < A; i++) {
            Arrays.fill(scored[i], false);
            Arrays.fill(harvestCell[i], false);
        }

        // Identify any areas that are ready to be scored. To determine this, we flood-fill and see which areas
        // form contiguous chunks of space.
        qx[0] = 0;
        qy[0] = 0;
        int qt = 0;
        int qh = 1;
        int count = 1;
        scored[0][0] = true;
        while (qt < qh) {
            int curx = qx[qt];
            int cury = qy[qt];
            qt++;
            for (int c = 0; c < 4; c++) {
                int nx = curx + CX[c];
                int ny = cury + CY[c];
                int chk1x = curx + CHX1[c];
                int chk2x = curx + CHX2[c];
                int chk1y = cury + CHY1[c];
                int chk2y = cury + CHY2[c];

                // We can only cross to the next cell if this does not cross a fence.
                if (map[chk1x][chk1y] == C && map[chk2x][chk2y] == C) {
                    continue;
                }

                if (nx < 0) nx += A - 1;
                if (nx >= A - 1) nx -= A - 1;
                if (ny < 0) ny += A - 1;
                if (ny >= A - 1) ny -= A - 1;
                if (!scored[nx][ny]) {
                    scored[nx][ny] = true;
                    qx[qh] = nx;
                    qy[qh] = ny;
                    qh++;
                    count++;
                }                
            }
        }
        
        // Find areas to score.
        List<Area> areas = new ArrayList<>();
        boolean value = count < (A * A / 2);
        for (int x = 0; x < A - 1; x++) {
            for (int y = 0; y < A - 1; y++) {
                if (scored[x][y] == value && !harvestCell[x][y]) {
                    // Flood-fill in here, finding any POST to score from.
                    int scoreX = -1, scoreY = -1;
                    int size = 1;
                    qx[0] = x;
                    qy[0] = y;
                    qt = 0;
                    qh = 1;
                    Area area = new Area();
                    areas.add(area);
                    area.x.add(x);
                    area.y.add(y);
                    harvestCell[x][y] = true;
                    while (qt < qh) {
                        int curx = qx[qt];
                        int cury = qy[qt];
                        qt++;
                        for (int c = 0; c < 4; c++) {
                            int nx = curx + CX[c];
                            int ny = cury + CY[c];
                            int chk1x = curx + CHX1[c];
                            int chk2x = curx + CHX2[c];
                            int chk1y = cury + CHY1[c];
                            int chk2y = cury + CHY2[c];

                            if (nx < 0) nx += A - 1;
                            if (nx >= A - 1) nx -= A - 1;
                            if (ny < 0) ny += A - 1;
                            if (ny >= A - 1) ny -= A - 1;

                            if (scored[nx][ny] != value) {
                                // If this fence crossing would take us outside the contained area,
                                // and if we do not yet have a POST to activate the score from, then
                                // make a note of one of these POSTs now.
                                if (scoreX == -1) {
                                    _assert(map[chk1x][chk1y] == C);
                                    scoreX = chk1x;
                                    scoreY = chk1y;
                                }
                                continue;
                            }

                            if (scored[nx][ny] == value && !harvestCell[nx][ny]) {
                                harvestCell[nx][ny] = true;
                                qx[qh] = nx;
                                qy[qh] = ny;
                                area.x.add(nx);
                                area.y.add(ny);
                                qh++;
                                size++;
                            }                
                        }
                    }
                    if (scoreX == -1) {
                        System.err.println("Bad area!");
                        for (int i = 0; i < A - 1; i++) {
                            for (int j = 0; j < A - 1; j++) {
                                if (harvestCell[i][j]) {
                                    System.err.println(i + " " + j);
                                }
                            }
                        }
                        throw new RuntimeException("Sprack!");
                    }
                    else {
                        area.sx = scoreX;
                        area.sy = scoreY;
                    }
                }
            }
        }
        return areas;
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
                if (worker.special) {
                    specialWriter.println("Unable to put a marker for " + worker.id + " because: " + map[worker.x][worker.y] + " " + nWorkers[worker.x][worker.y]);
                    specialWriter.flush();
                }
                continue;
            }
            boolean ok = true; //false;
            if (worker.special &&
                ((worker.x != wrap(worker.tx1) && worker.x != wrap(worker.tx1 + worker.txl)) &&
                 (worker.y != wrap(worker.ty1) && worker.y != wrap(worker.ty1 + worker.txl))) &&
                worker.numStored >= G - 2)
            {
                // In this scenario, the worker is not on the edge of the square that it should be
                // filling, and so we should prefer dropping another marker here rather than our own
                // to free up capacity for replacing other markers on the perimeter.
                ok = false;
            }
            /*for (int j = 0; j < 2; j++) {
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
            }*/
            if (ok && client.getCommandsUsed() < L - 1) {
                // We can attempt to put a marker down.
                try {
                    specialWriter.println("Putting marker for worker " + worker.id + " at " + worker.x + " " + worker.y);
                    //System.err.println("Worker " + worker.id + " placing marker at " + worker.x + ";" + worker.y);
                    client.writeCommand("PUT", worker.id, C);
                    
                    // If that succeeded, then make a note on the map.
                    map[worker.x][worker.y] = C;
                    markerExpiry[worker.x][worker.y] = F;
                }
                catch (ProtocolException e) {
                    // Failed for some reason; probably because another team beat us to it.
                }
            }
            else {
                if (worker.special) {
                    specialWriter.println("Unable to put marker for worker " + worker.id + " because we don't have enough commands left!");
                    specialWriter.flush();
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
            Worker worker = workers.get(i);
            if (worker.special) {
                worker.specialMove(workers);
                worker.allocatedToMove = true;
            }
            else {
                worker.allocatedToMove = false;
            }
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
                    if (worker.x != target.x || worker.y != target.y) {
                        //System.err.println("Moving worker " + worker.id + " from " + worker.x + ";" + worker.y + " to " + target.x + ";" + target.y + " for value " + val + " with " + worker.dirX[target.x][target.y] + " and " + worker.dirY[target.x][target.y]);
                        client.writeCommand("MOVE", worker.id, worker.dirX[target.x][target.y], worker.dirY[target.x][target.y]);
                        worker.nextX = wrap(worker.x + worker.dirX[target.x][target.y]);
                        worker.nextY = wrap(worker.y + worker.dirY[target.x][target.y]);
                        if (worker.nextX < 0) worker.nextX += A;
                        if (worker.nextX >= A) worker.nextX -= A;
                        if (worker.nextY < 0) worker.nextY += A;
                        if (worker.nextY >= A) worker.nextY -= A;
                    }
                    else {
                        worker.nextX = worker.x;
                        worker.nextY = worker.y;
                    }
                }
            }
        }
    }
    
    private void dropDestroyMarkers()
        throws Exception
    {
        for (int i = 0; i < workers.size(); i++) {
            Worker worker = workers.get(i);
            final boolean shouldReplace;
            if (worker.special) {
                shouldReplace = ((worker.x == wrap(worker.tx1) || worker.x == wrap(worker.tx1 + worker.txl) || worker.y == wrap(worker.ty1) || worker.y == wrap(worker.ty1 + worker.txl)) || worker.numStored < G - 3) & (worker.numStored < G);
            }
            else {
                shouldReplace = worker.numStored < G;
            }
            if (map[worker.x][worker.y] != '.' && map[worker.x][worker.y] != '#' && map[worker.x][worker.y] != C && shouldReplace && nWorkers[worker.x][worker.y] == 1 && canExecuteCommand()) {
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
            else if (map[worker.x][worker.y] != '.' && map[worker.x][worker.y] != '#' && map[worker.x][worker.y] != C && worker.numStored >= G && nWorkers[worker.x][worker.y] == 1 && worker.special) {
                specialWriter.println("[" + worker.id + "] Unable to replace marker at " + worker.x + " " + worker.y + " because we have no room");
                specialWriter.flush();
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
            final AGridCulture culture = new AGridCulture(client, index);
            while (true) {
                try {
                    culture.run();
                }
                catch (Exception e) {
                    if (e.getMessage() == null || !e.getMessage().contains("Failed harvest at blank cell")) {
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
