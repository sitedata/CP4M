/*
 *
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.chatbridge.configuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;
import com.meta.chatbridge.Service;
import com.meta.chatbridge.ServiceConfiguration;
import com.meta.chatbridge.ServicesRunner;
import com.meta.chatbridge.llm.LLMConfig;
import com.meta.chatbridge.llm.LLMPlugin;
import com.meta.chatbridge.message.HandlerConfig;
import com.meta.chatbridge.message.Message;
import com.meta.chatbridge.message.MessageHandler;
import com.meta.chatbridge.store.ChatStore;
import com.meta.chatbridge.store.StoreConfig;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.checkerframework.checker.nullness.qual.Nullable;

public class RootConfiguration {
  private final Map<String, LLMConfig> plugins;
  private final Map<String, StoreConfig> stores;
  private final Map<String, HandlerConfig> handlers;
  private final Collection<ServiceConfiguration> services;

  private final int port;

  @JsonCreator
  RootConfiguration(
      @JsonProperty("plugins") Collection<LLMConfig> plugins,
      @JsonProperty("stores") Collection<StoreConfig> stores,
      @JsonProperty("handlers") Collection<HandlerConfig> handlers,
      @JsonProperty("services") Collection<ServiceConfiguration> services,
      @JsonProperty("port") @Nullable Integer port) {
    this.port = port == null ? 8080 : port;
    Preconditions.checkArgument(
        this.port >= 0 && this.port <= 65535, "port must be between 0 and 65535");

    Preconditions.checkArgument(
        plugins != null && !plugins.isEmpty(), "At least one plugin must defined");
    Preconditions.checkArgument(
        stores != null && !stores.isEmpty(), "at least one store must be defined");
    Preconditions.checkArgument(
        handlers != null && !handlers.isEmpty(), "at least one handler must be defined");
    Preconditions.checkArgument(
        services != null && !services.isEmpty(), "at least one service must be defined");

    Preconditions.checkArgument(
        plugins.size()
            == plugins.stream().map(LLMConfig::name).collect(Collectors.toUnmodifiableSet()).size(),
        "all plugin names must be unique");
    this.plugins =
        plugins.stream()
            .collect(Collectors.toUnmodifiableMap(LLMConfig::name, Function.identity()));

    Preconditions.checkArgument(
        stores.size()
            == stores.stream()
                .map(StoreConfig::name)
                .collect(Collectors.toUnmodifiableSet())
                .size(),
        "all store names must be unique");
    this.stores =
        stores.stream()
            .collect(Collectors.toUnmodifiableMap(StoreConfig::name, Function.identity()));

    Preconditions.checkArgument(
        handlers.size()
            == handlers.stream()
                .map(HandlerConfig::name)
                .collect(Collectors.toUnmodifiableSet())
                .size(),
        "all handler names must be unique");
    this.handlers =
        handlers.stream()
            .collect(Collectors.toUnmodifiableMap(HandlerConfig::name, Function.identity()));

    for (ServiceConfiguration s : services) {
      Preconditions.checkArgument(
          this.plugins.containsKey(s.plugin()), s.plugin() + " must be the name of a plugin");
      Preconditions.checkArgument(
          this.stores.containsKey(s.store()), s.store() + " must be the name of a store");
      Preconditions.checkArgument(
          this.handlers.containsKey(s.handler()), s.handler() + " must be the name of a handler");
    }
    this.services = services;
  }

  Collection<LLMConfig> plugins() {
    return Collections.unmodifiableCollection(plugins.values());
  }

  Collection<StoreConfig> stores() {
    return Collections.unmodifiableCollection(stores.values());
  }

  Collection<HandlerConfig> handlers() {
    return Collections.unmodifiableCollection(handlers.values());
  }

  Collection<ServiceConfiguration> services() {
    return Collections.unmodifiableCollection(services);
  }

  public int port() {
    return port;
  }

  private <T extends Message> Service<T> createService(
      MessageHandler<T> handler, ServiceConfiguration serviceConfig) {
    LLMPlugin<T> plugin = plugins.get(serviceConfig.plugin()).toPlugin();
    ChatStore<T> store = stores.get(serviceConfig.store()).toStore();
    return new Service<>(store, handler, plugin, serviceConfig.webhookPath());
  }

  public ServicesRunner toServicesRunner() {
    ServicesRunner runner = ServicesRunner.newInstance().port(port);
    for (ServiceConfiguration service : services) {
      MessageHandler<?> handler = handlers.get(service.handler()).toMessageHandler();
      runner.service(createService(handler, service));
    }
    return runner;
  }
}
