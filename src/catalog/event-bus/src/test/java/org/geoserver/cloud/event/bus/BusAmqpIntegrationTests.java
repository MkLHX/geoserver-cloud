/*
 * Copyright 2015-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.geoserver.cloud.event.bus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;

import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CatalogTestData;
import org.geoserver.catalog.Info;
import org.geoserver.catalog.impl.ClassMappings;
import org.geoserver.catalog.impl.ModificationProxy;
import org.geoserver.catalog.plugin.Patch;
import org.geoserver.catalog.plugin.PropertyDiff;
import org.geoserver.cloud.event.GeoServerEvent;
import org.geoserver.cloud.event.catalog.CatalogInfoAdded;
import org.geoserver.cloud.event.catalog.CatalogInfoModified;
import org.geoserver.cloud.event.config.ConfigInfoModified;
import org.geoserver.cloud.event.info.ConfigInfoType;
import org.geoserver.cloud.event.info.InfoAdded;
import org.geoserver.cloud.event.info.InfoEvent;
import org.geoserver.cloud.event.info.InfoModified;
import org.geoserver.cloud.event.info.InfoRemoved;
import org.geoserver.config.GeoServer;
import org.geoserver.config.GeoServerInfo;
import org.geoserver.config.LoggingInfo;
import org.geoserver.config.SettingsInfo;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.function.Consumer;

@SpringBootTest(
        webEnvironment = RANDOM_PORT,
        classes = {TestConfigurationAutoConfiguration.class, BusEventCollector.class},
        properties = {
            "spring.cloud.bus.id=app:1",
            "spring.cloud.stream.bindings.springCloudBusOutput.producer.errorChannelEnabled=true",
            "spring.autoconfigure.exclude=org.springframework.cloud.stream.test.binder.TestSupportBinderAutoConfiguration",
            "logging.level.root=WARN",
            "logging.level.org.geoserver.cloud.bus.integration=info",
            "logging.level.org.springframework.cloud.bus.BusConsumer=debug"
        })
@Testcontainers
public abstract class BusAmqpIntegrationTests {

    @Container
    private static final RabbitMQContainer rabbitMQContainer =
            new RabbitMQContainer("rabbitmq:3.9-management");

    protected static ConfigurableApplicationContext remoteAppContext;
    private @Autowired ConfigurableApplicationContext localAppContext;

    protected @Autowired GeoServer geoserver;
    protected @Autowired Catalog catalog;

    protected CatalogTestData testData;

    protected EventsCaptor eventsCaptor;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", rabbitMQContainer::getHost);
        registry.add("spring.rabbitmq.port", rabbitMQContainer::getAmqpPort);
    }

    @BeforeAll
    static void setUpRemoteApplicationContext() {
        remoteAppContext =
                new SpringApplicationBuilder(
                                TestConfigurationAutoConfiguration.class, BusEventCollector.class)
                        .properties(
                                "server.port=0",
                                "spring.rabbitmq.host=" + rabbitMQContainer.getHost(),
                                "spring.rabbitmq.port=" + rabbitMQContainer.getAmqpPort(),
                                "spring.cloud.bus.id=app:2",
                                "spring.autoconfigure.exclude=org.springframework.cloud.stream.test.binder.TestSupportBinderAutoConfiguration")
                        .run();
    }

    @AfterAll
    static void afterAll() {
        if (remoteAppContext != null) {
            remoteAppContext.close();
        }
    }

    protected void setupClean() {
        eventsCaptor.stop();

        testData.initCatalog(true).initConfig(true).initialize();

        Catalog remoteCatalog = remoteAppContext.getBean("rawCatalog", Catalog.class);
        GeoServer remoteGeoserver = remoteAppContext.getBean(GeoServer.class);
        CatalogTestData remoteTestData =
                CatalogTestData.empty(() -> remoteCatalog, () -> remoteGeoserver).initialize();
        remoteTestData.initCatalog(true).initConfig(true).initialize();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        eventsCaptor.clear();
    }

    @BeforeEach
    public void before() {
        assertThat(rabbitMQContainer.isRunning());
        BusEventCollector localAppEvents = localAppContext.getBean(BusEventCollector.class);
        BusEventCollector remoteAppEvents = remoteAppContext.getBean(BusEventCollector.class);
        this.eventsCaptor = new EventsCaptor(localAppEvents, remoteAppEvents);

        eventsCaptor.stop().clear().capureEventsOf(InfoEvent.class);

        testData = CatalogTestData.empty(() -> catalog, () -> geoserver).initialize();
    }

    @AfterEach
    void after() {
        eventsCaptor.stop();
        eventsCaptor.clear();
        testData.deleteAll();
    }

    @SuppressWarnings({"rawtypes"})
    protected <T extends Info, E extends InfoRemoved> E testRemoteRemoveEvent(
            T info, Consumer<T> remover, Class<E> eventType) {

        this.eventsCaptor.clear().start();
        remover.accept(info);

        RemoteGeoServerEvent event = eventsCaptor.local().expectOne(eventType);
        assertRemoteEvent(info, event);

        // local-remote event ok, check the one sent over the wire

        RemoteGeoServerEvent parsedSentEvent = eventsCaptor.remote().expectOne(eventType);
        assertRemoteEvent(info, parsedSentEvent);
        return eventType.cast(event.getEvent());
    }

    protected <T extends Info> Patch testCatalogInfoModifyEvent( //
            @NonNull T info, //
            @NonNull Consumer<T> modifier, //
            @NonNull Consumer<T> saver) {
        return testRemoteModifyEvent(info, modifier, saver, CatalogInfoModified.class, true);
    }

    protected <T extends Info> Patch testCatalogInfoModifyEventNoEquals( //
            @NonNull T info, //
            @NonNull Consumer<T> modifier, //
            @NonNull Consumer<T> saver) {
        return testRemoteModifyEvent(info, modifier, saver, CatalogInfoModified.class, false);
    }

    protected <T extends Info> Patch testConfigInfoModifyEvent( //
            @NonNull T info, //
            @NonNull Consumer<T> modifier, //
            @NonNull Consumer<T> saver) {
        return testRemoteModifyEvent(info, modifier, saver, ConfigInfoModified.class, true);
    }

    protected <T extends Info> Patch testConfigInfoModifyEventNoEquals( //
            @NonNull T info, //
            @NonNull Consumer<T> modifier, //
            @NonNull Consumer<T> saver) {
        return testRemoteModifyEvent(info, modifier, saver, ConfigInfoModified.class, false);
    }

    @SuppressWarnings({"rawtypes"})
    protected <T extends Info> Patch testRemoteModifyEvent( //
            @NonNull T info, //
            @NonNull Consumer<T> modifier, //
            @NonNull Consumer<T> saver,
            @NonNull Class<? extends InfoModified> eventType) {
        return testRemoteModifyEvent(info, modifier, saver, eventType, true);
    }

    @SuppressWarnings({"rawtypes"})
    protected <T extends Info> Patch testRemoteModifyEvent( //
            @NonNull T info, //
            @NonNull Consumer<T> modifier, //
            @NonNull Consumer<T> saver,
            @NonNull Class<? extends InfoModified> eventType,
            boolean comparePatch) {

        this.eventsCaptor.stop().clear();

        final Class<T> type = resolveInfoInterface(info);
        final ConfigInfoType infoType = ConfigInfoType.valueOf(info);
        T proxy = ModificationProxy.create(ModificationProxy.unwrap(info), type);
        modifier.accept(proxy);

        Patch expected = resolveExpectedDiff(proxy).clean().toPatch();
        assertThat(expected.size()).isGreaterThan(0);

        this.eventsCaptor.start();
        saver.accept(proxy);

        RemoteGeoServerEvent localRemoteEvent = eventsCaptor.local().expectOne(eventType, infoType);
        assertRemoteEvent(info, localRemoteEvent);

        RemoteGeoServerEvent sentRemoteEvent = eventsCaptor.remote().expectOne(eventType, infoType);
        assertRemoteEvent(info, sentRemoteEvent);

        InfoModified localModifyEvent = (InfoModified) localRemoteEvent.getEvent();
        InfoModified remoteModifyEvent = (InfoModified) sentRemoteEvent.getEvent();

        Patch localPatch = localModifyEvent.getPatch();
        Patch remotePatch = remoteModifyEvent.getPatch();

        assertEquals(expected.getPropertyNames(), localPatch.getPropertyNames());
        assertEquals(expected.getPropertyNames(), remotePatch.getPropertyNames());
        if (comparePatch) {
            assertEquals(expected, localPatch);
            assertEquals(expected, remotePatch);
        }
        return remotePatch;
    }

    protected <T extends Info> PropertyDiff resolveExpectedDiff(T proxy) {
        ModificationProxy h = (ModificationProxy) Proxy.getInvocationHandler(proxy);
        List<String> propertyNames = h.getPropertyNames();
        List<Object> newValues = h.getNewValues();
        List<Object> oldValues = h.getOldValues();
        assertFalse(propertyNames.isEmpty(), "Test should change at least one property");

        PropertyDiff expected = PropertyDiff.valueOf(propertyNames, oldValues, newValues);
        return expected;
    }

    @SuppressWarnings("unchecked")
    protected <T extends Info> Class<T> resolveInfoInterface(T info) {
        Class<T> type;
        ClassMappings classMappings =
                ClassMappings.fromImpl(ModificationProxy.unwrap(info).getClass());
        if (classMappings != null) {
            type = (Class<T>) classMappings.getInterface();
        } else if (info instanceof GeoServerInfo) {
            type = (Class<T>) GeoServerInfo.class;
        } else if (info instanceof SettingsInfo) {
            type = (Class<T>) SettingsInfo.class;
        } else if (info instanceof LoggingInfo) {
            type = (Class<T>) LoggingInfo.class;
        } else {
            throw new IllegalArgumentException("Unknown Info type: " + info);
        }
        return type;
    }

    protected <T extends Info> RemoteGeoServerEvent testRemoteCatalogInfoAddEvent(
            T info, Consumer<T> addOp) {
        return testRemoteAddEvent(info, addOp, CatalogInfoAdded.class);
    }

    @SuppressWarnings({"rawtypes"})
    protected <T extends Info> RemoteGeoServerEvent testRemoteAddEvent(
            T info, Consumer<T> addOp, Class<? extends InfoAdded> eventType) {

        this.eventsCaptor.stop().clear().capureEventsOf(eventType);
        eventsCaptor.start();
        addOp.accept(info);

        final ConfigInfoType infoType = ConfigInfoType.valueOf(info);

        RemoteGeoServerEvent localRemoteEvent = eventsCaptor.local().expectOne(eventType, infoType);
        assertThat(localRemoteEvent.getEvent().isRemote()).isFalse();
        assertThat(localRemoteEvent.getEvent().isLocal()).isTrue();
        assertRemoteEvent(info, localRemoteEvent);

        // ok, that's the event published to the local application context, and which
        // spring-cloud-bus took care of not re-publishing. Let's capture the actual out-bound
        // message that traveled through the bus channel to the second application
        RemoteGeoServerEvent remoteRemoteEvent =
                eventsCaptor.remote().expectOne(eventType, infoType);
        assertThat(remoteRemoteEvent.getEvent().isRemote()).isTrue();
        assertThat(remoteRemoteEvent.getEvent().isLocal()).isFalse();
        assertRemoteEvent(info, remoteRemoteEvent);
        return remoteRemoteEvent;
    }

    @SuppressWarnings("rawtypes")
    protected <T extends Info> void assertRemoteEvent(T info, RemoteGeoServerEvent busEvent) {
        assertNotNull(busEvent.getId());
        assertNotNull(busEvent.getOriginService());
        assertEquals("**", busEvent.getDestinationService());

        GeoServerEvent<?> event = busEvent.getEvent();
        assertNotNull(event);
        assertNotNull(((InfoEvent) event).getObjectId());
        // assertNotNull(event.getTarget());
        // assertNull(event.getSource());

        final ConfigInfoType infoType = ((InfoEvent) event).getObjectType();
        assertThat(infoType).isNotNull();
        ConfigInfoType expectedType = ConfigInfoType.valueOf(info);
        assertThat(infoType).isEqualTo(expectedType);

        switch (infoType) {
            case Catalog:
            case GeoServerInfo:
            case LoggingInfo:
                assertNotNull(((InfoEvent) event).getObjectId());
                break;
            default:
                assertEquals(info.getId(), ((InfoEvent) event).getObjectId());
                break;
        }
        assertThat(infoType.isInstance(info)).isTrue();

        if (event instanceof InfoAdded) {
            InfoAdded e = (InfoAdded) event;
            assertThat(e.getObject()).isNotNull();
            assertThat(infoType.isInstance(e.getObject())).isTrue();
            assertThat(e.getObject().getId()).isEqualTo(info.getId());

            Info object = e.getObject();
            info = ModificationProxy.unwrap(info);
            object = ModificationProxy.unwrap(object);
            // testData.assertEqualsLenientConnectionParameters(info, object);
        }

        if (event instanceof InfoModified) {
            InfoModified modifyEvent = (InfoModified) event;
            assertThat(modifyEvent.getPatch()).isNotNull();
        }
    }

    @Accessors(fluent = true)
    @AllArgsConstructor
    protected static class EventsCaptor {
        final @Getter BusEventCollector local;
        final @Getter BusEventCollector remote;

        @SuppressWarnings("rawtypes")
        public <E extends InfoEvent> EventsCaptor capureEventsOf(Class<E> type) {
            local.capture(type);
            remote.capture(type);
            return this;
        }

        public EventsCaptor stop() {
            remote.stop();
            local.stop();
            return this;
        }

        public EventsCaptor start() {
            local.start();
            remote.start();
            return this;
        }

        public EventsCaptor clear() {
            remote.clear();
            local.clear();
            return this;
        }
    }
}
