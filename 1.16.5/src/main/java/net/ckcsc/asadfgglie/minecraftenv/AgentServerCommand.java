package net.ckcsc.asadfgglie.minecraftenv;

import com.google.gson.Gson;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.ckcsc.asadfgglie.minecraftenv.exception.VerifyException;
import net.ckcsc.asadfgglie.minecraftenv.util.SocketInputStream;
import net.ckcsc.asadfgglie.minecraftenv.util.SocketOutputStream;
import net.minecraft.client.GameSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.NativeImage;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.text.TranslationTextComponent;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class AgentServerCommand {
    private static ServerSocketThread server;
    private static final SimpleDateFormat date = new SimpleDateFormat("HH:mm:ss");

    private static int width = 640;
    private static int height = 360;

    private AgentServerCommand() {}

    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(Commands.literal(MinecraftEnv.MOD_ID)
            .then(Commands.literal("start")
                .executes(source ->
                    AgentServerCommand.startSocketServer(source.getSource(), 10000, "localhost"))
            .then(Commands.argument("port", IntegerArgumentType.integer(0, 65535))
                .executes(source ->
                    AgentServerCommand.startSocketServer(source.getSource(), source.getArgument("port", Integer.class), "localhost"))
            .then(Commands.argument("host", StringArgumentType.word())
                .executes(source ->
                    AgentServerCommand.startSocketServer(source.getSource(), source.getArgument("port", Integer.class), source.getArgument("host", String.class)))))));

        dispatcher.register(Commands.literal(MinecraftEnv.MOD_ID)
            .then(Commands.literal("stop")
                .executes(sourceCommandContext -> {
                    if (stopSocketServer()) {
                        sourceCommandContext.getSource().sendSuccess(new TranslationTextComponent("command.minecraftenv.stop_agent_server"), true);
                    }
                    else {
                        sourceCommandContext.getSource().sendFailure(new TranslationTextComponent("command.minecraftenv.agent_server_not_started"));
                    }
                    return 0;
                })));
    }

    private static int startSocketServer(CommandSource source, int port, String hostName) {
        if (server == null) {
            try {
                server = new ServerSocketThread(port, hostName, new ServerSocketThread.EventHandler() {
                    @Override
                    public void onPing(Socket socket) throws IOException {
                        SocketOutputStream out = new SocketOutputStream(socket);
                        SocketInputStream in = new SocketInputStream(socket);
                        String data = null;
                        try {
                            data = in.readString();
                        }
                        catch (IOException ignored) {}
                        MinecraftEnv.LOGGER.info("Client {} ping: {}, ip: {}", socket.getInetAddress().hashCode(), data, socket.getRemoteSocketAddress().toString());
                        source.sendSuccess(new TranslationTextComponent("command.minecraftenv.ping", socket.getInetAddress().hashCode(), data, socket.getRemoteSocketAddress().toString()), true);

                        out.writeString("pong");
                        out.flush();
                    }

                    @Override
                    public void onRender(Socket socket) throws IOException {
                        SocketOutputStream out = new SocketOutputStream(socket);
                        try {
                            MinecraftEnv.LOGGER.info("Client {} render, ip: {}", socket.getInetAddress().hashCode(), socket.getRemoteSocketAddress().toString());
                            out.writeResponse(getResponse());
                        }
                        catch (ExecutionException | InterruptedException e){
                            MinecraftEnv.LOGGER.error("Client {} render, ip: {}", socket.getInetAddress().hashCode(), socket.getRemoteSocketAddress().toString(), e);
                            out.writeString(e.getMessage());
                        }
                        out.flush();
                    }

                    @Override
                    public void onStep(Socket socket) throws IOException {
                        SocketOutputStream out = new SocketOutputStream(socket);
                        SocketInputStream in = new SocketInputStream(socket);
                        try {
                            AgentServerSchema.StepRequest request = AgentServerSchema.Request.fromSocketDataInputStream(in, AgentServerSchema.StepRequest.class);
                            MinecraftEnv.LOGGER.info("Client {} step: {}, ip: {}", socket.getInetAddress().hashCode(), new Gson().toJson(request), socket.getRemoteSocketAddress().toString());
                            out.writeResponse(getResponse());
                        }
                        catch (ExecutionException | InterruptedException | VerifyException e) {
                            MinecraftEnv.LOGGER.error("Client {} step, ip: {}", socket.getInetAddress().hashCode(), socket.getRemoteSocketAddress().toString(), e);
                            out.writeString(e.getMessage());
                        }
                        out.flush();
                    }

                    @Override
                    public void onClose(Socket socket) throws IOException {
                        socket.close();
                    }

                    @Override
                    public void onSetup(Socket socket) throws IOException {
                        SocketOutputStream out = new SocketOutputStream(socket);
                        SocketInputStream in = new SocketInputStream(socket);
                        try {
                            AgentServerSchema.SetupRequest request = AgentServerSchema.Request.fromSocketDataInputStream(in, AgentServerSchema.SetupRequest.class);
                            MinecraftEnv.LOGGER.info("Client {} setup: {}, ip: {}", socket.getInetAddress().hashCode(), new Gson().toJson(request), socket.getRemoteSocketAddress().toString());

                            GameSettings settings = Minecraft.getInstance().options;
                            settings.gamma = request.gamma;
                            settings.fov = request.fov;
                            Minecraft.getInstance().getWindow().setGuiScale(request.guiScale);

                            resize(request.width, request.height);

                            out.writeMap(getInfo());
                        }
                        catch (VerifyException e) {
                            MinecraftEnv.LOGGER.error("Client {} setup, ip: {}", socket.getInetAddress().hashCode(), socket.getRemoteSocketAddress().toString(), e);
                            out.writeString(e.getMessage());
                        }
                        out.flush();
                    }
                });
            } catch (IOException e) {
                server = null;
                MinecraftEnv.LOGGER.error("Error starting TCPServer", e);
                source.sendFailure(new TranslationTextComponent("command.minecraftenv.start_agent_server_failed", hostName, port, e.getMessage()));
                return 1;
            }

            server.start();
            source.sendSuccess(new TranslationTextComponent("command.minecraftenv.start_agent_server", hostName, port), true);
            return 0;
        }
        else {
            if (server.getLocalPort() != port || server.getInetAddress() != null || server.getInetAddress().getHostName().equals(hostName)) {
                try {
                    server.close();
                } catch (IOException ignored) {}
                server = null;
                return startSocketServer(source, port, hostName);
            }
            else {
                source.sendSuccess(new TranslationTextComponent("command.minecraftenv.already_start_agent_server", hostName, port), true);
                return 0;
            }
        }
    }

    public static boolean stopSocketServer() {
        if (server != null) {
            server.stop();
            server = null;

            return true;
        }
        else {
            MinecraftEnv.LOGGER.warn("Server not started");
            return false;
        }
    }

    public static ServerSocketThread getServer() {
        return server;
    }

    public static Map<String, Object> getInfo(NativeImage nativeImage) {
        Map<String, Object> info = getInfo();

        HashMap<String, Object> pov = new HashMap<>();
        pov.put("height", nativeImage.getHeight());
        pov.put("width", nativeImage.getWidth());
        info.put("pov", pov);

        return info;
    }

    public static Map<String, Object> getInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("timestamp", date.format(new Date()));
        info.put("width", width);
        info.put("height", height);
        GameSettings settings = Minecraft.getInstance().options;
        info.put("fov", settings.fov);
        info.put("guiScale", Minecraft.getInstance().getWindow().getGuiScale());
        info.put("gamma", settings.gamma);

        return info;
    }

    public static AgentServerSchema.Response getResponse() throws ExecutionException, InterruptedException {
        CompletableFuture<AgentServerSchema.Response> future = new CompletableFuture<>();
        resize();
        Minecraft.getInstance().executeBlocking(() -> {
            try (NativeImage nativeImage = ScreenShotHelper.takeScreenshot(Minecraft.getInstance().getWindow().getWidth(), Minecraft.getInstance().getWindow().getHeight(), Minecraft.getInstance().getMainRenderTarget())) {
                byte[] bytes = new byte[nativeImage.getWidth() * nativeImage.getHeight() * 4];
                int[] pixel = nativeImage.makePixelArray();
                for (int i = 0; i < nativeImage.getWidth() * nativeImage.getHeight(); i++) {
                    bytes[i * 4] = (byte) ((pixel[i] >>> 24) & 0xFF);
                    bytes[i * 4 + 1] = (byte) ((pixel[i] >>> 16) & 0xFF);
                    bytes[i * 4 + 2] = (byte) ((pixel[i] >>> 8) & 0xFF);
                    bytes[i * 4 + 3] = (byte) (pixel[i] & 0xFF);
                }
                future.complete(new AgentServerSchema.Response(bytes, getInfo(nativeImage)));
            }
        });
        return future.get();
    }

    public static void resize() {
        resize(width, height);
    }

    public static void resize(int width, int height) {
        AgentServerCommand.width = width;
        AgentServerCommand.height = height;
        Minecraft.getInstance().executeBlocking(() -> {
            if (Minecraft.getInstance().getWindow().getWidth() != AgentServerCommand.width || Minecraft.getInstance().getWindow().getHeight() != AgentServerCommand.height) {
                GLFW.glfwSetWindowSize(Minecraft.getInstance().getWindow().getWindow(), AgentServerCommand.width, AgentServerCommand.height);
            }
        });
    }
}