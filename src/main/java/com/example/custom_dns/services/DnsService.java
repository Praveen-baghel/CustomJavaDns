package com.example.custom_dns.services;

import com.example.custom_dns.DTO.DnsData;
import com.example.custom_dns.DTO.DnsHeader;
import com.example.custom_dns.DTO.DnsHeaderFlags;
import com.example.custom_dns.DTO.DnsQuestion;
import com.example.custom_dns.DnsExtractor;
import com.example.custom_dns.entities.DnsRecord;
import com.example.custom_dns.stores.DnsStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
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
        byte[] answers = createAnswersSection(dnsData.getDnsQuestionList());

        byte[] byteResponse;
        if (answers.length > 0) {
            System.out.println("Resolving query normally !!");
            byteResponse = resolveNormalQuery(packet, answers);
        } else if (dnsHeaderFlags.isRecursionDesired()) {
            System.out.println("Resolving query recursively !!");
            byteResponse = resolveRecursiveQuery(packet);
        } else {
            System.out.println("Making error response !!");
            byteResponse = createErrorResponse(packet, (short) 0);
        }

        return new DatagramPacket(byteResponse, byteResponse.length, packet.getSocketAddress());

    }

    public byte[] resolveRecursiveQuery(DatagramPacket packet) {
        try {
            InetAddress address = InetAddress.getByName("8.8.8.8");
            DatagramPacket responsePacket;
            try (DatagramSocket forwardSocket = new DatagramSocket()) {
                DatagramPacket forwardPacket = new DatagramPacket(packet.getData(), packet.getLength(), address, 53);
                forwardSocket.send(forwardPacket); // Send query to upstream DNS server

                byte[] bufResponse = new byte[512];
                responsePacket = new DatagramPacket(bufResponse, bufResponse.length);
                forwardSocket.receive(responsePacket); // Receive response from upstream DNS server
            }
            return responsePacket.getData();
        } catch (Exception e) {
            System.out.println("Exception: " + e.getMessage());
            System.out.println("Making error response !!");
            return createErrorResponse(packet, (short) 0);
        }
    }

    public byte[] resolveNormalQuery(DatagramPacket packet, byte[] answers) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(512);

        DnsData dnsData = DnsExtractor.extractData(packet);
        DnsHeader reqDnsHeader = dnsData.getDnsHeader();
        DnsHeaderFlags reqDnsHeaderFlags = dnsData.getDnsHeader().getDnsHeaderFlags();

        byte[] questions = createQuestionsSection(dnsData.getDnsQuestionList());
        short questionCount = (short) dnsData.getDnsQuestionList().size();
        short answersCount = (short) (answers.length / dnsData.getDnsQuestionList().size());

        DnsHeaderFlags resDnsHeaderFlags = DnsHeaderFlags.builder()
                .queryResponse(true)
                .opcode(0)
                .aa(false)
                .tc(false)
                .recursionDesired(reqDnsHeaderFlags.isRecursionDesired())
                .recursionAvailable(true)
                .z(0)
                .rcode(0)
                .build();
        DnsHeader resDnsHeader = DnsHeader.builder()
                .id(reqDnsHeader.getId())
                .dnsHeaderFlags(resDnsHeaderFlags)
                .questionsCount(questionCount)
                .answersCount(answersCount)
                .nsCount((short) 0)
                .arCount((short) 0)
                .build();


        writeHeaders(byteBuffer, resDnsHeader);
        byteBuffer.put(questions);
        byteBuffer.put(answers);

        // copy used bytes from buffer into buffResponse
        final byte[] res = new byte[byteBuffer.position()];
        byteBuffer.flip();
        byteBuffer.get(res);
        return res;
    }

    public byte[] createErrorResponse(DatagramPacket packet, short rCode) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(512);

        DnsData dnsData = DnsExtractor.extractData(packet);
        DnsHeader reqDnsHeader = dnsData.getDnsHeader();
        DnsHeaderFlags reqDnsHeaderFlags = dnsData.getDnsHeader().getDnsHeaderFlags();

        byte[] questions = createQuestionsSection(dnsData.getDnsQuestionList());
        short questionCount = (short) dnsData.getDnsQuestionList().size();


        DnsHeaderFlags resDnsHeaderFlags = DnsHeaderFlags.builder()
                .queryResponse(true)
                .opcode(0)
                .aa(false)
                .tc(false)
                .recursionDesired(reqDnsHeaderFlags.isRecursionDesired())
                .recursionAvailable(true)
                .z(0)
                .rcode(rCode)
                .build();
        DnsHeader resDnsHeader = DnsHeader.builder()
                .id(reqDnsHeader.getId())
                .dnsHeaderFlags(resDnsHeaderFlags)
                .questionsCount(questionCount)
                .answersCount((short) 0)
                .nsCount((short) 0)
                .arCount((short) 0)
                .build();

        // DNS headers
        writeHeaders(byteBuffer, resDnsHeader);

        // DNS question section (repeat the question in the response)
        byteBuffer.put(questions);

        return byteBuffer.array();
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

    public byte[] createQuestionsSection(List<DnsQuestion> dnsQuestionList) {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        DataOutputStream dataStream = new DataOutputStream(byteStream);
        try {
            for (DnsQuestion dnsQuestion : dnsQuestionList) {
                dataStream.write(encodeDomainName(dnsQuestion.getDomainName()));
                dataStream.writeShort(dnsQuestion.getQuestionType());
                dataStream.writeShort(dnsQuestion.getQuestionClass());
            }
        } catch (Exception e) {
            System.out.println("Exception: " + e.getMessage());
        }
        return byteStream.toByteArray();
    }

    public byte[] createAnswersSection(List<DnsQuestion> dnsQuestionList) {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        DataOutputStream dataStream = new DataOutputStream(byteStream);
        try {
            for (DnsQuestion dnsQuestion : dnsQuestionList) {
                String domain = dnsQuestion.getDomainName();
                DnsRecord dnsRecord = dnsStore.getRecord(domain.substring(0, domain.length() - 1), dnsQuestion.getQuestionType());
                if (dnsRecord == null) {
                    continue;
                }
                dataStream.write(encodeDomainName(dnsQuestion.getDomainName()));
                dataStream.writeShort(1); // Type A
                dataStream.writeShort(1); // Class IN
                dataStream.writeInt(300); // TTL

                if (dnsRecord.getRecordType() == 1) {
                    dataStream.writeShort(4); // Length of the IP address
                    dataStream.write(ipToByteArray(dnsRecord.getRecordValue()));
                }
            }
        } catch (Exception e) {
            System.out.println("Exception: " + e.getMessage());
        }
        return byteStream.toByteArray();
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
