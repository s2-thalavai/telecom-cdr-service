package com.telecom.repository;

import com.telecom.dto.CdrRequest;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Custom Spring Data fragment for bulk operations that JPA is slow at. */
public interface CdrBulkRepository {

    int batchInsert(List<CdrRequest> records, String source, String batchId);

    Set<String> findExistingRecordIds(Collection<String> recordIds);
}
