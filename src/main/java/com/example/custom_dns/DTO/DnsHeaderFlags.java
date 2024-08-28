package com.example.custom_dns.DTO;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DnsHeaderFlags {
    boolean queryResponse;
    int opcode;
    boolean aa;
    boolean tc;
    boolean recursionDesired;
    boolean recursionAvailable;
    int z;
    int rcode;
}
