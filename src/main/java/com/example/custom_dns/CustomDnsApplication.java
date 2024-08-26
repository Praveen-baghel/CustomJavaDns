package com.example.custom_dns;

import com.example.custom_dns.DTO.DnsData;
import com.example.custom_dns.DTO.DnsQuestion;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

@SpringBootApplication
public class CustomDnsApplication {

    public static void main(String[] args) {
        try (DatagramSocket serverSocket = new DatagramSocket(2053)) {

            System.out.println("Listening on port 2053...");
            // Continuously listen to port 2053
            while (true) {
                final byte[] buf = new byte[512];
                final DatagramPacket packet = new DatagramPacket(buf, buf.length);
                serverSocket.receive(packet);
                System.out.println("Received packet !!");

                DnsData dnsData = DnsExtractor.extractData(packet);
                short questionsCount = (short) dnsData.getDnsQuestionList().size();

                ByteBuffer byteResponse = ByteBuffer.allocate(512);

                // flags field is acombination of flags like query/response, recursion required etc
                short flags = (short) 0b00000000_00000000;
                short qdcount = questionsCount;
                short ancount = questionsCount;
                short nscount = 0;
                short arcount = 0;

                // Headers part
                writeHeaders(byteResponse, dnsData.getDnsHeader().getId(), flags, qdcount, ancount, nscount, arcount);

                // Questions part
                writeQuestions(byteResponse, dnsData.getDnsQuestionList());

                // Answers part
                writeAnswers(byteResponse, dnsData.getDnsQuestionList());


                // create array of size equal to number of bytes written in ByteBuffer
                final byte[] buffResponse = new byte[byteResponse.position()];
                // reset the buffer position to start
                byteResponse.flip();
                // copy data of size buffResponse from starting of byteResponse into buffResponse
                byteResponse.get(buffResponse);


                final DatagramPacket packetResponse = new DatagramPacket(buffResponse, buffResponse.length, packet.getSocketAddress());
                serverSocket.send(packetResponse);
                System.out.println("Response sent !!");
            }
        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
        }
    }

    public static void writeHeaders(ByteBuffer byteBuffer, short id, short flags, short qdcount, short ancount, short nscount, short arcount) {
        // Header part( 12 Bytes fixed)
        byteBuffer.putShort(id);
        byteBuffer.putShort(flags);
        byteBuffer.putShort(qdcount);
        byteBuffer.putShort(ancount);
        byteBuffer.putShort(nscount);
        byteBuffer.putShort(arcount);
    }

    public static void writeQuestions(ByteBuffer byteBuffer, List<DnsQuestion> dnsQuestionList) {
        for (DnsQuestion dnsQuestion : dnsQuestionList) {
            byteBuffer.put(encodeDomainName(dnsQuestion.getDomainName()));
            byteBuffer.putShort(dnsQuestion.getQuestionType());
            byteBuffer.putShort(dnsQuestion.getQuestionClass());
        }
    }

    public static void writeAnswers(ByteBuffer byteBuffer, List<DnsQuestion> dnsQuestionList) {
        for (DnsQuestion dnsQuestion : dnsQuestionList) {
            byteBuffer.put(encodeDomainName(dnsQuestion.getDomainName()));
            byteBuffer.putShort((short) 1);
            byteBuffer.putShort((short) 1);
            byteBuffer.putInt(300);
            byteBuffer.putShort((short) 4);
            byteBuffer.put(new byte[]{127, 0, 0, 1});
        }
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

}
