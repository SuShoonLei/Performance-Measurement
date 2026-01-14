
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

//GUI to display JMH benchmark results for Connect Four
public class BenchmarkGUI extends JFrame {
    private final JTextArea resultsArea;
    private final JButton runButton;
    private final JProgressBar progressBar;
    private final Map<String, JLabel> throughputLabels;
    private final JPanel metricsPanel;

    public BenchmarkGUI() {
        super("Connect Four - Performance Benchmark");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        setSize(700, 600);

        // Header
        JLabel headerLabel = new JLabel("JMH Performance Benchmarks", SwingConstants.CENTER);
        headerLabel.setFont(new Font("Arial", Font.BOLD, 20));
        headerLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        add(headerLabel, BorderLayout.NORTH);

        // Metrics Panel (shows throughput for each benchmark)
        metricsPanel = new JPanel(new GridLayout(0, 1, 5, 5));
        metricsPanel.setBorder(BorderFactory.createTitledBorder("Throughput Metrics (ops/sec)"));
        throughputLabels = new HashMap<>();
        
        String[] benchmarks = {
            "benchmarkDrop",
            "benchmarkCheckWinner",
            "benchmarkSerialize",
            "benchmarkDeserialize",
            "benchmarkFullGame"
        };
        
        for (String benchmark : benchmarks) {
            JPanel row = new JPanel(new BorderLayout(5, 5));
            JLabel nameLabel = new JLabel(formatBenchmarkName(benchmark));
            nameLabel.setFont(new Font("Arial", Font.PLAIN, 14));
            JLabel valueLabel = new JLabel("-- ops/sec");
            valueLabel.setFont(new Font("Arial", Font.BOLD, 14));
            valueLabel.setForeground(new Color(0, 128, 0));
            row.add(nameLabel, BorderLayout.WEST);
            row.add(valueLabel, BorderLayout.EAST);
            metricsPanel.add(row);
            throughputLabels.put(benchmark, valueLabel);
        }
        
        add(metricsPanel, BorderLayout.CENTER);

        // Results text area (detailed output)
        resultsArea = new JTextArea();
        resultsArea.setEditable(false);
        resultsArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        resultsArea.setText("Click 'Run Benchmarks' to start performance testing...\n");
        JScrollPane scrollPane = new JScrollPane(resultsArea);
        scrollPane.setPreferredSize(new Dimension(680, 200));
        scrollPane.setBorder(BorderFactory.createTitledBorder("Detailed Results"));
        add(scrollPane, BorderLayout.SOUTH);

        // Control panel
        JPanel controlPanel = new JPanel(new FlowLayout());
        runButton = new JButton("Run Benchmarks");
        runButton.setFont(new Font("Arial", Font.BOLD, 14));
        runButton.addActionListener(e -> runBenchmarks());
        
        progressBar = new JProgressBar();
        progressBar.setIndeterminate(false);
        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(200, 25));
        
        controlPanel.add(runButton);
        controlPanel.add(progressBar);
        
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(headerLabel, BorderLayout.NORTH);
        topPanel.add(controlPanel, BorderLayout.SOUTH);
        add(topPanel, BorderLayout.NORTH);

        setLocationRelativeTo(null);
    }

    private String formatBenchmarkName(String benchmark) {
        return benchmark.replace("benchmark", "")
                .replaceAll("([A-Z])", " $1")
                .trim() + ":";
    }

    private void runBenchmarks() {
        runButton.setEnabled(false);
        progressBar.setIndeterminate(true);
        resultsArea.setText("Running benchmarks...\n");
        
        // Clear previous results
        for (JLabel label : throughputLabels.values()) {
            label.setText("Running...");
        }

        SwingWorker<Collection<RunResult>, String> worker = new SwingWorker<>() {
            @Override
            protected Collection<RunResult> doInBackground() throws Exception {
                publish("Setting up JMH benchmark...\n");
                
                Options opt = new OptionsBuilder()
                        .include(ConnectFourBenchmark.class.getSimpleName())
                        .build();

                publish("Starting benchmark execution...\n");
                return new Runner(opt).run();
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                for (String msg : chunks) {
                    resultsArea.append(msg);
                }
            }

            @Override
            protected void done() {
                try {
                    Collection<RunResult> results = get();
                    displayResults(results);
                } catch (Exception e) {
                    resultsArea.append("\nError running benchmarks: " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    runButton.setEnabled(true);
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(100);
                }
            }
        };

        worker.execute();
    }

    private void displayResults(Collection<RunResult> results) {
        resultsArea.setText("=== Benchmark Results ===\n\n");
        
        for (RunResult result : results) {
            String benchmarkName = result.getPrimaryResult().getLabel();
            double throughput = result.getPrimaryResult().getScore();
            String unit = result.getPrimaryResult().getScoreUnit();
            double error = result.getPrimaryResult().getScoreError();
            
            // Extract just the benchmark method name
            String methodName = benchmarkName;
            if (benchmarkName.contains(".")) {
                methodName = benchmarkName.substring(benchmarkName.lastIndexOf('.') + 1);
            }
            
            // Update throughput label
            JLabel label = throughputLabels.get(methodName);
            if (label != null) {
                label.setText(String.format("%.2f ± %.2f ops/sec", throughput, error));
            }
            
            // Add to detailed results
            resultsArea.append(String.format("%s:\n", formatBenchmarkName(methodName)));
            resultsArea.append(String.format("  Throughput: %.2f ± %.2f %s\n", 
                    throughput, error, unit));
            resultsArea.append(String.format("  (Higher is better)\n\n"));
        }
        
        resultsArea.append("\nBenchmarks completed successfully!\n");
        resultsArea.setCaretPosition(0);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            BenchmarkGUI gui = new BenchmarkGUI();
            gui.setVisible(true);
        });
    }
}