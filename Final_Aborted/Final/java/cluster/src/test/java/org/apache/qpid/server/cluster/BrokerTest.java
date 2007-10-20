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
package org.apache.qpid.server.cluster;

import junit.framework.TestCase;
import org.apache.mina.common.ByteBuffer;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQBody;
import org.apache.qpid.framing.AMQDataBlock;
import org.apache.qpid.framing.AMQFrame;
import org.apache.qpid.framing.AMQFrameDecodingException;
import org.apache.qpid.framing.AMQMethodBody;
import org.apache.qpid.server.cluster.policy.StandardPolicies;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BrokerTest extends TestCase
{
    //group request (no failure)
    public void testGroupRequest_noFailure() throws AMQException
    {
        RecordingBroker[] brokers = new RecordingBroker[]{
                new RecordingBroker("A", 1),
                new RecordingBroker("B", 2),
                new RecordingBroker("C", 3)
        };
        GroupResponseValidator handler = new GroupResponseValidator(new TestMethod("response"), new ArrayList<Member>(Arrays.asList(brokers)));
        GroupRequest grpRequest = new GroupRequest(new SimpleBodySendable(new TestMethod("request")), StandardPolicies.SYNCH_POLICY, handler);
        for (Broker b : brokers)
        {
            b.invoke(grpRequest);
        }
        grpRequest.finishedSend();

        for (RecordingBroker b : brokers)
        {
            b.handleResponse(((AMQFrame) b.getMessages().get(0)).getChannel(), new TestMethod("response"));
        }

        assertTrue("Handler did not receive response", handler.isCompleted());
    }

    //group request (failure)
    public void testGroupRequest_failure() throws AMQException
    {
        RecordingBroker a = new RecordingBroker("A", 1);
        RecordingBroker b = new RecordingBroker("B", 2);
        RecordingBroker c = new RecordingBroker("C", 3);
        RecordingBroker[] all = new RecordingBroker[]{a, b, c};
        RecordingBroker[] succeeded = new RecordingBroker[]{a, c};

        GroupResponseValidator handler = new GroupResponseValidator(new TestMethod("response"), new ArrayList<Member>(Arrays.asList(succeeded)));
        GroupRequest grpRequest = new GroupRequest(new SimpleBodySendable(new TestMethod("request")), StandardPolicies.SYNCH_POLICY, handler);

        for (Broker broker : all)
        {
            broker.invoke(grpRequest);
        }
        grpRequest.finishedSend();

        for (RecordingBroker broker : succeeded)
        {
            broker.handleResponse(((AMQFrame) broker.getMessages().get(0)).getChannel(), new TestMethod("response"));
        }
        b.remove();

        assertTrue("Handler did not receive response", handler.isCompleted());
    }


    //simple send (no response)
    public void testSend_noResponse() throws AMQException
    {
        AMQBody[] msgs = new AMQBody[]{
                new TestMethod("A"),
                new TestMethod("B"),
                new TestMethod("C")
        };
        RecordingBroker broker = new RecordingBroker("myhost", 1);
        for (AMQBody msg : msgs)
        {
            broker.send(new SimpleBodySendable(msg), null);
        }
        List<AMQDataBlock> sent = broker.getMessages();
        assertEquals(msgs.length, sent.size());
        for (int i = 0; i < msgs.length; i++)
        {
            assertTrue(sent.get(i) instanceof AMQFrame);
            assertEquals(msgs[i], ((AMQFrame) sent.get(i)).getBodyFrame());
        }
    }

    //simple send (no failure)
    public void testSend_noFailure() throws AMQException
    {
        RecordingBroker broker = new RecordingBroker("myhost", 1);
        BlockingHandler handler = new BlockingHandler();
        broker.send(new SimpleBodySendable(new TestMethod("A")), handler);
        List<AMQDataBlock> sent = broker.getMessages();
        assertEquals(1, sent.size());
        assertTrue(sent.get(0) instanceof AMQFrame);
        assertEquals(new TestMethod("A"), ((AMQFrame) sent.get(0)).getBodyFrame());

        broker.handleResponse(((AMQFrame) sent.get(0)).getChannel(), new TestMethod("B"));

        assertEquals(new TestMethod("B"), handler.getResponse());
    }

    //simple send (failure)
    public void testSend_failure() throws AMQException
    {
        RecordingBroker broker = new RecordingBroker("myhost", 1);
        BlockingHandler handler = new BlockingHandler();
        broker.send(new SimpleBodySendable(new TestMethod("A")), handler);
        List<AMQDataBlock> sent = broker.getMessages();
        assertEquals(1, sent.size());
        assertTrue(sent.get(0) instanceof AMQFrame);
        assertEquals(new TestMethod("A"), ((AMQFrame) sent.get(0)).getBodyFrame());
        broker.remove();
        assertEquals(null, handler.getResponse());
        assertTrue(handler.isCompleted());
        assertTrue(handler.failed());
    }

    private static class TestMethod extends AMQMethodBody
    {
        private final Object id;

        TestMethod(Object id)
        {
            // AMQP version change: Hardwire the version to 0-8 (major=8, minor=0)
            // TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
            super((byte)8, (byte)0);
            this.id = id;
        }

        protected int getBodySize()
        {
            return 0;
        }

        protected int getClazz()
        {
            return 1002;
        }

        protected int getMethod()
        {
            return 1003;
        }

        protected void writeMethodPayload(ByteBuffer buffer)
        {
        }

        protected byte getType()
        {
            return 0;
        }

        protected int getSize()
        {
            return 0;
        }

        protected void writePayload(ByteBuffer buffer)
        {
        }

        protected void populateMethodBodyFromBuffer(ByteBuffer buffer) throws AMQFrameDecodingException
        {
        }

        protected void populateFromBuffer(ByteBuffer buffer, long size) throws AMQFrameDecodingException
        {
        }

        public boolean equals(Object o)
        {
            return o instanceof TestMethod && id.equals(((TestMethod) o).id);
        }

        public int hashCode()
        {
            return id.hashCode();
        }

    }

    private static class GroupResponseValidator implements GroupResponseHandler
    {
        private final AMQMethodBody _response;
        private final List<Member> _members;
        private boolean _completed = false;

        GroupResponseValidator(AMQMethodBody response, List<Member> members)
        {
            _response = response;
            _members = members;
        }

        public void response(List<AMQMethodBody> responses, List<Member> members)
        {
            for (AMQMethodBody r : responses)
            {
                assertEquals(_response, r);
            }
            assertEquals(_members, members);
            _completed = true;
        }

        boolean isCompleted()
        {
            return _completed;
        }
    }
}
