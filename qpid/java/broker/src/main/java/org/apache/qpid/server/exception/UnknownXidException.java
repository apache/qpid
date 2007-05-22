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
package org.apache.qpid.server.exception;

import javax.transaction.xa.Xid;

/**
 * UnknownXidException indicates that an Xid under which a transactional operation is to be run is not known.
 *
 * <p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Represents failure of a transaction manager to recognize an Xid.
 * </table>
 *
 * @todo Already have an InvalidXidException, this might be splitting things too far? There are cases where invalid is
 *       caught and rethrown as unknown. What is unknown specifically used for that invalid is not? For example, when
 *       recovering, is it important to distinguish between invalid and unknown?
 */
public class UnknownXidException extends Exception
{
    /**
     * Constructs a newr UnknownXidException with a standard message
     *
     * @param xid The unknown xid.
     *
     * @deprecated
     */
    public UnknownXidException(Xid xid)
    {
        super("The Xid: " + xid + " is unknown");
    }

    /**
     * Constructs a newr UnknownXidException with a cause
     *
     * @param xid   The unknown xid.
     * @param cause The casue for the xid to be unknown
     */
    public UnknownXidException(Xid xid, Throwable cause)
    {
        super("The Xid: " + xid + " is unknown", cause);
    }

    /**
     * Constructs a newr UnknownXidException with a reason message
     *
     * @param reason The reason why the xid is unknown
     * @param xid    The unknown xid.
     *
     * @deprecated
     */
    /*public UnknownXidException(Xid xid, String reason)
    {
        super("The Xid: " + xid + " is unknown, The reason is: " + reason);
    }*/

    /**
     * Constructs a newr UnknownXidException with a reason message and cause
     *
     * @param reason The reason why the xid is unknown
     * @param xid    The unknown xid.
     * @param cause  The casue for the xid to be unknown
     *
     * @deprected
     */
    public UnknownXidException(Xid xid, String reason, Throwable cause)
    {
        super("The Xid: " + xid + " is unknown, The reason is: " + reason, cause);
    }
}
