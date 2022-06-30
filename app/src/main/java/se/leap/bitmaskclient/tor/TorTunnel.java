package se.leap.bitmaskclient.tor;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.WeakReference;

import tun2torsocks.TorSocksTunnel;

public class TorTunnel {
    private final TorSocksTunnel torSocksTunnel;
    private final TunOutputStreamWriter packetWriter;

    public static class TunOutputStreamWriter implements tun2torsocks.PacketFlow {
        private final WeakReference<OutputStream> tunOutputStream;

        public TunOutputStreamWriter(OutputStream outputStream) {
            this.tunOutputStream = new WeakReference<>(outputStream);
        }

        @Override
        public void writePacket(byte[] buffer) {
            try {
                OutputStream out = tunOutputStream.get();
                synchronized(out){
                    out.write(buffer);
                }
            } catch (NullPointerException | IOException e) {
                e.printStackTrace();
            }
        }
    }

    public TorTunnel(OutputStream tunOutputstream) {
        torSocksTunnel = new TorSocksTunnel();
        packetWriter = new TunOutputStreamWriter(tunOutputstream);
    }

    public void start(String socksIP, long socksPort) {
        torSocksTunnel.startSocks(packetWriter, socksIP, socksPort);
    }

    public void stop() {
        torSocksTunnel.close();
    }

    public void inputPacket(byte[] data) {
        torSocksTunnel.inputPacket(data);
    }

}
