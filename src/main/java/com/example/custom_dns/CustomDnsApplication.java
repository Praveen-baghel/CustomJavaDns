package com.example.custom_dns;

import com.example.custom_dns.services.DnsService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

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
}
