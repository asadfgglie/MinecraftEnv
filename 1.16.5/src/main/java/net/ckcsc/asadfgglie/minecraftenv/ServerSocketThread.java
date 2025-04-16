package net.ckcsc.asadfgglie.minecraftenv;

import net.ckcsc.asadfgglie.minecraftenv.exception.IllegalSocketTitle;
import net.ckcsc.asadfgglie.minecraftenv.util.SocketInputStream;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

public class ServerSocketThread extends ServerSocket {
    private final AtomicBoolean state = new AtomicBoolean(false);
    private final EventHandler eventHandler;
    private Thread thread;

    public static final String SOCKET_TITLE = "<" + MinecraftEnv.FULL_NAME + ">";
    public static final int CHECK_NUMBER = SOCKET_TITLE.getBytes(StandardCharsets.UTF_8).length;

    public ServerSocketThread(int port, int backlog, InetAddress bindAddress, EventHandler eventHandler) throws IOException {
        super(port, backlog, bindAddress);
        this.eventHandler = eventHandler;
    }

    public ServerSocketThread(int port, String bindAddress, EventHandler eventHandler) throws IOException {
        this(port, 50, InetAddress.getByName(bindAddress), eventHandler);
    }

    public void start() {
        this.state.set(true);
        this.thread = new Thread(this::run, "ServerSocketThread");
        this.thread.start();
    }

    public void stop() {
        this.state.set(false);
        try {
            this.close();
        } catch (IOException ignored) {}
        this.thread = null;
    }

    private void run() {
        MinecraftEnv.LOGGER.info("MinecraftEnv server thread started with socket title: `{}`, check number: {}", SOCKET_TITLE, CHECK_NUMBER);
        while (state.get() && !this.isClosed()) {
            try {
                final Socket socket = this.accept();
                socket.setTcpNoDelay(true);
                new Thread(() -> {
                    try {
                        checkSocket(socket);
                    }
                    catch (IOException e) {
                        MinecraftEnv.LOGGER.error("MinecraftEnv got error when processing event", e);
                        try {
                            socket.close();
                        } catch (IOException ignored) {}
                        return;
                    }
                    catch (IllegalSocketTitle e) {
                        MinecraftEnv.LOGGER.error("MinecraftEnv got illegal socket title", e);
                        try {
                            socket.close();
                        } catch (IOException ignored) {}
                        return;
                    }

                    while (state.get() && !socket.isClosed()) {
                        String event;
                        try {
                            event = getEvent(socket);
                        }
                        catch (IOException ignored) {
                            try {
                                socket.close();
                            } catch (IOException ignored1) {}
                            return;
                        }
                        try {
                            eventHandler.getClass().getMethod("on" + event, Socket.class).invoke(eventHandler, socket);
                        }
                        catch (NoSuchMethodException | IllegalAccessException | IllegalArgumentException ignored) {}
                        catch (InvocationTargetException e) {
                            MinecraftEnv.LOGGER.error("MinecraftEnv got error when processing event", e.getTargetException());
                        }
                    }

                    MinecraftEnv.LOGGER.info("MinecraftEnv server SocketHandler stopped since it's closed");
                }, "SocketHandler").start();
            }
            catch (IOException e) {
                MinecraftEnv.LOGGER.fatal("MinecraftEnv can't build connection since I/O error!", e);
            }
        }
    }

    /**
     * Check Socket title format.
     * @exception IOException  if an I/O error occurs when creating the
     *      input stream, the socket is closed, the socket is
     *      ot connected, or the socket input has been shutdown
     *      using {@link Socket#shutdownInput()}
     * @exception IllegalSocketTitle if Socket title doesn't match title format.
 *          Detail about format please read {@link IllegalSocketTitle}
     */
    private void checkSocket(Socket socket) throws IOException, IllegalSocketTitle {
        SocketInputStream in = new SocketInputStream(socket);
        int check_num = in.readInt();

        if (check_num != CHECK_NUMBER) {
            throw new IllegalSocketTitle(check_num);
        }

        byte[] title = new byte[check_num];
        in.readFully(title);
        String title_str = new String(title, StandardCharsets.UTF_8);
        if (!title_str.startsWith("<" + MinecraftEnv.MOD_ID + ":") || !title_str.endsWith(">")) {
            throw new IllegalSocketTitle(title_str);
        }
    }

    /**
     * Get Event name from socket and make sure first charactor is uppercase, and other charactor is lowercase.
     * <p>
     * Example:
     * <blockquote><pre>
     *     "ping" -> "Ping"
     *     "rENDER" -> "Render"
     * </pre></blockquote>
     */
    private String getEvent(Socket socket) throws IOException {
        SocketInputStream in = new SocketInputStream(socket);
        String event = in.readString();
        return (!event.isEmpty()) ? String.valueOf(event.charAt(0)).toUpperCase() + event.substring(1).toLowerCase() : "";
    }

    public interface EventHandler {
        void onPing(Socket socket) throws Exception;
        void onRender(Socket socket) throws Exception;
        void onStep(Socket socket) throws Exception;
        void onClose(Socket socket) throws Exception;
        void onSetup(Socket socket) throws Exception;
    }
}