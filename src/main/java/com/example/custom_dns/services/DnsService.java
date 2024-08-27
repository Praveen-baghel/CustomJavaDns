package com.example.custom_dns.services;

import com.example.custom_dns.DTO.DnsData;
import com.example.custom_dns.DTO.DnsHeader;
import com.example.custom_dns.DTO.DnsHeaderFlags;
import com.example.custom_dns.DTO.DnsQuestion;
import com.example.custom_dns.DnsExtractor;
import com.example.custom_dns.entities.DnsRecord;
import com.example.custom_dns.stores.DnsStore;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class DnsService {
    @Autowired
    DnsStore dnsStore;

    public DatagramPacket getResponse(DatagramPacket packet) {
        DnsData dnsData = DnsExtractor.extractData(packet);
        DnsHeaderFlags dnsHeaderFlags = dnsData.getDnsHeader().getDnsHeaderFlags();
        if (dnsHeaderFlags.isRecursionRequired()) {
            return handleRecursiveQuery(packet);
        } else {
            return handleNormalQuery(packet);
        }

    }

    public DatagramPacket handleRecursiveQuery(DatagramPacket packet) {
        try {
            InetAddress address = InetAddress.getByName("8.8.8.8");
            DatagramSocket forwardSocket = new DatagramSocket();
            DatagramPacket forwardPacket = new DatagramPacket(packet.getData(), packet.getLength(), address, 53);
            forwardSocket.send(forwardPacket); // Send query to upstream DNS server

            byte[] bufResponse = new byte[512];
            DatagramPacket responsePacket = new DatagramPacket(bufResponse, bufResponse.length);
            forwardSocket.receive(responsePacket); // Receive response from upstream DNS server


            return new DatagramPacket(responsePacket.getData(), responsePacket.getLength(), packet.getSocketAddress());
        } catch (Exception e) {
            System.out.println("Exception: " + e.getMessage());
            return null;
        }
    }

    public DatagramPacket handleNormalQuery(DatagramPacket packet) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(512);

        DnsData dnsData = DnsExtractor.extractData(packet);
        DnsHeader reqDnsHeader = dnsData.getDnsHeader();
        DnsHeaderFlags reqDnsHeaderFlags = dnsData.getDnsHeader().getDnsHeaderFlags();

        DnsHeaderFlags resDnsHeaderFlags = DnsHeaderFlags.builder()
                .queryResponse(true)
                .opcode(0)
                .aa(false)
                .tc(false)
                .recursionRequired(reqDnsHeaderFlags.isRecursionRequired())
                .recursionAvailable(false)
                .z(0)
                .rcode(0)
                .build();
        DnsHeader resDnsHeader = DnsHeader.builder()
                .id(reqDnsHeader.getId())
                .dnsHeaderFlags(resDnsHeaderFlags)
                .questionsCount(reqDnsHeader.getQuestionsCount())
                .answersCount((short) 1)
                .nsCount((short) 0)
                .arCount((short) 0)
                .build();

        writeHeaders(byteBuffer, resDnsHeader);
        writeQuestions(byteBuffer, dnsData.getDnsQuestionList());
        boolean haveAnswer = writeAnswers(byteBuffer, dnsData.getDnsQuestionList());

        // copy used bytes from buffer into buffResponse
        final byte[] res = new byte[byteBuffer.position()];
        byteBuffer.flip();
        byteBuffer.get(res);
        return new DatagramPacket(res, res.length, packet.getSocketAddress());
    }

    public void writeHeaders(ByteBuffer byteBuffer, DnsHeader dnsHeader) {
        // Header part( 12 Bytes fixed)
        byteBuffer.putShort(dnsHeader.getId());
        byteBuffer.putShort(DnsExtractor.makeDnsHeaderFlags(dnsHeader.getDnsHeaderFlags()));
        byteBuffer.putShort(dnsHeader.getQuestionsCount());
        byteBuffer.putShort(dnsHeader.getAnswersCount());
        byteBuffer.putShort(dnsHeader.getNsCount());
        byteBuffer.putShort(dnsHeader.getArCount());
    }

    public void writeQuestions(ByteBuffer byteBuffer, List<DnsQuestion> dnsQuestionList) {
        for (DnsQuestion dnsQuestion : dnsQuestionList) {
            byteBuffer.put(encodeDomainName(dnsQuestion.getDomainName()));
            byteBuffer.putShort(dnsQuestion.getQuestionType());
            byteBuffer.putShort(dnsQuestion.getQuestionClass());
        }
    }

    public boolean writeAnswers(ByteBuffer byteBuffer, List<DnsQuestion> dnsQuestionList) {
        boolean haveAnswers = false;
        for (DnsQuestion dnsQuestion : dnsQuestionList) {
            String domain = dnsQuestion.getDomainName();
            DnsRecord dnsRecord = dnsStore.getRecord(domain.substring(0, domain.length() - 1), dnsQuestion.getQuestionType());
            if (dnsRecord == null) {
                continue;
            }
            haveAnswers = true;
            byteBuffer.put(encodeDomainName(dnsQuestion.getDomainName()));
            byteBuffer.putShort((short) 1);
            byteBuffer.putShort((short) 1);
            byteBuffer.putInt(300);
            if (dnsRecord.getRecordType() == 1) {
                byteBuffer.putShort((short) 4);
                byteBuffer.put(ipToByteArray(dnsRecord.getRecordValue()));
            }

        }
        return haveAnswers;
    }

    public static byte[] encodeDomainName(String domain) {

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        for (String label : domain.split("\\.")) {
            outputStream.write((byte) label.length());
            outputStream.writeBytes(label.getBytes(StandardCharsets.UTF_8));
        }
        outputStream.write(0); // Terminating null byte
        return outputStream.toByteArray();
    }

    public byte[] ipToByteArray(String ipAddress) {
        String[] segments = ipAddress.split("\\.");
        if (segments.length != 4) {
            throw new IllegalArgumentException("Invalid IPv4 address format");
        }
        byte[] byteArray = new byte[4];
        for (int i = 0; i < 4; i++) {
            try {
                byteArray[i] = (byte) Integer.parseInt(segments[i]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid segment in IP address", e);
            }
        }
        return byteArray;
    }
}
