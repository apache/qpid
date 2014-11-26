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

import org.apache.qpid.server.model.ManagedObject;

/**
 * This is a test managed type extending TestManagedClass0.
 * Because TestManagedClass0 implements managed interface TestManagedInterface1 with ManagedAnnotation set,
 * the instances of this class will be managed entities of type TestManagedInterface1.
 */
@ManagedObject( category = false , type = "ChildClass" )
public class TestManagedClass1 extends TestManagedClass0
{
    public TestManagedClass1(final Map<String, Object> attributes, TestRootCategory<?> parent)
    {
        super(attributes, parent);
    }
}
