package org.apache.qpid.framing;

import junit.framework.TestCase;
import org.apache.qpid.client.state.listener.SpecificMethodFrameListener;
import org.apache.mina.common.ByteBuffer;

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

public class SpecificMethodFrameListenerTest extends TestCase
{

    SpecificMethodFrameListener close1a = new SpecificMethodFrameListener(1, ChannelCloseOkBody.class);
    SpecificMethodFrameListener close1b = new SpecificMethodFrameListener(1, ChannelCloseOkBody.class);
    SpecificMethodFrameListener close2 = new SpecificMethodFrameListener(2, ChannelCloseOkBody.class);
    SpecificMethodFrameListener open1a = new SpecificMethodFrameListener(1, ChannelOpenOkBody.class);
    SpecificMethodFrameListener open1b = new SpecificMethodFrameListener(1, ChannelOpenOkBody.class);

    public void testEquals()
    {
        //Check that the the same objects are equal
        assertEquals("ChannelCloseOKBody a should equal a", close1a, close1a);
        assertEquals("ChannelOpenOkBody a should equal a", open1a, open1a);

        //check that the same values in differnt objects are equal
        assertEquals("ChannelCloseOKBody b should equal a", close1b, close1a);
        assertEquals("ChannelCloseOKBody a should equal b", close1a, close1b);
        assertEquals("ChannelOpenOkBody a should equal b", open1a, open1b);
        assertEquals("ChannelOpenOkBody a should equal b", open1a, open1b);

        //Chec that different values fail
        //Different channels
        assertFalse("ChannelCloseOKBody channel 1 should NOT equal channel 2", close1a.equals(close2));
        assertFalse("ChannelCloseOKBody channel 1 should NOT equal channel 2", close2.equals(close1a));

        //Different Bodies
        assertFalse("ChannelCloseOKBody should not equal ChannelOpenOkBody", close1a.equals(open1a));
        assertFalse("ChannelOpenOkBody should not equal ChannelCloseOKBody", open1a.equals(close1a));
    }

    public void testProcessMethod() throws AMQFrameDecodingException
    {
        ChannelCloseOkBody ccob = (ChannelCloseOkBody) ChannelCloseOkBody.getFactory().newInstance((byte) 8, (byte) 0, ByteBuffer.allocate(0), 0);
        ChannelOpenOkBody coob = (ChannelOpenOkBody) ChannelOpenOkBody.getFactory().newInstance((byte) 8, (byte) 0, ByteBuffer.allocate(0), 0);

        assertTrue("This SpecificMethodFrameListener should process a ChannelCloseOkBody", close1a.processMethod(1, ccob));
        assertFalse("This SpecificMethodFrameListener should NOT process a ChannelOpenOkBody", close1a.processMethod(1, coob));




    }
}
