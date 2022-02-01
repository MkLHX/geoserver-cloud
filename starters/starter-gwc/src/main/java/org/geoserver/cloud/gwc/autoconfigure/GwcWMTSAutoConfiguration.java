/*
 * (c) 2022 Open Source Geospatial Foundation - all rights reserved This code is licensed under the
 * GPL 2.0 license, available at the root application directory.
 */
package org.geoserver.cloud.gwc.autoconfigure;

import org.geoserver.cloud.config.factory.FilteringXmlBeanDefinitionReader;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;

/** @since 1.0 */
@Configuration(proxyBeanMethods = true)
@ImportResource(
    reader = FilteringXmlBeanDefinitionReader.class, //
    locations = {"jar:gs-gwc-.*!/geowebcache-geoserver-wmts-integration.xml"}
)
public class GwcWMTSAutoConfiguration {}