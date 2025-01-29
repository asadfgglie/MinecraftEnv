package net.ckcsc.asadfgglie.minecraftenv;

import java.util.Map;

public class AgentServerSchema {
    public static class StepRequest {
        public final boolean ESC;
        public final boolean attack;
        public final boolean back;
        public final float[] camera; // new float[2] with min -180, max 180
        public final boolean drop;
        public final boolean forward;
        public final boolean[] hotbar; // new boolean[9]
        public final boolean inventory;
        public final boolean jump;
        public final boolean left;
        public final boolean pickItem;
        public final boolean right;
        public final boolean sneak;
        public final boolean sprint;
        public final boolean swapHands;
        public final boolean use;

        public StepRequest(boolean esc, boolean attack, boolean back, float[] camera, boolean drop, boolean forward,
                           boolean[] hotbar, boolean inventory, boolean jump, boolean left, boolean pickItem, boolean right,
                           boolean sneak, boolean sprint, boolean swapHands, boolean use) throws VerifyError {
            if (camera == null) {
                throw new VerifyError("camera should not be null");
            }
            else if (camera.length != 2) {
                throw new VerifyError("camera length should be 2");
            }
            else if (camera[0] < -180 || camera[0] > 180) {
                throw new VerifyError("camera[0] should be between -180 and 180");
            }
            else if (camera[1] < -180 || camera[1] > 180) {
                throw new VerifyError("camera[1] should be between -180 and 180");
            }

            if (hotbar == null) {
                throw new VerifyError("hotbar should not be null");
            }
            else if (hotbar.length != 9) {
                throw new VerifyError("hotbar length should be 9");
            }

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
    }

    public static class Response {
        /**
         * np.ndarray((360, 640, 3), dtype=uint8) // rgb image with whid 640, height 360
         */
        public final short[][][] observation;
        /**
         * <code>
         * {
         *     "plain_inventory": {
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
         *     "location_stats": {
         *         "xpos": float,
         *         "ypos": float,
         *         "zpos": float,
         *         "pitch": float,
         *         "yaw": float
         *     }
         * }
         * </code>
         */
        public final Map<String, Object> info;

        public Response(short[][][] observation, Map<String, Object> info) {
            this.observation = observation;
            this.info = info;
        }
    }
}
