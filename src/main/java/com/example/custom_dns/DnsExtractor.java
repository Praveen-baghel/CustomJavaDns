package com.example.custom_dns;

import com.example.custom_dns.DTO.DnsData;
import com.example.custom_dns.DTO.DnsHeader;
import com.example.custom_dns.DTO.DnsQuestion;

import java.net.DatagramPacket;
import java.util.ArrayList;
import java.util.List;

public class DnsExtractor {
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
                .flags(flags)
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
}