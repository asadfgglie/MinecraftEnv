package net.ckcsc.asadfgglie.minecraftenv;

import com.google.gson.Gson;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.ckcsc.asadfgglie.minecraftenv.exception.VerifyException;
import net.ckcsc.asadfgglie.minecraftenv.util.SocketInputStream;
import net.ckcsc.asadfgglie.minecraftenv.util.SocketOutputStream;
import net.minecraft.client.GameSettings;
import net.minecraft.client.KeyboardListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHelper;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.renderer.texture.NativeImage;
import net.minecraft.client.util.InputMappings;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.biome.Biome;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class AgentServerCommand {
    private static ServerSocketThread server;
    private static final SimpleDateFormat date = new SimpleDateFormat("HH:mm:ss");

    private static int width = 640;
    private static int height = 360;

    private static HashSet<String> previousPressButton = null;
    private static HashSet<String> currentPressButton = new HashSet<>();
    private static HashSet<String> releaseButton = new HashSet<>();

    private static HashSet<String> previousMousePressButton = null;
    private static HashSet<String> currentMousePressButton = new HashSet<>();
    private static HashSet<String> releaseMouseButton = new HashSet<>();

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
                            step(request);
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

            previousPressButton = null;
            currentPressButton.clear();
            releaseButton.clear();

            previousMousePressButton = null;
            currentMousePressButton.clear();
            releaseMouseButton.clear();

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

            previousPressButton = null;
            currentPressButton.clear();
            releaseButton.clear();

            previousMousePressButton = null;
            currentMousePressButton.clear();
            releaseMouseButton.clear();

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

    public static Map<String, Object> getEquipmentInfo(ItemStack itemStack) {
        HashMap<String, Object> info = new HashMap<>();
        info.put("type", itemStack.toString());
        info.put("maxDamage", itemStack.getMaxDamage());
        info.put("damage", itemStack.getDamageValue());

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

        ClientPlayerEntity player = Minecraft.getInstance().player;
        if (player != null) {
            ArrayList<HashMap<String, Object>> inventory = new ArrayList<>();
            for (ItemStack itemStack: player.inventory.items) {
                if (itemStack.getCount() > 0) {
                    HashMap<String, Object> item = new HashMap<>();
                    item.put("type", itemStack.getItem().toString());
                    item.put("quantity", itemStack.getCount());
                    inventory.add(item);
                }
            }
            info.put("inventory", inventory);

            HashMap<String, Object> equipped_items = new HashMap<>();
            equipped_items.put("mainhand", getEquipmentInfo(player.inventory.getSelected()));
            equipped_items.put("offhand", getEquipmentInfo(player.inventory.offhand.get(0)));
            equipped_items.put("head", getEquipmentInfo(player.inventory.armor.get(3)));
            equipped_items.put("chest", getEquipmentInfo(player.inventory.armor.get(2)));
            equipped_items.put("legs", getEquipmentInfo(player.inventory.armor.get(1)));
            equipped_items.put("feet", getEquipmentInfo(player.inventory.armor.get(0)));
            info.put("equipped_items", equipped_items);

            HashMap<String, Object> life_stats = new HashMap<>();
            life_stats.put("life", player.getHealth());
            life_stats.put("score", player.getScore());
            life_stats.put("food", player.getFoodData().getFoodLevel());
            life_stats.put("saturation", player.getFoodData().getSaturationLevel());
            life_stats.put("xp", player.experienceLevel);
            life_stats.put("is_alive", player.isAlive());
            life_stats.put("air", player.getAirSupply());
            life_stats.put("name", player.getName().toString());
            info.put("life_stats", life_stats);

            info.put("xpos", player.getX());
            info.put("ypos", player.getY());
            info.put("zpos", player.getZ());
            info.put("pitch", player.xRot);
            info.put("yaw", player.yRot);

            BlockPos player_pos = player.blockPosition();
            Biome player_biome = player.level.getBiome(player_pos);
            info.put("biome_name", player_biome.toString());
            info.put("biome_temperature", player_biome.getBaseTemperature());
            info.put("biome_downfall", player_biome.getDownfall());
            info.put("sea_level", player.level.getSeaLevel());

            info.put("light_level", player.level.getLightEmission(player_pos));
            info.put("is_raining", player.level.isRaining());
            info.put("can_see_sky", player.level.canSeeSky(player_pos));
        }

        info.put("isGuiOpen", Minecraft.getInstance().screen != null);

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

    public static void step(AgentServerSchema.StepRequest request) {
        Minecraft.getInstance().executeBlocking(() -> {
            // all action should do in the same tick
            executeKeyboardAction(request);
            executeMouseAction(request);

            previousPressButton = currentPressButton;
            currentPressButton = new HashSet<>();
            releaseButton = new HashSet<>();

            previousMousePressButton = currentMousePressButton;
            currentMousePressButton = new HashSet<>();
            releaseMouseButton = new HashSet<>();
        });
    }

    private static void executeKeyboardAction(AgentServerSchema.StepRequest request) {
        KeyboardListener keyboardListener = Minecraft.getInstance().keyboardHandler;
        GameSettings settings = Minecraft.getInstance().options;

        if (request.back) {
            currentPressButton.add(settings.keyDown.getKey().getName());
        }
        else {
            releaseButton.add(settings.keyDown.getKey().getName());
        }

        if (request.drop) {
            currentPressButton.add(settings.keyDrop.getKey().getName());
        }
        else {
            releaseButton.add(settings.keyDrop.getKey().getName());
        }

        if (request.forward) {
            currentPressButton.add(settings.keyUp.getKey().getName());
        }
        else {
            releaseButton.add(settings.keyUp.getKey().getName());
        }

        for (int i = 0; i < 9; i++) {
            if (request.hotbar[i]) {
                currentPressButton.add(settings.keyHotbarSlots[i].getKey().getName());
            }
            else {
                releaseButton.add(settings.keyHotbarSlots[i].getKey().getName());
            }
        }

        if (request.inventory) {
            currentPressButton.add(settings.keyInventory.getKey().getName());
        }
        else {
            releaseButton.add(settings.keyInventory.getKey().getName());
        }

        if (request.jump) {
            currentPressButton.add(settings.keyJump.getKey().getName());
        }
        else {
            releaseButton.add(settings.keyJump.getKey().getName());
        }

        if (request.left) {
            currentPressButton.add(settings.keyLeft.getKey().getName());
        }
        else {
            releaseButton.add(settings.keyLeft.getKey().getName());
        }

        if (request.right) {
            currentPressButton.add(settings.keyRight.getKey().getName());
        }
        else {
            releaseButton.add(settings.keyRight.getKey().getName());
        }

        if (request.sneak) {
            currentPressButton.add(settings.keyShift.getKey().getName());
        }
        else {
            releaseButton.add(settings.keyShift.getKey().getName());
        }

        if (request.sprint) {
            currentPressButton.add(settings.keySprint.getKey().getName());
        }
        else {
            releaseButton.add(settings.keySprint.getKey().getName());
        }

        if (request.swapHands) {
            currentPressButton.add(settings.keySwapOffhand.getKey().getName());
        }
        else {
            releaseButton.add(settings.keySwapOffhand.getKey().getName());
        }

        if (request.ESC) {
            currentPressButton.add("key.keyboard.escape");
        }
        else {
            releaseButton.add("key.keyboard.escape");
        }

        if (previousPressButton != null) {
            HashSet<String> tmp = new HashSet<>(currentPressButton);
            currentPressButton.removeAll(previousPressButton);
            releaseButton.addAll(previousPressButton);
            releaseButton.removeAll(tmp);
        }

        for (String key: currentPressButton) {
            InputMappings.Input input = InputMappings.getKey(key);
            if (!previousPressButton.isEmpty() && Minecraft.getInstance().screen == null && key.equals("key.keyboard.escape")) {
                // prevent from opening "pause" menu by pressing escape key when no gui is opened
                continue;
            }
            keyboardListener.keyPress(Minecraft.getInstance().getWindow().getWindow(), input.getValue(), 0, 1, 0);
        }
        for (String key: releaseButton) {
            InputMappings.Input input = InputMappings.getKey(key);
            keyboardListener.keyPress(Minecraft.getInstance().getWindow().getWindow(), input.getValue(), 1, 0, 0);
        }
    }

    private static void executeMouseAction(AgentServerSchema.StepRequest request) {
        MouseHelper mouseHelper = Minecraft.getInstance().mouseHandler;
        GameSettings settings = Minecraft.getInstance().options;

        // mouse move
        double sensitivity = 2400.0 / 360, dx = request.camera[0] * sensitivity, dy = request.camera[1] * sensitivity;
        if (dx != 0 || dy != 0) {
            double scaleFactor = 1.0;
            if (Minecraft.getInstance().screen != null) {
                double retinaFactor = (double) Minecraft.getInstance().getMainRenderTarget().viewWidth / Minecraft.getInstance().getWindow().getScreenWidth();
                scaleFactor = Minecraft.getInstance().getWindow().getGuiScale() / retinaFactor;
            }
            double mouseX = mouseHelper.xpos() + dx * scaleFactor, mouseY = mouseHelper.ypos() + dy * scaleFactor;
            if (Minecraft.getInstance().screen != null) {
                mouseX = Math.min(Math.max(mouseX, 0), Minecraft.getInstance().getWindow().getScreenWidth());
                mouseY = Math.min(Math.max(mouseY, 0), Minecraft.getInstance().getWindow().getScreenHeight());
            }

            mouseHelper.onMove(Minecraft.getInstance().getWindow().getWindow(), mouseX, mouseY);
        }

        //mouse button press
        if (request.attack) {
            currentMousePressButton.add(settings.keyAttack.getKey().getName());
        }
        else {
            releaseMouseButton.add(settings.keyAttack.getKey().getName());
        }

        if (request.use) {
            currentMousePressButton.add(settings.keyUse.getKey().getName());
        }
        else {
            releaseMouseButton.add(settings.keyUse.getKey().getName());
        }

        if (request.pickItem) {
            currentMousePressButton.add(settings.keyPickItem.getKey().getName());
        }
        else {
            releaseMouseButton.add(settings.keyPickItem.getKey().getName());
        }

        if (previousMousePressButton != null) {
            HashSet<String> tmp = new HashSet<>(currentMousePressButton);
            currentMousePressButton.removeAll(previousMousePressButton);
            releaseMouseButton.addAll(previousMousePressButton);
            releaseMouseButton.removeAll(tmp);
        }

        for (String key: currentMousePressButton) {
            InputMappings.Input input = InputMappings.getKey(key);
            mouseHelper.onPress(Minecraft.getInstance().getWindow().getWindow(), input.getValue(), 1, 0);
        }
        for (String key: releaseMouseButton) {
            InputMappings.Input input = InputMappings.getKey(key);
            mouseHelper.onPress(Minecraft.getInstance().getWindow().getWindow(), input.getValue(), 0, 0);
        }
    }
}