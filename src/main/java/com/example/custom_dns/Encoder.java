package com.example.custom_dns;

import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Service
public class Encoder {


    public byte[] encodeDomainName(String domain) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        for (String label : domain.split("\\.")) {
            outputStream.write((byte) label.length());
            outputStream.writeBytes(label.getBytes(StandardCharsets.UTF_8));
        }
        outputStream.write(0); // Terminating null byte
        return outputStream.toByteArray();
    }

    public byte[] encodeAAAARecord(String rDataString) {
        String[] groups = rDataString.split(":");
        byte[] bytes = new byte[16];
        for (int i = 0; i < groups.length; i++) {
            int value = Integer.parseInt(groups[i], 16);
            bytes[i * 2] = (byte) (value >> 8);
            bytes[i * 2 + 1] = (byte) value;
        }
        return bytes;
    }

    public byte[] encodeARecord(String rDataString) {
        String[] octets = rDataString.split("\\.");
        byte[] bytes = new byte[4];
        for (int i = 0; i < 4; i++) {
            bytes[i] = (byte) Integer.parseInt(octets[i]);
        }
        return bytes;
    }

    public byte[] encodeTXTRecord(String rDataString) {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        byteStream.write((short) rDataString.length());
        try {
            byteStream.write(rDataString.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            System.out.println("Exception while encoding: " + e.getMessage());
        }
        return byteStream.toByteArray();
    }

    public byte[] parseToByteArray(String rDataString, int type) {
        return switch (type) {
            case 1 -> // A Record (IPv4)
                    encodeARecord(rDataString);
            case 28 -> // AAAA Record (IPv6)
                    encodeAAAARecord(rDataString);  // CNAME Record
            case 5, 2 ->  // NS Record
                    encodeDomainName(rDataString);
            case 16 -> // TXT Record
                    encodeTXTRecord(rDataString);
            default -> throw new UnsupportedOperationException("Unsupported record type: " + type);
        };
    }
}
