/*
 * (c) 2020 Open Source Geospatial Foundation - all rights reserved This code is licensed under the
 * GPL 2.0 license, available at the root application directory.
 */
package org.geoserver.cloud.autoconfigure.catalog.backend.jdbcconfig;

import org.geoserver.cloud.autoconfigure.catalog.backend.core.DefaultUpdateSequenceAutoConfiguration;
import org.geoserver.cloud.config.catalog.backend.jdbcconfig.JDBCConfigBackendConfigurer;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@ConditionalOnJdbcConfigEnabled
@Import(JDBCConfigBackendConfigurer.class)
@AutoConfigureBefore(DefaultUpdateSequenceAutoConfiguration.class)
public class JDBCConfigAutoConfiguration {}
