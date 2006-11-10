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
package org.apache.qpid.management.messaging;

import org.apache.qpid.schema.cml.CmlDocument;
import org.apache.qpid.schema.cml.InspectRequestType;
import org.apache.qpid.schema.cml.MethodRequestType;

public class CMLMessageFactory
{
    public static String createSchemaRequest()
    {
        CmlDocument cmlDoc = CmlDocument.Factory.newInstance();
        CmlDocument.Cml cml = createStandardCml(cmlDoc);
        cml.addNewSchemaRequest();
        return cmlDoc.toString();
    }

    public static String createInspectRequest(int objectId)
    {
        CmlDocument cmlDoc = CmlDocument.Factory.newInstance();
        CmlDocument.Cml cml = createStandardCml(cmlDoc);
        InspectRequestType inspect = cml.addNewInspectRequest();
        inspect.setObject(objectId);
        return cmlDoc.toString();
    }

    public static String createMethodRequest(int objectId, String methodName)
    {
        CmlDocument cmlDoc = CmlDocument.Factory.newInstance();
        CmlDocument.Cml cml = createStandardCml(cmlDoc);
        MethodRequestType methodReq = cml.addNewMethodRequest();
        methodReq.setObject(objectId);
        methodReq.setName(methodName);
        return cmlDoc.toString();
    }

    private static CmlDocument.Cml createStandardCml(CmlDocument cmlDoc)
    {
        CmlDocument.Cml cml = cmlDoc.addNewCml();
        cml.setVersion("1.0");
        return cml;
    }
}
