/*
 * (c) 2020 Open Source Geospatial Foundation - all rights reserved This code is licensed under the
 * GPL 2.0 license, available at the root application directory.
 */
package org.geotools.jackson.databind.filter;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import lombok.extern.slf4j.Slf4j;

import org.geotools.jackson.databind.filter.dto.Literal;
import org.geotools.jackson.databind.filter.dto.LiteralDeserializer;
import org.geotools.jackson.databind.filter.dto.LiteralSerializer;
import org.geotools.jackson.databind.filter.mapper.ExpressionMapper;
import org.geotools.jackson.databind.filter.mapper.FilterMapper;
import org.geotools.jackson.databind.filter.mapper.ValueMappers;
import org.geotools.jackson.databind.geojson.GeoToolsGeoJsonModule;
import org.geotools.jackson.databind.util.MapperDeserializer;
import org.geotools.jackson.databind.util.MapperSerializer;
import org.locationtech.jts.geom.Geometry;
import org.mapstruct.factory.Mappers;
import org.opengis.filter.Filter;
import org.opengis.filter.capability.FunctionName;
import org.opengis.filter.expression.Expression;
import org.opengis.filter.sort.SortBy;

import java.util.function.Function;

/**
 * Jackson {@link com.fasterxml.jackson.databind.Module} to handle GeoTools {@link Filter} and
 * {@link Expression} bindings.
 *
 * <p>Depends on {@link GeoToolsGeoJsonModule} to being able of encoding and decoding JTS {@link
 * Geometry} literals.
 *
 * <p>When running a spring-boot application, being on the classpath should be enough to get this
 * module auto-registered to all {@link ObjectMapper}s, by means of being registered under {@code
 * META-INF/services/com.fasterxml.jackson.databind.Module}.
 *
 * <p>To register the module for a specific {@link ObjectMapper}, either:
 *
 * <pre>
 * <code>
 * ObjectMapper objectMapper = ...
 * objectMapper.findAndRegisterModules();
 * </code>
 * </pre>
 *
 * Or:
 *
 * <pre>
 * <code>
 * ObjectMapper objectMapper = ...
 * objectMapper.registerModule(new GeoToolsGeoJsonModule());
 * objectMapper.registerModule(new GeoToolsFilterModule());
 * </code>
 * </pre>
 */
@Slf4j
public class GeoToolsFilterModule extends SimpleModule {
    private static final long serialVersionUID = 4898575169880138758L;

    private static final FilterMapper FILTERS = Mappers.getMapper(FilterMapper.class);
    private static final ExpressionMapper EXPRESSIONS = Mappers.getMapper(ExpressionMapper.class);
    private static final ValueMappers VALUES = Mappers.getMapper(ValueMappers.class);

    public GeoToolsFilterModule() {
        super(GeoToolsFilterModule.class.getSimpleName(), new Version(1, 0, 0, null, null, null));

        log.debug("registering jackson de/serializers for geotools Filter and Expression");

        addSerializer(
                Expression.class,
                new MapperSerializer<>(
                        org.opengis.filter.expression.Expression.class, EXPRESSIONS::map));
        addSerializer(Literal.class, new LiteralSerializer());
        addDeserializer(
                Expression.class,
                new MapperDeserializer<>(
                        org.geotools.jackson.databind.filter.dto.Expression.class,
                        EXPRESSIONS::map));
        addDeserializer(Literal.class, new LiteralDeserializer());

        addSerializer(Filter.class, new MapperSerializer<>(Filter.class, FILTERS::map));
        addDeserializer(
                Filter.class,
                new MapperDeserializer<>(
                        org.geotools.jackson.databind.filter.dto.Filter.class, FILTERS::map));

        addSerializer(SortBy.class, new MapperSerializer<>(SortBy.class, FILTERS::map));
        addDeserializer(
                SortBy.class,
                new MapperDeserializer<>(
                        org.geotools.jackson.databind.filter.dto.SortBy.class, FILTERS::map));

        addSerializer(
                FunctionName.class, new MapperSerializer<>(FunctionName.class, EXPRESSIONS::map));
        addDeserializer(
                FunctionName.class,
                new MapperDeserializer<>(
                        org.geotools.jackson.databind.filter.dto.Expression.FunctionName.class,
                        EXPRESSIONS::map));

        addCustomLiteralValueSerializers();
    }

    /** */
    private void addCustomLiteralValueSerializers() {
        addMapperSerializer(
                java.awt.Color.class,
                VALUES::awtColorToString,
                String.class,
                VALUES::stringToAwtColor);
    }

    private <T, DTO> void addMapperSerializer(
            Class<T> type,
            Function<T, DTO> serializerMapper,
            Class<DTO> dtoType,
            Function<DTO, T> deserializerMapper) {

        MapperSerializer<T, DTO> serializer = new MapperSerializer<>(type, serializerMapper);
        MapperDeserializer<DTO, T> deserializer =
                new MapperDeserializer<>(dtoType, deserializerMapper);
        super.addSerializer(type, serializer);
        super.addDeserializer(type, deserializer);
    }
}
