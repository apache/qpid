package org.apache.qpid.server.flow;

import org.apache.qpid.test.utils.QpidTestCase;

public class WindowCreditManagerTest extends QpidTestCase
{
    WindowCreditManager _creditManager;

    protected void setUp() throws Exception
    {
        super.setUp();
        _creditManager = new WindowCreditManager();
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
