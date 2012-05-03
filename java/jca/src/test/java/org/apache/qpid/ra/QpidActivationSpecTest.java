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
package org.apache.qpid.ra;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;

import javax.resource.spi.ResourceAdapterInternalException;

import org.apache.qpid.ra.inflow.QpidActivationSpec;

import junit.framework.TestCase;

public class QpidActivationSpecTest extends TestCase
{

    public void testActivationSpecBasicSerialization() throws Exception
    {
        QpidActivationSpec spec = new QpidActivationSpec();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(out);
        oos.writeObject(spec);
        oos.close();
        assertTrue(out.toByteArray().length > 0);
    }

}
