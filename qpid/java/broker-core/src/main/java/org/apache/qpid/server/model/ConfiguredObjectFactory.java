/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.apache.qpid.server.model;

import java.util.Collection;
import java.util.Map;

import com.google.common.util.concurrent.ListenableFuture;

import org.apache.qpid.server.plugin.ConfiguredObjectTypeFactory;
import org.apache.qpid.server.store.ConfiguredObjectRecord;
import org.apache.qpid.server.store.UnresolvedConfiguredObject;

public interface ConfiguredObjectFactory
{
    <X extends ConfiguredObject<X>> UnresolvedConfiguredObject<X> recover(ConfiguredObjectRecord record,
                                                                          ConfiguredObject<?>... parents);

    <X extends ConfiguredObject<X>> X create(Class<X> clazz, Map<String, Object> attributes, ConfiguredObject<?>... parents);

    <X extends ConfiguredObject<X>> ListenableFuture<X> createAsync(Class<X> clazz, Map<String, Object> attributes, ConfiguredObject<?>... parents);



    <X extends ConfiguredObject<X>> ConfiguredObjectTypeFactory<X> getConfiguredObjectTypeFactory(Class<X> categoryClass,
                                                                                                  Map<String, Object> attributes);

    <X extends ConfiguredObject<X>> ConfiguredObjectTypeFactory<X> getConfiguredObjectTypeFactory(String category,
                                                                                                  String type);

    Collection<String> getSupportedTypes(Class<? extends ConfiguredObject> category);

    Model getModel();
}
