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
package org.apache.qpid.server.store;

import java.nio.ByteBuffer;

import org.apache.qpid.server.message.AMQMessageHeader;
import org.apache.qpid.server.message.MessageReference;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.plugin.MessageMetaDataType;

public class TestMessageMetaDataType implements MessageMetaDataType<TestMessageMetaData>
{
    //largest metadata type value the BDBMessageStore can store (it uses a byte)
    private static final byte TYPE = 7;
    public static final String V0_8 = "v0_8";

    @Override
    public int ordinal()
    {
        return TYPE;
    }

    @Override
    public TestMessageMetaData createMetaData(ByteBuffer buf)
    {
        return TestMessageMetaData.FACTORY.createMetaData(buf);
    }

    @Override
    public ServerMessage<TestMessageMetaData> createMessage(StoredMessage<TestMessageMetaData> msg)
    {
        return new TestServerMessage(msg);
    }

    public int hashCode()
    {
        return ordinal();
    }

    public boolean equals(Object o)
    {
        return o != null && o.getClass() == getClass();
    }

    @Override
    public String getType()
    {
        return V0_8;
    }


    private static class TestServerMessage implements ServerMessage<TestMessageMetaData>
    {
        private final StoredMessage<TestMessageMetaData> _storedMsg;

        private final MessageReference<ServerMessage> _messageReference = new MessageReference<ServerMessage>()
        {

            @Override
            public ServerMessage getMessage()
            {
                return TestServerMessage.this;
            }

            @Override
            public void release()
            {
            }
        };

        public TestServerMessage(StoredMessage<TestMessageMetaData> storedMsg)
        {
            _storedMsg = storedMsg;
        }
        @Override
        public long getArrivalTime()
        {
            return 0;
        }

        @Override
        public int getContent(ByteBuffer buf, int offset)
        {
            return 0;
        }

        @Override
        public ByteBuffer getContent(int offset, int size)
        {
            return null;
        }

        @Override
        public Object getConnectionReference()
        {
            return null;
        }

        @Override
        public long getExpiration()
        {
            return 0;
        }

        @Override
        public AMQMessageHeader getMessageHeader()
        {
            return null;
        }

        @Override
        public long getMessageNumber()
        {
            return _storedMsg.getMessageNumber();
        }

        @Override
        public String getInitialRoutingAddress()
        {
            return null;
        }

        @Override
        public long getSize()
        {
            return 0;
        }

        @Override
        public StoredMessage<TestMessageMetaData> getStoredMessage()
        {
            return _storedMsg;
        }


        @Override
        public boolean isPersistent()
        {
            return _storedMsg.getMetaData().isPersistent();
        }

        @Override
        public MessageReference newReference()
        {
            return _messageReference;
        }

        @Override
        public MessageReference newReference(final TransactionLogResource object)
        {
            return _messageReference;
        }

        @Override
        public boolean isReferenced(final TransactionLogResource resource)
        {
            return false;
        }

        @Override
        public boolean isReferenced()
        {
            return false;
        }

        @Override
        public int hashCode()
        {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((_storedMsg == null) ? 0 : _storedMsg.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (this == obj)
            {
                return true;
            }
            if (obj == null)
            {
                return false;
            }
            if (getClass() != obj.getClass())
            {
                return false;
            }
            TestServerMessage other = (TestServerMessage) obj;
            if (_storedMsg == null)
            {
                if (other._storedMsg != null)
                {
                    return false;
                }
            }
            else if (!_storedMsg.equals(other._storedMsg))
            {
                return false;
            }
            return true;
        }


    }
}
