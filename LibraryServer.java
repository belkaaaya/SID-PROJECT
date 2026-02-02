package com.sidp.distributed;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LibraryServer {
    private final String serverId;
    private final int port;
    private final File booksFile;
    private final List<Book> books = new ArrayList<>();
    private final Map<String, Integer> keywordCounts = new HashMap<>();
    private final Map<String, Integer> bookSearchCounts = new HashMap<>();

    public LibraryServer(String serverId, int port, File booksFile) {
        this.serverId = serverId;
        this.port = port;
        this.booksFile = booksFile;
    }

    public void start() throws IOException {
        loadBooks();
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
            String line = r.readLine();
            if (line == null) return;
            if (line.startsWith("SEARCH ")) {
                String keyword = line.substring(7).trim();
                keywordCounts.put(keyword.toLowerCase(), keywordCounts.getOrDefault(keyword.toLowerCase(), 0) + 1);
                List<Book> matches = new ArrayList<>();
                for (Book b : books) {
                    if (b.matchesKeyword(keyword)) {
                        bookSearchCounts.put(b.getId(), bookSearchCounts.getOrDefault(b.getId(), 0) + 1);
                        matches.add(b);
                    }
                }
                for (Book b : matches) {
                    w.write(Book.toProtocolLine(b));
                    w.write("\n");
                }
                w.write("END\n");
                w.flush();
            } else if (line.equals("LIST")) {
                for (Book b : books) {
                    if (b.isAvailable()) {
                        w.write(Book.toProtocolLine(b));
                        w.write("\n");
                    }
                }
                w.write("END\n");
                w.flush();
            } else if (line.startsWith("LEASE ")) {
                String id = line.substring(6).trim();
                Book b = findBook(id);
                if (b == null) {
                    w.write("ERROR NotFound\n");
                    w.flush();
                } else if (!b.isAvailable()) {
                    w.write("ERROR NotAvailable\n");
                    w.flush();
                } else {
                    b.setAvailable(false);
                    saveBooks();
                    w.write("OK\n");
                    w.flush();
                }
            } else if (line.startsWith("RETURN ")) {
                String id = line.substring(7).trim();
                Book b = findBook(id);
                if (b == null) {
                    w.write("ERROR NotFound\n");
                    w.flush();
                } else if (b.isAvailable()) {
                    w.write("ERROR AlreadyAvailable\n");
                    w.flush();
                } else {
                    b.setAvailable(true);
                    saveBooks();
                    w.write("OK\n");
                    w.flush();
                }
            } else if (line.equals("STATS")) {
                List<Map.Entry<String, Integer>> keys = new ArrayList<>(keywordCounts.entrySet());
                List<Map.Entry<String, Integer>> booksC = new ArrayList<>(bookSearchCounts.entrySet());
                for (Map.Entry<String, Integer> e : keys) {
                    w.write("KEYWORD " + e.getKey() + "|" + e.getValue() + "\n");
                }
                for (Map.Entry<String, Integer> e : booksC) {
                    w.write("BOOKSEARCH " + e.getKey() + "|" + e.getValue() + "\n");
                }
                w.write("END\n");
                w.flush();
            } else {
                w.write("ERROR UnknownCommand\n");
                w.flush();
            }
        } catch (IOException ignored) {
        } finally {
            try { s.close(); } catch (IOException ignored) {}
        }
    }

    private Book findBook(String id) {
        for (Book b : books) {
            if (b.getId().equals(id)) return b;
        }
        return null;
    }

    private void loadBooks() throws IOException {
        if (!booksFile.exists()) {
            booksFile.getParentFile().mkdirs();
            List<Book> defaults = defaultBooks();
            books.clear();
            books.addAll(defaults);
            saveBooks();
            return;
        }
        books.clear();
        try (BufferedReader r = new BufferedReader(new FileReader(booksFile, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty()) continue;
                books.add(Book.fromStorageLine(line));
            }
        }
    }

    private void saveBooks() throws IOException {
        try (BufferedWriter w = new BufferedWriter(new FileWriter(booksFile, StandardCharsets.UTF_8))) {
            for (Book b : books) {
                w.write(b.toStorageLine());
                w.write("\n");
            }
        }
    }

    private List<Book> defaultBooks() {
        List<Book> list = new ArrayList<>();
        if (serverId.equals("LIB1")) {
            list.add(new Book("LIB1-001", "Distributed Systems", "Andrew Tanenbaum", asList("distributed", "systems", "networks"), true));
            list.add(new Book("LIB1-002", "Operating Systems", "Abraham Silberschatz", asList("os", "kernel", "threads"), true));
        } else if (serverId.equals("LIB2")) {
            list.add(new Book("LIB2-001", "Algorithms", "Robert Sedgewick", asList("algorithms", "data structures"), true));
            list.add(new Book("LIB2-002", "Java Concurrency in Practice", "Brian Goetz", asList("java", "concurrency", "threads"), true));
        } else if (serverId.equals("LIB3")) {
            list.add(new Book("LIB3-001", "Clean Code", "Robert Martin", asList("clean code", "best practices", "software"), true));
            list.add(new Book("LIB3-002", "Design Patterns", "Erich Gamma", asList("patterns", "oop", "design"), true));
        }
        return list;
    }

    private List<String> asList(String... a) {
        List<String> l = new ArrayList<>();
        Collections.addAll(l, a);
        return l;
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.println("Usage: LibraryServer <serverId> <port> <booksFile>");
            return;
        }
        String serverId = args[0];
        int port = Integer.parseInt(args[1]);
        File booksFile = new File(args[2]);
        new LibraryServer(serverId, port, booksFile).start();
    }
}

