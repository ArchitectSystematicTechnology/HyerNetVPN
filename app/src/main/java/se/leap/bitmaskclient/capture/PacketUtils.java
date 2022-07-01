package se.leap.bitmaskclient.capture;

import android.content.Context;
import android.net.ConnectivityManager;
import android.os.Build;
import android.util.Log;

import java.io.BufferedReader;
import java.io.FileReader;
import java.net.InetSocketAddress;

import se.leap.bitmaskclient.R;

public class PacketUtils {
    public static final int PROTOCOL_IPV6_HOPOPT = 0;
    public static final int PROTOCOL_ICMP = 1;
    public static final int PROTOCOL_TCP = 6;
    public static final int PROTOCOL_UDP = 17;

    public static final int INDEX_UID_COL = 7;
    public static final int INDEX_LOCAL_ADDRESS_COL = 1;
    public static final int INDEX_REMOTE_ADDRESS_COL = 2;
    public static final String PROC_TCP_FILE = "/proc/net/tcp";
    public static final String PROC_UDP_FILE = "/proc/net/udp";
    public static final String PROC_ICMP_FILE = "/proc/net/icmp";
    private static final String TAG = PacketUtils.class.getSimpleName();

    private final ConnectivityManager connectivityManager;
    private final Context context;

    public PacketUtils(Context context) {
        this.context = context;
        this.connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    public int getUIDFrom(int protocol, int version, InetSocketAddress source, InetSocketAddress destination) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return connectivityManager.getConnectionOwnerUid(protocol, source, destination);
        } else {
            return  getUIDFromProc(protocol, version, source, destination);
        }
    }

    public String getPackageNameFromUID(int uid) {
        // https://github.com/M66B/NetGuard/blob/master/app/src/main/java/eu/faircode/netguard/Rule.java

        switch (uid) {
            case -1:
                return "invalid UID -1";
            case 0:
                return context.getString(R.string.title_root);
            case 1013:
                return context.getString(R.string.title_mediaserver);
            case 1020:
                return context.getString(R.string.title_multicast_dns_responder);
            case 1021:
                return context.getString(R.string.title_gpsdaemon);
            case 1051:
                return context.getString(R.string.title_dnsdaemon);
            case 9999:
                return context.getString(R.string.title_nobody);
            default:
                String name = context.getPackageManager().getNameForUid(uid);
                return name != null ? name : context.getString(R.string.unknown);
        }
    }

    private int getUIDFromProc(int protocol, int version, InetSocketAddress source, InetSocketAddress destination) {
        int uid = -1;
        try {
            String procFileName = null;
            switch (protocol) {
                case PROTOCOL_TCP:
                    procFileName = PROC_TCP_FILE;
                    break;
                case PROTOCOL_UDP:
                    procFileName = PROC_UDP_FILE;
                    break;
                case PROTOCOL_ICMP:
                    procFileName = PROC_ICMP_FILE;
                    break;
            }
            if (procFileName != null && version == 6) {
                procFileName = procFileName+"6";
            }
            BufferedReader br = new BufferedReader(new FileReader(procFileName));

            //Ignore first line
            String line = br.readLine();
            while ((line = br.readLine()) != null) {
                /**
                 * Proc file table column sequence
                 * sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode
                 */
                String[] parts = line.trim().split("\\s+");
                if (parts.length < 8) {
                    Log.w(TAG, "proc line elements < 8: " + line.trim());
                    continue;
                }
                String localAddressAndPort = parts[INDEX_LOCAL_ADDRESS_COL];
                if (localAddressAndPort.isEmpty()) {
                    Log.w(TAG, "proc line localAddressAndPort is empty: " + line.trim());
                    continue;
                }

                String[] localAddressParts = localAddressAndPort.split(":");
                if (localAddressParts.length != 2) {
                    Log.w(TAG, "proc line localAddressParts != 2: " + localAddressAndPort);
                    continue;
                }
                String address = convertHexToIP(localAddressParts[0]);
                int port = Integer.parseInt(localAddressParts[1], 16);

                if (port == source.getPort() && address.equals(source.getAddress().getHostAddress())) {
                    String remoteAddressAndPort = parts[INDEX_REMOTE_ADDRESS_COL];
                    if (remoteAddressAndPort.isEmpty()) {
                        Log.w(TAG, "proc line remoteAddressAndPort is empty: " + line.trim());
                        continue;
                    }

                    String[] remoteAddressParts = remoteAddressAndPort.split(":");
                    if (remoteAddressParts.length != 2) {
                        Log.w(TAG, "proc line remoteAddressAndPort != 2: " + remoteAddressAndPort);
                        continue;
                    }

                    String remoteAddress = convertHexToIP(remoteAddressParts[0]);
                    int remoteAddressPort = Integer.parseInt(remoteAddressParts[1], 16);

                    if (remoteAddressPort == destination.getPort() && remoteAddress.equals(destination.getAddress().getHostAddress())) {
                        uid = Integer.parseInt(parts[INDEX_UID_COL]);
                        break;
                    }
                }
            }
            br.close();
        } catch (Exception ex) {
            Log.e("ProcFileParser", ex.getMessage());
        }
        return uid;
    }


    /**
     *
     * @param hex expected to be in reverse order
     * @return
     */
    public String convertHexToIP(String hex) {
        if (hex.length() < 8) {
            return "";
        }
        StringBuilder ip= new StringBuilder();
        for (int j = hex.length() - 2; j >= 0; j-=2) {
            String sub = hex.substring(j, j+2);
            int num = Integer.parseInt(sub, 16);
            ip.append(num).append(".");
        }

        ip = new StringBuilder(ip.substring(0, ip.length() - 1));
        return ip.toString();
    }
}
