package com.example.custom_dns.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DnsHeader {
    short id;
    short flags;
    short questionsCount;
    short answersCount;
    short nsCount;
    short arCount;
}