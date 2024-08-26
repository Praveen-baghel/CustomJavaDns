package com.example.custom_dns.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DnsData {
    DnsHeader dnsHeader;
    List<DnsQuestion> dnsQuestionList;
}