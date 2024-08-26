package com.example.custom_dns.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DnsQuestion {
    String domainName;              // domain name
    short questionType;             // 1 for A record etc.
    short questionClass;            // 1 for IN (Internet)
}

