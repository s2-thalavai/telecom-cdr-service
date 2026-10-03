package com.telecom.config;

import com.fasterxml.jackson.core.StreamReadConstraints;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Parser hardening, applied to the ObjectMapper used by MVC and by CdrIngestionService. */
@Configuration
public class JacksonConfig {

    public static final StreamReadConstraints CONSTRAINTS = StreamReadConstraints.builder()
            .maxNestingDepth(64)          // CDRs are flat; deep nesting means abuse
            .maxStringLength(1_000_000)   // no single string needs to be > 1M chars
            .maxNumberLength(64)
            .build();

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer streamReadConstraints() {
        return builder -> builder.postConfigurer(mapper -> mapper.getFactory().setStreamReadConstraints(CONSTRAINTS));
    }
}
