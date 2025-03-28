package net.ckcsc.asadfgglie.minecraftenv;

import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.listener.ExceptionListener;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;

import javax.annotation.Nullable;
import java.text.SimpleDateFormat;
import java.util.*;

public class AgentServerCommand extends CommandBase {
    private static final SocketConfig CONFIGURATION = new SocketConfig();
    private static SocketIOServer server;
    private static final SimpleDateFormat date = new SimpleDateFormat("HH:mm:ss");

    public AgentServerCommand() {}

    @Override
    public String getName() {
        return MinecraftEnv.MOD_ID;
    }

    @Override
    public String getUsage(ICommandSender iCommandSender) {
        return "command.minecraftenv.usage";
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos blockPos){
        MinecraftEnv.LOGGER.info(Arrays.toString(args));
        return args.length == 1 ? Arrays.asList("start", "stop") : Collections.emptyList();
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 1) {
            throw new CommandException("command.minecraftenv.usage");
        }
        if (args[0].equals("start")) {
            if (args.length == 1) {
                startSocketServer(sender, 10000, "localhost");
            }
            else if (args.length == 2) {
                startSocketServer(sender, parseInt(args[1], 0, 65535), "localhost");
            }
            else if (args.length == 3) {
                startSocketServer(sender, parseInt(args[1], 0, 65535), args[2]);
            }
            else {
                throw new CommandException("command.minecraftenv.usage");
            }
        }
        else if (args[0].equals("stop")) {
            if (stopSocketServer()) {
                sender.sendMessage(new TextComponentTranslation("command.minecraftenv.stop_agent_server"));
            }
            else {
                sender.sendMessage(new TextComponentTranslation("command.minecraftenv.agent_server_not_started"));
            }
        }
        else {
            throw new CommandException("command.minecraftenv.usage");
        }
    }

    private static void startSocketServer(ICommandSender source, int port, String hostName) {
        // TODO: 用TCP協議重寫網路通訊
        CONFIGURATION.setPort(port);
        CONFIGURATION.setHostname(hostName);

        if (server == null) {
            server = new SocketIOServer(CONFIGURATION);

            server.addEventListener("ping", String.class, (client, data, ackSender) -> {
                MinecraftEnv.LOGGER.info("Client {} ping: {}, ip: {}", client.getSessionId(), data, client.getRemoteAddress());
                source.sendMessage(new TextComponentTranslation("command.minecraftenv.ping", client.getSessionId(), data, client.getRemoteAddress()));
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
            source.sendMessage(new TextComponentTranslation("command.minecraftenv.start_agent_server", cfg.getHostname(), cfg.getPort()));
        }
        else {
            Configuration cfg = server.getConfiguration();
            if (!cfg.getHostname().equals(hostName) || cfg.getPort() != port) {
                server.stop();
                server = null;
                startSocketServer(source, port, hostName);
            }
            else {
                source.sendMessage(new TextComponentTranslation("command.minecraftenv.already_start_agent_server", cfg.getHostname(), cfg.getPort()));
            }
        }
//        TextureRender.setIsCustomRenderingEnabled(true);
    }

    public static boolean stopSocketServer() {
        if (server != null) {
            server.stop();
            server = null;
//            TextureRender.setIsCustomRenderingEnabled(false);

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

    public static AgentServerSchema.Response getResponse() {
        return new AgentServerSchema.Response(getObservation(), getInfo());
    }

    public static Map<String, Object> getInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("timestamp", date.format(new Date()));
        return info;
    }

    public static short[][][] getObservation(){
        synchronized (MinecraftEnv.class) {
            short[][][] observation = new short[MinecraftEnv.height][MinecraftEnv.width][3];

            for (int y = 0; y < MinecraftEnv.height; y++) {
                for (int x = 0; x < MinecraftEnv.width; x++) {
                    int index = (y * MinecraftEnv.width + x) * 3; // RGB 格式索引計算
                    observation[MinecraftEnv.height - 1 - y][x][0] = (short) (MinecraftEnv.current_frame.get(index) & 0xFF); // R
                    observation[MinecraftEnv.height - 1 - y][x][1] = (short) (MinecraftEnv.current_frame.get(index + 1) & 0xFF); // G
                    observation[MinecraftEnv.height - 1 - y][x][2] = (short) (MinecraftEnv.current_frame.get(index + 2) & 0xFF); // B
                }
            }

            return observation;
        }
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