import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;
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

    private int A, E, F, G, I, L;
    private double D, Z, W, T, Sw, Sh, Ss, N, M, K;
    private boolean B, H;
    private char C;
    private int lastU = -1;

    private class Worker
    {
        int x, y, id;
        char[] storage;
        int numStored = 0;
        boolean allocatedToMove;
        Worker(int id, int x, int y)
        {
            this.id = id;
            this.x = x;
            this.y = y;
            storage = new char[G];
        }
        void addToStorage(char color)
        {
            storage[numStored++] = color;
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
    
    private List<Worker> workers = new ArrayList<>();
    private List<EnemyWorker> enemyWorkers = new ArrayList<>();
    
    public AGridCulture(Client client)
    {
        this.client = client;        
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
                            worker.addToStorage(sc.next().charAt(0));
                        }
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

                    // Update marker expiries.
                    for (int i = 0; i < A; i++) {
                        for (int j = 0; j < A; j++) {
                            if (markerExpiry[j][i] >= 0) {
                                markerExpiry[j][i] = -1;
                                map[j][i] = '.';
                            }
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
                    // Ignore score data for now
                }
            }
        }
        catch (Exception e) {
            client.stop();
            throw e;
        }
    }
    
    private void scoreAreas()
        throws Exception
    {
        // Identify any areas that are ready to be scored.
        for (int i = 1; i < A; i++) {
            for (int j = 1; j < A; j++) {
                if (map[i][j] == C && map[i - 1][j] == C && map[i][j - 1] == C && map[i - 1][j - 1] == C && client.getCommandsUsed() < L - 1) {
                    // Ready!
                    System.err.println("Harvesting at " + i + " " + j);
                    client.writeCommand("SCORE", i, j);
                }
            }
        }
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
                    int ny = worker.y + cy[j];
                    if (nx > 0 && nx < A && ny > 0 && ny < A &&
                        (map[nx][ny] == '.' || map[nx][ny] == C) &&
                        (map[nx - 1][ny] == '.' || map[nx - 1][ny] == C) &&
                        (map[nx][ny - 1] == '.' || map[nx][ny - 1] == C) &&
                        (map[nx - 1][ny - 1] == '.' || map[nx - 1][ny - 1] == C))
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
    {
        int x, y, value, workerIndex;
    }
    private void moveWorkers()
        throws Exception
    {
        for (int i = 0; i < workers.size(); i++) {
            workers.get(i).allocatedToMove = false;
        }
        
        /*for (int val = 3; val >= 0; val++) {
            PriorityQueue<TargetCell> q = new PriorityQueue<>();
        }*/
        
        // For each worker, determine the cell that it should move to which has the best value.
        for (int i = 0; i < workers.size(); i++) {
            Worker worker = workers.get(i);
            int best = -1;
            int bestX = -1, bestY = -1;
            for (int x = 0; x < A; x++) {
                for (int y = 0; y < A; y++) {
                    // Skip marked cells and those with other workers in them.
                    if (claimedForMove[x][y] || map[x][y] != '.' || (nWorkers[x][y] > 0 && (x != worker.x || y != worker.y)) || nWorkers[x][y] > 1) {
                        continue;
                    }

                    // If we cannot place a square of markers around here, then skip it.
                    boolean ok = false;
                    int value = 0;
                    for (int j = 0; j < 2; j++) {
                        for (int k = 0; k < 2; k++) {
                            int nx = x + cx[j];
                            int ny = y + cy[k];
                            if (nx > 0 && nx < A && ny > 0 && ny < A &&
                                (map[nx][ny] == '.' || map[nx][ny] == C) &&
                                (map[nx - 1][ny] == '.' || map[nx - 1][ny] == C) &&
                                (map[nx][ny - 1] == '.' || map[nx][ny - 1] == C) &&
                                (map[nx - 1][ny - 1] == '.' || map[nx - 1][ny - 1] == C))
                            {
                                ok = true;
                                int curValue = (map[nx][ny] == C ? 1 : 0) + 
                                               (map[nx - 1][ny] == C ? 1 : 0) + 
                                               (map[nx][ny - 1] == C ? 1 : 0) + 
                                               (map[nx - 1][ny - 1] == C ? 1 : 0);
                                if (curValue > value) {
                                    value = curValue;
                                }
                            }
                        }
                    }
                    if (!ok) {
                        continue;
                    }
                    
                    // The value of A is equal to the number of markers we have in adjacent cells
                    // squared, minus the distance to get here from the worker's current location.
                    value = value * value + A + A - 2 - Math.abs(x - worker.x) - Math.abs(y - worker.y);
                    
                    if (best == -1 || value > best) {
                        best = value;
                        bestX = x;
                        bestY = y;
                    }
                }
            }
            
            if (best != -1 && client.getCommandsUsed() < L - 1 && (bestX != worker.x || bestY != worker.y)) {
                claimedForMove[bestX][bestY] = true;
                int diffX = bestX - worker.x;
                int diffY = bestY - worker.y;
                System.err.println("Moving worker " + worker.id + " from " + worker.x + ";" + worker.y + " to " + bestX + ";" + bestY);
                for (int iy = -2; iy <= 2; iy++) {
                    for (int ix = -2; ix <= 2; ix++) {
                        int nx = bestX + ix;
                        int ny = bestY + iy;
                        if (nx < 0 || nx >= A || ny < 0 || ny >= A) {
                            continue;
                        }
                        System.err.print(map[nx][ny]);
                        if (nx == worker.x && ny == worker.y) {
                            System.err.print('*');
                        }
                        else {
                            System.err.print(' ');
                        }
                    }
                    System.err.println();
                }
                if (Math.abs(diffX) > Math.abs(diffY)) {
                    // Move along X.
                    int diff = diffX < 0 ? -1 : 1;
                    client.writeCommand("MOVE", worker.id, diff, 0);
                    worker.x += diff;
                }
                else {
                    // Move along Y.
                    int diff = diffY < 0 ? -1 : 1;
                    client.writeCommand("MOVE", worker.id, 0, diff);
                    worker.y += diff;
                }
            }
            else if (best == -1) {
                System.err.println("Nothing for worker " + worker.id + " to do?");
            }
        }
    }
    
    public static void main(String[] args)
        throws Exception
    {
        final Client client = new Client(null, null, "localhost", PORTS[1], PROMETHEUS_PORTS[1]);
        final AGridCulture culture = new AGridCulture(client);
        culture.run();
    }
}
