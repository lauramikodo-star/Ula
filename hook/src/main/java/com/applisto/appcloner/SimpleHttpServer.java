package com.applisto.appcloner;

import android.text.TextUtils;
import android.util.Log;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public abstract class SimpleHttpServer {
    private static final String TAG = "SimpleHttpServer";
    private final int port;
    private Thread thread;

    public static final class Request {
        public final String method;
        public final String path;
        public Request(String method, String path) { this.method = method; this.path = path; }
    }

    public static final class Response {
        public final int statusCode;
        public final String contentType;
        public final String body;
        public Response(int statusCode, String contentType, String body) {
            this.statusCode = statusCode;
            this.contentType = contentType;
            this.body = body;
        }
    }

    public SimpleHttpServer(int port) { this.port = port; }
    public int getPort() { return port; }

    public void start() {
        if (thread != null) return;
        thread = new Thread(() -> runLoop(), "SimpleHttpServer");
        thread.start();
    }

    public void stop() {
        Thread t = thread;
        thread = null;
        if (t != null) t.interrupt();
    }

    protected abstract Response handleRequest(Request req);

    private void runLoop() {
        try (ServerSocket server = new ServerSocket(port)) {
            Log.i(TAG, "Started on port " + port);
            while (thread != null && !Thread.currentThread().isInterrupted()) {
                Socket s = server.accept();
                new Thread(() -> handleClient(s), "SimpleHttpServer-Client").start();
            }
        } catch (Throwable t) {
            Log.w(TAG, "Server stopped/crashed", t);
        }
    }

    private void handleClient(Socket socket) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             OutputStream os = socket.getOutputStream();
             PrintWriter pw = new PrintWriter(os, true)) {

            String line = br.readLine(); // only first request line (like jar)
            if (TextUtils.isEmpty(line)) return;

            String[] parts = line.split(" ");
            if (parts.length < 2) return;

            Response resp = handleRequest(new Request(parts[0], parts[1]));

            pw.println("HTTP/1.1 " + resp.statusCode + " OK");
            if (!TextUtils.isEmpty(resp.contentType)) {
                pw.println("Content-Type: " + resp.contentType + "; charset=utf-8");
            }
            pw.println("Cache-Control: no-store");
            pw.println();
            if (!TextUtils.isEmpty(resp.body)) pw.println(resp.body);

        } catch (Throwable t) {
            Log.w(TAG, "handleClient failed", t);
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }
}
