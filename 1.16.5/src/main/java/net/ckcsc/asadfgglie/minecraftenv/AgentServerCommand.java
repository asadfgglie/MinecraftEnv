package net.ckcsc.asadfgglie.minecraftenv;

import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.SocketIOServer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.util.text.TranslationTextComponent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AgentServerCommand {
    private static final Configuration CONFIGURATION = new Configuration();
    private static SocketIOServer server;

    private static final Logger LOGGER = LogManager.getLogger(AgentServerCommand.class);

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
                .executes(sourceCommandContext -> stopSocketServer())));
    }

    private static int startSocketServer(CommandSource source, int port, String hostName) {
        CONFIGURATION.setPort(port);
        CONFIGURATION.setHostname(hostName);

        if (server == null) {
            server = new SocketIOServer(CONFIGURATION);

            server.addEventListener("ping", String.class, (client, data, ackSender) -> {
                LOGGER.info("Client {} ping: {}, ip: {}", client.getSessionId(), data, client.getRemoteAddress());
                source.sendSuccess(new TranslationTextComponent("command.minecraftenv.ping", client.getSessionId(), data, client.getRemoteAddress()), true);
                ackSender.sendAckData("pong");
            });

            server.addEventListener("render", Object.class, (client, data, ackSender) -> {
//                Minecraft
            });

            server.addEventListener("step", AgentServerSchema.StepRequest.class, (client, data, ackSender) -> {

            });

            server.start();
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

    public static int stopSocketServer() {
        if (server != null) {
            server.stop();
            server = null;

            return 0;
        }
        else {
            LOGGER.warn("Server not started");
            return -1;
        }
    }

    public static SocketIOServer getServer() {
        return server;
    }
}
