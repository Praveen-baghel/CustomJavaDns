package com.example.custom_dns.stores;

import com.example.custom_dns.entities.DnsRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class RedisStore {

    @Autowired
    DnsStore dnsStore;
    @Autowired
    private StringRedisTemplate redisTemplate;

    public void cacheDnsRecord(List<DnsRecord> dnsRecordList) {
        for (DnsRecord dnsRecord : dnsRecordList) {
            String compositeKey = dnsRecord.getDomain() + dnsRecord.getRecordType();
            redisTemplate.opsForValue().set(compositeKey, dnsRecord.getRecordValue(), 1, TimeUnit.HOURS);
        }
    }

    public List<DnsRecord> getCachedDnsRecords(String domain, short recordType) {
        List<DnsRecord> dnsRecordList = new ArrayList<>();
        String records = redisTemplate.opsForValue().get(domain + recordType);
        if (records == null) {
            return dnsRecordList;
        }
        for (String d : records.split("\\|")) {
            dnsRecordList.add(DnsRecord.builder()
                    .domain(domain)
                    .recordType(recordType)
                    .recordValue(d)
                    .ttl((short) 300)
                    .build());
        }
        return dnsRecordList;
    }

    // Example usage
    public List<DnsRecord> resolveDomain(String domain, short recordType) {
        List<DnsRecord> dnsRecordList = getCachedDnsRecords(domain, recordType);
        if (dnsRecordList.isEmpty()) {
            System.out.println("Record not found in cache. Fetching from database...");
            dnsRecordList = dnsStore.getRecords(domain, recordType);
            cacheDnsRecord(dnsRecordList);
        } else {
            System.out.println("Record found in cache.");
        }
        return dnsRecordList;
    }
}
