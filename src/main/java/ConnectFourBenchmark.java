
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * JMH Benchmark for Connect Four Board operations
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class ConnectFourBenchmark {

    private Board board;
    private int columnCounter = 0;

    @Setup(Level.Iteration)
    public void setup() {
        board = new Board();
        columnCounter = 0;
    }

    @Benchmark
    public int benchmarkDrop() {
        int col = columnCounter % Board.COLS;
        columnCounter++;
        int player = (columnCounter % 2) + 1;
        return board.drop(player, col);
    }

    @Benchmark
    public int benchmarkCheckWinner() {
        // Setup a board with moves
        board.clear();
        board.drop(1, 3);
        board.drop(2, 3);
        board.drop(1, 3);
        board.drop(2, 3);
        board.drop(1, 3);
        board.drop(2, 3);
        return board.checkWinner();
    }

    @Benchmark
    public String benchmarkSerialize() {
        return board.serialize();
    }

    @Benchmark
    public void benchmarkDeserialize() {
        String data = "0,0,0,0,0,0,0;0,0,0,0,0,0,0;0,0,0,1,0,0,0;0,0,0,2,0,0,0;0,0,0,1,0,0,0;0,0,0,2,0,0,0";
        board.deserialize(data);
    }

    @Benchmark
    public void benchmarkFullGame() {
        board.clear();
        // Simulate a quick game
        for (int col = 0; col < Board.COLS; col++) {
            board.drop(1, col);
            board.checkWinner();
            if (board.isFull()) break;
            board.drop(2, col);
            board.checkWinner();
            if (board.isFull()) break;
        }
    }

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(ConnectFourBenchmark.class.getSimpleName())
                .build();

        Collection<RunResult> results = new Runner(opt).run();
        
        // Print results summary
        System.out.println("\n=== Benchmark Results ===");
        for (RunResult result : results) {
            String benchmarkName = result.getPrimaryResult().getLabel();
            double throughput = result.getPrimaryResult().getScore();
            String unit = result.getPrimaryResult().getScoreUnit();
            System.out.printf("%s: %.2f %s%n", benchmarkName, throughput, unit);
        }
    }
}