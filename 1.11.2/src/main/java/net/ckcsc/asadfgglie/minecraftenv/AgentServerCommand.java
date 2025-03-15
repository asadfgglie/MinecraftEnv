package net.ckcsc.asadfgglie.minecraftenv;

import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.SocketIOServer;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AgentServerCommand extends CommandBase {
    private static final Configuration CONFIGURATION = new Configuration();
    private static SocketIOServer server;

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

            });

            server.addEventListener("step", AgentServerSchema.StepRequest.class, (client, data, ackSender) -> {

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

}
