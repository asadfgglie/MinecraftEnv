package net.ckcsc.asadfgglie.minecraftenv;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;

// mark this mod is only work for physical client side
@Mod(modid = MinecraftEnv.MOD_ID, version = MinecraftEnv.VERSION, name = MinecraftEnv.NAME, clientSideOnly = true)
public class MinecraftEnv
{
    public static final String MOD_ID = "minecraftenv";
    public static final String VERSION = "1.0-SNAPSHOT";
    public static final String NAME = "MinecraftEnv";

    static final Logger LOGGER = LogManager.getLogger(MinecraftEnv.class);

    private volatile static EntityPlayer agent;

    @Instance(MOD_ID)
    public static MinecraftEnv instance;

    public MinecraftEnv() {
        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);
    }
    
    @SubscribeEvent
    public void onPlayerJoin(PlayerLoggedInEvent event) {
        LOGGER.info("Player joined in world: {}", event.player.getDisplayName().getFormattedText());
        agent = event.player;
    }

    @SubscribeEvent
    public void onPlayerLeave(PlayerLoggedOutEvent event) {
        LOGGER.info("Player left in world: {}", event.player.getDisplayName().getFormattedText());
        if (AgentServerCommand.getServer() != null) {
            AgentServerCommand.stopSocketServer();
        }
        agent = null;
    }

    @EventHandler
    public void onCommandRegister(FMLServerStartingEvent event) {
        event.registerServerCommand(new AgentServerCommand());
    }

    public static EntityPlayer getAgent() {
        return agent;
    }
}
