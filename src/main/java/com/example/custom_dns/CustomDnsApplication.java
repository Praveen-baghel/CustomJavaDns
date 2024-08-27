package com.example.custom_dns;

import com.example.custom_dns.DTO.DnsData;
import com.example.custom_dns.DTO.DnsHeaderFlags;
import com.example.custom_dns.entities.DnsRecord;
import com.example.custom_dns.repositories.DnsRepository;
import com.example.custom_dns.services.DnsService;
import com.github.javafaker.Faker;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;

@SpringBootApplication
public class CustomDnsApplication {
    @Autowired
    DnsService dnsService;

    public static void main(String[] args) {
        SpringApplication.run(CustomDnsApplication.class, args);
    }

    @PostConstruct
    public void startServer() {
        try (DatagramSocket serverSocket = new DatagramSocket(2053)) {
            System.out.println("Listening on port 2053...");


            while (true) {
                // fetch packet
                final byte[] buf = new byte[512];
                final DatagramPacket packet = new DatagramPacket(buf, buf.length);
                serverSocket.receive(packet);
                System.out.println("Received packet !!");

                DatagramPacket packetResponse = dnsService.getResponse(packet);


                serverSocket.send(packetResponse);
                System.out.println("Response sent !!");
            }
        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
        }
    }

//    @Bean
//    public CommandLineRunner commandLineRunner(DnsRepository dnsRepository) {
//        return args -> {
//            for (int i = 0; i < 50; i++) {
//                Faker faker = new Faker();
//                DnsRecord dnsRecord = DnsRecord.builder()
//                        .domain(faker.internet().domainName())
//                        .recordType((short) 1)
//                        .recordValue(faker.internet().ipV4Address())
//                        .ttl((short) 300)
//                        .build();
//                dnsRepository.save(dnsRecord);
//            }
//
//
//        };
//    }

}
