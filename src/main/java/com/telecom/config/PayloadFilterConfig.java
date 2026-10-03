package com.telecom.config;

import com.telecom.filter.GzipRequestDecompressionFilter;
import com.telecom.filter.RequestSizeLimitFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class PayloadFilterConfig {

    /** First: limits bytes on the wire (the compressed size if gzip). */
    @Bean
    public FilterRegistrationBean<RequestSizeLimitFilter> requestSizeLimitFilter(PayloadProperties properties) {
        FilterRegistrationBean<RequestSizeLimitFilter> registration =
                new FilterRegistrationBean<>(new RequestSizeLimitFilter(properties));
        registration.addUrlPatterns("/api/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    /** Second: transparently decompresses gzip bodies and limits the decompressed size. */
    @Bean
    public FilterRegistrationBean<GzipRequestDecompressionFilter> gzipRequestFilter(PayloadProperties properties) {
        FilterRegistrationBean<GzipRequestDecompressionFilter> registration = new FilterRegistrationBean<>(
                new GzipRequestDecompressionFilter(properties.maxDecompressedSize().toBytes()));
        registration.addUrlPatterns("/api/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return registration;
    }
}
