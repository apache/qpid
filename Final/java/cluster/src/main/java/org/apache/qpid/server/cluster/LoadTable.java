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
package org.apache.qpid.server.cluster;

import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Maintains loading information about the local member and its cluster peers.
 *
 */
public class LoadTable
{
    private final Map<MemberHandle, Loading> _peers = new HashMap<MemberHandle, Loading>();
    private final PriorityQueue<Loading> _loads = new PriorityQueue<Loading>();
    private final Loading _local = new Loading(null);

    public LoadTable()
    {
        _loads.add(_local);
    }

    public void setLoad(Member member, long load)
    {
        synchronized (_peers)
        {
            Loading loading = _peers.get(member);
            if (loading == null)
            {
                loading = new Loading(member);
                synchronized (_loads)
                {
                    _loads.add(loading);
                }
                _peers.put(member, loading);
            }
            loading.load = load;
        }
    }

    public void incrementLocalLoad()
    {
        synchronized (_local)
        {
            _local.load++;
        }
    }

    public void decrementLocalLoad()
    {
        synchronized (_local)
        {
            _local.load--;
        }
    }

    public long getLocalLoad()
    {
        synchronized (_local)
        {
            return _local.load;
        }
    }

    public Member redirect()
    {
        synchronized (_loads)
        {
            return _loads.peek().member;
        }
    }

    private static class Loading implements Comparable
    {
        private final Member member;
        private long load;

        Loading(Member member)
        {
            this.member = member;
        }

        public int compareTo(Object o)
        {
            return (int) (load - ((Loading) o).load);
        }
    }
}
