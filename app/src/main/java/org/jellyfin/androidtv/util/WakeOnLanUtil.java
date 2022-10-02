package org.jellyfin.androidtv.util;

import android.content.Context;

import androidx.core.util.Pair;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

public class WakeOnLanUtil {

    public static final int PORT = 9;

    public static Boolean wake(Context context, String broadcastIp, String macTarget) {

        try {
            byte[] macBytes = getMacBytes(macTarget);
            byte[] bytes = new byte[6 + 16 * macBytes.length];
            for (int i = 0; i < 6; i++) {
                bytes[i] = (byte) 0xff;
            }
            for (int i = 6; i < bytes.length; i += macBytes.length) {
                System.arraycopy(macBytes, 0, bytes, i, macBytes.length);
            }

            InetAddress address = InetAddress.getByName(broadcastIp);
            DatagramPacket packet = new DatagramPacket(bytes, bytes.length, address, PORT);
            DatagramSocket socket = new DatagramSocket();
            socket.send(packet);
            socket.close();

            return true;
        }
        catch (Exception e) {
            return false;
        }

    }

    private static byte[] getMacBytes(String macStr) throws IllegalArgumentException {
        byte[] bytes = new byte[6];
        String[] hex = macStr.split("(\\:|\\-)");
        if (hex.length != 6) {
            throw new IllegalArgumentException("Invalid MAC address.");
        }
        try {
            for (int i = 0; i < 6; i++) {
                bytes[i] = (byte) Integer.parseInt(hex[i], 16);
            }
        }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid hex digit in MAC address.");
        }
        return bytes;
    }

    public static Map<String, InetAddress> getBroadcastAddresses(InetAddress address) throws SocketException {

        Map<String, InetAddress> results = new HashMap<>();
        Enumeration<NetworkInterface> intfs = NetworkInterface.getNetworkInterfaces();
        while (intfs.hasMoreElements()) {
            NetworkInterface intf = intfs.nextElement();

            intf.getInterfaceAddresses().forEach(x -> {

                try {
                    InetAddress targetNetwork = getNetworkAddress(address, x.getNetworkPrefixLength());
                    InetAddress broadcastAddr = x.getBroadcast();
                    if (broadcastAddr != null && getNetworkAddress(x).equals(targetNetwork)) {
                        results.put(intf.getName(), broadcastAddr);
                    }

                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }
            });

        }

        return results;
    }

    public static InetAddress getNetworkAddress(InterfaceAddress intf) throws UnknownHostException {
        return getNetworkAddress(intf.getBroadcast(), intf.getNetworkPrefixLength());
    }
    public static InetAddress getNetworkAddress(InetAddress inetAddress, int prefixLength) throws UnknownHostException {

        if (inetAddress == null) return null;
        byte[] addr = inetAddress.getAddress();

        int left = prefixLength;
        for (int i = 0; i < addr.length; ++i) {
            if (left <= 0) {
                addr[i] = 0;
                continue;
            }
            if (left >= 8) {
                left -= 8;
                continue;
            }

            for (int j = 0; j < 8 - left; ++j) {
                addr[i] &= ~(1 << j);
            }
            left = 0;
        }


        return InetAddress.getByAddress(addr);
    }

    public static Pair<String, String> getMacAddressOf(InetAddress ip) throws IOException {

        String addr;
        if (ip == null || (addr = ip.getHostAddress()) == null) return null;

        Process process = Runtime.getRuntime().exec("cat /proc/net/arp");
        BufferedReader readIn = new BufferedReader(new InputStreamReader(process.getInputStream()));

        String output = null;

        {
            String str;
            while ((str = readIn.readLine()) != null) {
                if (str.startsWith(addr)) {
                    output = str;
                    break;
                }
            }
        }

        if (output == null) return null;

        String[] splitOutput = output.split("\\s+");
        if (splitOutput.length < 6) return null;

        String macAddr = splitOutput[3];
        String intf = splitOutput[5];

        return Pair.create(intf, macAddr);
    }

}
