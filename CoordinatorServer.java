package com.sidp.distributed;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class CoordinatorServer {
    private final int port;
    private final List<LibraryEndpoint> endpoints = new ArrayList<>();

    public CoordinatorServer(int port, List<LibraryEndpoint> endpoints) {
        this.port = port;
        this.endpoints.addAll(endpoints);
    }

    public void start() throws IOException {
        ServerSocket serverSocket = new ServerSocket(port);
        ExecutorService pool = Executors.newCachedThreadPool();
        while (true) {
            Socket s = serverSocket.accept();
            pool.submit(() -> handleClient(s));
        }
    }

    private void handleClient(Socket s) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter w = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8))) {
            while (true) {
                String line = r.readLine();
                if (line == null) break;
                if (line.startsWith("SEARCH ")) {
                    String keyword = line.substring(7).trim();
                    List<Book> books = broadcastSearch(keyword);
                    for (Book b : books) {
                        w.write(Book.toProtocolLine(b));
                        w.write("\n");
                    }
                    w.write("END\n");
                    w.flush();
                } else if (line.equals("LIST")) {
                    List<Book> books = broadcastList();
                    for (Book b : books) {
                        w.write(Book.toProtocolLine(b));
                        w.write("\n");
                    }
                    w.write("END\n");
                    w.flush();
                } else if (line.startsWith("LEASE ")) {
                    String id = line.substring(6).trim();
                    String sid = serverIdFrom(id);
                    LibraryEndpoint ep = byServerId(sid);
                    if (ep == null) {
                        w.write("ERROR UnknownServer\n");
                        w.flush();
                    } else {
                        String resp = forward(ep, line);
                        w.write(resp + "\n");
                        w.flush();
                    }
                } else if (line.startsWith("RETURN ")) {
                    String id = line.substring(7).trim();
                    String sid = serverIdFrom(id);
                    LibraryEndpoint ep = byServerId(sid);
                    if (ep == null) {
                        w.write("ERROR UnknownServer\n");
                        w.flush();
                    } else {
                        String resp = forward(ep, line);
                        w.write(resp + "\n");
                        w.flush();
                    }
                } else if (line.equals("STATS")) {
                    Map<String, Integer> kw = new HashMap<>();
                    Map<String, Integer> bc = new HashMap<>();
                    for (LibraryEndpoint ep : endpoints) {
                        try {
                            Socket ls = new Socket();
                            ls.connect(new InetSocketAddress(ep.host, ep.port), 1000);
                            ls.setSoTimeout(1500);
                            BufferedWriter lw = new BufferedWriter(new OutputStreamWriter(ls.getOutputStream(), StandardCharsets.UTF_8));
                            BufferedReader lr = new BufferedReader(new InputStreamReader(ls.getInputStream(), StandardCharsets.UTF_8));
                            lw.write("STATS\n");
                            lw.flush();
                            String ln;
                            while ((ln = lr.readLine()) != null) {
                                if ("END".equals(ln)) break;
                                if (ln.startsWith("KEYWORD ")) {
                                    String[] p = ln.substring(8).split("\\|");
                                    if (p.length == 2) kw.put(p[0], kw.getOrDefault(p[0], 0) + Integer.parseInt(p[1]));
                                } else if (ln.startsWith("BOOKSEARCH ")) {
                                    String[] p = ln.substring(11).split("\\|");
                                    if (p.length == 2) bc.put(p[0], bc.getOrDefault(p[0], 0) + Integer.parseInt(p[1]));
                                }
                            }
                            ls.close();
                        } catch (Exception ignored) {
                        }
                    }
                    List<Map.Entry<String, Integer>> kwList = new ArrayList<>(kw.entrySet());
                    List<Map.Entry<String, Integer>> bcList = new ArrayList<>(bc.entrySet());
                    Collections.sort(kwList, (a, b) -> Integer.compare(b.getValue(), a.getValue()));
                    Collections.sort(bcList, (a, b) -> Integer.compare(b.getValue(), a.getValue()));
                    int kmax = Math.min(5, kwList.size());
                    int bmax = Math.min(5, bcList.size());
                    for (int i = 0; i < kmax; i++) {
                        Map.Entry<String, Integer> e = kwList.get(i);
                        w.write("KEYWORD " + e.getKey() + " " + e.getValue() + "\n");
                    }
                    for (int i = 0; i < bmax; i++) {
                        Map.Entry<String, Integer> e = bcList.get(i);
                        w.write("BOOKSEARCH " + e.getKey() + " " + e.getValue() + "\n");
                    }
                    w.write("END\n");
                    w.flush();
                } else if (line.equals("SERVERS")) {
                    for (LibraryEndpoint ep : endpoints) {
                        w.write("SERVER " + ep.serverId + " " + ep.host + ":" + ep.port + "\n");
                    }
                    w.write("END\n");
                    w.flush();
                } else if (line.equals("QUIT")) {
                    break;
                } else {
                    w.write("ERROR UnknownCommand\n");
                    w.flush();
                }
            }
        } catch (IOException ignored) {
        } finally {
            try { s.close(); } catch (IOException ignored) {}
        }
    }

    private List<Book> broadcastSearch(String keyword) {
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, endpoints.size()));
        List<CompletableFuture<List<Book>>> futures = new ArrayList<>();
        for (LibraryEndpoint ep : endpoints) {
            futures.add(CompletableFuture.supplyAsync(() -> queryBooks(ep, "SEARCH " + keyword), pool));
        }
        List<Book> all = new ArrayList<>();
        for (CompletableFuture<List<Book>> f : futures) {
            try {
                all.addAll(f.get(2, TimeUnit.SECONDS));
            } catch (Exception ignored) {
            }
        }
        pool.shutdownNow();
        return all;
    }

    private List<Book> broadcastList() {
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, endpoints.size()));
        List<CompletableFuture<List<Book>>> futures = new ArrayList<>();
        for (LibraryEndpoint ep : endpoints) {
            futures.add(CompletableFuture.supplyAsync(() -> queryBooks(ep, "LIST"), pool));
        }
        List<Book> all = new ArrayList<>();
        for (CompletableFuture<List<Book>> f : futures) {
            try {
                all.addAll(f.get(2, TimeUnit.SECONDS));
            } catch (Exception ignored) {
            }
        }
        pool.shutdownNow();
        return all;
    }

    private List<Book> queryBooks(LibraryEndpoint ep, String cmd) {
        List<Book> list = new ArrayList<>();
        try {
            Socket s = new Socket();
            s.connect(new InetSocketAddress(ep.host, ep.port), 1000);
            s.setSoTimeout(1500);
            BufferedWriter w = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            w.write(cmd + "\n");
            w.flush();
            String line;
            while ((line = r.readLine()) != null) {
                if ("END".equals(line)) break;
                if (line.startsWith("BOOK ")) list.add(Book.fromProtocolLine(line));
            }
            s.close();
        } catch (Exception ignored) {
        }
        return list;
    }

    private String serverIdFrom(String id) {
        int idx = id.indexOf('-');
        if (idx <= 0) return "";
        return id.substring(0, idx);
    }

    private LibraryEndpoint byServerId(String sid) {
        for (LibraryEndpoint ep : endpoints) {
            if (ep.serverId.equals(sid)) return ep;
        }
        return null;
    }

    private String forward(LibraryEndpoint ep, String cmd) {
        try {
            Socket s = new Socket();
            s.connect(new InetSocketAddress(ep.host, ep.port), 1000);
            s.setSoTimeout(1500);
            BufferedWriter w = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
            BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            w.write(cmd + "\n");
            w.flush();
            String line = r.readLine();
            s.close();
            if (line == null) return "ERROR NoResponse";
            return line;
        } catch (Exception e) {
            return "ERROR Unreachable";
        }
    }

    public static class LibraryEndpoint {
        public final String serverId;
        public final String host;
        public final int port;
        public LibraryEndpoint(String serverId, String host, int port) {
            this.serverId = serverId;
            this.host = host;
            this.port = port;
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: CoordinatorServer <port> <serverId@host:port>...");
            return;
        }
        int port = Integer.parseInt(args[0]);
        List<LibraryEndpoint> eps = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            int at = a.indexOf('@');
            int col = a.lastIndexOf(':');
            if (at <= 0 || col <= at) continue;
            String sid = a.substring(0, at);
            String host = a.substring(at + 1, col);
            int p = Integer.parseInt(a.substring(col + 1));
            eps.add(new LibraryEndpoint(sid, host, p));
        }
        new CoordinatorServer(port, eps).start();
    }
}

