package net.ckcsc.asadfgglie.minecraftenv.util;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class SocketOutputStream extends DataOutputStream {
    /**
     * Creates a new data output stream to write data to the specified
     * underlying Socket. The counter <code>written</code> is
     * set to zero.
     *
     * @param socket the underlying output stream by {@code socket.getOutputStream()},
     *               to be saved for later use.
     */
    public SocketOutputStream(Socket socket) throws IOException {
        super(socket.getOutputStream());
    }

    public void writeString(String s) throws IOException {
        this.writeInt(s.getBytes().length);
        this.write(s.getBytes());
    }
}
