package com.example.custom_dns.stores;

import com.example.custom_dns.entities.DnsRecord;
import com.example.custom_dns.repositories.DnsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DnsStore {
    @Autowired
    DnsRepository dnsRepository;

    public List<DnsRecord> getRecords(String domain, short type) {
        return dnsRepository.findByDomainAndRecordType(domain, type);
    }

    public DnsRecord saveRecord(DnsRecord dnsRecord) {
        return dnsRepository.save(dnsRecord);
    }
}
