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

import org.apache.qpidity.QpidException;
import org.apache.qpidity.nclient.DtxSession;

import javax.jms.XASession;
import javax.jms.Session;
import javax.jms.JMSException;
import javax.jms.TransactionInProgressException;
import javax.transaction.xa.XAResource;

/**
 * This is an implementation of the javax.njms.XASEssion interface.
 */
public class XASessionImpl extends SessionImpl implements XASession
{
    /**
     * XAResource associated with this XASession
     */
    private final XAResourceImpl _xaResource;

    /**
     * This XASession Qpid DtxSession
     */
    private DtxSession _qpidDtxSession;

    /**
     * The standard session
     */
    private Session _jmsSession;

    //-- Constructors
    /**
     * Create a JMS XASession
     *
     * @param connection The ConnectionImpl object from which the Session is created.
     * @throws QpidException In case of internal error.
     */
    protected XASessionImpl(ConnectionImpl connection) throws QpidException
    {
        super(connection, true,  // this is a transacted session
              Session.SESSION_TRANSACTED, // the ack mode is transacted
              true); // this is an XA session so do not set tx
        _qpidDtxSession = getConnection().getQpidConnection().createDTXSession(0);
        _xaResource = new XAResourceImpl(this);
    }

    //--- javax.njms.XASEssion API

    /**
     * Gets the session associated with this XASession.
     *
     * @return The session object.
     * @throws JMSException if an internal error occurs.
     */
    public Session getSession() throws JMSException
    {
       if( _jmsSession == null )
       {
           _jmsSession = getConnection().createSession(true, getAcknowledgeMode());
       }
        return _jmsSession;
    }

    /**
     * Returns an XA resource.
     *
     * @return An XA resource.
     */
    public XAResource getXAResource()
    {
        return _xaResource;
    }

    //-- overwritten mehtods
    /**
     * Throws a {@link TransactionInProgressException}, since it should
     * not be called for an XASession object.
     *
     * @throws TransactionInProgressException always.
     */
    public void commit() throws JMSException
    {
        throw new TransactionInProgressException(
                "XASession:  A direct invocation of the commit operation is probibited!");
    }

    /**
     * Throws a {@link TransactionInProgressException}, since it should
     * not be called for an XASession object.
     *
     * @throws TransactionInProgressException always.
     */
    public void rollback() throws JMSException
    {
        throw new TransactionInProgressException(
                "XASession: A direct invocation of the rollback operation is probibited!");
    }

    /**
     * Access to the underlying Qpid Session
     *
     * @return The associated Qpid Session.
     */
    protected org.apache.qpidity.nclient.DtxSession getQpidSession()
    {
        return _qpidDtxSession;
    }
}
