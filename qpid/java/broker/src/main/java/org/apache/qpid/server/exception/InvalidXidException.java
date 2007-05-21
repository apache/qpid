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
 * InvalidXidException indicates that an Xid under which to conduct a transaction is invalid. This may be because it
 * has an incorrect format, is null, or a transcaction with the same Xid is already running.
 *
 * <p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Represents an invalid Xid for a transaction.
 * <tr><td>
 */
public class InvalidXidException extends Exception
{
    /**
     * Constructs a newr InvalidXidException with a standard message
     *
     * @param xid The invalid xid.
     *
     * @deprected
     */
    public InvalidXidException(Xid xid)
    {
        super("The Xid: " + xid + " is invalid");
    }

    /**
     * Constructs a newr InvalidXidException with a cause
     *
     * @param xid   The invalid xid.
     * @param cause The casue for the xid to be invalid
     *
     * @deprected
     */
    public InvalidXidException(Xid xid, Throwable cause)
    {
        super("The Xid: " + xid + " is invalid", cause);
    }

    /**
     * Constructs a newr InvalidXidException with a reason message
     *
     * @param reason The reason why the xid is invalid
     * @param xid    The invalid xid.
     */
    public InvalidXidException(Xid xid, String reason)
    {
        super("The Xid: " + xid + " is invalid, The reason is: " + reason);
    }

    /**
     * Constructs a newr InvalidXidException with a reason message and cause
     *
     * @param reason The reason why the xid is invalid
     * @param xid    The invalid xid.
     * @param cause  The casue for the xid to be invalid
     *
     * @deprected
     */
    public InvalidXidException(Xid xid, String reason, Throwable cause)
    {
        super("The Xid: " + xid + " is invalid, The reason is: " + reason, cause);
    }
}
