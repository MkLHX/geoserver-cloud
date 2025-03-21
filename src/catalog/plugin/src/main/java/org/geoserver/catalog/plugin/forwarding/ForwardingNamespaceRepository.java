/*
 * (c) 2020 Open Source Geospatial Foundation - all rights reserved This code is licensed under the
 * GPL 2.0 license, available at the root application directory.
 */
package org.geoserver.catalog.plugin.forwarding;

import org.geoserver.catalog.NamespaceInfo;
import org.geoserver.catalog.plugin.CatalogInfoRepository.NamespaceRepository;

import java.util.Optional;
import java.util.stream.Stream;

public class ForwardingNamespaceRepository
        extends ForwardingCatalogRepository<NamespaceInfo, NamespaceRepository>
        implements NamespaceRepository {

    public ForwardingNamespaceRepository(NamespaceRepository subject) {
        super(subject);
    }

    public @Override void setDefaultNamespace(NamespaceInfo namespace) {
        subject.setDefaultNamespace(namespace);
    }

    public @Override Optional<NamespaceInfo> getDefaultNamespace() {
        return subject.getDefaultNamespace();
    }

    public @Override Optional<NamespaceInfo> findOneByURI(String uri) {
        return subject.findOneByURI(uri);
    }

    public @Override Stream<NamespaceInfo> findAllByURI(String uri) {
        return subject.findAllByURI(uri);
    }

    public @Override void unsetDefaultNamespace() {
        subject.unsetDefaultNamespace();
    }
}
