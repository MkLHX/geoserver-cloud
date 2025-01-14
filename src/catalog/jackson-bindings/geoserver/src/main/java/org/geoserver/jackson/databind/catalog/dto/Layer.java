/*
 * (c) 2020 Open Source Geospatial Foundation - all rights reserved This code is licensed under the
 * GPL 2.0 license, available at the root application directory.
 */
package org.geoserver.jackson.databind.catalog.dto;

import com.fasterxml.jackson.annotation.JsonTypeName;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Generated;

import java.util.Set;

@Data
@Generated
@EqualsAndHashCode(callSuper = true)
@JsonTypeName("LayerInfo")
public class Layer extends Published {
    public enum WMSInterpolation {
        Nearest,
        Bilinear,
        Bicubic
    }

    protected String path;
    protected InfoReference defaultStyle;
    protected Set<InfoReference> styles;
    protected InfoReference resource;
    protected Legend legend;
    private PublishedType type;
    protected Boolean queryable;
    protected Boolean opaque;
    protected WMSInterpolation defaultWMSInterpolationMethod;
}
