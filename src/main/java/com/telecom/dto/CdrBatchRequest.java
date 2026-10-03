package com.telecom.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Buffered batch: the list size cap keeps memory per request bounded. */
public record CdrBatchRequest(
        @NotBlank @Size(max = 50) String source,
        @Size(max = 64) String batchId,
        @NotEmpty @Size(max = CdrBatchRequest.MAX_RECORDS) List<@Valid CdrRequest> records) {

    public static final int MAX_RECORDS = 5000;
}
