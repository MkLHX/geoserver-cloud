/*
 * (c) 2022 Open Source Geospatial Foundation - all rights reserved This code is licensed under the
 * GPL 2.0 license, available at the root application directory.
 */
package org.geoserver.cloud.autoconfigure.gwc.service;

import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.geoserver.cloud.autoconfigure.gwc.GeoWebCacheConfigurationProperties;
import org.geoserver.cloud.config.factory.FilteringXmlBeanDefinitionReader;
import org.gwc.web.wms.WMSController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;

/** @since 1.0 */
@Configuration
@ConditionalOnProperty(
    name = GeoWebCacheConfigurationProperties.SERVICE_WMS_ENABLED,
    havingValue = "true",
    matchIfMissing = false
)
@ComponentScan(basePackageClasses = WMSController.class)
@ImportResource(
    reader = FilteringXmlBeanDefinitionReader.class,
    locations = "jar:gs-gwc-.*!/geowebcache-wmsservice-context.xml"
)
@Slf4j(topic = "org.geoserver.cloud.autoconfigure.gwc.service")
public class WebMapServiceAutoConfiguration {

    public @PostConstruct void log() {
        log.info("{} enabled", GeoWebCacheConfigurationProperties.SERVICE_WMS_ENABLED);
    }
}