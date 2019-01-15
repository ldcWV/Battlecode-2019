package bc19;

import static bc19.Consts.*;

public class Movable {
    public MyRobot Z;
    public Movable (MyRobot z) { Z = z; }

    boolean enoughResources() {
        return Z.CUR.fuel > 25 || (Z.CUR.fuel > 0 && !Z.fuelMap[Z.CUR.y][Z.CUR.x]) 
        	|| Z.CUR.karbonite > 5 || (Z.CUR.karbonite > 0 && !Z.karboniteMap[Z.CUR.y][Z.CUR.x]);
    }
    boolean canMove(Robot2 R, int dx, int dy) {
        if (R == null || R.unit == -1) return false;
        int u = R.unit;
        if (dx == 0 && dy == 0) return false;
        int d = dx*dx+dy*dy;
        return Z.passable(R.x+dx,R.y+dy) && d <= MOVE_SPEED[u] && d*MOVE_F_COST[u] <= Z.fuel;
    }

    Action2 nextMove(int x, int y) {
        if (Z.nextMove[y][x] == MOD) return null;
        int Y = Z.nextMove[y][x] % 64, X = Z.fdiv(Z.nextMove[y][x],64);
        if (!canMove(Z.CUR,X-Z.CUR.x,Y-Z.CUR.y)) return null;
        return Z.moveAction(X-Z.CUR.x, Y-Z.CUR.y);
    }
    Action2 nextMove(int x) { return x == MOD ? null : nextMove(Z.fdiv(x,64), x % 64); }
    Action2 moveToward(int x, int y) { return nextMove(Z.closeEmpty(x, y)); }
    Action2 moveToward(int x) { return moveToward(Z.fdiv(x,64),x%64); }
    Action2 moveToward(Robot2 R) { return R == null ? null : moveToward(R.x, R.y); }

    Action2 nextMoveSafe(int x, int y) {
        if (Z.nextMoveSafe[y][x] == MOD) return null;
        int Y = Z.nextMoveSafe[y][x] % 64, X = Z.fdiv(Z.nextMoveSafe[y][x],64);
        if (!canMove(Z.CUR,X-Z.CUR.x,Y-Z.CUR.y)) return null;
        return Z.moveAction(X-Z.CUR.x, Y-Z.CUR.y);
    }
    Action2 nextMoveSafe(int x) { return x == MOD ? null : nextMoveSafe(Z.fdiv(x,64), x % 64); }
    Action2 moveTowardSafe(int x, int y) { return nextMoveSafe(Z.closeEmptySafe(x,y)); }
    Action2 moveTowardSafe(int x) { return moveTowardSafe(Z.fdiv(x,64),x%64); }

    Action2 nextMoveShort(int x, int y) {
        if (Z.nextMoveShort[y][x] == MOD) return null;
        int Y = Z.nextMoveShort[y][x] % 64, X = Z.fdiv(Z.nextMoveShort[y][x],64);
        if (!canMove(Z.CUR,X-Z.CUR.x,Y-Z.CUR.y)) return null;
        return Z.moveAction(X-Z.CUR.x, Y-Z.CUR.y);
    }
    Action2 nextMoveShort(int x) { return x == MOD ? null : nextMoveShort(Z.fdiv(x,64), x % 64); }
    Action2 moveTowardShort(int x, int y) { return nextMoveShort(Z.closeEmptyShort(x, y)); }
    Action2 moveTowardShort(int x) { return moveTowardShort(Z.fdiv(x,64),x%64); }

    Action2 moveAway(int x, int y) {
        int farthest = -MOD; Action2 best = null;
        for (int i = -3; i <= 3; i++) for (int j = -3; j <= 3; j++)
            if (canMove(Z.CUR,i,j)) {
                int dis = Z.sq(x - Z.CUR.x - i) + Z.sq(y - Z.CUR.y - j);
                if (dis > farthest) { farthest = dis; best = Z.moveAction(i, j); }
            }
        return best;
    }
    Action2 moveAway(Robot2 R) { return R == null ?  null : moveAway(R.x, R.y); }
    Action2 moveHome() {
        for (Robot2 R: Z.robots)
            if (R.isStructure() && R.team == Z.CUR.team && Z.adjacent(Z.CUR,R) && enoughResources()) {
                Z.resource = -1;
                return Z.giveAction(R.x-Z.CUR.x,R.y-Z.CUR.y,Z.CUR.karbonite,Z.CUR.fuel);
            }
        if (Z.CUR.unit == PILGRIM) {
            int p = Z.closeEmptySafe(Z.closestStruct(true));
            if (Z.bfsDistSafe(p) != MOD) {
                Z.log("SAFE PATH");
                return nextMoveSafe(p);
            }
        } 
        return moveToward(Z.closestStruct(true));
    }
    Action2 moveEnemyStruct() {
		return moveToward(Z.closestStruct(false));
	}
}
