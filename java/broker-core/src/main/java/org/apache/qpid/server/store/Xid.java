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
package org.apache.qpid.server.store;

import java.util.Arrays;

public final class Xid
{
    private final long _format;
    private final byte[] _globalId;
    private final byte[] _branchId;

    public Xid(long format, byte[] globalId, byte[] branchId)
    {
        _format = format;
        _globalId = globalId;
        _branchId = branchId;
    }

    public long getFormat()
    {
        return _format;
    }

    public byte[] getGlobalId()
    {
        return _globalId;
    }

    public byte[] getBranchId()
    {
        return _branchId;
    }

    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode(_branchId);
        result = prime * result + (int) (_format ^ (_format >>> 32));
        result = prime * result + Arrays.hashCode(_globalId);
        return result;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }

        if (obj == null)
        {
            return false;
        }

        if (getClass() != obj.getClass())
        {
            return false;
        }

        Xid other = (Xid) obj;

        if (!Arrays.equals(_branchId, other._branchId))
        {
            return false;
        }

        if (_format != other._format)
        {
            return false;
        }

        if (!Arrays.equals(_globalId, other._globalId))
        {
            return false;
        }
        return true;
    }


}