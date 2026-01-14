
import java.io.Serializable;
import java.util.Arrays;

//Board for Connect Four (6 rows x 7 columns).
//0 = empty, 1 = player1 (red), 2 = player2 (blue)
 
public class Board implements Serializable {
    public static final int ROWS = 6;
    public static final int COLS = 7;
    private final int[][] grid = new int[ROWS][COLS];
    private int lastRow = -1, lastCol = -1;

    public Board() { 
        clear(); 
    }

    public synchronized void clear() {
        for (int r = 0; r < ROWS; r++) 
        Arrays.fill(grid[r], 0);
        lastRow = lastCol = -1;
    }

    //Attempt to drop a disk for player in column col.
    //Returns row index where placed, or -1 if column full/invalid.
    public synchronized int drop(int player, int col) { // synchronized prevents data races
        if (col < 0 || col >= COLS) return -1;
        for (int r = ROWS - 1; r >= 0; r--) {
            if (grid[r][col] == 0) {
                grid[r][col] = player;
                lastRow = r; lastCol = col;
                return r;
            }
        }
        return -1;
    }

    public synchronized int getCell(int row, int col) {
        if (row < 0 || row >= ROWS || col < 0 || col >= COLS) return -1;
        return grid[row][col];
    }

    public synchronized String serialize() {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                sb.append(grid[r][c]);
                if (c < COLS - 1) sb.append(',');
            }
            if (r < ROWS - 1) sb.append(';');
        }
        return sb.toString();
    }

    public synchronized void deserialize(String s) {
        // not used by server, but available
        String[] rows = s.split(";");
        for (int r = 0; r < Math.min(rows.length, ROWS); r++) {
            String[] cols = rows[r].split(",");
            for (int c = 0; c < Math.min(cols.length, COLS); c++) {
                grid[r][c] = Integer.parseInt(cols[c]);
            }
        }
    }

    // Check whether the last move produced a win for the player who occupies (lastRow,lastCol).
    //If lastRow/lastCol are -1, return 0 (no winner).
    //Returns 0 if no winner, otherwise returns player number (1 or 2).
     
    public synchronized int checkWinner() {
        if (lastRow == -1 || lastCol == -1) return 0;
        int player = grid[lastRow][lastCol];
        if (player == 0) return 0;

        // directions: horizontal, vertical, diag1, diag2
        int[][] dirs = {{0,1},{1,0},{1,1},{1,-1}};
        for (int[] d : dirs) {
            int count = 1;
            count += countDirection(lastRow, lastCol, d[0], d[1], player);
            count += countDirection(lastRow, lastCol, -d[0], -d[1], player);
            if (count >= 4) return player;
        }
        return 0;
    }

    private int countDirection(int r, int c, int dr, int dc, int player) {
        int cnt = 0;
        int rr = r + dr, cc = c + dc;
        while (rr >= 0 && rr < ROWS && cc >= 0 && cc < COLS && grid[rr][cc] == player) {
            cnt++; rr += dr; cc += dc;
        }
        return cnt;
    }

    public synchronized boolean isFull() {
        for (int c = 0; c < COLS; c++) if (grid[0][c] == 0) return false;
        return true;
    }
}
