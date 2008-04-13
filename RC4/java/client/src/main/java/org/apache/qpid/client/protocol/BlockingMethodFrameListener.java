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
package org.apache.qpid.client.protocol;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.qpid.AMQException;
import org.apache.qpid.AMQTimeoutException;
import org.apache.qpid.client.failover.FailoverException;
import org.apache.qpid.framing.AMQMethodBody;
import org.apache.qpid.protocol.AMQMethodEvent;
import org.apache.qpid.protocol.AMQMethodListener;

/**
 * BlockingMethodFrameListener is a 'rendezvous' which acts as a {@link AMQMethodListener} that delegates handling of
 * incoming methods to a method listener implemented as a sub-class of this and hands off the processed method or
 * error to a consumer. The producer of the event does not have to wait for the consumer to take the event, so this
 * differs from a 'rendezvous' in that sense.
 *
 * <p/>BlockingMethodFrameListeners are used to coordinate waiting for replies to method calls that expect a response.
 * They are always used in a 'one-shot' manner, that is, to recieve just one response. Usually the caller has to register
 * them as method listeners with an event dispatcher and remember to de-register them (in a finally block) once they
 * have been completed.
 *
 * <p/>The {@link #processMethod} must return <tt>true</tt> on any incoming method that it handles. This indicates to
 * this listeners that the method it is waiting for has arrived. Incoming methods are also filtered by channel prior to
 * being passed to the {@link #processMethod} method, so responses are only received for a particular channel. The
 * channel id must be passed to the constructor.
 *
 * <p/>Errors from the producer are rethrown to the consumer.
 *
 * <p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Accept notification of AMQP method events. <td> {@link AMQMethodEvent}
 * <tr><td> Delegate handling of the method to another method listener. <td> {@link AMQMethodBody}
 * <tr><td> Block until a method is handled by the delegated to handler.
 * <tr><td> Propagate the most recent exception to the consumer.
 * </table>
 *
 * @todo Might be neater if this method listener simply wrapped another that provided the method handling using a
 *       methodRecevied method. The processMethod takes an additional channelId, however none of the implementations
 *       seem to use it. So wrapping the listeners is possible.
 *
 * @todo What is to stop a blocking method listener, receiving a second method whilst it is registered as a listener,
 *       overwriting the first one before the caller of the block method has had a chance to examine it? If one-shot
 *       behaviour is to be intended it should be enforced, perhaps by always returning false once the blocked for
 *       method has been received.
 *
 * @todo Interuption is caught but not handled. This could be allowed to fall through. This might actually be usefull
 *       for fail-over where a thread is blocking when failure happens, it could be interrupted to abandon or retry
 *       when this happens. At the very least, restore the interrupted status flag.
 *
 * @todo If the retrotranslator can handle it, could use a SynchronousQueue to implement this rendezvous. Need to
 *       check that SynchronousQueue has a non-blocking put method available.
 */
public abstract class BlockingMethodFrameListener implements AMQMethodListener
{
    /** This flag is used to indicate that the blocked for method has been received. */
    private volatile boolean _ready = false;

    /** This flag is used to indicate that the received error has been processed. */
    private volatile boolean _errorAck = false;

    /** Used to protect the shared event and ready flag between the producer and consumer. */
    private final ReentrantLock _lock = new ReentrantLock();

    /**
     * Used to signal that a method has been received
     */
    private final Condition _receivedCondition = _lock.newCondition();

    /**
      * Used to signal that a error has been processed
      */
    private final Condition _errorConditionAck = _lock.newCondition();

    /** Used to hold the most recent exception that is passed to the {@link #error(Exception)} method. */
    private volatile Exception _error;

    /** Holds the channel id for the channel upon which this listener is waiting for a response. */
    protected int _channelId;

    /** Holds the incoming method. */
    protected AMQMethodEvent _doneEvt = null;

    /**
     * Creates a new method listener, that filters incoming method to just those that match the specified channel id.
     *
     * @param channelId The channel id to filter incoming methods with.
     */
    public BlockingMethodFrameListener(int channelId)
    {
        _channelId = channelId;
    }

    /**
     * Delegates any additional handling of the incoming methods to another handler.
     *
     * @param channelId The channel id of the incoming method.
     * @param frame     The method body.
     *
     * @return <tt>true</tt> if the method was handled, <tt>false</tt> otherwise.
     */
    public abstract boolean processMethod(int channelId, AMQMethodBody frame); // throws AMQException;

    /**
     * Informs this listener that an AMQP method has been received.
     *
     * @param evt The AMQP method.
     *
     * @return <tt>true</tt> if this listener has handled the method, <tt>false</tt> otherwise.
     */
    public boolean methodReceived(AMQMethodEvent evt) // throws AMQException
    {
        AMQMethodBody method = evt.getMethod();

        /*try
        {*/
        boolean ready = (evt.getChannelId() == _channelId) && processMethod(evt.getChannelId(), method);

        if (ready)
        {
            // we only update the flag from inside the synchronized block
            // so that the blockForFrame method cannot "miss" an update - it
            // will only ever read the flag from within the synchronized block
            _lock.lock();
            try
            {
                _doneEvt = evt;
                _ready = ready;
                _receivedCondition.signal();
            }
            finally
            {
                _lock.unlock();
            }
        }

        return ready;

        /*}
        catch (AMQException e)
        {
            error(e);
            // we rethrow the error here, and the code in the frame dispatcher will go round
            // each listener informing them that an exception has been thrown
            throw e;
        }*/
    }

    /**
     * Blocks until a method is received that is handled by the delegated to method listener, or the specified timeout
     * has passed.
     *
     * @param timeout The timeout in milliseconds.
     *
     * @return The AMQP method that was received.
     *
     * @throws AMQException
     * @throws FailoverException
     */
    public AMQMethodEvent blockForFrame(long timeout) throws AMQException, FailoverException
    {
        long nanoTimeout = TimeUnit.MILLISECONDS.toNanos(timeout);

        _lock.lock();

        try
        {
            while (!_ready)
            {
                try
                {
                    if (timeout == -1)
                    {
                        _receivedCondition.await();
                    }
                    else
                    {
                        nanoTimeout = _receivedCondition.awaitNanos(nanoTimeout);

                        if (nanoTimeout <= 0 && !_ready && _error == null)
                        {
                            _error = new AMQTimeoutException("Server did not respond in a timely fashion");
                            _ready = true;
                        }
                    }
                }
                catch (InterruptedException e)
                {
                    // IGNORE    -- //fixme this isn't ideal as being interrupted isn't equivellant to sucess
                    // if (!_ready && timeout != -1)
                    // {
                    // _error = new AMQException("Server did not respond timely");
                    // _ready = true;
                    // }
                }
            }


            if (_error != null)
            {
                if (_error instanceof AMQException)
                {
                    throw (AMQException) _error;
                }
                else if (_error instanceof FailoverException)
                {
                    // This should ensure that FailoverException is not wrapped and can be caught.
                    throw (FailoverException) _error; // needed to expose FailoverException.
                }
                else
                {
                    throw new AMQException("Woken up due to " + _error.getClass(), _error);
                }
            }

        }
        finally
        {
            _errorAck = true;
            _errorConditionAck.signal();
            _error = null;
            _lock.unlock();
        }

        return _doneEvt;
    }

    /**
     * This is a callback, called by the MINA dispatcher thread only. It is also called from within this
     * class to avoid code repetition but again is only called by the MINA dispatcher thread.
     *
     * @param e
     */
    public void error(Exception e)
    {
        // set the error so that the thread that is blocking (against blockForFrame())
        // can pick up the exception and rethrow to the caller


        _lock.lock();

        if (_error == null)
        {
            _error = e;
        }
        else
        {
            System.err.println("WARNING: new error arrived while old one not yet processed");
        }

        try
        {
            _ready = true;
            _receivedCondition.signal();

            while (!_errorAck)
            {
                try
                {
                    _errorConditionAck.await();
                }
                catch (InterruptedException e1)
                {
                    //
                }
            }
            _errorAck = false;
        }
        finally
        {
            _lock.unlock();
        }
    }

}
