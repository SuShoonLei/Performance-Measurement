import java.io.Serializable;
import java.util.Arrays;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Board for Connect Four using ReadWriteLock instead of synchronized.
 * This implementation allows multiple concurrent readers but exclusive writers.
 * 
 * KEY DIFFERENCE FROM Board.java:
 * - Board.java uses synchronized (one thread at a time, even for reads)
 * - BoardRWLock.java uses ReadWriteLock (multiple readers, exclusive writer)
 * 
 * PERFORMANCE CHARACTERISTICS:
 * - Better performance when reads > writes (common in games)
 * - Multiple threads can check winner simultaneously
 * - Only blocks when someone is modifying the board
 */
public class BoardRWLock implements Serializable {
    public static final int ROWS = 6;
    public static final int COLS = 7;
    
    private final int[][] grid = new int[ROWS][COLS];
    private int lastRow = -1, lastCol = -1;
    
   
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();// Read-write lock

    public BoardRWLock() {
        clear();
    }

    public void clear() {
        rwLock.writeLock().lock();  // Exclusive lock for writing
        try {
            for (int r = 0; r < ROWS; r++) 
                Arrays.fill(grid[r], 0);
            lastRow = lastCol = -1;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    // Drop requires WRITE lock (modifies board state)
    public int drop(int player, int col) {
        rwLock.writeLock().lock();//no data races
        try {
            if (col < 0 || col >= COLS) return -1;
            for (int r = ROWS - 1; r >= 0; r--) {
                if (grid[r][col] == 0) {
                    grid[r][col] = player;
                    lastRow = r;
                    lastCol = col;
                    return r;
                }
            }
            return -1;
        } finally {
            rwLock.writeLock().unlock();//relase write lock
        }
    }

    //getCell requires READ lock (only reads, doesn't modify 
    //Multiple threads can read simultaneously!
    public int getCell(int row, int col) {
        rwLock.readLock().lock();  // Shared lock for reading
        try {
            if (row < 0 || row >= ROWS || col < 0 || col >= COLS) return -1;
            return grid[row][col];
        } finally {
            rwLock.readLock().unlock();
        }
    }

    //serialize requires READ lock
    public String serialize() {
        rwLock.readLock().lock();
        try {
            StringBuilder sb = new StringBuilder();
            for (int r = 0; r < ROWS; r++) {
                for (int c = 0; c < COLS; c++) {
                    sb.append(grid[r][c]);
                    if (c < COLS - 1) sb.append(',');
                }
                if (r < ROWS - 1) sb.append(';');
            }
            return sb.toString();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    //deserialize requires WRITE lock
    public void deserialize(String s) {
        rwLock.writeLock().lock();
        try {
            String[] rows = s.split(";");
            for (int r = 0; r < Math.min(rows.length, ROWS); r++) {
                String[] cols = rows[r].split(",");
                for (int c = 0; c < Math.min(cols.length, COLS); c++) {
                    grid[r][c] = Integer.parseInt(cols[c]);
                }
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    //checkWinner requires READ lock (only examines board)
    //Multiple threads can check winner simultaneously!
    
    public int checkWinner() {
        rwLock.readLock().lock();   //SHARED READ LOCK - Multiple readers OK!
        try {
            if (lastRow == -1 || lastCol == -1) return 0;
            int player = grid[lastRow][lastCol];
            if (player == 0) return 0;
            
            int[][] dirs = {{0,1},{1,0},{1,1},{1,-1}};
            for (int[] d : dirs) {
                int count = 1;
                count += countDirection(lastRow, lastCol, d[0], d[1], player);
                count += countDirection(lastRow, lastCol, -d[0], -d[1], player);
                if (count >= 4) return player;
            }
            return 0;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    private int countDirection(int r, int c, int dr, int dc, int player) {
        // Called within a lock, so no additional locking needed
        int cnt = 0;
        int rr = r + dr, cc = c + dc;
        while (rr >= 0 && rr < ROWS && cc >= 0 && cc < COLS && grid[rr][cc] == player) {
            cnt++;
            rr += dr;
            cc += dc;
        }
        return cnt;
    }

    public boolean isFull() {
        rwLock.readLock().lock();
        try {
            for (int c = 0; c < COLS; c++) 
                if (grid[0][c] == 0) return false;
            return true;
        } finally {
            rwLock.readLock().unlock();
        }
    }
}
