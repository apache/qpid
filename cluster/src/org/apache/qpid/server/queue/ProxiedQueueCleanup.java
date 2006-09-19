/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.server.queue;

import org.apache.qpid.server.cluster.MembershipChangeListener;
import org.apache.qpid.server.cluster.MemberHandle;
import org.apache.qpid.server.cluster.GroupManager;
import org.apache.qpid.server.cluster.util.LogMessage;
import org.apache.qpid.AMQException;
import org.apache.log4j.Logger;

import java.util.List;

class ProxiedQueueCleanup implements MembershipChangeListener
{
    private static final Logger _logger = Logger.getLogger(ProxiedQueueCleanup.class);

    private final MemberHandle _subject;
    private final RemoteQueueProxy _queue;

    ProxiedQueueCleanup(MemberHandle subject, RemoteQueueProxy queue)
    {
        _subject = subject;
        _queue = queue;
    }

    public void changed(List<MemberHandle> members)
    {
        if(!members.contains(_subject))
        {
            try
            {
                _queue.delete();
                _logger.info(new LogMessage("Deleted {0} in response to exclusion of {1}", _queue, _subject));
            }
            catch (AMQException e)
            {
                _logger.info(new LogMessage("Failed to delete {0} in response to exclusion of {1}: {2}", _queue, _subject, e), e);
            }
        }
    }
}
