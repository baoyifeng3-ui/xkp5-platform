package com.match.agent.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class WakeOnLanSender {
    private static final Pattern MAC_PATTERN =
            Pattern.compile("^[0-9a-fA-F]{2}([:-][0-9a-fA-F]{2}){5}$");

    private final String broadcastAddress;
    private final int port;

    public WakeOnLanSender(
            @Value("${xkp.agent.wol.broadcast-address:255.255.255.255}") String broadcastAddress,
            @Value("${xkp.agent.wol.port:9}") int port) {
        if (broadcastAddress == null || broadcastAddress.trim().isEmpty()) {
            throw new IllegalArgumentException("Wake-on-LAN broadcast address is required");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Wake-on-LAN port is invalid");
        }
        this.broadcastAddress = broadcastAddress.trim();
        this.port = port;
    }

    public void send(String macAddress) {
        byte[] packetBytes = magicPacket(macAddress);
        try {
            InetAddress target = InetAddress.getByName(broadcastAddress);
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setBroadcast(true);
                socket.send(new DatagramPacket(packetBytes, packetBytes.length, target, port));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Wake-on-LAN packet could not be sent", exception);
        }
    }

    static byte[] magicPacket(String macAddress) {
        if (macAddress == null || !MAC_PATTERN.matcher(macAddress.trim()).matches()) {
            throw new IllegalArgumentException("Processing server MAC address is invalid");
        }
        String[] parts = macAddress.trim().toLowerCase(Locale.ROOT).split("[:-]");
        byte[] mac = new byte[6];
        boolean allZero = true;
        boolean allBroadcast = true;
        for (int index = 0; index < mac.length; index++) {
            mac[index] = (byte) Integer.parseInt(parts[index], 16);
            allZero &= mac[index] == 0;
            allBroadcast &= (mac[index] & 0xff) == 0xff;
        }
        if (allZero || allBroadcast || (mac[0] & 0x01) != 0) {
            throw new IllegalArgumentException("Processing server MAC address is not unicast");
        }
        byte[] packet = new byte[6 + 16 * mac.length];
        for (int index = 0; index < 6; index++) {
            packet[index] = (byte) 0xff;
        }
        for (int copy = 0; copy < 16; copy++) {
            System.arraycopy(mac, 0, packet, 6 + copy * mac.length, mac.length);
        }
        return packet;
    }
}
