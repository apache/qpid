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
import org.apache.log4j.Logger;

import java.util.List;

class SubscriberCleanup implements MembershipChangeListener
{
    private static final Logger _logger = Logger.getLogger(SubscriberCleanup.class);

    private final MemberHandle _subject;
    private final ClusteredQueue _queue;
    private final GroupManager _manager;

    SubscriberCleanup(MemberHandle subject, ClusteredQueue queue, GroupManager manager)
    {
        _subject = subject;
        _queue = queue;
        _manager = manager;
        _manager.addMemberhipChangeListener(this);
    }

    public void changed(List<MemberHandle> members)
    {
        if(!members.contains(_subject))
        {
            _queue.removeAllRemoteSubscriber(_subject);
            _manager.removeMemberhipChangeListener(this);
            _logger.info(new LogMessage("Removed {0} from {1}", _subject, _queue));
        }
    }
}
