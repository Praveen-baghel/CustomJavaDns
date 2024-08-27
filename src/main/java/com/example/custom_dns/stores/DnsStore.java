package com.example.custom_dns.stores;

import com.example.custom_dns.entities.DnsRecord;
import com.example.custom_dns.repositories.DnsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DnsStore {
    @Autowired
    DnsRepository dnsRepository;

    public DnsRecord getRecord(String domain, short type) {
        return dnsRepository.findByDomainAndRecordType(domain, type).orElse(null);
    }

    public DnsRecord saveRecord(DnsRecord dnsRecord) {
        return dnsRepository.save(dnsRecord);
    }
}
