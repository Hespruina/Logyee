package top.zhrhello.logyee.velocity;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Velocity 与 Bukkit 的长连接通讯（Socket 客户端）。
 * <p>
 * 协议与 BungeeCord 端完全一致，两端可互换：
 * <ul>
 *   <li>Bungee/Velocity -> Bukkit: {@code CONNECT <playerName>} 查询登录状态</li>
 *   <li>Bukkit -> Bungee/Velocity: {@code CONNECT_RESULT <playerName> <0/1>}</li>
 *   <li>Bukkit -> Bungee/Velocity: {@code PLAYER_LOGIN <playerName>} / {@code PLAYER_LOGOUT}</li>
 *   <li>Bungee/Velocity -> Bukkit: {@code KEEP_LOGGED_IN <player> <time> <sign>}</li>
 *   <li>心跳: PING / PONG</li>
 * </ul>
 */
public class Communication {

    private static volatile Socket socket;
    private static volatile BufferedWriter writer;
    private static volatile BufferedReader reader;
    private static final Object WRITE_LOCK = new Object();
    private static final ConcurrentHashMap<String, CompletableFuture<Integer>> PENDING_RESULTS = new ConcurrentHashMap<>();
    private static volatile boolean running = false;
    private static volatile long lastPongTime = 0;
    private static volatile boolean connected = false;

    private static void log(String message) {
        PluginMain.getInstance().getLogger().info("[Comm] " + message);
    }

    private static void logWarn(String message) {
        PluginMain.getInstance().getLogger().warn("[Comm] " + message);
    }

    public static boolean isConnected() {
        return connected && socket != null && !socket.isClosed();
    }

    public static void start() {
        running = true;
        new Thread(Communication::connectLoop, "Logyee-Velocity-Comm-Connect").start();
    }

    public static void stop() {
        running = false;
        closeSocket();
    }

    private static void connectLoop() {
        while (running) {
            if (connected) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
                continue;
            }
            try {
                Socket s = new Socket(Config.Host, Config.Port);
                synchronized (WRITE_LOCK) {
                    socket = s;
                    writer = new BufferedWriter(new OutputStreamWriter(s.getOutputStream()));
                    reader = new BufferedReader(new InputStreamReader(s.getInputStream()));
                }
                connected = true;
                lastPongTime = System.currentTimeMillis();
                log("Connected to Bukkit " + Config.Host + ":" + Config.Port);
                new Thread(Communication::readLoop, "Logyee-Velocity-Comm-Reader").start();
                PluginMain.runAsync(Communication::heartbeatLoop);
            } catch (IOException e) {
                logWarn("Connection failed: " + e.getMessage() + ", retrying in 5s...");
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ignored) {
                    break;
                }
            }
        }
    }

    private static void closeSocket() {
        connected = false;
        PENDING_RESULTS.forEach((key, future) -> future.complete(0));
        PENDING_RESULTS.clear();
        synchronized (WRITE_LOCK) {
            try {
                if (socket != null) socket.close();
            } catch (IOException ignored) {
            }
            socket = null;
            writer = null;
            reader = null;
        }
    }

    private static void readLoop() {
        while (running && connected) {
            try {
                BufferedReader localReader;
                synchronized (WRITE_LOCK) {
                    localReader = reader;
                }
                if (localReader == null) {
                    Thread.sleep(100);
                    continue;
                }
                String line = localReader.readLine();
                if (line == null) {
                    throw new IOException("Connection closed");
                }
                String[] parts = line.split(" ", -1);
                if (parts.length == 0) continue;
                if (!parts[0].equals("PONG")) {
                    log("Received: " + line);
                }
                switch (parts[0]) {
                    case "CONNECT_RESULT":
                        if (parts.length >= 3) {
                            try {
                                handleConnectResult(parts[1], Integer.parseInt(parts[2]));
                            } catch (NumberFormatException e) {
                                logWarn("Invalid CONNECT_RESULT port: " + parts[2]);
                            }
                        }
                        break;
                    case "PLAYER_LOGIN":
                        if (parts.length >= 2) {
                            log("Processing PLAYER_LOGIN for " + parts[1]);
                            Listeners.markLoggedIn(parts[1]);
                        }
                        break;
                    case "PLAYER_LOGOUT":
                        if (parts.length >= 2) {
                            log("Processing PLAYER_LOGOUT for " + parts[1]);
                            Listeners.markLoggedOut(parts[1]);
                        }
                        break;
                    case "PONG":
                        lastPongTime = System.currentTimeMillis();
                        break;
                    default:
                        break;
                }
            } catch (Exception e) {
                logWarn("ReadLoop error: " + e.getMessage());
                closeSocket();
                break;
            }
        }
    }

    private static void heartbeatLoop() {
        while (running && connected) {
            try {
                Thread.sleep(30000);
                if (!running || !connected) break;
                if (System.currentTimeMillis() - lastPongTime > 60000) {
                    logWarn("Heartbeat timeout, reconnecting...");
                    closeSocket();
                    break;
                }
                sendLineQuiet("PING " + System.currentTimeMillis());
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private static void handleConnectResult(String playerName, int result) {
        CompletableFuture<Integer> future = PENDING_RESULTS.remove(playerName);
        if (future != null) {
            log("Completing CONNECT_RESULT for " + playerName + " = " + result);
            future.complete(result);
        }
    }

    public static int sendConnectRequest(String playerName) {
        if (!isConnected()) {
            logWarn("sendConnectRequest: not connected for " + playerName);
            return 0;
        }
        log("Sending CONNECT for " + playerName);
        CompletableFuture<Integer> future = new CompletableFuture<>();
        PENDING_RESULTS.put(playerName, future);
        sendLine("CONNECT " + playerName);
        try {
            int result = future.get(5, TimeUnit.SECONDS);
            log("CONNECT result for " + playerName + " = " + result);
            return result;
        } catch (Exception e) {
            PENDING_RESULTS.remove(playerName);
            logWarn("CONNECT timeout for " + playerName);
            return 0;
        }
    }

    public static void sendKeepLoggedInRequest(String playerName) {
        if (!isConnected()) {
            return;
        }
        log("Sending KEEP_LOGGED_IN for " + playerName);
        String time = String.valueOf(System.currentTimeMillis());
        String sign = top.zhrhello.logyee.util.CommunicationAuth.encryption(playerName, time, Config.AuthKey);
        sendLine("KEEP_LOGGED_IN " + playerName + " " + time + " " + sign);
    }

    private static void sendLine(String line) {
        sendLine(line, true);
    }

    private static void sendLineQuiet(String line) {
        sendLine(line, false);
    }

    private static void sendLine(String line, boolean logMessage) {
        synchronized (WRITE_LOCK) {
            if (writer != null) {
                try {
                    writer.write(line);
                    writer.newLine();
                    writer.flush();
                    if (logMessage) {
                        log("Sent: " + line);
                    }
                } catch (IOException e) {
                    logWarn("Send failed: " + e.getMessage());
                    closeSocket();
                }
            }
        }
    }
}
