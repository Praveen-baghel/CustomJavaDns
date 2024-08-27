package com.example.custom_dns;

import com.example.custom_dns.DTO.DnsData;
import com.example.custom_dns.DTO.DnsHeader;
import com.example.custom_dns.DTO.DnsHeaderFlags;
import com.example.custom_dns.DTO.DnsQuestion;

import java.net.DatagramPacket;
import java.util.ArrayList;
import java.util.List;

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

            System.out.println("Question " + (i + 1) + ":");
            System.out.println("  Domain: " + domainName.toString());
            System.out.println("  Type: " + qType);
            System.out.println("  Class: " + qClass);

            dnsQuestionList.add(DnsQuestion.builder()
                    .domainName(domainName.toString())
                    .questionClass(qClass)
                    .questionType(qType)
                    .build());
        }
        return DnsData.builder()
                .dnsHeader(dnsHeader)
                .dnsQuestionList(dnsQuestionList)
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
                .recursionRequired(rd)
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
        if (dnsHeaderFlags.isRecursionRequired()) {
            flags |= RD_MASK;
        }
        if (dnsHeaderFlags.isRecursionAvailable()) {
            flags |= RA_MASK;
        }
        flags |= (short) ((dnsHeaderFlags.getZ() << 4) & Z_MASK);
        flags |= (short) (dnsHeaderFlags.getRcode() & RCODE_MASK);
        return flags;
    }
}