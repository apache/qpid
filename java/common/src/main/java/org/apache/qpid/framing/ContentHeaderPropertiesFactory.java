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
package org.apache.qpid.framing;

import org.apache.mina.common.ByteBuffer;

public class ContentHeaderPropertiesFactory
{
    private static final ContentHeaderPropertiesFactory _instance = new ContentHeaderPropertiesFactory();

    public static ContentHeaderPropertiesFactory getInstance()
    {
        return _instance;
    }

    private ContentHeaderPropertiesFactory()
    {
    }

    public ContentHeaderProperties createContentHeaderProperties(int classId, int propertyFlags,
                                                                 ByteBuffer buffer, int size)
             throws AMQFrameDecodingException
    {
        ContentHeaderProperties properties;
        switch (classId)
        {
            case BasicConsumeBody.CLASS_ID:
                properties = new BasicContentHeaderProperties();
                break;
            default:
                throw new AMQFrameDecodingException("Unsupport content header class id: " + classId);
        }
        properties.populatePropertiesFromBuffer(buffer, propertyFlags, size);
        return properties;
    }
}
