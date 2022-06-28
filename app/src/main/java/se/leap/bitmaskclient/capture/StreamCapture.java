package se.leap.bitmaskclient.capture;

import static se.leap.bitmaskclient.capture.PacketUtils.PROTOCOL_ICMP;
import static se.leap.bitmaskclient.capture.PacketUtils.PROTOCOL_IPV6_HOPOPT;
import static se.leap.bitmaskclient.capture.PacketUtils.PROTOCOL_TCP;
import static se.leap.bitmaskclient.capture.PacketUtils.PROTOCOL_UDP;

import android.content.Context;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.HashSet;

import se.leap.bitmaskclient.capture.ip.IPPacket;
import se.leap.bitmaskclient.capture.ip.TCPPacket;
import se.leap.bitmaskclient.capture.ip.UDPPacket;
import se.leap.bitmaskclient.tor.TorStatusObservable;
import se.leap.bitmaskclient.tor.TorTunnel;

public class StreamCapture {

    private static final String TAG = StreamCapture.class.getSimpleName();
    private static final String VERSION="0.0.4";

    private static final int FORWARD_DNS_PORT = 5300;
    private static final StreamCapture INSTANCE = new StreamCapture();
    private static final int BUFFER_SIZE = 20000;
    private static int MTU = 1500;
    private static int[] LOCAL_IP;

    private static final String local_TO_remote = "local_TO_remote";
    private static final String remote_TO_local = "remote_TO_local";
    private static TransferThread from_local;
    private static TransferThread from_remote;

    private PacketUtils packetUtils;
    private HashSet<Integer> torifiedUids = new HashSet<>();

    public static void init(@NonNull Context context) {
        INSTANCE.packetUtils = new PacketUtils(context.getApplicationContext());
    }

    public static void setTorifiedUids(HashSet<Integer> uids) {
        INSTANCE.torifiedUids = uids;
    }

    public static StreamCapture getInstance() {
        return INSTANCE;
    }

    public static void setMTU(int mtu){
        MTU = mtu;
    }

   /* public static void setLocalIP(String mIp)  {
        try {
            LOCAL_IP = IPPacket.ip2int(InetAddress.getByName(mIp));
        } catch (UnknownHostException e) {
            e.printStackTrace();
            LogObservable.getInstance().addLog("ERROR: failed to set local IP.");
        }
    } */

    private ParcelFileDescriptor remote = null;
    private ParcelFileDescriptor remote_stub = null;
    private ParcelFileDescriptor captured_fd = null;
    private FileInputStream remote_in = null;
    private FileOutputStream remote_out = null;
    private FileInputStream local_in = null;
    private FileOutputStream local_out = null;

    private boolean closed = true;


    private StreamCapture() {}  //private constructor


    private class TransferThread  implements Runnable {
        InputStream in;
        OutputStream out;
        String role;
        boolean fromLocal;
        boolean isClosed = false;
        TorTunnel torTunnel;

        public TransferThread(InputStream in, OutputStream out, String role){
            this.in = new BufferedInputStream(in,BUFFER_SIZE);
            this.out = out;
            this.role = role;
            fromLocal = role.equals(local_TO_remote);
            new Thread(this).start();
        }

        public void setClosed(){
            isClosed = true;
        }

        private void sleep(long millis) {
            Object obj = new Object();
            synchronized (obj) {
                try {
                    obj.wait(millis);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        private int byteArrayToInt(int offs, byte[] b) {
            return b[offs+3] & 0xFF |
                    (b[offs+2] & 0xFF) << 8 |
                    (b[offs+1] & 0xFF) << 16 |
                    (b[offs] & 0xFF) << 24;
        }

        private int readInt(int offs, byte[] buf, InputStream in) throws IOException {
            int r = in.read(buf,offs, 4);
            if (r < 4)
                throw new IOException("Invalid packet data!");

            return byteArrayToInt(offs, buf);
        }

        private int readPacket(byte[] buf) throws IOException {

            int firstInt = readInt(0,buf,in);
            int offs = 4;

            int length = firstInt & 0xFFFF;
            int version = buf[0] >> 4;

            if (version != 4 && version != 6)
                throw new IOException("IP Version " + version + " not supported!");

            if (version == 6) {
                int nextInt = readInt(offs,buf,in);
                offs = 8;
                length = 40 + (nextInt >>> 16);
            }

            if (length > MTU)
                throw new IOException("Invalid IP header! MTU exceeded! MTU:" + MTU + ", Len:" + length + "!");

            while (offs < length) {
                int r = in.read(buf, offs, length - offs);
                offs = offs + r;
            }

            return offs;
        }

        private void writeThrough(OutputStream out, byte[] buf, int offs, int len) throws IOException{
            synchronized(out){
                out.write(buf, offs, len);
            }
        }

        @Override
        public void run() {

            Log.w(TAG, "Starting transfer:" + role + "!");

            if (fromLocal) {
                torTunnel = new TorTunnel(out);
                torTunnel.start("127.0.0.1", TorStatusObservable.getSocksProxyPort());
            }

            int r = 0;
            while (r != -1 && !isClosed) {
                try {
                    byte[] buffer = new byte[MTU];

                    r = readPacket(buffer);

                    if (r == -1 || isClosed)
                        break;

                    if (r == 0)
                        sleep(50);

                    else if (!fromLocal)
                        writeThrough(out, buffer, 0, r);

                    else {
                        int version = buffer[0] >> 4;
                        int protocol = buffer[9]&0XFF;
                        InetSocketAddress sourceAddress = null;
                        InetSocketAddress destinationAddress = null;
                        switch (protocol) {
                            case PROTOCOL_TCP:
                                TCPPacket tcpPacket = new TCPPacket(buffer, 0, r);
                                sourceAddress = new InetSocketAddress(IPPacket.int2ip(tcpPacket.getSourceIP()), tcpPacket.getSourcePort());
                                destinationAddress = new InetSocketAddress(IPPacket.int2ip(tcpPacket.getDestIP()), tcpPacket.getDestPort());
                                break;
                            case PROTOCOL_UDP:
                                UDPPacket udpPacket = new UDPPacket(buffer, 0, r);
                                sourceAddress = new InetSocketAddress(IPPacket.int2ip(udpPacket.getSourceIP()), udpPacket.getSourcePort());
                                destinationAddress = new InetSocketAddress(IPPacket.int2ip(udpPacket.getDestIP()), udpPacket.getDestPort());
                                break;
                            case PROTOCOL_IPV6_HOPOPT:
                                IPPacket ipPacket = new IPPacket(buffer, 0, r);
                                Log.w(TAG, "unhandled protocol: HOPOPT: " + IPPacket.int2ip(ipPacket.getSourceIP()) + " -> " + IPPacket.int2ip(ipPacket.getDestIP()));
                                break;
                            case PROTOCOL_ICMP:
                                IPPacket icmpPacket = new IPPacket(buffer, 0, r);
                                Log.w(TAG, "unhandled protocol: ICMP: " + IPPacket.int2ip(icmpPacket.getSourceIP()) + " -> " + IPPacket.int2ip(icmpPacket.getDestIP()));
                                break;
                            default:
                                Log.w(TAG, "unhandled protocol: " + protocol);
                                break;
                        }
                        if (sourceAddress != null) {
                            int uid = packetUtils.getUIDFrom(protocol, version, sourceAddress, destinationAddress);
                            String firstPackage = packetUtils.getPackageNameFromUID(uid);
                            String msg = "packageID: " + firstPackage + "\nUID: " + uid + "\npacket: " + sourceAddress.toString() + " -> " + destinationAddress.toString();
                            Log.i(TAG, msg);
                            if (torifiedUids.contains(uid) && protocol == PROTOCOL_TCP) {
                                Log.i(TAG, "^--- torifying this packet ---^");
                                //DNS resolution is currently done over OpenVpn!
                                torTunnel.inputPacket(buffer);
                                continue;
                            }
                        }
                        writeThrough(out, buffer, 0, r);
                    }

                } catch (Exception e) {
                    if (!isClosed) {
                        sleep(100);
                    }
                }
            }

            if (!isClosed) {
                closeIgnoreException();
                if (torTunnel != null) {
                    torTunnel.stop();
                }
            }

            Log.w(TAG, "Terminated transfer:" + role + "!");
        }
    }

    public synchronized void closeIgnoreException() {
        if (closed)
            return;

        from_local.setClosed();
        from_remote.setClosed();

        try {
            captured_fd.close();
        } catch (IOException e) {
        }
        try {
            local_in.close();
        } catch (IOException e) {
        }
        try {
            local_out.close();
        } catch (IOException e) {
        }
        try {
            remote.close();
        } catch (IOException e) {
        }
        try {
            remote_in.close();
        } catch (IOException e) {
        }
        try {
            remote_out.close();
        } catch (IOException e) {
        }
        try {
            remote_stub.close();
        } catch (IOException e) {
        }
        closed = true;
    }


    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public ParcelFileDescriptor getCapturedParcelFileDescriptor(ParcelFileDescriptor pfd) throws IOException{

        if (!closed)
            closeIgnoreException();

        Log.w(TAG, "StreamCapture Version:"+VERSION);

        closed = false;

        captured_fd = pfd;
        local_in = new FileInputStream(pfd.getFileDescriptor());
        local_out = new FileOutputStream(pfd.getFileDescriptor());
        ParcelFileDescriptor[] pair = ParcelFileDescriptor.createReliableSocketPair();

        remote_stub = pair[0];
        remote = pair[1];
        remote_out =new FileOutputStream(remote_stub.getFileDescriptor());
        remote_in = new FileInputStream(remote_stub.getFileDescriptor());
        from_local = new TransferThread(local_in, remote_out, local_TO_remote);
        from_remote = new TransferThread(remote_in, local_out, remote_TO_local);
        return remote;
    }
}
