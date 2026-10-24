package com.tntmod;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 24/7 Cloud Team Sync Client
 * Kết nối tự động tới Cloud Relay Server (HTTPS / WSS / Plain TCP Socket)
 * Chạy luồng đọc và luồng gửi tách biệt, chống kẹt executor, tự động reconnect liên tục.
 */
public class TeamSyncClient {
    private static final Gson GSON = new Gson();
    private static volatile Socket socket;
    private static volatile OutputStream out;
    private static volatile boolean running = false;
    private static volatile boolean isConnected = false;
    private static long lastConnectAttempt = 0;
    private static Thread networkThread;
    private static final java.util.Set<String> connectedPlayers = new java.util.concurrent.ConcurrentSkipListSet<>();

    public static boolean isConnected() {
        return isConnected;
    }

    public static java.util.List<String> getConnectedPlayers() {
        return new java.util.ArrayList<>(connectedPlayers);
    }

    public static void start() {
        if (running) return;
        running = true;

        networkThread = new Thread(() -> {
            while (running) {
                try {
                    if (socket == null || socket.isClosed() || !socket.isConnected()) {
                        isConnected = false;
                        long now = System.currentTimeMillis();
                        if (now - lastConnectAttempt > 4000) {
                            lastConnectAttempt = now;
                            connect();
                        }
                    }
                    Thread.sleep(1500);
                } catch (InterruptedException e) {
                    break;
                } catch (Throwable ignored) {}
            }
        }, "TNTMod-TeamSync-Loop");
        networkThread.setDaemon(true);
        networkThread.start();
    }

    public static void reconnect() {
        new Thread(() -> {
            closeSocket();
            lastConnectAttempt = 0;
            connect();
        }, "TNTMod-Reconnect").start();
    }

    private static void connect() {
        try {
            String url = ModConfig.teamServerUrl.trim();
            boolean isSecure = url.startsWith("https://") || url.startsWith("wss://");

            if (url.startsWith("http://")) url = url.substring(7);
            else if (url.startsWith("https://")) url = url.substring(8);
            else if (url.startsWith("ws://")) url = url.substring(5);
            else if (url.startsWith("wss://")) url = url.substring(6);

            String host = url;
            int port = isSecure ? 443 : 80;

            if (url.contains("/")) {
                url = url.substring(0, url.indexOf("/"));
            }
            if (url.contains(":")) {
                String[] p = url.split(":");
                host = p[0].trim();
                port = Integer.parseInt(p[1].trim());
            } else {
                host = url.trim();
            }

            if (isSecure || host.contains("onrender.com")) {
                SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
                SSLSocket sslSocket = (SSLSocket) factory.createSocket(host, 443);
                sslSocket.startHandshake();
                socket = sslSocket;
                out = socket.getOutputStream();
                InputStream in = socket.getInputStream();

                // WebSocket Upgrade Handshake qua HTTPS (WSS)
                byte[] nonce = new byte[16];
                new SecureRandom().nextBytes(nonce);
                String key = Base64.getEncoder().encodeToString(nonce);

                String req = "GET / HTTP/1.1\r\n" +
                        "Host: " + host + "\r\n" +
                        "Upgrade: websocket\r\n" +
                        "Connection: Upgrade\r\n" +
                        "Sec-WebSocket-Key: " + key + "\r\n" +
                        "Sec-WebSocket-Version: 13\r\n\r\n";
                out.write(req.getBytes(StandardCharsets.UTF_8));
                out.flush();

                // Đọc HTTP response header
                StringBuilder header = new StringBuilder();
                int b;
                while ((b = in.read()) != -1) {
                    header.append((char) b);
                    if (header.toString().endsWith("\r\n\r\n")) break;
                }

                if (!header.toString().contains("101")) {
                    closeSocket();
                    return;
                }

                isConnected = true;

                // Gửi tham gia phòng
                JsonObject join = new JsonObject();
                join.addProperty("action", "join");
                join.addProperty("room", ModConfig.teamRoomId);
                String playerName = (Minecraft.getMinecraft().player != null) ? Minecraft.getMinecraft().player.getName() : "player";
                join.addProperty("player", playerName);
                sendWsFrame(join.toString());

                // Khởi động luồng đọc WebSocket frames riêng biệt để không chặn luồng kết nối
                new Thread(() -> readWsLoop(in), "TNTMod-Ws-Reader").start();

            } else {
                // Kết nối Plain TCP Socket truyền thống
                Socket plainSocket = new Socket(host, port);
                plainSocket.setKeepAlive(true);
                socket = plainSocket;
                out = socket.getOutputStream();
                InputStream in = socket.getInputStream();
                isConnected = true;

                JsonObject join = new JsonObject();
                join.addProperty("action", "join");
                join.addProperty("room", ModConfig.teamRoomId);
                String playerName = (Minecraft.getMinecraft().player != null) ? Minecraft.getMinecraft().player.getName() : "player";
                join.addProperty("player", playerName);
                sendRawJson(join);

                new Thread(() -> {
                    try {
                        byte[] buf = new byte[4096];
                        StringBuilder sb = new StringBuilder();
                        int read;
                        while ((read = in.read(buf)) != -1) {
                            sb.append(new String(buf, 0, read, StandardCharsets.UTF_8));
                            int idx;
                            while ((idx = sb.indexOf("\n")) != -1) {
                                String line = sb.substring(0, idx).trim();
                                sb.delete(0, idx + 1);
                                if (!line.isEmpty()) {
                                    processMessage(line);
                                }
                            }
                        }
                    } catch (Throwable ignored) {
                    } finally {
                        closeSocket();
                    }
                }, "TNTMod-Tcp-Reader").start();
            }
        } catch (Throwable t) {
            closeSocket();
        }
    }

    private static void readWsLoop(InputStream in) {
        try {
            while (isConnected) {
                int b1 = in.read();
                if (b1 == -1) break;
                int b2 = in.read();
                if (b2 == -1) break;

                int opcode = b1 & 0x0F;
                if (opcode == 8) break; // Close frame

                int len = b2 & 0x7F;
                if (len == 126) {
                    len = ((in.read() & 0xFF) << 8) | (in.read() & 0xFF);
                } else if (len == 127) {
                    len = (int) in.read(); // Đơn giản hóa cho gói nhỏ
                }

                boolean masked = (b2 & 0x80) != 0;
                byte[] mask = new byte[4];
                if (masked) {
                    int mr = 0;
                    while (mr < 4) {
                        int r = in.read(mask, mr, 4 - mr);
                        if (r == -1) break;
                        mr += r;
                    }
                }

                byte[] data = new byte[len];
                int total = 0;
                while (total < len) {
                    int r = in.read(data, total, len - total);
                    if (r == -1) break;
                    total += r;
                }

                if (masked) {
                    for (int i = 0; i < len; i++) {
                        data[i] = (byte) (data[i] ^ mask[i % 4]);
                    }
                }

                String text = new String(data, StandardCharsets.UTF_8);
                processMessage(text);
            }
        } catch (Throwable ignored) {
        } finally {
            closeSocket();
        }
    }

    private static synchronized void sendWsFrame(String text) {
        if (out == null) return;
        try {
            byte[] payload = text.getBytes(StandardCharsets.UTF_8);
            byte[] mask = new byte[4];
            new SecureRandom().nextBytes(mask);

            int len = payload.length;
            out.write(0x81); // Text frame FIN

            if (len <= 125) {
                out.write(0x80 | len);
            } else if (len <= 65535) {
                out.write(0x80 | 126);
                out.write((len >> 8) & 0xFF);
                out.write(len & 0xFF);
            }

            out.write(mask);
            for (int i = 0; i < len; i++) {
                out.write(payload[i] ^ mask[i % 4]);
            }
            out.flush();
        } catch (Throwable t) {
            closeSocket();
        }
    }

    private static synchronized void sendRawJson(JsonObject obj) {
        if (out == null) return;
        try {
            byte[] data = (obj.toString() + "\n").getBytes(StandardCharsets.UTF_8);
            out.write(data);
            out.flush();
        } catch (Throwable t) {
            closeSocket();
        }
    }

    private static void closeSocket() {
        isConnected = false;
        try {
            if (out != null) out.close();
            if (socket != null) socket.close();
        } catch (Throwable ignored) {}
        socket = null;
        out = null;
    }

    private static final java.util.concurrent.ExecutorService networkSendExecutor = 
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "TNTMod-Network-Sender");
                t.setDaemon(true);
                return t;
            });

    public static void broadcast(BlockPos pos, int dim, String type, int count) {
        networkSendExecutor.execute(() -> {
            try {
                String playerName = (Minecraft.getMinecraft().player != null) ? Minecraft.getMinecraft().player.getName() : "player";
                JsonObject msg = new JsonObject();
                msg.addProperty("action", "update");
                msg.addProperty("room", ModConfig.teamRoomId);
                msg.addProperty("player", playerName);
                msg.addProperty("dim", dim);
                msg.addProperty("x", pos.getX());
                msg.addProperty("y", pos.getY());
                msg.addProperty("z", pos.getZ());
                msg.addProperty("type", type);
                msg.addProperty("count", count);

                if (socket instanceof SSLSocket || ModConfig.teamServerUrl.contains("onrender.com")) {
                    sendWsFrame(msg.toString());
                } else {
                    sendRawJson(msg);
                }
            } catch (Throwable ignored) {}
        });
    }

    public static void broadcastClearAll() {
        networkSendExecutor.execute(() -> {
            try {
                JsonObject msg = new JsonObject();
                msg.addProperty("action", "clear_all");
                msg.addProperty("room", ModConfig.teamRoomId);

                if (socket instanceof SSLSocket || ModConfig.teamServerUrl.contains("onrender.com")) {
                    sendWsFrame(msg.toString());
                } else {
                    sendRawJson(msg);
                }
            } catch (Throwable ignored) {}
        });
    }

    public static void updatePlayer(String playerName) {
        networkSendExecutor.execute(() -> {
            try {
                JsonObject join = new JsonObject();
                join.addProperty("action", "join");
                join.addProperty("room", ModConfig.teamRoomId);
                join.addProperty("player", playerName);

                if (socket instanceof SSLSocket || ModConfig.teamServerUrl.contains("onrender.com")) {
                    sendWsFrame(join.toString());
                } else {
                    sendRawJson(join);
                }
            } catch (Throwable ignored) {}
        });
    }

    public static void requestRoster() {
        networkSendExecutor.execute(() -> {
            try {
                JsonObject msg = new JsonObject();
                msg.addProperty("action", "get_status");
                msg.addProperty("room", ModConfig.teamRoomId);
                String playerName = (Minecraft.getMinecraft().player != null) ? Minecraft.getMinecraft().player.getName() : "player";
                msg.addProperty("player", playerName);

                if (socket instanceof SSLSocket || ModConfig.teamServerUrl.contains("onrender.com")) {
                    sendWsFrame(msg.toString());
                } else {
                    sendRawJson(msg);
                }
            } catch (Throwable ignored) {}
        });
    }

    private static void processMessage(String line) {
        try {
            JsonObject json = GSON.fromJson(line, JsonObject.class);
            if (json == null || !json.has("action")) return;
            String action = json.get("action").getAsString();

            if ("room_roster".equals(action)) {
                connectedPlayers.clear();
                if (json.has("players") && json.get("players").isJsonArray()) {
                    for (com.google.gson.JsonElement el : json.getAsJsonArray("players")) {
                        String name = el.getAsString();
                        if (name != null && !name.trim().isEmpty()) {
                            connectedPlayers.add(name.trim());
                        }
                    }
                }
            } else if ("update".equals(action)) {
                int dim = json.get("dim").getAsInt();
                int x = json.get("x").getAsInt();
                int y = json.get("y").getAsInt();
                int z = json.get("z").getAsInt();
                String type = json.get("type").getAsString();
                int count = json.has("count") ? json.get("count").getAsInt() : 0;
                BlockStateManager.onRemoteUpdate(dim, x, y, z, type, count);
            } else if ("clear_all".equals(action)) {
                BlockStateManager.onRemoteClear();
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static void stop() {
        running = false;
        closeSocket();
        if (networkThread != null) {
            networkThread.interrupt();
        }
    }
}
