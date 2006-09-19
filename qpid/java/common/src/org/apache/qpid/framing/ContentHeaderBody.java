/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.framing;

import org.apache.mina.common.ByteBuffer;

public class ContentHeaderBody extends AMQBody
{
    public static final byte TYPE = 2;

    public int classId;

    public int weight;

    /** unsigned long but java can't handle that anyway when allocating byte array */
    public long bodySize;

    /** must never be null */
    public ContentHeaderProperties properties;

    public ContentHeaderBody()
    {
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

    protected byte getType()
    {
        return TYPE;
    }

    protected void populateFromBuffer(ByteBuffer buffer, long size) throws AMQFrameDecodingException
    {
        classId = buffer.getUnsignedShort();
        weight = buffer.getUnsignedShort();
        bodySize = buffer.getLong();
        int propertyFlags = buffer.getUnsignedShort();
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
    public static ContentHeaderBody createFromBuffer(ByteBuffer buffer, long size) throws AMQFrameDecodingException
    {
        ContentHeaderBody body = new ContentHeaderBody();
        body.populateFromBuffer(buffer, size);
        return body;
    }

    public int getSize()
    {
        return 2 + 2 + 8 + 2 + properties.getPropertyListSize();
    }

    public void writePayload(ByteBuffer buffer)
    {
        EncodingUtils.writeUnsignedShort(buffer, classId);
        EncodingUtils.writeUnsignedShort(buffer, weight);
        buffer.putLong(bodySize);
        EncodingUtils.writeUnsignedShort(buffer, properties.getPropertyFlags());
        properties.writePropertyListPayload(buffer);
    }

    public static AMQFrame createAMQFrame(int channelId, int classId, int weight, BasicContentHeaderProperties properties,
                                          long bodySize)
    {
        final AMQFrame frame = new AMQFrame();
        frame.channel = channelId;
        frame.bodyFrame = new ContentHeaderBody(classId, weight, properties, bodySize);
        return frame;
    }

    public static AMQFrame createAMQFrame(int channelId, ContentHeaderBody body)
    {
        final AMQFrame frame = new AMQFrame();
        frame.channel = channelId;
        frame.bodyFrame = body;
        return frame;
    }
}
