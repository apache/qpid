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
package org.apache.qpid.server.protocol.v0_10;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.qpid.server.protocol.ServerProtocolEngine;
import org.apache.qpid.test.utils.QpidTestCase;

public class WindowCreditManagerTest extends QpidTestCase
{
    private WindowCreditManager _creditManager;
    private ServerProtocolEngine _protocolEngine;

    protected void setUp() throws Exception
    {
        super.setUp();

        _protocolEngine = mock(ServerProtocolEngine.class);
        when(_protocolEngine.isTransportBlockedForWriting()).thenReturn(false);

        _creditManager = new WindowCreditManager(0l, 0l, _protocolEngine);
    }

    /**
     * Tests that after the credit limit is cleared (e.g. from a message.stop command), credit is
     * restored (e.g. from completed MessageTransfer) without increasing the available credit, and
     * more credit is added, that the 'used' count is correct and the proper values for bytes
     * and message credit are returned along with appropriate 'hasCredit' results (QPID-3592).
     */
    public void testRestoreCreditDecrementsUsedCountAfterCreditClear()
    {
        assertEquals("unexpected credit value", 0, _creditManager.getMessageCredit());
        assertEquals("unexpected credit value", 0, _creditManager.getBytesCredit());

        //give some message credit
        _creditManager.addCredit(1, 0);
        assertFalse("Manager should not 'haveCredit' due to having 0 bytes credit", _creditManager.hasCredit());
        assertEquals("unexpected credit value", 1, _creditManager.getMessageCredit());
        assertEquals("unexpected credit value", 0, _creditManager.getBytesCredit());

        //give some bytes credit
        _creditManager.addCredit(0, 1);
        assertTrue("Manager should 'haveCredit'", _creditManager.hasCredit());
        assertEquals("unexpected credit value", 1, _creditManager.getMessageCredit());
        assertEquals("unexpected credit value", 1, _creditManager.getBytesCredit());

        //use all the credit
        _creditManager.useCreditForMessage(1);
        assertEquals("unexpected credit value", 0, _creditManager.getBytesCredit());
        assertEquals("unexpected credit value", 0, _creditManager.getMessageCredit());
        assertFalse("Manager should not 'haveCredit'", _creditManager.hasCredit());

        //clear credit out (eg from a message.stop command)
        _creditManager.clearCredit();
        assertEquals("unexpected credit value", 0, _creditManager.getBytesCredit());
        assertEquals("unexpected credit value", 0, _creditManager.getMessageCredit());
        assertFalse("Manager should not 'haveCredit'", _creditManager.hasCredit());

        //restore credit (e.g the original message transfer command got completed)
        //this should not increase credit, because it is now limited to 0
        _creditManager.restoreCredit(1, 1);
        assertEquals("unexpected credit value", 0, _creditManager.getBytesCredit());
        assertEquals("unexpected credit value", 0, _creditManager.getMessageCredit());
        assertFalse("Manager should not 'haveCredit'", _creditManager.hasCredit());

        //give more credit to open the window again
        _creditManager.addCredit(1, 1);
        assertEquals("unexpected credit value", 1, _creditManager.getBytesCredit());
        assertEquals("unexpected credit value", 1, _creditManager.getMessageCredit());
        assertTrue("Manager should 'haveCredit'", _creditManager.hasCredit());
    }
}
