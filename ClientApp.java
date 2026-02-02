package com.sidp.distributed;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class ClientApp {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: ClientApp <host> <port>");
            return;
        }
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("> ");
            if (!scanner.hasNextLine()) break;
            String input = scanner.nextLine();
            String cmd = input.trim();
            if (cmd.equals("quit")) break;
            if (cmd.equals("servers")) {
                sendAndPrint(host, port, "SERVERS");
                continue;
            }
            if (cmd.startsWith("search ")) {
                String kw = cmd.substring(7);
                sendAndPrint(host, port, "SEARCH " + kw);
                continue;
            }
            if (cmd.equals("list")) {
                sendAndPrint(host, port, "LIST");
                continue;
            }
            if (cmd.startsWith("lease ")) {
                String id = cmd.substring(6);
                sendAndPrint(host, port, "LEASE " + id);
                continue;
            }
            if (cmd.startsWith("return ")) {
                String id = cmd.substring(7);
                sendAndPrint(host, port, "RETURN " + id);
                continue;
            }
            if (cmd.equals("stats")) {
                sendAndPrint(host, port, "STATS");
                continue;
            }
            System.out.println("Unknown command");
        }
    }

    private static void sendAndPrint(String host, int port, String msg) throws IOException {
        Socket s = new Socket();
        s.connect(new InetSocketAddress(host, port), 1000);
        s.setSoTimeout(3000);
        BufferedWriter w = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
        BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
        w.write(msg + "\n");
        w.flush();
        String line;
        while ((line = r.readLine()) != null) {
            if (line.equals("END")) break;
            System.out.println(line);
            if (line.equals("OK") || line.startsWith("ERROR ")) break;
        }
        s.close();
    }
}
