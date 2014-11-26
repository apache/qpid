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
package org.apache.qpid.server.model.testmodel;

import java.util.Map;

import org.apache.qpid.server.model.ManagedInterface;
import org.apache.qpid.server.model.ManagedObject;

/**
 * This is a test managed type implementing TestManagedInterface1 which extends ManagedInterface.
 * Because TestManagedInterface1 already has ManagedAnnotation set, the instances of this class will be managed entities
 * of type TestManagedInterface1.
 */
@ManagedObject( category = false , type = "SuperClass" )
public class TestManagedClass0 extends TestChildCategoryImpl implements TestManagedInterface1
{
    public TestManagedClass0(final Map<String, Object> attributes, TestRootCategory<?> parent)
    {
        super(attributes, parent);
    }
}
