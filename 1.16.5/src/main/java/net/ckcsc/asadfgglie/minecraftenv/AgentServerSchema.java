package net.ckcsc.asadfgglie.minecraftenv;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import net.ckcsc.asadfgglie.minecraftenv.exception.VerifyException;
import net.ckcsc.asadfgglie.minecraftenv.util.SocketInputStream;

import java.io.IOException;
import java.util.Map;

public class AgentServerSchema {
    public abstract static class Request {
        public abstract <T extends Request> T verify() throws VerifyException;
        static <T extends Request> T fromJson(String json, Gson gson, Class<T> clazz) throws VerifyException {
            return gson.fromJson(json, clazz).verify();
        }

        static <T extends Request> T fromSocketDataInputStream(SocketInputStream in, Class<T> clazz) throws IOException, VerifyException {
            return fromJson(in.readString(), new Gson(), clazz);
        }
    }

    public static class StepRequest extends Request {
        @SerializedName("esc")
        public final boolean ESC;       // put esc
        public final boolean attack;    // click mouse left
        public final boolean back;      // put S
        public final float[] camera;    // new float[2] with min -180, max 180, move mouse
        public final boolean drop;      // put Q
        public final boolean forward;   // put W
        public final boolean[] hotbar;  // new boolean[9], put hotbar 1-9
        public final boolean inventory; // put E
        public final boolean jump;      // put space
        public final boolean left;      // put A
        public final boolean pickItem;  // click mouse middle
        public final boolean right;     // put D
        public final boolean sneak;     // put shift
        public final boolean sprint;    // put ctrl + W
        public final boolean swapHands; // put F
        public final boolean use;       // click mouse right

        private StepRequest(boolean esc, boolean attack, boolean back, float[] camera, boolean drop, boolean forward,
                           boolean[] hotbar, boolean inventory, boolean jump, boolean left, boolean pickItem, boolean right,
                           boolean sneak, boolean sprint, boolean swapHands, boolean use) {
            this.ESC = esc;
            this.attack = attack;
            this.back = back;
            this.camera = camera;
            this.drop = drop;
            this.forward = forward;
            this.hotbar = hotbar;
            this.inventory = inventory;
            this.jump = jump;
            this.left = left;
            this.pickItem = pickItem;
            this.right = right;
            this.sneak = sneak;
            this.sprint = sprint;
            this.swapHands = swapHands;
            this.use = use;
        }

        public <T extends Request> T verify() throws VerifyException {
            if (camera == null) {
                throw new VerifyException("camera should not be null");
            }
            else if (camera.length != 2) {
                throw new VerifyException("camera length should be 2");
            }
            else if (camera[0] < -180 || camera[0] > 180) {
                throw new VerifyException("camera[0] should be between -180 and 180");
            }
            else if (camera[1] < -180 || camera[1] > 180) {
                throw new VerifyException("camera[1] should be between -180 and 180");
            }

            if (hotbar == null) {
                throw new VerifyException("hotbar should not be null");
            }
            else if (hotbar.length != 9) {
                throw new VerifyException("hotbar length should be 9");
            }
            return (T) this;
        }
    }

    public static class SetupRequest extends Request {
        public final int width;
        public final int height;
        public final double gamma;
        public final double fov;
        public final double guiScale;

        private SetupRequest(int width, int height, double gamma, double fov, double guiScale) {
            this.width = width;
            this.height = height;
            this.gamma = gamma;
            this.fov = fov;
            this.guiScale = guiScale;
        }

        public <T extends Request> T verify() throws VerifyException {
            if (width <= 0) {
                throw new VerifyException("width should be greater than 0");
            }
            if (height <= 0) {
                throw new VerifyException("height should be greater than 0");
            }
            if (guiScale <= 0) {
                throw new VerifyException("guiScale should be greater than 0");
            }
            if (gamma <= 0) {
                throw new VerifyException("gamma should be greater than 0");
            }
            if (fov <= 0) {
                throw new VerifyException("fov should be greater than 0");
            }
            return (T) this;
        }
    }

    public static class Response {
        /**
         * np.ndarray((360 * 640), dtype=uint8) // rgb image with whid 640, height 360
         */
        public final byte[] observation;
        /**
         * <code>
         * {
         *     "inventory": {
         *         i: {
         *             "quantity": int, "type": item name
         *         } for i in range(36)
         *         // 36 slot of agent inventory
         *     },
         *     "equipped_items": {
         *         "chest": {
         *             "damage": int, "maxDamage": int, "type": item name
         *         },
         *         "feet": {
         *             "damage": int, "maxDamage": int, "type": item name
         *         },
         *         "head": {
         *             "damage": int, "maxDamage": int, "type": item name
         *         },
         *         "legs": {
         *             "damage": int, "maxDamage": int, "type": item name
         *         },
         *         "mainhand": {
         *             "damage": int, "maxDamage": int, "type": item name
         *         },
         *         "offhand": {
         *             "damage": int, "maxDamage": int, "type": item name
         *         }
         *     },
         *     "life_stats": {
         *         "air": int,
         *         "food": int,
         *         "is_alive": boolean,
         *         "life": float,
         *         "saturation": float,
         *         "score": int,
         *         "xp": int
         *     },
         *     "xpos": float,
         *     "ypos": float,
         *     "zpos": float,
         *     "pitch": float,
         *     "yaw": float
         * }
         * </code>
         */
        public final Map<String, Object> info;

        public final static Gson gson = new Gson();

        public Response(byte[] observation, Map<String, Object> info) {
            this.observation = observation;
            this.info = info;
        }
    }
}