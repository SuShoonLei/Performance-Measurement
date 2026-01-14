
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ConnectServer {
    private final int port;
    private final Board board = new Board();
    private final List<PlayerHandler> players = Collections.synchronizedList(new ArrayList<>(2));
    private volatile int currentPlayer = 1;
    private volatile boolean gameOver = false;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private int resetRequests = 0;

    public ConnectServer(int port) { this.port = port; }

    public void start() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server listening on port " + port);
            while (players.size() < 2) {
                Socket s = serverSocket.accept();
                PlayerHandler ph = new PlayerHandler(s, players.size() + 1);
                players.add(ph);
                pool.submit(ph);
                System.out.println("Player " + ph.playerId + " connected.");
                ph.send("ASSIGN:" + ph.playerId);
                ph.send("INFO:Waiting for " + (2 - players.size()) + " more player(s)...");
            }
            startNewGame();
        }
    }

    private void startNewGame() {
        synchronized (board) {
            board.clear();
            gameOver = false;
            resetRequests = 0;
            currentPlayer = 1;
        }
        broadcastInfo("New game started! Player 1 begins.");
        broadcastBoard();
        getPlayerHandler(1).send("YOUR_TURN");
        getPlayerHandler(2).send("OPPONENT_TURN");
    }

    private PlayerHandler getPlayerHandler(int id) {
        synchronized (players) {
            for (PlayerHandler p : players) if (p.playerId == id) return p;
        }
        return null;
    }

    private void broadcastBoard() {
        String msg = "BOARD:" + board.serialize();
        synchronized (players) {
            for (PlayerHandler p : players) p.send(msg);
        }
    }

    private void broadcastInfo(String text) {
        synchronized (players) {
            for (PlayerHandler p : players) p.send("INFO:" + text);
        }
    }

    private void broadcast(String msg) {
        synchronized (players) {
            for (PlayerHandler p : players) p.send(msg);
        }
    }

    private class PlayerHandler implements Runnable {
        final Socket socket;
        final int playerId;
        private PrintWriter out;
        private BufferedReader in;

        PlayerHandler(Socket socket, int playerId) {
            this.socket = socket;
            this.playerId = playerId;
        }

        void send(String line) {
            if (out != null) {
                out.println(line);
                out.flush();
            }
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
                send("INFO:You are player " + playerId + " (" + (playerId == 1 ? "RED" : "BLUE") + ")");

                String line;
                while ((line = in.readLine()) != null) {
                    if (line.startsWith("MOVE:")) {
                        handleMove(playerId, Integer.parseInt(line.substring(5)));
                    } else if (line.equals("RESET")) {
                        handleResetRequest();
                    } else {
                        send("INFO:Unknown command: " + line);
                    }
                }
            } catch (IOException ex) {
                System.err.println("Player " + playerId + " disconnected: " + ex.getMessage());
            } finally {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }
    }

    private synchronized void handleMove(int player, int col) {
        if (gameOver) return;
        if (player != currentPlayer) {
            getPlayerHandler(player).send("INFO:Not your turn.");
            return;
        }

        int row = board.drop(player, col);
        if (row == -1) {
            getPlayerHandler(player).send("INFO:Column " + col + " is full.");
            return;
        }

        broadcastBoard();
        int winner = board.checkWinner();

        if (winner != 0) {
            gameOver = true;
            broadcast("WIN:" + winner);
            broadcastInfo("Player " + winner + " wins!");
            return;
        }

        if (board.isFull()) {
            gameOver = true;
            broadcast("DRAW");
            broadcastInfo("Game is a draw!");
            return;
        }

        currentPlayer = (currentPlayer == 1) ? 2 : 1;
        getPlayerHandler(currentPlayer).send("YOUR_TURN");
        getPlayerHandler(3 - currentPlayer).send("OPPONENT_TURN");
    }

    private synchronized void handleResetRequest() {
        resetRequests++;
        broadcastInfo("Reset request: " + resetRequests + "/2 players ready.");
        if (resetRequests >= 2) {
            startNewGame();
        }
    }

    public static void main(String[] args) throws Exception {
        int port = 5000;
        if (args.length > 0) port = Integer.parseInt(args[0]);
        new ConnectServer(port).start();
    }
}
