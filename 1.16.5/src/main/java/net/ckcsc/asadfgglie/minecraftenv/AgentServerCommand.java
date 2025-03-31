package net.ckcsc.asadfgglie.minecraftenv;

import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.listener.ExceptionListener;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.NativeImage;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.text.TranslationTextComponent;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class AgentServerCommand {
    private static final SocketConfig CONFIGURATION = new SocketConfig();
    private static SocketIOServer server;
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
        CONFIGURATION.setPort(port);
        CONFIGURATION.setHostname(hostName);

        if (server == null) {
            server = new SocketIOServer(CONFIGURATION);

            server.addEventListener("ping", String.class, (client, data, ackSender) -> {
                MinecraftEnv.LOGGER.info("Client {} ping: {}, ip: {}", client.getSessionId(), data, client.getRemoteAddress());
                source.sendSuccess(new TranslationTextComponent("command.minecraftenv.ping", client.getSessionId(), data, client.getRemoteAddress()), true);
                ackSender.sendAckData("pong");
            });

            server.addEventListener("render", Object.class, (client, data, ackSender) -> {
                MinecraftEnv.LOGGER.info("Client {} render: {}, ip: {}", client.getSessionId(), data, client.getRemoteAddress());
                ackSender.sendAckData(getResponse());
            });

            server.addEventListener("step", AgentServerSchema.StepRequest.class, (client, data, ackSender) -> {
                MinecraftEnv.LOGGER.info("Client {} step: {}, ip: {}", client.getSessionId(), data, client.getRemoteAddress());
                ackSender.sendAckData(getResponse());
            });

            server.start();

            Configuration cfg = server.getConfiguration();
            source.sendSuccess(new TranslationTextComponent("command.minecraftenv.start_agent_server", cfg.getHostname(), cfg.getPort()), true);
            return 0;
        }
        else {
            Configuration cfg = server.getConfiguration();
            if (!cfg.getHostname().equals(hostName) || cfg.getPort() != port) {
                server.stop();
                server = null;
                return startSocketServer(source, port, hostName);
            }
            else {
                source.sendSuccess(new TranslationTextComponent("command.minecraftenv.already_start_agent_server", cfg.getHostname(), cfg.getPort()), true);
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

    public static SocketIOServer getServer() {
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

class SocketConfig extends Configuration {
    public SocketConfig() {
        super();
        this.setExceptionListener(new ExceptionListener() {
            @Override
            public void onEventException(Exception e, List<Object> args, SocketIOClient client) {
                MinecraftEnv.LOGGER.error(e.getMessage(), e);
            }

            @Override
            public void onDisconnectException(Exception e, SocketIOClient client) {
                MinecraftEnv.LOGGER.error(e.getMessage(), e);
            }

            @Override
            public void onConnectException(Exception e, SocketIOClient client) {
                MinecraftEnv.LOGGER.error(e.getMessage(), e);
            }

            @Override
            public void onPingException(Exception e, SocketIOClient client) {
                MinecraftEnv.LOGGER.error(e.getMessage(), e);
            }

            @Override
            public void onPongException(Exception e, SocketIOClient client) {
                MinecraftEnv.LOGGER.error(e.getMessage(), e);
            }

            @Override
            public boolean exceptionCaught(ChannelHandlerContext ctx, Throwable e) {
                MinecraftEnv.LOGGER.error(e.getMessage(), e);
                return true;
            }

            @Override
            public void onAuthException(Throwable e, SocketIOClient client) {
                MinecraftEnv.LOGGER.error(e.getMessage(), e);
            }
        });
    }
}