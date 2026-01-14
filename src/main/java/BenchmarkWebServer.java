import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.*;

public class BenchmarkWebServer {
    private static final int PORT = 8888;
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static volatile String latestResults = "No benchmarks run yet.";
    private static volatile Map<String, Object> latestData = new HashMap<>();
    private static volatile boolean isRunning = false;

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/", new HomeHandler());
        server.createContext("/api/run", new RunBenchmarkHandler());
        server.createContext("/api/results", new ResultsHandler());
        server.createContext("/api/chartdata", new ChartDataHandler());
        server.setExecutor(executor);
        server.start();
        System.out.println("===========================================");
        System.out.println("Benchmark Web Server Started!");
        System.out.println("===========================================");
        System.out.println("Open your browser and go to:");
        System.out.println("  http://localhost:" + PORT);
        System.out.println("===========================================");
    }

    static class HomeHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            String html = getHtmlPage();
            byte[] response = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        }
    }

    static class RunBenchmarkHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if (isRunning) {
                sendJsonResponse(exchange, "{\"status\":\"running\"}");
                return;
            }
            String query = exchange.getRequestURI().getQuery();
            int threadCount = 4;
            if (query != null && query.startsWith("threads=")) {
                try {
                    threadCount = Integer.parseInt(query.substring(8));
                } catch (NumberFormatException e) {}
            }
            final int threads = threadCount;
            executor.submit(() -> {
                try {
                    isRunning = true;
                    latestResults = "Running with " + threads + " threads...\n\n";
                    runBenchmarks(threads);
                    isRunning = false;
                } catch (Exception e) {
                    latestResults = "Error: " + e.getMessage();
                    isRunning = false;
                }
            });
            sendJsonResponse(exchange, "{\"status\":\"started\"}");
        }
    }

    static class ResultsHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            String json = String.format("{\"status\":\"%s\",\"results\":\"%s\"}", 
                isRunning ? "running" : "complete", escapeJson(latestResults));
            sendJsonResponse(exchange, json);
        }
    }

    static class ChartDataHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            StringBuilder json = new StringBuilder("{");
            json.append("\"status\":\"").append(isRunning ? "running" : "complete").append("\",");
            json.append("\"data\":").append(mapToJson(latestData));
            json.append("}");
            sendJsonResponse(exchange, json.toString());
        }
    }

    private static void sendJsonResponse(HttpExchange exchange, String json) throws IOException {
        byte[] response = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static String mapToJson(Map<String, Object> map) {
        if (map.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":");
            Object val = entry.getValue();
            if (val instanceof String) {
                sb.append("\"").append(escapeJson((String)val)).append("\"");
            } else if (val instanceof Map) {
                sb.append(mapToJson((Map<String, Object>)val));
            } else {
                sb.append(val);
            }
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private static void runBenchmarks(int numThreads) {
        StringBuilder results = new StringBuilder();
        results.append("=== Multi-threaded Benchmark Results ===\n");
        results.append("Threads: ").append(numThreads).append("\n");
        results.append("Platform: ").append(System.getProperty("os.name")).append("\n\n");
        
        Map<String, Object> chartData = new HashMap<>();
        
        try {
            results.append("Implementation 1: Synchronized Board\n");
            results.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
            Map<String, Double> syncResults = benchmarkImpl("Sync", new Board(), numThreads, results);
            
            results.append("\nImplementation 2: ReadWriteLock Board\n");
            results.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
            Map<String, Double> rwResults = benchmarkImpl("RWLock", new BoardRWLock(), numThreads, results);
            
            results.append("\nPerformance Comparison\n");
            results.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
            for (String test : syncResults.keySet()) {
                double sync = syncResults.get(test);
                double rw = rwResults.get(test);
                double imp = ((rw - sync) / sync) * 100;
                results.append(String.format("%s:\n  Sync: %,.0f  RWLock: %,.0f  Improvement: %+.1f%%\n", 
                    test, sync, rw, imp));
            }
            
            chartData.put("synchronized", syncResults);
            chartData.put("readwritelock", rwResults);
            results.append("\nCompleted!\n");
        } catch (Exception e) {
            results.append("Error: ").append(e.toString());
        }
        
        latestResults = results.toString();
        latestData = chartData;
    }

    private static Map<String, Double> benchmarkImpl(String name, Object board, int threads, StringBuilder results) throws Exception {
        Map<String, Double> throughputs = new HashMap<>();
        
        results.append("1. Concurrent Drops\n");
        double drop = benchmarkDrops(board, threads);
        throughputs.put("Drop", drop);
        results.append(String.format("   %,.0f ops/sec\n", drop));
        
        results.append("2. Concurrent Winner Checks\n");
        double winner = benchmarkWinner(board, threads);
        throughputs.put("CheckWinner", winner);
        results.append(String.format("   %,.0f ops/sec\n", winner));
        
        results.append("3. Mixed Operations\n");
        double mixed = benchmarkMixed(board, threads);
        throughputs.put("Mixed", mixed);
        results.append(String.format("   %,.0f ops/sec\n", mixed));
        
        return throughputs;
    }


// multi threaded benchmark methods
    private static double benchmarkDrops(Object board, int threads) throws Exception {
        final int opsPerThread = 50000;
        // CREATE THREAD POOL - where multi-threading starts!
        ExecutorService exec = Executors.newFixedThreadPool(threads);
        // COUNTDOWN LATCH - Ensures all threads finish before measuring
        CountDownLatch latch = new CountDownLatch(threads);
        //timer start
        long start = System.nanoTime();
        
        // Simulate multi-threaded application with shared data
        for (int t = 0; t < threads; t++) {
            final int tid = t;
            exec.submit(() -> {
                try {
                    for (int i = 0; i < opsPerThread; i++) {
                        if (board instanceof Board) {
                            Board b = (Board) board;
                            b.clear();
                            for (int c = 0; c < 7; c++) b.drop((tid % 2) + 1, c);
                        } else {
                            BoardRWLock b = (BoardRWLock) board;
                            b.clear();
                            for (int c = 0; c < 7; c++) b.drop((tid % 2) + 1, c);
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        //timer end
        long end = System.nanoTime();
        exec.shutdown();
        // Calculate throughput
        return (opsPerThread * threads * 7) / ((end - start) / 1e9);
    }

    private static double benchmarkWinner(Object board, int threads) throws Exception {
        if (board instanceof Board) {
            Board b = (Board) board;
            b.clear();
            for (int i = 0; i < 6; i++) b.drop((i % 2) + 1, 3);
        } else {
            BoardRWLock b = (BoardRWLock) board;
            b.clear();
            for (int i = 0; i < 6; i++) b.drop((i % 2) + 1, 3);
        }
        
        final int opsPerThread = 500000;
        ExecutorService exec = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        long start = System.nanoTime();
        
        for (int t = 0; t < threads; t++) {
            exec.submit(() -> {
                try {
                    for (int i = 0; i < opsPerThread; i++) {
                        if (board instanceof Board) {
                            ((Board) board).checkWinner();
                        } else {
                            ((BoardRWLock) board).checkWinner();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        long end = System.nanoTime();
        exec.shutdown();
        return (opsPerThread * threads) / ((end - start) / 1e9);
    }

    private static double benchmarkMixed(Object board, int threads) throws Exception {
        final int opsPerThread = 50000;
        ExecutorService exec = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        long start = System.nanoTime();
        
        for (int t = 0; t < threads; t++) {
            final int tid = t;
            exec.submit(() -> {
                try {
                    Random rand = new Random(tid);
                    for (int i = 0; i < opsPerThread; i++) {
                        if (rand.nextDouble() < 0.8) {
                            if (board instanceof Board) {
                                ((Board) board).checkWinner();
                            } else {
                                ((BoardRWLock) board).checkWinner();
                            }
                        } else {
                            if (board instanceof Board) {
                                ((Board) board).drop((tid % 2) + 1, rand.nextInt(7));
                            } else {
                                ((BoardRWLock) board).drop((tid % 2) + 1, rand.nextInt(7));
                            }
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        long end = System.nanoTime();
        exec.shutdown();
        return (opsPerThread * threads) / ((end - start) / 1e9);
    }

    private static String getHtmlPage() {
        return "<!DOCTYPE html>\n<html>\n<head>\n" +
            "<meta charset=\"UTF-8\">\n" +
            "<title>Connect Four Benchmarks</title>\n" +
            "<script src=\"https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js\"></script>\n" +
            "<style>\n" +
            "* { margin: 0; padding: 0; box-sizing: border-box; }\n" +
            "body { font-family: 'Segoe UI', sans-serif; background: linear-gradient(135deg, #a8b1daff 0%, #764ba2 100%); min-height: 100vh; padding: 20px; }\n" +
            ".container { max-width: 1200px; margin: 0 auto; background: white; border-radius: 15px; box-shadow: 0 20px 60px rgba(0,0,0,0.3); overflow: hidden; }\n" +
            ".header { background: linear-gradient(135deg, #ddb3b3ff 0%, #ecb9a4ff 100%); color: white; padding: 30px; text-align: center; }\n" +
            ".header h1 { font-size: 2.5em; margin-bottom: 10px; }\n" +
            ".controls { padding: 30px; text-align: center; display: flex; gap: 20px; justify-content: center; align-items: center; }\n" +
            ".btn { background: linear-gradient(135deg, #e8e8c8ff 0%, #939fcbff 100%); color: white; border: none; padding: 15px 40px; font-size: 1.1em; border-radius: 25px; cursor: pointer; font-weight: bold; }\n" +
            ".btn:hover { transform: translateY(-2px); box-shadow: 0 10px 25px rgba(189, 197, 232, 0.4); }\n" +
            ".btn:disabled { background: #ccc; cursor: not-allowed; }\n" +
            "select { padding: 10px; font-size: 1em; border: 2px solid #858aa5ff; border-radius: 8px; }\n" +
            ".status { padding: 20px 30px; background: #f8f9fa; margin: 20px 30px; border-radius: 5px; border-left: 4px solid #667eea; }\n" +
            ".status.running { background: #fff9e6; border-left-color: #c69999ff; }\n" +
            ".status.complete { background: #e8f5e9; border-left-color: #c0e0c7ff; }\n" +
            ".charts { padding: 30px; display: grid; grid-template-columns: 1fr 1fr; gap: 30px; }\n" +
            ".chart-box { background: #f8f9fa; border-radius: 10px; padding: 20px; }\n" +
            ".chart-box h3 { text-align: center; color: #afb6d3ff; margin-bottom: 15px; }\n" +
            ".results { padding: 30px; }\n" +
            ".results-box { background: #f8f9fa; border-radius: 10px; padding: 25px; font-family: 'Courier New', monospace; white-space: pre-wrap; font-size: 0.95em; max-height: 500px; overflow-y: auto; }\n" +
            ".spinner { display: inline-block; width: 20px; height: 20px; border: 3px solid #f3f3f3; border-top: 3px solid #667eea; border-radius: 50%; animation: spin 1s linear infinite; margin-right: 10px; }\n" +
            "@keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }\n" +
            "@media (max-width: 768px) { .charts { grid-template-columns: 1fr; } }\n" +
            "</style>\n</head>\n<body>\n" +
            "<div class=\"container\">\n" +
            "<div class=\"header\"><h1>🎮 Connect Four</h1><p>Multi-threaded Performance Benchmark</p></div>\n" +
            "<div class=\"controls\">\n" +
            "<label>Threads:</label>\n" +
            "<select id=\"threadCount\"><option value=\"1\">1</option><option value=\"2\">2</option><option value=\"4\" selected>4</option><option value=\"8\">8</option><option value=\"16\">16</option></select>\n" +
            "<button class=\"btn\" onclick=\"runBenchmark()\">Run Benchmarks</button>\n" +
            "</div>\n" +
            "<div id=\"statusBar\" class=\"status\" style=\"display:none;\"><span id=\"statusText\"></span></div>\n" +
            "<div class=\"charts\" id=\"charts\" style=\"display:none;\">\n" +
            "<div class=\"chart-box\"><h3>Implementation Comparison</h3><canvas id=\"chart1\"></canvas></div>\n" +
            "<div class=\"chart-box\"><h3>Performance Improvement</h3><canvas id=\"chart2\"></canvas></div>\n" +
            "</div>\n" +
            "<div class=\"results\"><div class=\"results-box\" id=\"results\">Click 'Run Benchmarks' to start.\\n\\nTests:\\n• Concurrent drop operations\\n• Concurrent winner checks\\n• Mixed read/write (80% read, 20% write)\\n\\nCompares:\\n• Synchronized (traditional)\\n• ReadWriteLock (concurrent reads)</div></div>\n" +
            "</div>\n" +
            "<script>\n" +
            "let polling = null, chart1 = null, chart2 = null;\n" +
            "async function runBenchmark() {\n" +
            "  const btn = event.target;\n" +
            "  const threads = document.getElementById('threadCount').value;\n" +
            "  btn.disabled = true;\n" +
            "  document.getElementById('statusBar').style.display = 'block';\n" +
            "  document.getElementById('statusBar').className = 'status running';\n" +
            "  document.getElementById('statusText').innerHTML = '<span class=\"spinner\"></span>Running with ' + threads + ' threads...';\n" +
            "  await fetch('/api/run?threads=' + threads);\n" +
            "  startPolling();\n" +
            "}\n" +
            "function startPolling() {\n" +
            "  if (polling) clearInterval(polling);\n" +
            "  polling = setInterval(async () => {\n" +
            "    const r = await fetch('/api/results');\n" +
            "    const d = await r.json();\n" +
            "    document.getElementById('results').textContent = d.results;\n" +
            "    if (d.status === 'complete') {\n" +
            "      clearInterval(polling);\n" +
            "      document.getElementById('statusBar').className = 'status complete';\n" +
            "      document.getElementById('statusText').textContent = 'Complete!';\n" +
            "      document.querySelector('.btn').disabled = false;\n" +
            "      fetchCharts();\n" +
            "    }\n" +
            "  }, 1000);\n" +
            "}\n" +
            "async function fetchCharts() {\n" +
            "  const r = await fetch('/api/chartdata');\n" +
            "  const json = await r.json();\n" +
            "  if (json.data && Object.keys(json.data).length > 0) {\n" +
            "    displayCharts(json.data);\n" +
            "  }\n" +
            "}\n" +
            "function displayCharts(data) {\n" +
            "  document.getElementById('charts').style.display = 'grid';\n" +
            "  const sync = data.synchronized || {};\n" +
            "  const rw = data.readwritelock || {};\n" +
            "  const ops = Object.keys(sync);\n" +
            "  const ctx1 = document.getElementById('chart1');\n" +
            "  if (chart1) chart1.destroy();\n" +
            "  chart1 = new Chart(ctx1, {\n" +
            "    type: 'bar',\n" +
            "    data: {\n" +
            "      labels: ops,\n" +
            "      datasets: [{\n" +
            "        label: 'Synchronized',\n" +
            "        data: ops.map(o => sync[o]),\n" +
            "        backgroundColor: 'rgba(255, 99, 132, 0.7)'\n" +
            "      }, {\n" +
            "        label: 'ReadWriteLock',\n" +
            "        data: ops.map(o => rw[o]),\n" +
            "        backgroundColor: 'rgba(75, 192, 192, 0.7)'\n" +
            "      }]\n" +
            "    },\n" +
            "    options: { responsive: true, scales: { y: { beginAtZero: true } } }\n" +
            "  });\n" +
            "  const imp = ops.map(o => ((rw[o] - sync[o]) / sync[o] * 100));\n" +
            "  const ctx2 = document.getElementById('chart2');\n" +
            "  if (chart2) chart2.destroy();\n" +
            "  chart2 = new Chart(ctx2, {\n" +
            "    type: 'bar',\n" +
            "    data: {\n" +
            "      labels: ops,\n" +
            "      datasets: [{\n" +
            "        label: 'Improvement %',\n" +
            "        data: imp,\n" +
            "        backgroundColor: imp.map(v => v >= 0 ? 'rgba(172, 216, 216, 0.7)' : 'rgba(224, 173, 184, 0.7)')\n" +
            "      }]\n" +
            "    },\n" +
            "    options: { responsive: true, scales: { y: { beginAtZero: true } } }\n" +
            "  });\n" +
            "}\n" +
            "</script>\n</body>\n</html>";
    }
}
