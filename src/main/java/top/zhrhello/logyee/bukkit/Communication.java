package top.zhrhello.logyee.bukkit;

import top.zhrhello.logyee.bukkit.database.Cache;
import top.zhrhello.logyee.bukkit.object.LoginPlayer;
import top.zhrhello.logyee.bukkit.object.LoginPlayerHelper;
import top.zhrhello.logyee.util.CommunicationAuth;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 * bukkit 与 bc 的长连接通讯交流
 */
public class Communication {
    private static ServerSocket serverSocket;
    private static volatile Socket clientSocket;
    private static volatile BufferedWriter clientWriter;
    private static final Object WRITE_LOCK = new Object();

    private static void log(String message) {
        Logyee.instance.getLogger().info("[Comm] " + message);
    }

    private static void logWarn(String message) {
        Logyee.instance.getLogger().warning("[Comm] " + message);
    }

    /**
     * 异步关闭 socket server
     */
    public static void socketServerStopAsync() {
        Logyee.instance.runTaskAsync(Communication::socketServerStop);
    }

    public static void socketServerStop() {
        try {
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (IOException ignored) {
        }
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 异步启动 socket server 监听bc端发来的请求
     */
    public static void socketServerStartAsync() {
        Logyee.instance.runTaskAsync(Communication::socketServerStart);
    }

    /**
     * 启动 socket server 监听bc端发来的长连接
     */
    private static void socketServerStart() {
        try {
            InetAddress inetAddress = InetAddress.getByName(Config.BungeeCord.Host);
            serverSocket = new ServerSocket(Integer.parseInt(Config.BungeeCord.Port), 50, inetAddress);
            log("ServerSocket started on " + Config.BungeeCord.Host + ":" + Config.BungeeCord.Port);
            while (!serverSocket.isClosed()) {
                Socket socket;
                try {
                    socket = serverSocket.accept();
                } catch (IOException e) {
                    break;
                }
                log("Accepted connection from " + socket.getRemoteSocketAddress());
                synchronized (WRITE_LOCK) {
                    if (clientSocket != null && !clientSocket.isClosed()) {
                        try {
                            clientSocket.close();
                        } catch (IOException ignored) {
                        }
                    }
                    clientSocket = socket;
                    try {
                        clientWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                    } catch (IOException e) {
                        logWarn("Failed to get output stream: " + e.getMessage());
                        try {
                            socket.close();
                        } catch (IOException ignored) {
                        }
                        continue;
                    }
                }
                new Thread(new ClientReader(socket), "Logyee-Comm-Reader").start();
            }
        } catch (UnknownHostException e) {
            logWarn("Unable to resolve address: " + Config.BungeeCord.Host);
            e.printStackTrace();
        } catch (IOException e) {
            logWarn("ServerSocket error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static class ClientReader implements Runnable {
        private final Socket socket;

        ClientReader(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(" ", -1);
                    if (parts.length == 0) continue;
                    if (!parts[0].equals("PING")) {
                        log("Received: " + line);
                    }
                    String type = parts[0];
                    switch (type) {
                        case "CONNECT":
                            if (parts.length >= 2) {
                                handleConnectRequest(parts[1]);
                            }
                            break;
                        case "KEEP_LOGGED_IN":
                            if (parts.length >= 4) {
                                handleKeepLoggedInRequest(parts[1], parts[2], parts[3]);
                            }
                            break;
                        case "PING":
                            if (parts.length >= 2) {
                                sendLineQuiet("PONG " + parts[1]);
                            }
                            break;
                        default:
                            break;
                    }
                }
            } catch (IOException e) {
                logWarn("ClientReader IOException: " + e.getMessage());
            } finally {
                synchronized (WRITE_LOCK) {
                    if (clientSocket == socket) {
                        clientSocket = null;
                        clientWriter = null;
                    }
                }
                logWarn("Connection closed");
            }
        }
    }

    private static void handleKeepLoggedInRequest(String playerName, String time, String sign) {
        log("Processing KEEP_LOGGED_IN for " + playerName);
        if (CommunicationAuth.encryption(playerName, time, Config.BungeeCord.AuthKey).equals(sign)) {
            log("KEEP_LOGGED_IN auth success for " + playerName);
            Bukkit.getScheduler().runTask(Logyee.instance, () -> {
                LoginPlayer lp = Cache.getIgnoreCase(playerName);
                if (lp != null) {
                    LoginPlayerHelper.add(lp, false);
                    log("KEEP_LOGGED_IN added " + playerName + " to login set (no notify)");
                    Player player = Bukkit.getPlayerExact(playerName);
                    if (player != null) {
                        player.updateInventory();
                    }
                } else {
                    logWarn("KEEP_LOGGED_IN player " + playerName + " not found in cache");
                }
            });
        } else {
            logWarn("KEEP_LOGGED_IN auth failed for " + playerName);
        }
    }

    private static void handleConnectRequest(String playerName) {
        log("Processing CONNECT for " + playerName);
        Bukkit.getScheduler().runTask(Logyee.instance, () -> {
            boolean result = LoginPlayerHelper.isLogin(playerName);
            log("CONNECT result for " + playerName + " = " + result);
            sendLine("CONNECT_RESULT " + playerName + " " + (result ? 1 : 0));
        });
    }

    private static void sendLine(String line) {
        sendLine(line, true);
    }

    private static void sendLineQuiet(String line) {
        sendLine(line, false);
    }

    private static void sendLine(String line, boolean logMessage) {
        synchronized (WRITE_LOCK) {
            if (clientWriter != null) {
                try {
                    clientWriter.write(line);
                    clientWriter.newLine();
                    clientWriter.flush();
                    if (logMessage) {
                        log("Sent: " + line);
                    }
                } catch (IOException e) {
                    logWarn("sendLine failed: " + e.getMessage());
                    try {
                        if (clientSocket != null) {
                            clientSocket.close();
                        }
                    } catch (IOException ignored) {
                    }
                }
            } else {
                logWarn("sendLine failed: clientWriter is null");
            }
        }
    }

    public static void notifyPlayerLogin(String playerName) {
        if (!Config.BungeeCord.Enable) {
            return;
        }
        log("notifyPlayerLogin: " + playerName);
        Logyee.instance.runTaskAsync(() -> sendLine("PLAYER_LOGIN " + playerName));
    }

    public static void notifyPlayerLogout(String playerName) {
        if (!Config.BungeeCord.Enable) {
            return;
        }
        log("notifyPlayerLogout: " + playerName);
        Logyee.instance.runTaskAsync(() -> sendLine("PLAYER_LOGOUT " + playerName));
    }
}