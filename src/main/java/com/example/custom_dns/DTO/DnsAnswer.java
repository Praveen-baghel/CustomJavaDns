package com.example.custom_dns.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DnsAnswer {
    String domainName;
    short answerType;
    short answerClass;
    short ttl;
    short dataLength;
    String rData;
}

