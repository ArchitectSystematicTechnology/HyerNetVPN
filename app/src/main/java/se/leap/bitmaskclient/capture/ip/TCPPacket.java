package se.leap.bitmaskclient.capture.ip;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

public class TCPPacket extends IPPacket{
    private final IntBuffer tcpHeader;

    public TCPPacket(byte[] packet, int offs, int len) {
        super(packet, offs, len);
        tcpHeader = ByteBuffer.wrap(packet, offs + ipHdrlen, 20).order(ByteOrder.BIG_ENDIAN).asIntBuffer();
    }

    public int getSourcePort() {
        return tcpHeader.get(0) >>> 16;
    }

    public int getDestPort() {
        return tcpHeader.get(0) & 0x0000FFFF;
    }


    public int getIPPacketLength() {
        return super.getLength();
    }

    public int getOffset() {
        return super.getOffset() + ipHdrlen;
    }
}
