package com.sidp.distributed;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class TestClient {
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.println("Usage: TestClient <host> <port> <command>");
            return;
        }
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        StringBuilder sb = new StringBuilder();
        for (int i = 2; i < args.length; i++) {
            if (i > 2) sb.append(' ');
            sb.append(args[i]);
        }
        String cmd = sb.toString();
        Socket s = new Socket();
        s.connect(new InetSocketAddress(host, port), 1000);
        s.setSoTimeout(3000);
        BufferedWriter w = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
        BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
        w.write(cmd + "\n");
        w.flush();
        String line;
        while ((line = r.readLine()) != null) {
            System.out.println(line);
            if (line.equals("END")) break;
            if (line.equals("OK") || line.startsWith("ERROR ")) break;
        }
        s.close();
    }
}
