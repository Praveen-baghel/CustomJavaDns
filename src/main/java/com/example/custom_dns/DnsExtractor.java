package com.example.custom_dns;

import com.example.custom_dns.DTO.*;

import java.io.Serializable;
import java.net.DatagramPacket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class DnsExtractor {
    private static final int QR_MASK = 0x8000;
    private static final int OPCODE_MASK = 0x7800;
    private static final int AA_MASK = 0x0400;
    private static final int TC_MASK = 0x0200;
    private static final int RD_MASK = 0x0100;
    private static final int RA_MASK = 0x0080;
    private static final int Z_MASK = 0x0070;
    private static final int RCODE_MASK = 0x000F;

    public static DnsData extractData(DatagramPacket datagramPacket) {
        final byte[] a = datagramPacket.getData();

        short id = (short) ((a[0] << 8) | (a[1] & 0xFF));
        short flags = (short) ((a[2] << 8) | (a[3] & 0xFF));
        short qdCount = (short) ((a[4] << 8) | (a[5] & 0xFF));
        short anCount = (short) ((a[6] << 8) | (a[7] & 0xFF));
        short nsCount = (short) ((a[8] << 8) | (a[9] & 0xFF));
        short arCount = (short) ((a[10] << 8) | (a[11] & 0xFF));

        DnsHeader dnsHeader = DnsHeader.builder()
                .id(id)
                .dnsHeaderFlags(extractDnsHeaderFlags(flags))
                .questionsCount(qdCount)
                .answersCount(anCount)
                .nsCount(nsCount)
                .arCount(arCount)
                .build();

        List<DnsQuestion> dnsQuestionList = new ArrayList<>();
        int index = 12;
        for (int i = 0; i < qdCount; i++) {
            StringBuilder domainName = new StringBuilder();
            while (a[index] != 0) {
                int length = a[index++];
                domainName.append(new String(a, index, length)).append(".");
                index += length;
            }
            index++; // Skip the null byte at the end of the domain name
            short qType = (short) ((a[index++] << 8) | (a[index++] & 0xFF));
            short qClass = (short) ((a[index++] << 8) | (a[index++] & 0xFF));

//            System.out.println("Question " + (i + 1) + ":");
//            System.out.println("  Domain: " + domainName);
//            System.out.println("  Type: " + qType);
//            System.out.println("  Class: " + qClass);

            dnsQuestionList.add(DnsQuestion.builder()
                    .domainName(domainName.toString())
                    .questionClass(qClass)
                    .questionType(qType)
                    .build());
        }

        List<DnsAnswer> dnsAnswerList = new ArrayList<>();
        for (int i = 0; i < anCount; i++) {
            Map<String, Serializable> domainMap = parseDomainName(a, index);
            String domainName = (String) domainMap.get("domainName");
            index = (int) domainMap.get("index");
            // Parse the type, class, TTL, and data length
            int type = (a[index++] << 8) | (a[index++] & 0xFF);
            int recordClass = (a[index++] << 8) | (a[index++] & 0xFF);
            int ttl = ((a[index++] << 24) | (a[index++] << 16) | (a[index++] << 8) | (a[index++] & 0xFF));
            int dataLength = (a[index++] << 8) | (a[index++] & 0xFF);

            // Extract RDATA
            byte[] rData = Arrays.copyOfRange(a, index, index + dataLength);
            index += dataLength; // Move index after the RDATA

            // Convert RDATA to string based on the type
            String rDataString;
            switch (type) {
                case 1: // A Record (IPv4)
                    rDataString = String.format("%d.%d.%d.%d", rData[0] & 0xFF, rData[1] & 0xFF, rData[2] & 0xFF, rData[3] & 0xFF);
                    break;
                case 28: // AAAA Record (IPv6)
                    StringBuilder sb = new StringBuilder();
                    for (int j = 0; j < rData.length; j += 2) {
                        sb.append(String.format("%02x", rData[j] & 0xFF));
                        sb.append(String.format("%02x", rData[j + 1] & 0xFF));
                        if (j < rData.length - 2) sb.append(":");
                    }
                    rDataString = sb.toString();
                    break;
                case 5: // CNAME Record
                case 2: // NS Record
                    StringBuilder s = new StringBuilder();
                    while (rData[index] != 0) {
                        int length = rData[index++];
                        s.append(new String(rData, index, length)).append(".");
                        index += length;
                    }
                    rDataString = s.toString();
                    break;
                case 16: // TXT Record
                    StringBuilder txt = new StringBuilder();
                    int offset = 0;
                    while (offset < rData.length) {
                        // Read the length of the current string
                        int length = rData[offset] & 0xFF;
                        offset++;
                        // Extract the string using the length
                        String text = new String(rData, offset, length);
                        txt.append(text);
                        // Move the offset to the next string (if any)
                        offset += length;
                        // Add a space or separator if there are more strings
                        if (offset < rData.length) {
                            txt.append(" "); // Change this separator if needed
                        }
                    }
                    rDataString = txt.toString();
                    break;
                default:
                    rDataString = null;
            }

//            System.out.println("Answer Name: " + domainName);
//            System.out.println("Type: " + type);
//            System.out.println("Class: " + recordClass);
//            System.out.println("TTL: " + ttl);
//            System.out.println("Data Length: " + dataLength);
//            System.out.println("RDATA: " + rDataString);
            dnsAnswerList.add(DnsAnswer.builder()
                    .domainName(domainName)
                    .answerType((short) type)
                    .answerClass((short) recordClass)
                    .ttl((short) ttl)
                    .dataLength((short) dataLength)
                    .rData(rDataString)
                    .build());
        }
        return DnsData.builder()
                .dnsHeader(dnsHeader)
                .dnsQuestionList(dnsQuestionList)
                .dnsAnswerList(dnsAnswerList)
                .build();
    }

    public static DnsHeaderFlags extractDnsHeaderFlags(short flags) {
        boolean qr = (flags & QR_MASK) != 0;
        int opcode = (flags & OPCODE_MASK) >> 11;
        boolean aa = (flags & AA_MASK) != 0;
        boolean tc = (flags & TC_MASK) != 0;
        boolean rd = (flags & RD_MASK) != 0;
        boolean ra = (flags & RA_MASK) != 0;
        int z = (flags & Z_MASK) >> 4;
        int rcode = (flags & RCODE_MASK);
        return DnsHeaderFlags.builder()
                .queryResponse(qr)
                .opcode(opcode)
                .aa(aa)
                .tc(tc)
                .recursionDesired(rd)
                .recursionAvailable(ra)
                .z(z)
                .rcode(rcode)
                .build();
    }

    public static short makeDnsHeaderFlags(DnsHeaderFlags dnsHeaderFlags) {
        short flags = 0;
        if (dnsHeaderFlags.isQueryResponse()) {
            flags |= (short) QR_MASK;
        }
        flags |= (short) ((dnsHeaderFlags.getOpcode() << 11) & OPCODE_MASK);
        if (dnsHeaderFlags.isAa()) {
            flags |= AA_MASK;
        }
        if (dnsHeaderFlags.isTc()) {
            flags |= TC_MASK;
        }
        if (dnsHeaderFlags.isRecursionDesired()) {
            flags |= RD_MASK;
        }
        if (dnsHeaderFlags.isRecursionAvailable()) {
            flags |= RA_MASK;
        }
        flags |= (short) ((dnsHeaderFlags.getZ() << 4) & Z_MASK);
        flags |= (short) (dnsHeaderFlags.getRcode() & RCODE_MASK);
        return flags;
    }

    public static Map<String, Serializable> parseDomainName(byte[] data, int offset) {
        StringBuilder domainName = new StringBuilder();
        int length = data[offset] & 0xFF; // Length of the first label

        while (length > 0) {
            if ((length & 0xC0) == 0xC0) {
                // Pointer to a previous domain name
                int pointer = ((length & 0x3F) << 8) | (data[offset + 1] & 0xFF);
                String domainName1 = (String) parseDomainName(data, pointer).get("domainName"); // Recursively resolve the pointer
                domainName = new StringBuilder(domainName1);
                domainName.append(".");
                offset += 2;
                break;
            } else {
                // Extract the label
                offset++;
                domainName.append(new String(data, offset, length)).append(".");
                offset += length;
            }
            length = data[offset] & 0xFF; // Next label length
        }

        // Remove the trailing dot
        if (!domainName.isEmpty()) {
            domainName.setLength(domainName.length() - 1);
        }
        return Map.of("domainName", domainName.toString(), "index", offset);
    }

}