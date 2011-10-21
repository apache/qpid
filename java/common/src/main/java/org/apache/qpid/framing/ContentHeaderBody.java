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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.apache.qpid.protocol.AMQVersionAwareProtocolSession;
import org.apache.qpid.AMQException;

public class ContentHeaderBody implements AMQBody
{
    public static final byte TYPE = 2;

    public int classId;

    public int weight;

    /** unsigned long but java can't handle that anyway when allocating byte array */
    public long bodySize;

    /** must never be null */
    private ContentHeaderProperties properties;

    public ContentHeaderBody()
    {
    }

    public ContentHeaderBody(DataInputStream buffer, long size) throws AMQFrameDecodingException, IOException
    {
        classId = buffer.readUnsignedShort();
        weight = buffer.readUnsignedShort();
        bodySize = buffer.readLong();
        int propertyFlags = buffer.readUnsignedShort();
        ContentHeaderPropertiesFactory factory = ContentHeaderPropertiesFactory.getInstance();
        properties = factory.createContentHeaderProperties(classId, propertyFlags, buffer, (int)size - 14);

    }


    public ContentHeaderBody(ContentHeaderProperties props, int classId)
    {
        properties = props;
        this.classId = classId;
    }

    public ContentHeaderBody(int classId, int weight, ContentHeaderProperties props, long bodySize)
    {
        this(props, classId);
        this.weight = weight;
        this.bodySize = bodySize;
    }

    public byte getFrameType()
    {
        return TYPE;
    }

    protected void populateFromBuffer(DataInputStream buffer, long size)
        throws AMQFrameDecodingException, AMQProtocolVersionException, IOException
    {
        classId = buffer.readUnsignedShort();
        weight = buffer.readUnsignedShort();
        bodySize = buffer.readLong();
        int propertyFlags = buffer.readUnsignedShort();
        ContentHeaderPropertiesFactory factory = ContentHeaderPropertiesFactory.getInstance();
        properties = factory.createContentHeaderProperties(classId, propertyFlags, buffer, (int)size - 14);
    }

    /**
     * Helper method that is used currently by the persistence layer (by BDB at the moment).
     * @param buffer
     * @param size
     * @return
     * @throws AMQFrameDecodingException
     */
    public static ContentHeaderBody createFromBuffer(DataInputStream buffer, long size)
        throws AMQFrameDecodingException, AMQProtocolVersionException, IOException
    {
        ContentHeaderBody body = new ContentHeaderBody(buffer, size);
        
        return body;
    }

    public int getSize()
    {
        return 2 + 2 + 8 + 2 + properties.getPropertyListSize();
    }

    public void writePayload(DataOutputStream buffer) throws IOException
    {
        EncodingUtils.writeUnsignedShort(buffer, classId);
        EncodingUtils.writeUnsignedShort(buffer, weight);
        buffer.writeLong(bodySize);
        EncodingUtils.writeUnsignedShort(buffer, properties.getPropertyFlags());
        properties.writePropertyListPayload(buffer);
    }

    public void handle(final int channelId, final AMQVersionAwareProtocolSession session)
            throws AMQException
    {
        session.contentHeaderReceived(channelId, this);
    }

    public static AMQFrame createAMQFrame(int channelId, int classId, int weight, BasicContentHeaderProperties properties,
                                          long bodySize)
    {
        return new AMQFrame(channelId, new ContentHeaderBody(classId, weight, properties, bodySize));
    }

    public static AMQFrame createAMQFrame(int channelId, ContentHeaderBody body)
    {
        return new AMQFrame(channelId, body);
    }

    public ContentHeaderProperties getProperties()
    {
        return properties;
    }

    public void setProperties(ContentHeaderProperties props)
    {
        properties = props;
    }

    @Override
    public String toString()
    {
        return "ContentHeaderBody{" +
                "classId=" + classId +
                ", weight=" + weight +
                ", bodySize=" + bodySize +
                ", properties=" + properties +
                '}';
    }
}
