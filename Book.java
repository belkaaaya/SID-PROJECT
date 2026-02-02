package com.sidp.distributed;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Book {
    private final String id;
    private final String title;
    private final String author;
    private final List<String> keywords;
    private boolean available;

    public Book(String id, String title, String author, List<String> keywords, boolean available) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.keywords = new ArrayList<>(keywords);
        this.available = available;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public String getServerId() {
        int idx = id.indexOf("-");
        if (idx <= 0) return "";
        return id.substring(0, idx);
    }

    public boolean matchesKeyword(String k) {
        String q = k.toLowerCase();
        if (title.toLowerCase().contains(q)) return true;
        if (author.toLowerCase().contains(q)) return true;
        for (String kw : keywords) {
            if (kw.toLowerCase().contains(q)) return true;
        }
        return false;
    }

    public String toStorageLine() {
        String kw = String.join(";", keywords);
        return escape(id) + "|" + escape(title) + "|" + escape(author) + "|" + escape(kw) + "|" + (available ? "true" : "false");
    }

    public static Book fromStorageLine(String line) {
        String[] parts = split(line, 5);
        String id = unescape(parts[0]);
        String title = unescape(parts[1]);
        String author = unescape(parts[2]);
        String kw = unescape(parts[3]);
        boolean available = "true".equalsIgnoreCase(parts[4]);
        List<String> kws = kw.isEmpty() ? new ArrayList<>() : Arrays.asList(kw.split(";"));
        return new Book(id, title, author, kws, available);
    }

    public static String toProtocolLine(Book b) {
        return "BOOK " + b.getId() + "|" + safe(b.getTitle()) + "|" + safe(b.getAuthor()) + "|" + b.getServerId() + "|" + (b.isAvailable() ? "available" : "leased");
    }

    public static Book fromProtocolLine(String line) {
        String body = line.substring("BOOK ".length());
        String[] parts = split(body, 5);
        String id = parts[0];
        String title = parts[1];
        String author = parts[2];
        String serverId = parts[3];
        boolean available = "available".equalsIgnoreCase(parts[4]);
        List<String> kws = new ArrayList<>();
        return new Book(id, title, author, kws, available);
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("|", "\\|");
    }

    private static String unescape(String s) {
        StringBuilder sb = new StringBuilder();
        boolean esc = false;
        for (char c : s.toCharArray()) {
            if (esc) {
                sb.append(c);
                esc = false;
            } else if (c == '\\') {
                esc = true;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String[] split(String s, int expected) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean esc = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (esc) {
                current.append(c);
                esc = false;
            } else if (c == '\\') {
                esc = true;
            } else if (c == '|') {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        parts.add(current.toString());
        while (parts.size() < expected) parts.add("");
        return parts.toArray(new String[0]);
    }

    private static String safe(String s) {
        return s.replace("|", "/");
    }
}

