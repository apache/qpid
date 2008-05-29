package org.apache.qpid.test.unit.xa;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.*;
import javax.transaction.xa.Xid;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.XAException;

import junit.framework.TestSuite;


public class FaultTest extends AbstractXATestCase
{
    /* this clas logger */
    private static final Logger _logger = LoggerFactory.getLogger(FaultTest.class);

    /**
     * the queue use by all the tests
     */
    private static Queue _queue = null;
    /**
     * the queue connection factory used by all tests
     */
    private static XAQueueConnectionFactory _queueFactory = null;

    /**
     * standard xa queue connection
     */
    private static XAQueueConnection _xaqueueConnection = null;

    /**
     * standard xa queue connection
     */
    private static QueueConnection _queueConnection = null;


    /**
     * standard queue session created from the standard connection
     */
    private static QueueSession _nonXASession = null;

    /**
     * the queue name
     */
    private static final String QUEUENAME = "xaQueue";

    /** ----------------------------------------------------------------------------------- **/
    /**
     * ----------------------------- JUnit support  ----------------------------------------- *
     */

    /**
     * Gets the test suite tests
     *
     * @return the test suite tests
     */
    public static TestSuite getSuite()
    {
        return new TestSuite(QueueTest.class);
    }

    /**
     * Run the test suite.
     *
     * @param args Any command line arguments specified to this class.
     */
    public static void main(String args[])
    {
        junit.textui.TestRunner.run(getSuite());
    }

    public void tearDown() throws Exception
    {
        if (!isBroker08())
        {
            try
            {
                _xaqueueConnection.close();
                _queueConnection.close();
            }
            catch (Exception e)
            {
                fail("Exception thrown when cleaning standard connection: " + e);
            }
        }
        super.tearDown();
    }

    /**
     * Initialize standard actors
     */
    public void init()
    {
        if (!isBroker08())
        {
            // lookup test queue
            try
            {
                _queue = (Queue) getInitialContext().lookup(QUEUENAME);
            }
            catch (Exception e)
            {
                fail("cannot lookup test queue " + e.getMessage());
            }
            // lookup connection factory
            try
            {
                _queueFactory = getConnectionFactory();
            }
            catch (Exception e)
            {
                fail("enable to lookup connection factory ");
            }
            // create standard connection
            try
            {
                _xaqueueConnection = _queueFactory.createXAQueueConnection("guest", "guest");
            }
            catch (JMSException e)
            {
                fail("cannot create queue connection: " + e.getMessage());
            }
            // create xa session
            XAQueueSession session = null;
            try
            {
                session = _xaqueueConnection.createXAQueueSession();
            }
            catch (JMSException e)
            {
                fail("cannot create queue session: " + e.getMessage());
            }
            // create a standard session
            try
            {
                _queueConnection = _queueFactory.createQueueConnection();
                _nonXASession = _queueConnection.createQueueSession(true, Session.AUTO_ACKNOWLEDGE);
            }
            catch (JMSException e)
            {
                fail("cannot create queue session: " + e.getMessage());
            }
            init(session, _queue);
        }
    }

    /** -------------------------------------------------------------------------------------- **/
    /** ----------------------------- Test Suite  -------------------------------------------- **/
    /** -------------------------------------------------------------------------------------- **/

    /**
     * Strategy:
     * Invoke start twice with the same xid on an XA resource.
     * Check that the second
     * invocation is throwing the expected XA exception.
     */
    public void testSameXID()
    {
        _logger.debug("running testSameXID");
        Xid xid = getNewXid();
        try
        {
            _xaResource.start(xid, XAResource.TMNOFLAGS);
        }
        catch (XAException e)
        {
            fail("cannot start the transaction with xid: " + e.getMessage());
        }
        // we now exepct this operation to fail
        try
        {
            _xaResource.start(xid, XAResource.TMNOFLAGS);
            fail("We managed to start a transaction with the same xid");
        }
        catch (XAException e)
        {
            assertEquals("Wrong error code: ", XAException.XAER_DUPID, e.errorCode);
        }
        catch (Exception ex)
        {
            fail("Caught wrong exception, expected XAException, got: " + ex);
        }
    }

    /**
     * Strategy:
     * Invoke start on a XA resource with flag other than TMNOFLAGS, TMJOIN, or TMRESUME.
     * Check that a XA Exception is thrown.
     */
    public void testWrongStartFlag()
    {
        _logger.debug("running testWrongStartFlag");
        Xid xid = getNewXid();
        try
        {
            _xaResource.start(xid, XAResource.TMONEPHASE);
            fail("We managed to start a transaction with a wrong flag");
        }
        catch (XAException e)
        {
            assertEquals("Wrong error code: ", XAException.XAER_INVAL, e.errorCode);
        }
        catch (Exception ex)
        {
            fail("Caught wrong exception, expected XAException, got: " + ex);
        }
    }

    /**
     * Strategy:
     * Check that a XA exception is thrown when:
     * A non started xid is ended
     */
    public void testEnd()
    {
        _logger.debug("running testEnd");
        Xid xid = getNewXid();
        try
        {
            _xaResource.end(xid, XAResource.TMSUCCESS);
            fail("We managed to end a transaction before it is started");
        }
        catch (XAException e)
        {
            assertEquals("Wrong error code: ", XAException.XAER_PROTO, e.errorCode);
        }
        catch (Exception ex)
        {
            fail("Caught wrong exception, expected XAException, got: " + ex);
        }
    }


    /**
     * Strategy:
     * Check that a XA exception is thrown when:
     * Call forget on an unknown xid
     * call forget on a started xid
     * A non started xid is prepared
     * A non ended xis is prepared
     */
    public void testForget()
    {
        _logger.debug("running testForget");
        Xid xid = getNewXid();
        try
        {
            _xaResource.forget(xid);
            fail("We managed to forget an unknown xid");
        }
        catch (XAException e)
        {
            // assertEquals("Wrong error code: ", XAException.XAER_NOTA, e.errorCode);
        }
        catch (Exception ex)
        {
            fail("Caught wrong exception, expected XAException, got: " + ex);
        }
        xid = getNewXid();
        try
        {
            _xaResource.start(xid, XAResource.TMNOFLAGS);
            _xaResource.forget(xid);
            fail("We managed to forget a started xid");
        }
        catch (XAException e)
        {
            assertEquals("Wrong error code: ", XAException.XAER_PROTO, e.errorCode);
        }
        catch (Exception ex)
        {
            fail("Caught wrong exception, expected XAException, got: " + ex);
        }
    }

    /**
     * Strategy:
     * Check that a XA exception is thrown when:
     * A non started xid is prepared
     * A non ended xid is prepared
     */
    public void testPrepare()
    {
        _logger.debug("running testPrepare");
        Xid xid = getNewXid();
        try
        {
            _xaResource.prepare(xid);
            fail("We managed to prepare an unknown xid");
        }
        catch (XAException e)
        {
            assertEquals("Wrong error code: ", XAException.XAER_NOTA, e.errorCode);
        }
        catch (Exception ex)
        {
            fail("Caught wrong exception, expected XAException, got: " + ex);
        }
        xid = getNewXid();
        try
        {
            _xaResource.start(xid, XAResource.TMNOFLAGS);
            _xaResource.prepare(xid);
            fail("We managed to prepare a started xid");
        }
        catch (XAException e)
        {
            assertEquals("Wrong error code: ", XAException.XAER_PROTO, e.errorCode);
        }
        catch (Exception ex)
        {
            fail("Caught wrong exception, expected XAException, got: " + ex);
        }
    }

    /**
     * Strategy:
     * Check that the expected XA exception is thrown when:
     * A non started xid is committed
     * A non ended xid is committed
     * A non prepared xid is committed with one phase set to false.
     * A prepared xid is committed with one phase set to true.
     */
    public void testCommit()
    {
        _logger.debug("running testCommit");
        Xid xid = getNewXid();
        try
        {
            _xaResource.commit(xid, true);
            fail("We managed to commit an unknown xid");
        }
        catch (XAException e)
        {
            assertEquals("Wrong error code: ", XAException.XAER_NOTA, e.errorCode);
        }
        catch (Exception ex)
        {
            fail("Caught wrong exception, expected XAException, got: " + ex);
        }
        xid = getNewXid();
        try
        {
            _xaResource.start(xid, XAResource.TMNOFLAGS);
            _xaResource.commit(xid, true);
            fail("We managed to commit a not ended xid");
        }
        catch (XAException e)
        {
            assertEquals("Wrong error code: ", XAException.XAER_PROTO, e.errorCode);
        }
        catch (Exception ex)
        {
            fail("Caught wrong exception, expected XAException, got: " + ex);
        }
        xid = getNewXid();
        try
        {
            _xaResource.start(xid, XAResource.TMNOFLAGS);
            _xaResource.end(xid, XAResource.TMSUCCESS);
            _xaResource.commit(xid, false);
            fail("We managed to commit a not prepared xid");
        }
        catch (XAException e)
        {
            assertEquals("Wrong error code: ", XAException.XAER_PROTO, e.errorCode);
        }
        catch (Exception ex)
        {
            fail("Caught wrong exception, expected XAException, got: " + ex);
        }
        xid = getNewXid();
        try
        {
            _xaResource.start(xid, XAResource.TMNOFLAGS);
            _xaResource.end(xid, XAResource.TMSUCCESS);
            _xaResource.prepare(xid);
            _xaResource.commit(xid, true);
            fail("We managed to commit a prepared xid");
        }
        catch (XAException e)
        {
            assertEquals("Wrong error code: ", XAException.XAER_PROTO, e.errorCode);
        }
        catch (Exception ex)
        {
            fail("Caught wrong exception, expected XAException, got: " + ex);
        }
        finally
        {
            try
            {
                _xaResource.commit(xid, false);
            }
            catch (XAException e)
            {
               fail("Cannot commit prepared tx: " + e);
            }
        }
    }

     /**
     * Strategy:
     * Check that the expected XA exception is thrown when:
     * A non started xid is rolled back
     * A non ended xid is rolled back
     */
    public void testRollback()
    {
        _logger.debug("running testRollback");
        Xid xid = getNewXid();
        try
        {
            _xaResource.rollback(xid);
            fail("We managed to rollback an unknown xid");
        }
        catch (XAException e)
        {
            assertEquals("Wrong error code: ", XAException.XAER_NOTA, e.errorCode);
        }
        catch (Exception ex)
        {
            fail("Caught wrong exception, expected XAException, got: " + ex);
        }
        xid = getNewXid();
        try
        {
            _xaResource.start(xid, XAResource.TMNOFLAGS);
            _xaResource.rollback(xid);
            fail("We managed to rollback a not ended xid");
        }
        catch (XAException e)
        {
            assertEquals("Wrong error code: ", XAException.XAER_PROTO, e.errorCode);
        }
        catch (Exception ex)
        {
            fail("Caught wrong exception, expected XAException, got: " + ex);
        }
    }

    /**
     * Strategy:
     * Check that the timeout is set correctly
     */
    public void testTransactionTimeoutvalue()
    {
        _logger.debug("running testRollback");
        Xid xid = getNewXid();
        try
        {
            _xaResource.start(xid, XAResource.TMNOFLAGS);
            assertEquals("Wrong timeout", _xaResource.getTransactionTimeout(), 0);
            _xaResource.setTransactionTimeout(1000);
            assertEquals("Wrong timeout", _xaResource.getTransactionTimeout(), 1000);
            _xaResource.end(xid, XAResource.TMSUCCESS);
            xid = getNewXid();
            _xaResource.start(xid, XAResource.TMNOFLAGS);
            assertEquals("Wrong timeout", _xaResource.getTransactionTimeout(), 0);            
        }
        catch (Exception ex)
        {
            fail("Caught wrong exception, expected XAException, got: " + ex);
        }
    }

    /**
     * Strategy:
     * Check that a transaction timeout as expected
     * - set timeout to 10ms
     * - sleep 1000ms
     * - call end and check that the expected exception is thrown   
     */
    public void testTransactionTimeout()
    {
        _logger.debug("running testRollback");
        Xid xid = getNewXid();
        try
        {
            _xaResource.start(xid, XAResource.TMNOFLAGS);
            assertEquals("Wrong timeout", _xaResource.getTransactionTimeout(), 0);
            _xaResource.setTransactionTimeout(10);
            Thread.sleep(1000);
            _xaResource.end(xid, XAResource.TMSUCCESS);
        }
        catch (XAException e)
        {
            assertEquals("Wrong error code: ", XAException.XA_RBTIMEOUT, e.errorCode);
        }
        catch (Exception ex)
        {
            fail("Caught wrong exception, expected XAException, got: " + ex);
        }
    }
}
