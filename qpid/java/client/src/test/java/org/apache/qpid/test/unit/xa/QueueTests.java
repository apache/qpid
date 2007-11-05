/* Licensed to the Apache Software Foundation (ASF) under one
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
 */
package org.apache.qpid.test.unit.xa;

import org.apache.qpid.testutil.QpidTestCase;
import org.apache.qpidity.dtx.XidImpl;

import javax.jms.*;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import javax.transaction.xa.XAException;

import junit.framework.TestSuite;

public class QueueTests extends QpidTestCase
{
    /**
     * the queue use by all the tests
     */
    private static Queue _queue = null;

    /**
     * the queue connection factory used by all tests
     */
    private static XAQueueConnectionFactory _queueFactory = null;

    /**
     * standard queue connection
     */
    private static XAQueueConnection _queueConnection = null;

    /**
     * standard queue session created from the standard connection
     */
    private static XAQueueSession _session = null;

    /**
     * standard queue session created from the standard connection
     */
    private static QueueSession _nonXASession = null;

    /**
     * the xaResource associated with the standard session
     */
    private static XAResource _xaResource = null;

    /**
     * producer registered with the standard session
     */
    private static MessageProducer _producer = null;

    /**
     * consumer registered with the standard session
     */
    private static MessageConsumer _consumer = null;

    /**
     * a standard message
     */
    private static TextMessage _message = null;

    /**
     * the queue name
     */
    private static final String QUEUENAME = "xaQueue";
    private static final String _sequenceNumberPropertyName = "seqNumber";

    /**
     * xid counter
     */
    private static int _xidCounter = 0;

    /** ----------------------------------------------------------------------------------- **/
    /** ----------------------------- JUnit support  ----------------------------------------- **/

    protected void setUp() throws Exception
    {
        super.setUp();
    }

    /**
     * Gets the test suite tests
     *
     * @return the test suite tests
     */
    public static TestSuite getSuite()
    {
        return new TestSuite(QueueTests.class);
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

    /** -------------------------------------------------------------------------------------- **/
    /** ----------------------------- Test Suite  -------------------------------------------- **/
    /** -------------------------------------------------------------------------------------- **/

    /**
     * Initialize standard actors
     */
    public void testInit()
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
            _queueConnection = getNewQueueXAConnection();
        }
        catch (JMSException e)
        {
            fail("cannot create queue connection: " + e.getMessage());
        }
        // create xa session
        try
        {
            _session = _queueConnection.createXAQueueSession();
        }
        catch (JMSException e)
        {
            fail("cannot create queue session: " + e.getMessage());
        }
        // create a standard session
        try
        {
            _nonXASession = _queueConnection.createQueueSession(true, Session.AUTO_ACKNOWLEDGE);
        }
        catch (JMSException e)
        {
            fail("cannot create queue session: " + e.getMessage());
        }
        // get the xaResource
        try
        {
            _xaResource = _session.getXAResource();
        }
        catch (Exception e)
        {
            fail("cannot access the xa resource: " + e.getMessage());
        }
        // create standard producer
        try
        {
            _producer = _session.createProducer(_queue);
            _producer.setDeliveryMode(DeliveryMode.PERSISTENT);
        }
        catch (JMSException e)
        {
            e.printStackTrace();
            fail("cannot create message producer: " + e.getMessage());
        }
        // create standard consumer
        try
        {
            _consumer = _session.createConsumer(_queue);
        }
        catch (JMSException e)
        {
            fail("cannot create message consumer: " + e.getMessage());
        }
        // create a standard message
        try
        {
            _message = _session.createTextMessage();
            _message.setText("test XA");
        }
        catch (JMSException e)
        {
            fail("cannot create standard message: " + e.getMessage());
        }
    }


    /**
     * Uses two transactions respectively with xid1 and xid2 that are used to send a message
     * within xid1 and xid2.  xid2 is committed and xid1 is used to receive the message that was sent within xid2.
     * Xid is then committed and a standard transaction is used to receive the message that was sent within xid1.
     */
    public void testProducer()
    {
        Xid xid1 = getNewXid();
        Xid xid2 = getNewXid();
        // start the xaResource for xid1
        try
        {
            _xaResource.start(xid1, XAResource.TMSUCCESS);
        }
        catch (XAException e)
        {
            e.printStackTrace();
            fail("cannot start the transaction with xid1: " + e.getMessage());
        }
        try
        {
            // start the connection
            _queueConnection.start();
            // produce a message with sequence number 1
            _message.setLongProperty(_sequenceNumberPropertyName, 1);
            _producer.send(_message);
        }
        catch (JMSException e)
        {
            fail(" cannot send persistent message: " + e.getMessage());
        }
        // suspend the transaction
        try
        {
            _xaResource.end(xid1, XAResource.TMSUSPEND);
        }
        catch (XAException e)
        {
            fail("Cannot end the transaction with xid1: " + e.getMessage());
        }
        // start the xaResource for xid2
        try
        {
            _xaResource.start(xid2, XAResource.TMSUCCESS);
        }
        catch (XAException e)
        {
            fail("cannot start the transaction with xid2: " + e.getMessage());
        }
        try
        {
            // produce a message
            _message.setLongProperty(_sequenceNumberPropertyName, 2);
            _producer.send(_message);
        }
        catch (JMSException e)
        {
            fail(" cannot send second persistent message: " + e.getMessage());
        }
        // end xid2 and start xid1
        try
        {
            _xaResource.end(xid2, XAResource.TMSUCCESS);
            _xaResource.start(xid1, XAResource.TMRESUME);
        }
        catch (XAException e)
        {
            fail("Exception when ending and starting transactions: " + e.getMessage());
        }
        // two phases commit transaction with xid2
        try
        {
            int resPrepare = _xaResource.prepare(xid2);
            if (resPrepare != XAResource.XA_OK)
            {
                fail("prepare returned: " + resPrepare);
            }
            _xaResource.commit(xid2, false);
        }
        catch (XAException e)
        {
            fail("Exception thrown when preparing transaction with xid2: " + e.getMessage());
        }
        // receive a message from queue test we expect it to be the second one
        try
        {
            TextMessage message = (TextMessage) _consumer.receiveNoWait();
            if (message == null)
            {
                fail("did not receive second message as expected ");
            }
            else
            {
                if (message.getLongProperty(_sequenceNumberPropertyName) != 2)
                {
                    fail("receive wrong message its sequence number is: " + message
                            .getLongProperty(_sequenceNumberPropertyName));
                }
            }
        }
        catch (JMSException e)
        {
            fail("Exception when receiving second message: " + e.getMessage());
        }
        // end and one phase commit the first transaction
        try
        {
            _xaResource.end(xid1, XAResource.TMSUCCESS);
            _xaResource.commit(xid1, true);
        }
        catch (XAException e)
        {
            fail("Exception thrown when commiting transaction with xid1");
        }
        // We should now be able to receive the first message
        try
        {
            Session nonXASession = _nonXASession;
            MessageConsumer nonXAConsumer = nonXASession.createConsumer(_queue);
            TextMessage message1 = (TextMessage) nonXAConsumer.receiveNoWait();
            if (message1 == null)
            {
                fail("did not receive first message as expected ");
            }
            else
            {
                if (message1.getLongProperty(_sequenceNumberPropertyName) != 1)
                {
                    fail("receive wrong message its sequence number is: " + message1
                            .getLongProperty(_sequenceNumberPropertyName));
                }
            }
            // commit that transacted session
            nonXASession.commit();
            // the queue should be now empty
            message1 = (TextMessage) nonXAConsumer.receiveNoWait();
            if (message1 != null)
            {
                fail("receive an unexpected message ");
            }
        }
        catch (JMSException e)
        {
            fail("Exception thrown when emptying the queue: " + e.getMessage());
        }
    }

    /**
     * strategy: Produce a message within Tx1 and prepare tx1. crash the server then commit tx1 and consume the message
     */
    public void testSendAndRecover()
    {
        Xid xid1 = getNewXid();
        // start the xaResource for xid1
        try
        {
            _xaResource.start(xid1, XAResource.TMSUCCESS);
        }
        catch (XAException e)
        {
            fail("cannot start the transaction with xid1: " + e.getMessage());
        }
        try
        {
            // start the connection
            _queueConnection.start();
            // produce a message with sequence number 1
            _message.setLongProperty(_sequenceNumberPropertyName, 1);
            _producer.send(_message);
        }
        catch (JMSException e)
        {
            fail(" cannot send persistent message: " + e.getMessage());
        }
        // suspend the transaction
        try
        {
            _xaResource.end(xid1, XAResource.TMSUSPEND);
        }
        catch (XAException e)
        {
            fail("Cannot end the transaction with xid1: " + e.getMessage());
        }
        // prepare the transaction with xid1
        try
        {
            _xaResource.prepare(xid1);
        }
        catch (XAException e)
        {
            fail("Exception when preparing xid1: " + e.getMessage());
        }

        /////// stop the server now !!
        try
        {
            shutdownServer();
        }
        catch (Exception e)
        {
            fail("Exception when stopping and restarting the server");
        }

        // get the list of in doubt transactions
        try
        {
            Xid[] inDoubt = _xaResource.recover(XAResource.TMSTARTRSCAN);
            if (inDoubt == null)
            {
                fail("the array of in doubt transactions should not be null ");
            }
            // At that point we expect only two indoubt transactions:
            if (inDoubt.length != 1)
            {
                fail("in doubt transaction size is diffenrent thatn 2, there are " + inDoubt.length + "in doubt transactions");
            }

            // commit them
            for (Xid anInDoubt : inDoubt)
            {
                if (anInDoubt.equals(xid1))
                {
                    System.out.println("commit xid1 ");
                    try
                    {
                        _xaResource.commit(anInDoubt, false);
                    }
                    catch (Exception e)
                    {
                        System.out.println("PB when aborted xid1");
                    }
                }
                else
                {
                    fail("did not receive right xid ");
                }
            }
        }
        catch (XAException e)
        {
            e.printStackTrace();
            fail("exception thrown when recovering transactions " + e.getMessage());
        }
        // the queue should contain the first message!
        try
        {
            Session nonXASession = _nonXASession;
            MessageConsumer nonXAConsumer = nonXASession.createConsumer(_queue);
            _queueConnection.start();
            TextMessage message1 = (TextMessage) nonXAConsumer.receiveNoWait();

            if (message1 == null)
            {
                fail("queue does not contain any message!");
            }
            if (message1.getLongProperty(_sequenceNumberPropertyName) != 1)
            {
                fail("Wrong message returned! Sequence number is " + message1
                        .getLongProperty(_sequenceNumberPropertyName));
            }
        }
        catch (JMSException e)
        {
            fail("Exception thrown when testin that queue test is not empty: " + e.getMessage());
        }
    }

    /**
     * strategy: Produce a message within Tx1 and prepare tx1. Produce a standard message and consume
     * it within tx2 and prepare tx2. Shutdown the server and get the list of in doubt transactions:
     * we expect tx1 and tx2! Then, Tx1 is aborted and tx2 is committed so we expect the test's queue to be empty!
     */
    public void testRecover()
    {
        Xid xid1 = getNewXid();
        Xid xid2 = getNewXid();
        // start the xaResource for xid1
        try
        {
            _xaResource.start(xid1, XAResource.TMSUCCESS);
        }
        catch (XAException e)
        {
            fail("cannot start the transaction with xid1: " + e.getMessage());
        }
        try
        {
            // start the connection
            _queueConnection.start();
            // produce a message with sequence number 1
            _message.setLongProperty(_sequenceNumberPropertyName, 1);
            _producer.send(_message);
        }
        catch (JMSException e)
        {
            fail(" cannot send persistent message: " + e.getMessage());
        }
        // suspend the transaction
        try
        {
            _xaResource.end(xid1, XAResource.TMSUSPEND);
        }
        catch (XAException e)
        {
            fail("Cannot end the transaction with xid1: " + e.getMessage());
        }
        // prepare the transaction with xid1
        try
        {
            _xaResource.prepare(xid1);
        }
        catch (XAException e)
        {
            fail("Exception when preparing xid1: " + e.getMessage());
        }

        // send a message using the standard session
        try
        {
            Session nonXASession = _nonXASession;
            MessageProducer nonXAProducer = nonXASession.createProducer(_queue);
            TextMessage message2 = nonXASession.createTextMessage();
            message2.setText("non XA ");
            message2.setLongProperty(_sequenceNumberPropertyName, 2);
            nonXAProducer.setDeliveryMode(DeliveryMode.PERSISTENT);
            nonXAProducer.send(message2);
            // commit that transacted session
            nonXASession.commit();
        }
        catch (Exception e)
        {
            fail("Exception thrown when emptying the queue: " + e.getMessage());
        }
        // start the xaResource for xid2
        try
        {
            _xaResource.start(xid2, XAResource.TMSUCCESS);
        }
        catch (XAException e)
        {
            fail("cannot start the transaction with xid1: " + e.getMessage());
        }
        // receive a message from queue test we expect it to be the second one
        try
        {
            TextMessage message = (TextMessage) _consumer.receiveNoWait();
            if (message == null || message.getLongProperty(_sequenceNumberPropertyName) != 2)
            {
                fail("did not receive second message as expected ");
            }
        }
        catch (JMSException e)
        {
            fail("Exception when receiving second message: " + e.getMessage());
        }
        // suspend the transaction
        try
        {
            _xaResource.end(xid2, XAResource.TMSUSPEND);
        }
        catch (XAException e)
        {
            fail("Cannot end the transaction with xid2: " + e.getMessage());
        }
        // prepare the transaction with xid1
        try
        {
            _xaResource.prepare(xid2);
        }
        catch (XAException e)
        {
            fail("Exception when preparing xid2: " + e.getMessage());
        }

        /////// stop the server now !!
        try
        {
            shutdownServer();
        }
        catch (Exception e)
        {
            fail("Exception when stopping and restarting the server");
        }

        // get the list of in doubt transactions
        try
        {
            Xid[] inDoubt = _xaResource.recover(XAResource.TMSTARTRSCAN);
            if (inDoubt == null)
            {
                fail("the array of in doubt transactions should not be null ");
            }
            // At that point we expect only two indoubt transactions:
            if (inDoubt.length != 2)
            {
                fail("in doubt transaction size is diffenrent thatn 2, there are " + inDoubt.length + "in doubt transactions");
            }

            // commit them
            for (Xid anInDoubt : inDoubt)
            {
                if (anInDoubt.equals(xid1))
                {
                    System.out.println("rollback xid1 ");
                    try
                    {
                        _xaResource.rollback(anInDoubt);
                    }
                    catch (Exception e)
                    {
                        System.out.println("PB when aborted xid1");
                    }
                }
                else if (anInDoubt.equals(xid2))
                {
                    System.out.println("commit xid2 ");
                    try
                    {
                        _xaResource.commit(anInDoubt, false);
                    }
                    catch (Exception e)
                    {
                        System.out.println("PB when commiting xid2");
                    }
                }
            }
        }
        catch (XAException e)
        {
            e.printStackTrace();
            fail("exception thrown when recovering transactions " + e.getMessage());
        }
        // the queue should be empty
        try
        {
            Session nonXASession = _nonXASession;
            MessageConsumer nonXAConsumer = nonXASession.createConsumer(_queue);
            _queueConnection.start();
            TextMessage message1 = (TextMessage) nonXAConsumer.receiveNoWait();
            if (message1 != null)
            {
                fail("The queue is not empty! ");
            }
        }
        catch (JMSException e)
        {
            fail("Exception thrown when testin that queue test is empty: " + e.getMessage());
        }
    }

    /**
     * close the standard connection
     */
    public void testEnd()
    {
        try
        {
            _queueConnection.stop();
            _queueConnection.close();
        }
        catch (Exception e)
        {
            fail("Exception thrown when cleaning standard connection: " + e.getStackTrace());
        }

    }
    /** -------------------------------------------------------------------------------------- **/
    /** ----------------------------- Utility methods  --------------------------------------- **/
    /** -------------------------------------------------------------------------------------- **/

    /**
     * get a new queue connection
     *
     * @return a new queue connection
     * @throws JMSException If the JMS provider fails to create the queue connection
     *                      due to some internal error or in case of authentication failure
     */
    private XAQueueConnection getNewQueueXAConnection() throws JMSException
    {
        return _queueFactory.createXAQueueConnection("guest", "guest");
    }

    /**
     * construct a new Xid
     *
     * @return a new Xid
     */
    private Xid getNewXid()
    {
        byte[] branchQualifier;
        byte[] globalTransactionID;
        int format = _xidCounter;
        String branchQualifierSt = "branchQualifier" + _xidCounter;
        String globalTransactionIDSt = "globalTransactionID" + _xidCounter;
        branchQualifier = branchQualifierSt.getBytes();
        globalTransactionID = globalTransactionIDSt.getBytes();
        _xidCounter++;
        return new XidImpl(branchQualifier, format, globalTransactionID);
    }

    public void shutdownServer() throws Exception
    {
        killBroker();
        System.out.println("initializing server connection and actores ");
        setUp();
        testInit();
    }
}
