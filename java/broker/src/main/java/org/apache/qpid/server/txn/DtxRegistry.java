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

package org.apache.qpid.server.txn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.qpid.AMQStoreException;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.transport.Xid;

public class DtxRegistry
{
    private final Map<ComparableXid, DtxBranch> _branches = new HashMap<ComparableXid, DtxBranch>();


    private static final class ComparableXid
    {
        private final Xid _xid;
        
        private ComparableXid(Xid xid)
        {
            _xid = xid;
        }

        @Override
        public boolean equals(Object o)
        {
            if(this == o)
            {
                return true;
            }
            if(o == null || getClass() != o.getClass())
            {
                return false;
            }

            ComparableXid that = (ComparableXid) o;

            return compareBytes(_xid.getBranchId(), that._xid.getBranchId())
                    && compareBytes(_xid.getGlobalId(), that._xid.getGlobalId()); 
        }

        private static boolean compareBytes(byte[] a, byte[] b)
        {
            if(a.length != b.length)
            {
                return false;
            }
            for(int i = 0; i < a.length; i++)
            {
                if(a[i] != b[i])
                {
                    return false;
                }
            }
            return true;
        }


        @Override
        public int hashCode()
        {
            int result = 0;
            for(int i = 0; i < _xid.getGlobalId().length; i++)
            {
                result = 31 * result + (int) _xid.getGlobalId()[i];
            }
            for(int i = 0; i < _xid.getBranchId().length; i++)
            {
                result = 31 * result + (int) _xid.getBranchId()[i];
            }

            return result;
        }
    }
    
    public synchronized DtxBranch getBranch(Xid xid)
    {
        return _branches.get(new ComparableXid(xid));
    }

    public synchronized boolean registerBranch(DtxBranch branch)
    {
        ComparableXid xid = new ComparableXid(branch.getXid());
        if(!_branches.containsKey(xid))
        {
            _branches.put(xid, branch);
            return true;
        }
        return false;
    }

    synchronized boolean unregisterBranch(DtxBranch branch)
    {
        return (_branches.remove(new ComparableXid(branch.getXid())) != null);
    }

    public void commit(Xid id, boolean onePhase)
            throws IncorrectDtxStateException, UnknownDtxBranchException, AMQStoreException, RollbackOnlyDtxException, TimeoutDtxException
    {
        DtxBranch branch = getBranch(id);
        if(branch != null)
        {
            synchronized (branch)
            {
                if(!branch.hasAssociatedActiveSessions())
                {
                    branch.clearAssociations();

                    if(branch.expired() || branch.getState() == DtxBranch.State.TIMEDOUT)
                    {
                        unregisterBranch(branch);
                        throw new TimeoutDtxException(id);
                    }
                    else if(branch.getState() == DtxBranch.State.ROLLBACK_ONLY)
                    {
                        throw new RollbackOnlyDtxException(id);
                    }
                    else if(onePhase && branch.getState() == DtxBranch.State.PREPARED)
                    {
                        throw new IncorrectDtxStateException("Cannot call one-phase commit on a prepared branch", id);
                    }
                    else if(!onePhase && branch.getState() != DtxBranch.State.PREPARED)
                    {
                        throw new IncorrectDtxStateException("Cannot call two-phase commit on a non-prepared branch",
                                                             id);
                    }
                    branch.commit();
                    branch.setState(DtxBranch.State.FORGOTTEN);
                    unregisterBranch(branch);
                }
                else
                {
                    throw new IncorrectDtxStateException("Branch was still associated with a session", id);
                }
            }
        }
        else
        {
            throw new UnknownDtxBranchException(id);
        }
    }

    public synchronized void prepare(Xid id)
            throws UnknownDtxBranchException,
            IncorrectDtxStateException, AMQStoreException, RollbackOnlyDtxException, TimeoutDtxException
    {
        DtxBranch branch = getBranch(id);
        if(branch != null)
        {
            synchronized (branch)
            {
                if(!branch.hasAssociatedActiveSessions())
                {
                    branch.clearAssociations();

                    if(branch.expired() || branch.getState() == DtxBranch.State.TIMEDOUT)
                    {
                        unregisterBranch(branch);
                        throw new TimeoutDtxException(id);
                    }
                    else if(branch.getState() != DtxBranch.State.ACTIVE
                            && branch.getState() != DtxBranch.State.ROLLBACK_ONLY)
                    {
                        throw new IncorrectDtxStateException("Cannot prepare a transaction in state "
                                                             + branch.getState(), id);
                    }
                    else
                    {
                        branch.prepare();
                        branch.setState(DtxBranch.State.PREPARED);
                    }
                }
                else
                {
                    throw new IncorrectDtxStateException("Branch still has associated sessions", id);
                }
            }
        }
        else
        {
            throw new UnknownDtxBranchException(id);
        }
    }

    public void rollback(Xid id)
            throws IncorrectDtxStateException,
            UnknownDtxBranchException,
            AMQStoreException, TimeoutDtxException
    {

        DtxBranch branch = getBranch(id);
        if(branch != null)
        {
            synchronized (branch)
            {
                if(branch.expired() || branch.getState() == DtxBranch.State.TIMEDOUT)
                {
                    unregisterBranch(branch);
                    throw new TimeoutDtxException(id);
                }
                if(!branch.hasAssociatedActiveSessions())
                {
                    branch.clearAssociations();
                    branch.rollback();
                    branch.setState(DtxBranch.State.FORGOTTEN);
                    unregisterBranch(branch);
                }
                else
                {
                    throw new IncorrectDtxStateException("Branch was still associated with a session", id);
                }
            }
        }
        else
        {
            throw new UnknownDtxBranchException(id);
        }
    }


    public void forget(Xid id) throws UnknownDtxBranchException, IncorrectDtxStateException
    {
        DtxBranch branch = getBranch(id);
        if(branch != null)
        {
            synchronized (branch)
            {
                if(!branch.hasAssociatedSessions())
                {
                    if(branch.getState() != DtxBranch.State.HEUR_COM && branch.getState() != DtxBranch.State.HEUR_RB)
                    {
                        throw new IncorrectDtxStateException("Branch should not be forgotten - "
                                                             + "it is not heuristically complete", id);
                    }
                    branch.setState(DtxBranch.State.FORGOTTEN);
                    unregisterBranch(branch);
                }
                else
                {
                    throw new IncorrectDtxStateException("Branch was still associated with a session", id);
                }
            }
        }
        else
        {
            throw new UnknownDtxBranchException(id);
        }
    }

    public long getTimeout(Xid id) throws UnknownDtxBranchException
    {
        DtxBranch branch = getBranch(id);
        if(branch != null)
        {
            return branch.getTimeout();
        }
        else
        {
            throw new UnknownDtxBranchException(id);
        }
    }

    public void setTimeout(Xid id, long timeout) throws UnknownDtxBranchException
    {
        DtxBranch branch = getBranch(id);
        if(branch != null)
        {
            branch.setTimeout(timeout);
        }
        else
        {
            throw new UnknownDtxBranchException(id);
        }
    }

    public synchronized List<Xid> recover()
    {
        List<Xid> inDoubt = new ArrayList<Xid>();
        for(DtxBranch branch : _branches.values())
        {
            if(branch.getState() == DtxBranch.State.PREPARED)
            {
                inDoubt.add(branch.getXid());
            }
        }
        return inDoubt;
    }

    public synchronized void endAssociations(AMQSessionModel session)
    {
        for(DtxBranch branch : _branches.values())
        {
            if(branch.isAssociated(session))
            {
                branch.setState(DtxBranch.State.ROLLBACK_ONLY);
                branch.disassociateSession(session);
            }
        }
    }


    public synchronized void close()
    {
        for(DtxBranch branch : _branches.values())
        {
            branch.close();
        }
        _branches.clear();
    }

}
