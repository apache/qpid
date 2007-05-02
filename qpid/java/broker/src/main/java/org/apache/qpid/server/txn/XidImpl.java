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
package org.apache.qpid.server.txn;


import org.apache.log4j.Logger;

import javax.transaction.xa.Xid;

/**
 * Created by Arnaud Simon
 * Date: 03-Apr-2007
 * Time: 20:32:55
 */
public class XidImpl implements Xid
{
    //========================================================================
    // Static Constants
    //========================================================================
    // The logger for this class
    private static final Logger _log = Logger.getLogger(XidImpl.class);

    //========================================================================
    // Instance Fields
    //========================================================================

    //the transaction branch identifier part of XID as an array of bytes
    private byte[] m_branchQualifier;

    // the format identifier part of the XID.
    private int m_formatID;

    // the global transaction identifier part of XID as an array of bytes.
    private byte[] m_globalTransactionID;

    //========================================================================
    // Constructor(s)
    //========================================================================

    /**
     * Create new Xid.
     */
    public XidImpl()
    {

    }

    /**
     * Create new XidImpl from an existing Xid.
     * <p/>
     * This is usually called when an application server provides some implementation
     * of the Xid interface and we need to cast this into our own XidImpl.
     *
     * @param xid the xid to cloning
     */
    public XidImpl(Xid xid)
    {
        if (_log.isDebugEnabled())
        {
            _log.debug("Cloning Xid");
        }
        m_branchQualifier = xid.getBranchQualifier();
        m_formatID = xid.getFormatId();
        m_globalTransactionID = xid.getGlobalTransactionId();
    }

    /**
     * Create a new Xid.
     *
     * @param branchQualifier     The transaction branch identifier part of XID as an array of bytes.
     * @param format              The format identifier part of the XID.
     * @param globalTransactionID The global transaction identifier part of XID as an array of bytes.
     */
    public XidImpl(byte[] branchQualifier, int format, byte[] globalTransactionID)
    {
        if (_log.isDebugEnabled())
        {
            _log.debug("creating Xid");
        }
        m_branchQualifier = branchQualifier;
        m_formatID = format;
        m_globalTransactionID = globalTransactionID;
    }

//========================================================================

    //  Xid interface implementation
    //========================================================================
    /**
     * Format identifier. O means the OSI CCR format.
     *
     * @return Global transaction identifier.
     */
    public byte[] getGlobalTransactionId()
    {
        return m_globalTransactionID;
    }

    /**
     * Obtain the transaction branch identifier part of XID as an array of bytes.
     *
     * @return Branch identifier part of XID.
     */
    public byte[] getBranchQualifier()
    {
        return m_branchQualifier;
    }

    /**
     * Obtain the format identifier part of the XID.
     *
     * @return Format identifier. O means the OSI CCR format.
     */
    public int getFormatId()
    {
        return m_formatID;
    }

//========================================================================
//  Object operations
//========================================================================

    /**
     * Indicates whether some other Xid is "equal to" this one.
     * <p/>
     * Two Xids are equal if and only if their three elementary parts are equal
     *
     * @param o the object to compare this <code>XidImpl</code> against.
     * @return code>true</code> if the <code>XidImpl</code> are equal; <code>false</code> otherwise.
     */
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o instanceof XidImpl)
        {
            XidImpl other = (XidImpl) o;
            if (m_formatID == other.getFormatId())
            {
                if (m_branchQualifier.length == other.getBranchQualifier().length)
                {
                    for (int i = 0; i < m_branchQualifier.length; i++)
                    {
                        if (m_branchQualifier[i] != other.getBranchQualifier()[i])
                        {
                            return false;
                        }
                    }

                    if (m_globalTransactionID.length == other.getGlobalTransactionId().length)
                    {
                        for (int i = 0; i < m_globalTransactionID.length; i++)
                        {
                            if (m_globalTransactionID[i] != other.getGlobalTransactionId()[i])
                            {
                                return false;
                            }
                        }
                        // everithing is equal
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Returns a hash code for this Xid.
     * <p/>
     * As this object is used as a key entry in a hashMap it is necessary to provide an implementation
     * of hashcode in order to fulfill the following aspect of the general contract of
     * {@link Object#hashCode()} that is:
     * <ul>
     * <li>If two objects are equal according to the <tt>equals(Object)</tt>
     * method, then calling the <code>hashCode</code> method on each of
     * the two objects must produce the same integer result.
     * </ul>
     * <p/>
     * The hash code for a
     * <code>XidImpl</code> object is computed as
     * <blockquote><pre>
     *  hashcode( globalTransactionID ) + hashcode( branchQualifier ) + formatID
     * </pre></blockquote>
     *
     * @return a hash code value for this object.
     */
    public int hashCode()
    {
        return (new String(m_globalTransactionID)).hashCode() + (new String(m_branchQualifier)).hashCode() + m_formatID;
    }

}
