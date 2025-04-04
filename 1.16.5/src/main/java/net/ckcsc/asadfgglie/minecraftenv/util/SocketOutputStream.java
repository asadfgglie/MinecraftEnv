package net.ckcsc.asadfgglie.minecraftenv.util;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class SocketOutputStream extends DataOutputStream {
    /**
     * Creates a new data output stream to write data to the specified
     * underlying Socket. The counter <code>written</code> is
     * set to zero.
     * <p>
     * Closing Stream will close the associated socket.
     *
     * @param socket the underlying output stream by {@code socket.getOutputStream()},
     *               to be saved for later use.
     */
    public SocketOutputStream(Socket socket) throws IOException {
        super(socket.getOutputStream());
    }

    public void writeString(String s) throws IOException {
        byte[] tmp = s.getBytes(StandardCharsets.UTF_8);
        this.writeInt(tmp.length);
        this.write(tmp);
    }
}
