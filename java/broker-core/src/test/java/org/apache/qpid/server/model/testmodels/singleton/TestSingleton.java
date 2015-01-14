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
package org.apache.qpid.server.model.testmodels.singleton;

import java.util.Map;
import java.util.Set;

import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.DerivedAttribute;
import org.apache.qpid.server.model.ManagedAttribute;
import org.apache.qpid.server.model.ManagedContextDefault;
import org.apache.qpid.server.model.ManagedObject;

@ManagedObject( defaultType = TestSingletonImpl.TEST_SINGLETON_TYPE)
public interface TestSingleton<X extends TestSingleton<X>> extends ConfiguredObject<X>
{
    String AUTOMATED_PERSISTED_VALUE = "automatedPersistedValue";
    String AUTOMATED_NONPERSISTED_VALUE = "automatedNonPersistedValue";
    String DERIVED_VALUE = "derivedValue";
    String DEFAULTED_VALUE = "defaultedValue";
    String STRING_VALUE = "stringValue";
    String MAP_VALUE = "mapValue";
    String ENUM_VALUE = "enumValue";
    String INT_VALUE = "intValue";
    String VALID_VALUE = "validValue";
    String SECURE_VALUE = "secureValue";
    String ENUMSET_VALUES = "enumSetValues";

    String TEST_CONTEXT_DEFAULT = "TEST_CONTEXT_DEFAULT";

    @ManagedContextDefault(name = TEST_CONTEXT_DEFAULT)
    String testGlobalDefault = "default";

    @ManagedAttribute
    String getAutomatedPersistedValue();

    @ManagedAttribute( persist = false )
    String getAutomatedNonPersistedValue();

    String DEFAULTED_VALUE_DEFAULT = "myDefaultVar";
    String VALID_VALUE1 = "FOO";
    String VALID_VALUE2 = "BAR";

    @ManagedAttribute( defaultValue = DEFAULTED_VALUE_DEFAULT)
    String getDefaultedValue();

    @ManagedAttribute
    String getStringValue();

    @ManagedAttribute
    Map<String,String> getMapValue();

    @ManagedAttribute
    TestEnum getEnumValue();

    @ManagedAttribute
    int getIntValue();

    @ManagedAttribute(validValues = {VALID_VALUE1, VALID_VALUE2} )
    String getValidValue();

    @ManagedAttribute( validValues = {"[\"TEST_ENUM1\"]", "[\"TEST_ENUM2\", \"TEST_ENUM3\"]"})
    Set<TestEnum> getEnumSetValues();

    @DerivedAttribute
    long getDerivedValue();

    @ManagedAttribute(secure = true)
    String getSecureValue();

}
