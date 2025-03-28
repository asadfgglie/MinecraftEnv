package net.ckcsc.asadfgglie.minecraftenv;

import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.Display;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;

// mark this mod is only work for physical client side
@Mod(modid = MinecraftEnv.MOD_ID, version = MinecraftEnv.VERSION, name = MinecraftEnv.NAME, clientSideOnly = true)
public class MinecraftEnv {
    public static final String MOD_ID = "minecraftenv";
    public static final String VERSION = "1.0-SNAPSHOT";
    public static final String NAME = "MinecraftEnv";

    static final Logger LOGGER = LogManager.getLogger(MinecraftEnv.class);

    public static volatile ByteBuffer current_frame;
    public static volatile int width, height;

    @Instance(MOD_ID)
    public static MinecraftEnv instance;

    public MinecraftEnv() {
        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerLoggedInEvent event) {
        LOGGER.info("Player joined in world: {}", event.player.getDisplayName().getFormattedText());
    }

    @SubscribeEvent
    public void onPlayerLeave(PlayerLoggedOutEvent event) {
        LOGGER.info("Player left in world: {}", event.player.getDisplayName().getFormattedText());
        if (AgentServerCommand.getServer() != null) {
            AgentServerCommand.stopSocketServer();
        }
    }

    /**
     * 實現方式與原始malmo不同，不確定會不會有未知錯誤，但至少是能正常使用的
     * <br>
     * 已知錯誤: 在擷取過去存檔地圖的遊戲畫面時會發生畫面擷取錯誤，每次都只能在新創建的地圖中才能正常擷取畫面
     */
    @SubscribeEvent
    public void postRender(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            Framebuffer framebuffer = Minecraft.getMinecraft().getFramebuffer();

            // render FOV in screen
            framebuffer.framebufferRenderExt(Display.getWidth(), Display.getHeight(), true);
            framebuffer.bindFramebuffer(true);

            synchronized (MinecraftEnv.class) {
                if (MinecraftEnv.current_frame == null) {
                    width = framebuffer.framebufferWidth;
                    height = framebuffer.framebufferHeight;
                    MinecraftEnv.current_frame = BufferUtils.createByteBuffer(width * height * 3);
                } else if (width != framebuffer.framebufferWidth || height != framebuffer.framebufferHeight) {
                    width = framebuffer.framebufferWidth;
                    height = framebuffer.framebufferHeight;
                    MinecraftEnv.current_frame = BufferUtils.createByteBuffer(width * height * 3);
                }

                // read screenshot form FOV
                glReadPixels(0, 0, width, height, GL_RGB, GL_UNSIGNED_BYTE, MinecraftEnv.current_frame);
            }
        }
    }

    @EventHandler
    public void onCommandRegister(FMLServerStartingEvent event) {
        event.registerServerCommand(new AgentServerCommand());
    }
}
