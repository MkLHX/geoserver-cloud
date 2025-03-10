/*
 * (c) 2020 Open Source Geospatial Foundation - all rights reserved This code is licensed under the
 * GPL 2.0 license, available at the root application directory.
 */
package org.geoserver.cloud.config.catalog.backend.datadirectory;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import org.geoserver.GeoServerConfigurationLock;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.plugin.CatalogPlugin;
import org.geoserver.catalog.plugin.DefaultMemoryCatalogFacade;
import org.geoserver.catalog.plugin.ExtendedCatalogFacade;
import org.geoserver.cloud.catalog.locking.LockProviderGeoServerConfigurationLock;
import org.geoserver.cloud.catalog.locking.LockingCatalog;
import org.geoserver.cloud.catalog.locking.LockingGeoServer;
import org.geoserver.cloud.config.catalog.backend.core.CatalogProperties;
import org.geoserver.cloud.config.catalog.backend.core.GeoServerBackendConfigurer;
import org.geoserver.config.GeoServerLoader;
import org.geoserver.config.plugin.RepositoryGeoServerFacade;
import org.geoserver.platform.GeoServerResourceLoader;
import org.geoserver.platform.ModuleStatusImpl;
import org.geoserver.platform.config.UpdateSequence;
import org.geoserver.platform.resource.LockProvider;
import org.geoserver.platform.resource.ResourceStore;
import org.geoserver.security.GeoServerSecurityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import java.io.File;
import java.nio.file.Path;
import java.util.Objects;

/** */
@Configuration(proxyBeanMethods = true)
@EnableConfigurationProperties(DataDirectoryProperties.class)
@Slf4j(topic = "org.geoserver.cloud.config.datadirectory")
public class DataDirectoryBackendConfiguration implements GeoServerBackendConfigurer {

    private @Autowired CatalogProperties properties;

    private DataDirectoryProperties dataDirectoryConfig;

    @Autowired
    public DataDirectoryBackendConfiguration(DataDirectoryProperties dataDirectoryConfig) {
        this.dataDirectoryConfig = dataDirectoryConfig;
        log.info(
                "Loading geoserver config backend with {}",
                DataDirectoryBackendConfiguration.class.getSimpleName());
    }

    @Bean
    public ModuleStatusImpl moduleStatus() {
        ModuleStatusImpl module =
                new ModuleStatusImpl("gs-cloud-backend-datadir", "DataDirectory loader");
        module.setAvailable(true);
        module.setEnabled(true);
        return module;
    }

    public @Bean CatalogPlugin rawCatalog() {
        boolean isolated = properties.isIsolated();
        GeoServerConfigurationLock configurationLock = configurationLock();
        ExtendedCatalogFacade catalogFacade = catalogFacade();
        GeoServerResourceLoader resourceLoader = resourceLoader();
        CatalogPlugin rawCatalog = new LockingCatalog(configurationLock, catalogFacade, isolated);
        rawCatalog.setResourceLoader(resourceLoader);
        return rawCatalog;
    }

    @Bean(name = "geoServer")
    public LockingGeoServer geoServer(@Qualifier("catalog") Catalog catalog) {

        GeoServerConfigurationLock configurationLock = configurationLock();
        LockingGeoServer gs = new LockingGeoServer(configurationLock, geoserverFacade());
        gs.setCatalog(catalog);
        return gs;
    }

    @Bean
    public @Override UpdateSequence updateSequence() {
        return new DataDirectoryUpdateSequence();
    }

    @Bean
    public @Override GeoServerConfigurationLock configurationLock() {
        LockProvider lockProvider = resourceStoreImpl().getLockProvider();
        return new LockProviderGeoServerConfigurationLock(lockProvider);
    }

    public @Override @Bean DefaultMemoryCatalogFacade catalogFacade() {
        return new org.geoserver.catalog.plugin.DefaultMemoryCatalogFacade();
    }

    public @Override @Bean RepositoryGeoServerFacade geoserverFacade() {
        return new org.geoserver.config.plugin.RepositoryGeoServerFacadeImpl();
    }

    /**
     * Contributes the default {@link GeoServerLoader} if {@code
     * geoserver.backend.data-directory.parallel-loader=false}
     */
    @DependsOn({
        "extensions",
        "wmsLoader",
        "wfsLoader",
        "wcsLoader",
        "wpsServiceLoader",
        "wmtsLoader",
        "geoServerSecurityManager"
    })
    @Bean(name = "geoServerLoaderImpl")
    @ConditionalOnProperty(
            name = "geoserver.backend.data-directory.parallel-loader",
            havingValue = "false",
            matchIfMissing = false)
    public @Override GeoServerLoader geoServerLoaderImpl() {
        log.info("Using default data directory config loader");
        UpdateSequence updateSequence = updateSequence();
        GeoServerResourceLoader resourceLoader = resourceLoader();
        Catalog rawCatalog = rawCatalog();
        LockingGeoServer geoserver = geoServer(rawCatalog);
        return new DataDirectoryGeoServerLoader(
                updateSequence, resourceLoader, geoserver, rawCatalog);
    }

    /**
     * Contributes the optimized parallel {@link GeoServerLoader} if {@code
     * geoserver.backend.data-directory.parallel-loader=true} (default behavior).
     */
    @DependsOn({
        "extensions",
        "wmsLoader",
        "wfsLoader",
        "wcsLoader",
        "wpsServiceLoader",
        "wmtsLoader",
        "geoServerSecurityManager"
    })
    @Bean(name = "geoServerLoaderImpl")
    @ConditionalOnProperty(
            name = "geoserver.backend.data-directory.parallel-loader",
            havingValue = "true",
            matchIfMissing = true)
    public GeoServerLoader geoServerLoaderImplParallel(GeoServerSecurityManager securityManager) {
        log.info("Using optimized parallel data directory config loader");
        UpdateSequence updateSequence = updateSequence();
        GeoServerResourceLoader resourceLoader = resourceLoader();
        Catalog rawCatalog = rawCatalog();
        LockingGeoServer geoserver = geoServer(rawCatalog);

        return new ParallelDataDirectoryGeoServerLoader(
                updateSequence, resourceLoader, geoserver, rawCatalog, securityManager);
    }

    public @Override @Bean GeoServerResourceLoader resourceLoader() {
        ResourceStore resourceStoreImpl = resourceStoreImpl();
        GeoServerResourceLoader resourceLoader = new GeoServerResourceLoader(resourceStoreImpl);
        final @NonNull Path datadir = dataDirectoryFile();
        log.debug("geoserver.backend.data-directory.location: {}", datadir);
        resourceLoader.setBaseDirectory(datadir.toFile());
        return resourceLoader;
    }

    @Bean(name = {"resourceStoreImpl"})
    public @Override ResourceStore resourceStoreImpl() {
        final @NonNull File dataDirectory = dataDirectoryFile().toFile();
        NoServletContextDataDirectoryResourceStore store =
                new NoServletContextDataDirectoryResourceStore(dataDirectory);
        store.setLockProvider(new NoServletContextFileLockProvider(dataDirectory));
        return store;
    }

    private Path dataDirectoryFile() {
        DataDirectoryProperties dataDirectoryConfig = this.dataDirectoryConfig;
        Path path = dataDirectoryConfig.getLocation();
        Objects.requireNonNull(
                path, "geoserver.backend.data-directory.location config property resolves to null");
        return path;
    }
}
