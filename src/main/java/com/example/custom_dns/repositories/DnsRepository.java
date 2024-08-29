package com.example.custom_dns.repositories;

import com.example.custom_dns.entities.DnsRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DnsRepository extends JpaRepository<DnsRecord, Long> {
    List<DnsRecord> findByDomainAndRecordType(String domain, short recordType);
}