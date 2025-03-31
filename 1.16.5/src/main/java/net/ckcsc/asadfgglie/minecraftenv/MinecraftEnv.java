package net.ckcsc.asadfgglie.minecraftenv;
import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import net.minecraftforge.client.event.ScreenshotEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.server.command.ConfigCommand;
import org.apache.commons.lang3.tuple.Pair;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.network.FMLNetworkConstants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(MinecraftEnv.MOD_ID)
public class MinecraftEnv {
    public static final String MOD_ID = "minecraftenv";
    // Directly reference a log4j logger.
    public static final Logger LOGGER = LogManager.getLogger(MinecraftEnv.class);

    public MinecraftEnv() {
        // mark this mod is only work for physical client side
        ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.DISPLAYTEST,
                () -> Pair.of(() -> FMLNetworkConstants.IGNORESERVERONLY, (a, b) -> true));

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        LOGGER.info("Player joined in world: {}", event.getPlayer().getDisplayName());
    }

    @SubscribeEvent
    public void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        LOGGER.info("Player left in world: {}", event.getPlayer().getDisplayName());
        if (AgentServerCommand.getServer() != null) {
            AgentServerCommand.stopSocketServer();
        }
    }

    @SubscribeEvent
    public void onCommandRegister(RegisterCommandsEvent event) {
        AgentServerCommand.register(event.getDispatcher());

        ConfigCommand.register(event.getDispatcher());
    }
}
