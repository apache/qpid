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

import org.apache.qpid.server.exception.*;

import javax.transaction.xa.Xid;
import java.util.Set;

/**
 * Created by Arnaud Simon
 * Date: 02-May-2007
 * Time: 08:41:33
 */
public class MemoryTransactionManager implements TransactionManager
{

    public XAFlag begin(Xid xid)
            throws
            InternalErrorException,
            InvalidXidException
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public XAFlag prepare(Xid xid)
            throws
            InternalErrorException,
            CommandInvalidException,
            UnknownXidException
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public XAFlag rollback(Xid xid)
            throws
            InternalErrorException,
            CommandInvalidException,
            UnknownXidException
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public XAFlag commit(Xid xid)
            throws
            InternalErrorException,
            CommandInvalidException,
            UnknownXidException,
            NotPreparedException
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public XAFlag commit_one_phase(Xid xid)
            throws
            InternalErrorException,
            CommandInvalidException,
            UnknownXidException
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void forget(Xid xid)
            throws
            InternalErrorException,
            CommandInvalidException,
            UnknownXidException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void setTimeout(Xid xid, long timeout)
            throws
            InternalErrorException,
            UnknownXidException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public long getTimeout(Xid xid)
            throws
            InternalErrorException,
            UnknownXidException
    {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public Set<Xid> recover(boolean startscan, boolean endscan)
            throws
            InternalErrorException,
            CommandInvalidException
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void HeuristicOutcome(Xid xid)
            throws
            UnknownXidException,
            InternalErrorException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public Transaction getTransaction(Xid xid)
            throws
            UnknownXidException
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }
}
