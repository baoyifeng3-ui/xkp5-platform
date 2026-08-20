package com.match.agent.service;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class WakeOnLanSenderTest {
    @Test
    public void createsStandardMagicPacket() {
        byte[] packet = WakeOnLanSender.magicPacket("02:23:45:67:89:ab");

        assertEquals(102, packet.length);
        for (int index = 0; index < 6; index++) {
            assertEquals(0xff, packet[index] & 0xff);
        }
        byte[] mac = {0x02, 0x23, 0x45, 0x67, (byte) 0x89, (byte) 0xab};
        for (int copy = 0; copy < 16; copy++) {
            for (int octet = 0; octet < mac.length; octet++) {
                assertEquals(mac[octet], packet[6 + copy * 6 + octet]);
            }
        }
    }

    @Test
    public void acceptsHyphenatedMac() {
        assertEquals(102, WakeOnLanSender.magicPacket("02-23-45-67-89-AB").length);
    }

    @Test
    public void rejectsUnsafeMacAddresses() {
        assertInvalid(null);
        assertInvalid("");
        assertInvalid("not-a-mac");
        assertInvalid("00:00:00:00:00:00");
        assertInvalid("ff:ff:ff:ff:ff:ff");
        assertInvalid("01:00:5e:00:00:01");
    }

    private void assertInvalid(String mac) {
        try {
            WakeOnLanSender.magicPacket(mac);
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("expected invalid MAC rejection: " + mac);
    }
}
