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
package org.apache.qpidity.njms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.IllegalStateException;
import javax.jms.JMSException;

/**
 * MessageActor is the superclass for MessageProducerImpl and MessageProducerImpl.
 */
public abstract class MessageActor
{
    /**
     * Used for debugging.
     */
    protected static final Logger _logger = LoggerFactory.getLogger(MessageActor.class);

    /**
     * Indicates whether this MessageActor is closed.
     */
    protected boolean _isClosed = false;

    /**
     * This messageActor's session
     */
    private SessionImpl _session;

    /**
     * The JMS destination this actor is set for.
     */
    DestinationImpl _destination;

    /**
     * Indicates that this actor is stopped
     */
    protected boolean _isStopped;

    /**
     * The ID of this actor for the session.
     */
    private String _messageActorID;

    //-- Constructor

    //TODO define the parameters

    protected MessageActor(String messageActorID)
    {
        _messageActorID = messageActorID;
    }

    protected MessageActor(SessionImpl session, DestinationImpl destination,String messageActorID)
    {
        _session = session;
        _destination = destination;
        _messageActorID = messageActorID;
    }

    //--- public methods (part of the njms public API)
    /**
     * Closes the MessageActor and deregister it from its session.
     *
     * @throws JMSException if the MessaeActor cannot be closed due to some internal error.
     */
    public void close() throws JMSException
    {
        if (!_isClosed)
        {
            closeMessageActor();
            getSession().getQpidSession().messageCancel(getMessageActorID());
            //todo: We need to unset the qpid message listener  
            // notify the session that this message actor is closing
            _session.closeMessageActor(this);
        }
    }

    //-- protected methods

    /**
     * Stop this message actor
     *
     * @throws Exception If the consumer cannot be stopped due to some internal error.
     */
    protected void stop() throws Exception
    {
        _isStopped = true;
    }

    /**
     * Start this message Actor
     *
     * @throws Exception If the consumer cannot be started due to some internal error.
     */
    protected void start() throws Exception
    {

        _isStopped = false;

    }

    /**
     * Check if this MessageActor is not closed.
     * <p> If the MessageActor is closed, throw a javax.njms.IllegalStateException.
     * <p> The method is not synchronized, since MessageProducers can only be used by a single thread.
     *
     * @throws IllegalStateException if the MessageActor is closed
     */
    protected void checkNotClosed() throws IllegalStateException
    {
        if (_isClosed || _session == null)
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("Actor " + this + " is already closed");
            }
            throw new IllegalStateException("Actor " + this + " is already closed");
        }
        _session.checkNotClosed();
    }

    /**
     * Closes a MessageActor.
     * <p> This method is invoked when the session is closing or when this
     * messageActor is closing.
     *
     * @throws JMSException If the MessaeActor cannot be closed due to some internal error.
     */
    protected void closeMessageActor() throws JMSException
    {
        if (!_isClosed)
        {
            getSession().getQpidSession().messageCancel(getMessageActorID());
            _isClosed = true;
        }
    }

    /**
     * Get the associated session object.
     *
     * @return This Actor's Session.
     */
    public SessionImpl getSession()
    {
        return _session;
    }

    /**
     * Get the ID of this actor within its session.
     *
     * @return This actor ID.
     */
    protected String getMessageActorID()
    {
        return _messageActorID;
    }


}
