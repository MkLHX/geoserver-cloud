/*
 * (c) 2020 Open Source Geospatial Foundation - all rights reserved This code is licensed under the
 * GPL 2.0 license, available at the root application directory.
 */
package org.geoserver.cloud.event.catalog;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;

import lombok.NonNull;

import org.geoserver.catalog.CatalogInfo;
import org.geoserver.cloud.event.info.ConfigInfoType;
import org.geoserver.cloud.event.info.InfoRemoved;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_OBJECT)
@JsonTypeName("CatalogInfoRemoved")
public class CatalogInfoRemoved extends InfoRemoved<CatalogInfoRemoved, CatalogInfo> {

    protected CatalogInfoRemoved() {}

    CatalogInfoRemoved(
            @NonNull Long updateSequence, @NonNull String id, @NonNull ConfigInfoType type) {
        super(updateSequence, id, type);
    }

    public static CatalogInfoRemoved createLocal(
            @NonNull Long updateSequence, @NonNull CatalogInfo info) {
        String id = resolveId(info);
        ConfigInfoType type = ConfigInfoType.valueOf(info);
        return new CatalogInfoRemoved(updateSequence, id, type);
    }
}
