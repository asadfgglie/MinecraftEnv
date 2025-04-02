package net.ckcsc.asadfgglie.minecraftenv.util;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class SocketInputStream extends DataInputStream {
    /**
     * Creates a DataInputStream that uses the specified
     * underlying Socket.
     *
     * @param socket the specified input stream by {@code socket.getInputStream()}
     */
    public SocketInputStream(Socket socket) throws IOException {
        super(socket.getInputStream());
    }

    public String readString(Charset charset) throws IOException {
        byte[] tmp = new byte[this.readInt()];
        return new String(tmp, charset);
    }

    public String readString() throws IOException {
        return readString(StandardCharsets.UTF_8);
    }
}
