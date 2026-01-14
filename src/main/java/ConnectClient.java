
import java.awt.*;
import java.io.*;
import java.net.*;
import javax.swing.*;

/**
 * Connect Four client with simple Swing GUI.
 *
 * Usage: java ConnectClient <host> <port>
 * Example: java ConnectClient localhost 5000
 *
 * GUI:
 * - Top label shows status (your color and whose turn)
 * - 7 column buttons to click and drop a disk
 * - 6x7 grid displays the board (empty, red, blue)
 */
public class ConnectClient {
    private final String host;
    private final int port;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private int myPlayer = 0;
    private JFrame frame;
    private BoardModel boardModel = new BoardModel();
    private JLabel statusLabel;
    private JButton[] colButtons = new JButton[Board.COLS];

    public ConnectClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void start() throws IOException {
        socket = new Socket(host, port);
        out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        SwingUtilities.invokeLater(this::createAndShowGUI);

        // reader thread
        new Thread(this::readerLoop, "ServerReader").start();
    }

    private void createAndShowGUI() {
        frame = new JFrame("Connect Four - Client");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        statusLabel = new JLabel("Connecting...");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(6,6,6,6));
        frame.add(statusLabel, BorderLayout.NORTH);

        JPanel topButtons = new JPanel(new GridLayout(1, Board.COLS));
        for (int c = 0; c < Board.COLS; c++) {
            final int col = c;
            JButton b = new JButton("↓");
            b.setFont(b.getFont().deriveFont(Font.BOLD, 18f));
            b.addActionListener(e -> {
                sendMove(col);
            });
            colButtons[c] = b;
            topButtons.add(b);
        }
        frame.add(topButtons, BorderLayout.PAGE_START);

        BoardPanel boardPanel = new BoardPanel(boardModel);
        frame.add(boardPanel, BorderLayout.CENTER);

        frame.setSize(420, 520);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void readerLoop() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                final String msg = line;
                SwingUtilities.invokeLater(() -> handleServerMessage(msg));
            }
        } catch (IOException ex) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(frame, "Disconnected from server.", "Disconnected", JOptionPane.ERROR_MESSAGE);
                frame.dispose();
            });
        }
    }

    private void handleServerMessage(String line) {
        if (line.startsWith("ASSIGN:")) {
            myPlayer = Integer.parseInt(line.substring(7));
            statusLabel.setText("You are player " + myPlayer + " (" + (myPlayer == 1 ? "RED" : "BLUE") + "). Waiting...");
        } else if (line.startsWith("INFO:")) {
            statusLabel.setText(line.substring(5));
        } else if (line.startsWith("BOARD:")) {
            String payload = line.substring(6);
            boardModel.deserialize(payload);
            frame.repaint();
        } else if (line.equals("YOUR_TURN")) {
            statusLabel.setText("Your turn (" + (myPlayer == 1 ? "RED" : "BLUE") + ")");
            setButtonsEnabled(true);
        } else if (line.equals("OPPONENT_TURN")) {
            statusLabel.setText("Opponent's turn");
            setButtonsEnabled(false);
        } else if (line.startsWith("WIN:")) {
            int winner = Integer.parseInt(line.substring(4));
            boolean isWinner = (winner == myPlayer);
            statusLabel.setText(isWinner ? "You win!" : "You lose.");
            JOptionPane.showMessageDialog(frame,
                    isWinner ? "🎉 You win!" : "💀 You lose. Player " + winner + " won.",
                    "Game over",
                    JOptionPane.INFORMATION_MESSAGE);
            setButtonsEnabled(false);
        
            int choice = JOptionPane.showConfirmDialog(frame,
                    "Play again?", "Rematch", JOptionPane.YES_NO_OPTION);
            if (choice == JOptionPane.YES_OPTION) {
                out.println("RESET");
                out.flush();
                statusLabel.setText("Waiting for other player...");
            }
        
        } else if (line.equals("DRAW")) {
            statusLabel.setText("Draw!");
            JOptionPane.showMessageDialog(frame, "Game is a draw.", "Game over", JOptionPane.INFORMATION_MESSAGE);
            setButtonsEnabled(false);
        
            int choice = JOptionPane.showConfirmDialog(frame,
                    "Play again?", "Rematch", JOptionPane.YES_NO_OPTION);
            if (choice == JOptionPane.YES_OPTION) {
                out.println("RESET");
                out.flush();
                statusLabel.setText("Waiting for other player...");
            }
        }else {
            // ignore or show
            //statusLabel.setText(line);
        }
    }

    private void sendMove(int col) {
        out.println("MOVE:" + col);
        out.flush();
        // disable buttons until server confirms turn switch
        setButtonsEnabled(false);
    }

    private void setButtonsEnabled(boolean enabled) {
        for (JButton b : colButtons) b.setEnabled(enabled);
    }

    public static void main(String[] args) throws Exception {
        String host = "localhost";
        int port = 522;
        if (args.length >= 1) host = args[0];
        if (args.length >= 2) port = Integer.parseInt(args[1]);
        ConnectClient client = new ConnectClient(host, port);
        client.start();
    }

    // small client-side model to render board
    private static class BoardModel {
        private final int[][] grid = new int[Board.ROWS][Board.COLS];

        void deserialize(String s) {
            // rows separated by ';', cols by ','
            String[] rows = s.split(";");
            for (int r = 0; r < Board.ROWS; r++) {
                if (r < rows.length) {
                    String[] cols = rows[r].split(",");
                    for (int c = 0; c < Board.COLS; c++) {
                        if (c < cols.length) grid[r][c] = Integer.parseInt(cols[c]);
                        else grid[r][c] = 0;
                    }
                } else {
                    for (int c = 0; c < Board.COLS; c++) grid[r][c] = 0;
                }
            }
        }

        int getCell(int r, int c) { return grid[r][c]; }
    }

    // component to draw grid
    private static class BoardPanel extends JPanel {
        private final BoardModel model;
        private final int cellSize = 60;
        BoardPanel(BoardModel model) {
            this.model = model;
            setPreferredSize(new Dimension(Board.COLS * cellSize, Board.ROWS * cellSize));
            setBackground(new Color(30, 144, 255)); // board blue-ish
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            for (int r = 0; r < Board.ROWS; r++) {
                for (int c = 0; c < Board.COLS; c++) {
                    int x = c * cellSize + 10;
                    int y = r * cellSize + 10;
                    int d = cellSize - 20;
                    // empty circle
                    g.setColor(Color.WHITE);
                    g.fillOval(x, y, d, d);

                    int val = model.getCell(r, c);
                    if (val == 1) {
                        g.setColor(Color.RED);
                        g.fillOval(x, y, d, d);
                    } else if (val == 2) {
                        g.setColor(Color.BLUE);
                        g.fillOval(x, y, d, d);
                    }
                }
            }
        }
    }
}
