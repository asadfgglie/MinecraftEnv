package net.ckcsc.asadfgglie.minecraftenv;

import com.google.gson.Gson;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
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
                            // TODO: decode obs byt array to rgb array in python
                            MinecraftEnv.LOGGER.info("Client {} render, ip: {}", socket.getInetAddress().hashCode(), socket.getRemoteSocketAddress().toString());
                            String info = new Gson().toJson(getInfo());
                            out.writeString(info);
                            byte[] obs = getObservation();
                            out.writeInt(obs.length);
                            out.write(obs);
                            out.flush();
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
//                        try {
//                            AgentServerSchema.StepRequest request = AgentServerSchema.StepRequest.fromSocketDataInputStream(in);
//                            MinecraftEnv.LOGGER.info("Client {} step: {}, ip: {}", socket.getInetAddress().hashCode(), new Gson().toJson(request), socket.getRemoteSocketAddress().toString());
//                            out.writeString(getResponse().toJson());
//                        }
//                        catch (ExecutionException | InterruptedException | VerifyException e) {
//                            MinecraftEnv.LOGGER.error("Client {} step, ip: {}", socket.getInetAddress().hashCode(), socket.getRemoteSocketAddress().toString(), e);
//                            out.writeString(e.getMessage());
//                        }
                        out.flush();
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

//    public static AgentServerSchema.Response getResponse() throws ExecutionException, InterruptedException {
//        return new AgentServerSchema.Response(getObservation(), getInfo());
//    }

    public static Map<String, Object> getInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("timestamp", date.format(new Date()));
        return info;
    }

    public static byte[] getObservation() throws ExecutionException, InterruptedException {
        CompletableFuture<byte[]> future = new CompletableFuture<>();
        Minecraft.getInstance().executeBlocking(() -> {
            try (NativeImage nativeImage = ScreenShotHelper.takeScreenshot(Minecraft.getInstance().getWindow().getWidth(), Minecraft.getInstance().getWindow().getHeight(), Minecraft.getInstance().getMainRenderTarget())) {
                future.complete(nativeImage.asByteArray());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        return future.get();
    }
}