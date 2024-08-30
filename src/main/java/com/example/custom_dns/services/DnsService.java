package com.example.custom_dns.services;

import com.example.custom_dns.DTO.*;
import com.example.custom_dns.DnsExtractor;
import com.example.custom_dns.Encoder;
import com.example.custom_dns.entities.DnsRecord;
import com.example.custom_dns.stores.DnsStore;
import com.example.custom_dns.stores.RedisStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.Serializable;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
public class DnsService {
    @Autowired
    DnsStore dnsStore;

    @Autowired
    RedisStore redisStore;

    @Autowired
    Encoder encoder;

    public DatagramPacket getResponse(DatagramPacket packet) {
        DnsData dnsData = DnsExtractor.extractData(packet);
        DnsHeaderFlags dnsHeaderFlags = dnsData.getDnsHeader().getDnsHeaderFlags();
        Map<String, Serializable> answersDict = createAnswersSection(dnsData.getDnsQuestionList());
        byte[] answers = (byte[]) answersDict.get("answers");
        int anCount = (int) answersDict.get("anCount");

        byte[] byteResponse;
        if (answers.length > 0) {
            System.out.println("Resolving query normally !!");
            byteResponse = resolveNormalQuery(dnsData, answers, (short) anCount);
        } else if (dnsHeaderFlags.isRecursionDesired()) {
            System.out.println("Resolving query recursively !!");
            byteResponse = resolveRecursiveQuery(packet, dnsData);
        } else {
            System.out.println("Making error response !!");
            byteResponse = createErrorResponse(dnsData, (short) 2);
        }

        return new DatagramPacket(byteResponse, byteResponse.length, packet.getSocketAddress());

    }

    public byte[] resolveRecursiveQuery(DatagramPacket packet, DnsData dnsData) {
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
            // save records in database
            saveRecords(responsePacket);
            return responsePacket.getData();
        } catch (Exception e) {
            System.out.println("Exception: " + e.getMessage());
            System.out.println("Making error response !!");
            return createErrorResponse(dnsData, (short) 2);
        }
    }

    public byte[] resolveNormalQuery(DnsData dnsData, byte[] answers, short anCount) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(512);

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
                .rcode(0)
                .build();
        DnsHeader resDnsHeader = DnsHeader.builder()
                .id(reqDnsHeader.getId())
                .dnsHeaderFlags(resDnsHeaderFlags)
                .questionsCount(questionCount)
                .answersCount(anCount)
                .nsCount((short) 0)
                .arCount((short) 0)
                .build();


        writeHeaders(byteBuffer, resDnsHeader);
        byteBuffer.put(questions);
        byteBuffer.put(answers);

        // copy used bytes from buffer into buffResponse
        return removeExtraBytes(byteBuffer);
    }

    public byte[] createErrorResponse(DnsData dnsData, short rCode) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(512);

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

        return removeExtraBytes(byteBuffer);
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
                dataStream.write(encoder.encodeDomainName(dnsQuestion.getDomainName()));
                dataStream.writeShort(dnsQuestion.getQuestionType());
                dataStream.writeShort(dnsQuestion.getQuestionClass());
            }
        } catch (Exception e) {
            System.out.println("Exception: " + e.getMessage());
        }
        return byteStream.toByteArray();
    }

    public Map<String, Serializable> createAnswersSection(List<DnsQuestion> dnsQuestionList) {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        DataOutputStream dataStream = new DataOutputStream(byteStream);
        int anCount = 0;
        try {
            for (DnsQuestion dnsQuestion : dnsQuestionList) {
                String domain = dnsQuestion.getDomainName();
                List<DnsRecord> dnsRecordList = redisStore.resolveDomain(domain.substring(0, domain.length() - 1), dnsQuestion.getQuestionType());
                for (DnsRecord dnsRecord : dnsRecordList) {
                    if (dnsRecord == null) {
                        continue;
                    }
                    dataStream.write(encoder.encodeDomainName(dnsQuestion.getDomainName()));
                    dataStream.writeShort(dnsRecord.getRecordType()); // Type A
                    dataStream.writeShort(1); // Class IN
                    dataStream.writeInt(dnsRecord.getTtl()); // TTL

                    byte[] rData = encoder.parseToByteArray(dnsRecord.getRecordValue(), dnsRecord.getRecordType());
                    dataStream.writeShort((short) rData.length); // Length of the RData
                    dataStream.write(rData);
                    anCount += 1;
                }
            }
        } catch (Exception e) {
            System.out.println("Exception: " + e.getMessage());
        }
        return Map.of("answers", byteStream.toByteArray(), "anCount", anCount);
    }

    public void saveRecords(DatagramPacket packet) {
        DnsData resDnsData = DnsExtractor.extractData(packet);
        for (DnsAnswer dnsAnswer : resDnsData.getDnsAnswerList()) {
            if (dnsAnswer != null && dnsAnswer.getRData() != null && !dnsAnswer.getRData().isEmpty()) {
                DnsRecord dnsRecord = DnsRecord.builder()
                        .domain(dnsAnswer.getDomainName())
                        .recordType(dnsAnswer.getAnswerType())
                        .recordValue(dnsAnswer.getRData())
                        .ttl(dnsAnswer.getTtl())
                        .build();
                dnsStore.saveRecord(dnsRecord);
            }
        }

    }

    public byte[] removeExtraBytes(ByteBuffer byteBuffer) {
        final byte[] res = new byte[byteBuffer.position()];
        byteBuffer.flip();
        byteBuffer.get(res);
        return res;
    }
}
