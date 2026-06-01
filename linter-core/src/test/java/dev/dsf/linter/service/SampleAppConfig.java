package dev.dsf.linter.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sample {@code @Configuration} class used by
 * {@link SpringConfigurationLinterIntegrationTest} to simulate a real
 * DSF plugin configuration. The {@code @Bean} method return type matches
 * the {@code camunda:class} reference placed into the test BPMN file,
 * so the linter can cross-reference them.
 */
@Configuration
public class SampleAppConfig {

    /**
     * A {@code @Bean} method whose return type is a BPMN-delegate-like class.
     * The actual behavior is irrelevant for the linter – only the return type
     * is inspected.
     */
    @Bean
    public SampleDelegate sampleDelegate() {
        return new SampleDelegate();
    }
}
