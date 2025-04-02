package net.ckcsc.asadfgglie.minecraftenv.exception;


/**
 * Throw when Socket title length not equal {@code ServerSocketThread.SOCKET_TITLE.getBytes(StandardCharsets.UTF_8).length}
 * or Socket title not start with {@code "<" + MinecraftEnv.MOD_ID + ":"} and end with {@code ">"}
 */
public class IllegalSocketTitle extends Exception {
    public IllegalSocketTitle(int length) {
        super("Invalid title length: " + length);
    }

    public IllegalSocketTitle(String title) {
        super("Invalid title: " + title);
    }
}
