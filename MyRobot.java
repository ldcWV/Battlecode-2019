package bc19;

import java.util.*;
import java.math.*;

import static bc19.Consts.*;

public class MyRobot extends BCAbstractRobot {
    // int turn = 0; turns since start of game
    Robot2 ME;

    // MAP (arrays are by y and then x)
    int w, h; // width, height
    Robot2[] robots;
    Robot2[][] robotMap; // stores last robot seen in pos
    int[][] robotMapID; // stores last id seen in pos
    boolean[][] noStructure; // whether square does not contain structure or not

    int[][] bfsDist, nextMove; 
    boolean updEnemy = false;
    int[][][] enemyDist;
    ArrayList<Integer> myCastle = new ArrayList<>(), otherCastle = new ArrayList<>();
    ArrayList<Integer> myChurch = new ArrayList<>(), otherChurch = new ArrayList<>();

    // PERSONAL
    boolean goHome; // whether unit is going home or not
    boolean signaled;

    // FOR CASTLE
    int numAttack;
    int[] numUnits = new int[6];
    int[] sortedKarb, sortedFuel, karbToPil, fuelToPil, karbPos, fuelPos;
    boolean[] isOccupiedKarb , isOccupiedFuel;
    int karbcount=0, fuelcount=0;
    Set<Integer> castle = new HashSet<>();
    Map<Integer,Integer> castleX = new HashMap<>();
    Map<Integer,Integer> castleY = new HashMap<>();
    
    // FOR PILGRIM
    int resource = -1; // karbonite or fuel
    pi resourceLoc = new pi(-1,-1);

    /*void genTurn() {
        for (int dx = -1; dx <= 1; ++dx) for (int dy = -1; dy <= 1; ++dy) {
            int x = ME.x+dx, y = ME.y+dy;
            if (containsRobot(x,y)) {
                Robot2 R = getRobot2(robotMap[y][x]);
                if (R.isStructure() && R.team == ME.team && R.signal > 0) turn = R.signal;
            }
        }
    }*/

    // MATH
    int fdiv(int a, int b) { return (a-(a%b))/b; }
    int sq(int x) { return x*x; }

    // ROBOT
    Robot2 makeRobot(int unit, int team, int x, int y) {
        Robot2 R = new Robot2(null);
        R.unit = unit; R.team = team; R.x = x; R.y = y;
        return R;
    }
    Robot2 getRobot2(int id) { return new Robot2(getRobot(id)); }
    boolean withinMoveRadius(Robot2 R, int dx, int dy) { return R != null && R.withinMoveRadius(dx,dy,fuel); }
    void dumpRobots() {
        String T = ""; for (Robot2 R: robots) T += R.getInfo();
        log(T);
    }
    void dumpInfo() {
        String T = ""; T += ME.getInfo();
        T += nextMove[ME.y][ME.x]+" ";
        if (ME.y > 0) T += nextMove[ME.y-1][ME.x]+" ";
        T += "\n";
        T += myCastle.size()+" "+otherCastle.size();
        if (otherCastle.size() > 0) {
            T += " " + otherCastle.get(0);
            int x = fdiv(otherCastle.get(0),64), y = otherCastle.get(0) % 64;
            log(""+nextMove[y][x]);
        }
        T += "\n";
        log(T);
    }

    // ARRAYLIST
    void removeDup(ArrayList<Integer> A) {
        ArrayList<Integer> B = new ArrayList<>();
        for (Integer i : A) if (!B.contains(i)) B.add(i);
        A.clear();
        for (Integer i : B) A.add(i);
    }
    void rem(ArrayList<Integer> A) {
        ArrayList<Integer> B = new ArrayList<>();
        for (Integer i : A) {
            int y = i % 64; int x = fdiv(i,64);
            if (!noStructure[y][x]) B.add(i);
        }
        A.clear();
        for (Integer i : B) A.add(i);
    }

    // MAP
    boolean hsim() { // symmetric with respect to y
        for (int i = 0; i < h-1-i; ++i)
            for (int j = 0; j < w; ++j) {
                if (map[i][j] != map[h-1-i][j]) return false;
                if (karboniteMap[i][j] != karboniteMap[h-1-i][j]) return false;
                if (fuelMap[i][j] != fuelMap[h-1-i][j]) return false;
            }
        return true;
    }
    boolean wsim() { // symmetric with respect to x
        for (int i = 0; i < h; ++i)
            for (int j = 0; j < w-1-j; ++j) {
                if (map[i][j] != map[i][w-1-j]) return false;
                if (karboniteMap[i][j] != karboniteMap[i][w-1-j]) return false;
                if (fuelMap[i][j] != fuelMap[i][w-1-j]) return false;
            }
        return true;
    }
    boolean inMap(int x, int y) { return x >= 0 && x < w && y >= 0 && y < h; }
    boolean valid(int x, int y) { return inMap(x,y) && map[y][x]; }
    boolean containsRobot(int x, int y) { return valid(x, y) && robotMapID[y][x] > 0; }
    boolean yourAttacker(int x, int y) { return containsRobot(x,y) && robotMap[y][x].team == ME.team && robotMap[y][x].unit > 2; }
    boolean enemyRobot(int x, int y) { return containsRobot(x,y) && robotMap[y][x].team == 1-ME.team; }
    boolean enemyRobot(int x, int y, int t) { return enemyRobot(x,y) && robotMap[y][x].unit == t; }
    boolean enemyAttacker(int x, int y) { return enemyRobot(x,y) && robotMap[y][x].unit > 2; }
    boolean passable(int x, int y) { return valid(x, y) && robotMapID[y][x] <= 0; }
    boolean adjacent(Robot2 r) { return Math.abs(ME.x-r.x) <= 1 && Math.abs(ME.y-r.y) <= 1; }
    int euclidDist(int x, int y) { return sq(ME.x-x)+sq(ME.y-y); }
    int euclidDist(int x1, int y1, int x2, int y2) { return sq(x1-x2)+sq(y1-y2); }
    int euclidDist(Robot2 A, Robot2 B) { return sq(A.x-B.x)+sq(A.y-B.y); }
    int euclidDist(Robot2 B) { return B == null ? MOD : euclidDist(B.x,B.y); }
    int numOpen(int t) { // how many squares around t are free
        int y = t % 64; int x = fdiv(t,64);
        int ret = 0;
        for (int i = x-1; i <= x+1; ++i) for (int j = y-1; j <= y+1; ++j)
            if (passable(i,j)) ret ++;
        return ret;
    }
    Robot2 closestEnemy() {
        Robot2 bes = null;
        for (int i = 0; i < h; ++i) for (int j = 0; j < w; ++j) 
            if (enemyRobot(j,i) && euclidDist(j,i) < euclidDist(bes)) bes = robotMap[i][j];
        return bes;
    }
    Robot2 closestAttacker(int t) {
        Robot2 bes = null;
        for (int i = 0; i < h; ++i) for (int j = 0; j < w; ++j) 
            if (enemyAttacker(j,i) && euclidDist(j,i) < euclidDist(bes)) bes = robotMap[i][j];
        return bes;
    }

    // BFS DIST
    void genBfsDist(int mx) { 
        if (bfsDist == null) { bfsDist = new int[h][w];  nextMove = new int[h][w]; }
        for (int i = 0; i < h; ++i) for (int j = 0; j < w; ++j) {
            bfsDist[i][j] = MOD; nextMove[i][j] = MOD;
        }
        LinkedList<Integer> Q = new LinkedList<Integer>(); bfsDist[ME.y][ME.x] = 0; Q.add(64 * ME.x + ME.y);

        int t = 0;
        while (Q.size() > 0) {
            t ++;
            int x = Q.poll(); int y = x % 64; x = fdiv(x,64);
            for (int dx = -3; dx <= 3; ++dx) for (int dy = -3; dy <= 3; ++dy) {
                int X = x + dx, Y = y + dy;
                if (dx*dx+dy*dy <= mx && valid(X, Y) && bfsDist[Y][X] == MOD) {
                    bfsDist[Y][X] = bfsDist[y][x] + 1;
                    if (nextMove[y][x] == MOD) nextMove[Y][X] = 64 * X + Y;
                    else nextMove[Y][X] = nextMove[y][x];
                    if (passable(X,Y)) Q.add(64 * X + Y);
                }
            }
        }
    }
    void genEnemyDist() {
        if (enemyDist == null) {
            enemyDist = new int[h][w][2];
            for (int i = 0; i < h; ++i) for (int j = 0; j < w; ++j) 
                for (int k = 0; k < 2; ++k) enemyDist[i][j][k] = MOD;
        }
        if (!updEnemy) return;
        updEnemy = false;
        LinkedList<Integer> Q = new LinkedList<Integer>();
        for (int i = 0; i < h; ++i) for (int j = 0; j < w; ++j) 
            for (int k = 0; k < 2; ++k) enemyDist[i][j][k] = MOD;
            
        for (int i: otherCastle) {
            Q.add(2*i);
            enemyDist[i%64][fdiv(i,64)][0] = 0;
        }
        for (int i: otherChurch) {
            Q.add(2*i);
            enemyDist[i%64][fdiv(i,64)][0] = 0;
        }

        while (Q.size() > 0) {
            int t = Q.poll();
            int k = t % 2; t = fdiv(t,2);
            int y = t % 64; int x = fdiv(t,64);
            for (int z = 0; z < 4; ++z) {
                int X = x+xd[z], Y = y+yd[z];
                if (inMap(X,Y)) {
                    int K = k+1; if (valid(X,Y)) K = 0;
                    if (K == 2 || enemyDist[Y][X][K] != MOD) continue;
                    enemyDist[Y][X][K] = enemyDist[y][x][k]+1;
                    Q.push(2*(64*X+Y)+K);
                }
            }
        }
    }
    int bfsDist(int x) { return x == MOD ? MOD : bfsDist[x % 64][fdiv(x,64)]; }
    int closest(ArrayList<Integer> A) {
        int bestDist = MOD, bestPos = MOD; if (A == null) return bestPos;
        for (int x : A) if (bfsDist(x) < bestDist) {
            bestDist = bfsDist(x); bestPos = x;
        }
        return bestPos;
    }
    int closestUnseen() {
        int bestDist = MOD, bestPos = MOD;
        for (int i = 0; i < h; ++i) for (int j = 0; j < w; ++j)
            if (passable(j, i) && bfsDist[i][j] < bestDist && robotMapID[i][j] == -1) {
                bestDist = bfsDist[i][j]; bestPos = 64*j+i;
            }
        return bestPos;
    }
    int closestUnused(boolean[][] B) {
        int bestDist = MOD, bestPos = MOD; if (B == null) return bestPos;
        for (int i = 0; i < h; ++i) for (int j = 0; j < w; ++j)
            if (B[i][j] && bfsDist[i][j] < bestDist && robotMapID[i][j] <= 0) {
                bestDist = bfsDist[i][j]; bestPos = 64*j+i;
            }
        return bestPos;
    }
    int closerSquare(int x, int y) {
		int bestDist = MOD, bestPos = MOD;
        for (int i = -10; i <= 10; ++i) for (int j = -10; j <= 10; ++j) {
            int X = me.x+i, Y = me.y+j;
            if (valid(X,Y) && bfsDist[Y][X] != MOD && euclidDist(X,Y,x,y) < bestDist) {
                bestDist = i*i+j*j; bestPos = 64*X+Y;
            }
        }
        return bestPos;
	}
    int closeEmpty(int x, int y) {
        int bestDist = MOD, bestPos = MOD;
        for (int i = -10; i <= 10; ++i) for (int j = -10; j <= 10; ++j) {
            int X = x+i, Y = y+j;
            if (valid(X,Y) && bfsDist[Y][X] != MOD && i*i+j*j < bestDist) {
                bestDist = i*i+j*j; bestPos = 64*X+Y;
            }
        }
        return bestPos;
    }
    int bfsDistHome() { return Math.min(bfsDist(closest(myCastle)),bfsDist(closest(myChurch))); }
    int closestChurch(boolean ourteam) { return closest(ourteam ? myChurch : otherChurch); }
    int closestCastle(boolean ourteam) { return closest(ourteam ? myCastle : otherCastle); }
    int closestStruct(boolean ourteam) {
        int bestCastle = closestCastle(ourteam);
        int bestChurch = closestChurch(ourteam);
        if (bfsDist(bestCastle) < bfsDist(bestChurch)) return bestCastle;
        return bestChurch;
    }

    public boolean canBuild(int t) { 
        if (!(fuel >= CONSTRUCTION_F[t] && karbonite >= CONSTRUCTION_K[t])) return false;
        for (int dx = -1; dx <= 1; ++dx) for (int dy = -1; dy <= 1; ++dy)
            if (passable(ME.x + dx, ME.y + dy)) return true;
        return false;
    }
    public Action tryBuild(int t) {
        if (!canBuild(t)) return null;
        for (int dx = -1; dx <= 1; ++dx) for (int dy = -1; dy <= 1; ++dy)
            if (passable(ME.x + dx, ME.y + dy)) return buildUnit(t, dx, dy);
        return null;
    }

    void addStruct(Robot2 R) {
        int t = 64*R.x+R.y;
        if (R.unit == CHURCH) {
            if(R.team == ME.team && !myChurch.contains(t)) {
                myChurch.add(t);
            } else if(R.team != ME.team && !otherChurch.contains(t)) {
                otherChurch.add(t);
                updEnemy = true;
            }
        } else {
            if (R.team == ME.team && !myCastle.contains(t)) {
                myCastle.add(t);
                if (wsim() && R.unit == 0 && !noStructure[R.y][w-1-R.x]) {
                    updEnemy = true; otherCastle.add(64*(w-1-R.x)+R.y);
                }
                if (hsim() && R.unit == 0 && !noStructure[h-1-R.y][R.x]) {
                    updEnemy = true; otherCastle.add(64*R.x+(h-1-R.y));
                }
            } else if(R.team != ME.team && !otherCastle.contains(t)) {
                updEnemy = true; otherCastle.add(t);
                if (wsim() && R.unit == 0 && !noStructure[R.y][w-1-R.x]) myCastle.add(64*(w-1-R.x)+R.y);
                if (hsim() && R.unit == 0 && !noStructure[h-1-R.y][R.x]) myCastle.add(64*R.x+(h-1-R.y));
            }
        }
    }


    int getSignal(Robot2 R) {
        return 441*(R.unit-3)+21*(R.x-me.x+10)+(R.y-me.y+10)+1;
    }

    boolean clearVision(Robot2 R) {
        if (ME.unit == CASTLE && fdiv(R.castle_talk,7) % 2 == 1) return false;
        for (int i = -10; i <= 10; ++i)
            for (int j = -10; j <= 10; ++j) {
                if (i*i+j*j > VISION_R[R.unit]) continue;
                if (enemyRobot(R.x+i,R.y+j)) return false;
            }
        for (Robot2 A: robots) if (A.team == ME.team && 0 < A.signal && A.signal < 2000) 
            if (euclidDist(R,A) <= A.signal_radius) return false;
        return true;
    }

    void checkSignal() {
        for (Robot2 R: robots) if (R.team == ME.team && 0 < R.signal && R.signal < 2000) {
            int tmp = R.signal-1;
            int type = fdiv(tmp,441)+3; tmp %= 441;
            int x = fdiv(tmp,21)-10; x += R.x;
            int y = (tmp%21)-10; y += R.y;
            robotMapID[y][x] = MOD; robotMap[y][x] = makeRobot(type,1-ME.team,x,y);
            log("ADDED "+ME.x+" "+ME.y+" "+x+" "+y);
        }
    }

    void warnOthers() {
        if (ME.unit == CRUSADER || ME.unit == PREACHER) return;
        Robot2 R = closestEnemy(); if (euclidDist(R) > VISION_R[ME.unit]) return;
        int needDist = 0;
        for (int i = -4; i <= 4; ++i) for (int j = -4; j <= 4; ++j) {
            int x = me.x+i, y = me.y+j;
            if (i*i+j*j <= 16 && yourAttacker(x,y) && clearVision(robotMap[y][x])) 
                needDist = Math.max(needDist,i*i+j*j);
        }
        if (needDist > 0) {
            log("SIGNAL ENEMY: "+me.turn+" "+me.x+" "+me.y+" "+R.x+" "+R.y+" "+getSignal(R));
            signal(getSignal(R),needDist);
            signaled = true;
        }
    }

    void updateData() {
        h = map.length; w = map[0].length; ME = new Robot2(me); 
        signaled = false;
        robots = new Robot2[getVisibleRobots().length];
        for (int i = 0; i < robots.length; ++i) robots[i] = new Robot2(getVisibleRobots()[i]);

        if (robotMap == null) {
            robotMap = new Robot2[h][w];
            robotMapID = new int[h][w];
            noStructure = new boolean[h][w];
            for (int i = 0; i < h; ++i) for (int j = 0; j < w; ++j) robotMapID[i][j] = -1;
        }

        for (Robot2 R: robots) if (R.isStructure()) addStruct(R);
        checkSignal();
        
        for (int i = 0; i < h; ++i) for (int j = 0; j < w; ++j) {
            int t = getVisibleRobotMap()[i][j];
            if (t != -1) {
                robotMapID[i][j] = t;
                if (robotMapID[i][j] == 0) {
                    noStructure[i][j] = true;
                    robotMap[i][j] = null;
                } else {
                    robotMap[i][j] = getRobot2(robotMapID[i][j]);
                    if (robotMap[i][j].isStructure()) noStructure[i][j] = false;
                    else noStructure[i][j] = true;
                }
            }
        }

        rem(myCastle); rem(otherCastle);
    }

    void sendToCastle() {
        // 0 to 5: unit, 6: assigned pilgrim
        int res = ME.unit; if (res == 0) return;
        if (ME.unit == PILGRIM && resourceLoc.f != -1) res = 6;
        boolean seeEnemy = false;
        for (int i = -14; i <= 14; ++i) for (int j = -14; j <= 14; ++j) 
            if (i*i+j*j <= 196 && enemyRobot(ME.x+i,ME.y+j)) seeEnemy = true;
        if (seeEnemy) res += 7;
        if (otherCastle.size()+otherChurch.size() == 0) res += 14;
        castleTalk(res);
    }

    public Action turn() {
        updateData();
        if (ME.turn == 1) log("TYPE: "+ME.unit);
        genBfsDist(ME.unit == CRUSADER ? 9 : 4);
        genEnemyDist();
        warnOthers();
        // dumpInfo();
        
        switch (ME.unit) {
            case CASTLE: {
                Castle C = new Castle(this);
                return C.run();
            }
            case CHURCH: {
                Church C = new Church(this);
                return C.run();
            }
            case PILGRIM: {
                Pilgrim C = new Pilgrim(this);
                return C.run();
            }
            case CRUSADER: {
                Crusader C = new Crusader(this);
                return C.run();
            }
            case PROPHET: {
                Prophet C = new Prophet(this);
                return C.run();
            }
            case PREACHER: {
                Preacher C = new Preacher(this);
                return C.run();
            }
        }
        return null;
    }
}

/*
to fix:
* pilgrims don't flee from attackers
* look at last year's code and make other troops attack
*/

// file path:
// cd /usr/local/lib/node_modules/bc19/bots
// bcr
