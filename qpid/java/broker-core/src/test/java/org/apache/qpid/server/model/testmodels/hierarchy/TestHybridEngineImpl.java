/*
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
 */

package org.apache.qpid.server.model.testmodels.hierarchy;

import java.util.Map;

import org.apache.qpid.server.model.AbstractConfiguredObject;
import org.apache.qpid.server.model.ManagedObject;
import org.apache.qpid.server.model.ManagedObjectFactoryConstructor;

@ManagedObject( category = false, type = TestHybridEngineImpl.TEST_HYBRID_ENGINE_TYPE)
public class TestHybridEngineImpl extends AbstractConfiguredObject<TestHybridEngineImpl> implements TestHybridEngine<TestHybridEngineImpl>
{
    public static final String TEST_HYBRID_ENGINE_TYPE = "HYBRID";

    @ManagedObjectFactoryConstructor
    public TestHybridEngineImpl(final Map<String, Object> attributes, TestCar<?> parent)
    {
        super(parentsMap(parent), attributes);
    }
}
