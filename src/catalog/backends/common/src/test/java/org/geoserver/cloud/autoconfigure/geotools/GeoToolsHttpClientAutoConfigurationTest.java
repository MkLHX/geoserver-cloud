/*
 * (c) 2022 Open Source Geospatial Foundation - all rights reserved This code is licensed under the
 * GPL 2.0 license, available at the root application directory.
 */
package org.geoserver.cloud.autoconfigure.geotools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * @since 1.0
 */
class GeoToolsHttpClientAutoConfigurationTest {

    private ApplicationContextRunner runner =
            new ApplicationContextRunner() //
                    .withInitializer(new GeoToolsStaticContextInitializer()) //
                    .withConfiguration(
                            AutoConfigurations.of(GeoToolsHttpClientAutoConfiguration.class));

    private final String httpClientFactorySystemProperty = "HTTP_CLIENT_FACTORY";
    private final String forceXYSystemProperty = "org.geotools.referencing.forceXY";

    @BeforeEach
    void clearSystemProperties() {
        System.clearProperty(httpClientFactorySystemProperty);
        System.clearProperty(forceXYSystemProperty);
    }

    @Test
    void enabledByDefault() {
        runner.run(
                context -> {
                    assertThat(context)
                            .hasSingleBean(GeoToolsHttpClientProxyConfigurationProperties.class);
                    assertThat(context)
                            .hasSingleBean(SpringEnvironmentAwareGeoToolsHttpClientFactory.class);
                    assertThat(
                                    context.getBean(
                                            GeoToolsHttpClientProxyConfigurationProperties.class))
                            .hasFieldOrPropertyWithValue("enabled", true);
                });
    }

    @Test
    void testInitializerSetsForceXYSystemProperty() {
        assertNull(System.getProperty(forceXYSystemProperty));
        runner.run(
                context -> assertThat(System.getProperty(forceXYSystemProperty)).isEqualTo("true"));
    }

    @Test
    void testInitializerSetsHttpClientFactorySystemProperty() {
        final String expected =
                SpringEnvironmentAwareGeoToolsHttpClientFactory.class.getCanonicalName();

        assertNull(System.getProperty(httpClientFactorySystemProperty));
        runner.run(
                context ->
                        assertThat(System.getProperty(httpClientFactorySystemProperty))
                                .isEqualTo(expected));

        System.clearProperty(httpClientFactorySystemProperty);
        runner.withPropertyValues("geotools.httpclient.proxy.enabled: true")
                .run(
                        context ->
                                assertThat(System.getProperty(httpClientFactorySystemProperty))
                                        .isEqualTo(expected));

        System.clearProperty(httpClientFactorySystemProperty);
        runner.withPropertyValues("geotools.httpclient.proxy.enabled: false")
                .run(
                        context ->
                                assertThat(System.getProperty(httpClientFactorySystemProperty))
                                        .isNull());
    }
}
