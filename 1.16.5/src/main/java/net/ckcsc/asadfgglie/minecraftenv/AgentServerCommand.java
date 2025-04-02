package net.ckcsc.asadfgglie.minecraftenv;

import com.google.gson.Gson;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.ckcsc.asadfgglie.minecraftenv.exception.VerifyException;
import net.ckcsc.asadfgglie.minecraftenv.util.SocketInputStream;
import net.ckcsc.asadfgglie.minecraftenv.util.SocketOutputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.NativeImage;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.text.TranslationTextComponent;

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
        // TODO: Re-implement by TCP socket for low network delay

        if (server == null) {
            try {
                server = new ServerSocketThread(port, hostName, new ServerSocketThread.EventHandler() {
                    @Override
                    public void onPing(Socket socket) throws IOException {
                        try (SocketOutputStream out = new SocketOutputStream(socket)) {
                            SocketInputStream in = new SocketInputStream(socket);
                            String data = in.readString();
                            MinecraftEnv.LOGGER.info("Client {} ping: {}, ip: {}", socket.getInetAddress().hashCode(), data, socket.getRemoteSocketAddress().toString());
                            source.sendSuccess(new TranslationTextComponent("command.minecraftenv.ping", socket.getInetAddress().hashCode(), data, socket.getRemoteSocketAddress().toString()), true);

                            out.writeString("pong");
                        }
                    }

                    @Override
                    public void onRender(Socket socket) throws IOException, ExecutionException, InterruptedException {
                        try (SocketOutputStream out = new SocketOutputStream(socket)) {
                            MinecraftEnv.LOGGER.info("Client {} render, ip: {}", socket.getInetAddress().hashCode(), socket.getRemoteSocketAddress().toString());
                            out.writeString(getResponse().toJson());
                        }
                    }

                    @Override
                    public void onStep(Socket socket) throws IOException, ExecutionException, InterruptedException, VerifyException {
                        try (SocketOutputStream out = new SocketOutputStream(socket)) {
                            SocketInputStream in = new SocketInputStream(socket);
                            MinecraftEnv.LOGGER.info("Client {} step: {}, ip: {}",
                                    socket.getInetAddress().hashCode(),
                                    new Gson().toJson(AgentServerSchema.StepRequest.fromSocketDataInputStream(in)),
                                    socket.getRemoteSocketAddress().toString());
                            out.writeString(getResponse().toJson());
                        }
                    }

                    @Override
                    public void onClose(Socket socket) throws IOException {
                        socket.close();
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

    public static AgentServerSchema.Response getResponse() throws ExecutionException, InterruptedException {
        return new AgentServerSchema.Response(getObservation(), getInfo());
    }

    public static Map<String, Object> getInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("timestamp", date.format(new Date()));
        return info;
    }

    public static int[][][] getObservation() throws ExecutionException, InterruptedException {
        CompletableFuture<int[][][]> future = new CompletableFuture<>();
        Minecraft.getInstance().executeBlocking(() -> {
            int[][][] image;
            try (NativeImage nativeImage = ScreenShotHelper.takeScreenshot(Minecraft.getInstance().getWindow().getWidth(), Minecraft.getInstance().getWindow().getHeight(), Minecraft.getInstance().getMainRenderTarget())) {
                image = new int[nativeImage.getHeight()][nativeImage.getWidth()][3];
                for (int y = 0; y < nativeImage.getHeight(); y++) {
                    for (int x = 0; x < nativeImage.getWidth(); x++) {
                        int rgba = nativeImage.getPixelRGBA(x, y);
                        image[y][x][0] = NativeImage.getR(rgba);
                        image[y][x][1] = NativeImage.getG(rgba);
                        image[y][x][2] = NativeImage.getB(rgba);
                    }
                }
            }
            future.complete(image);
        });
        return future.get();
    }
}