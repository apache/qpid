package org.apache.qpid.server.store.berkeleydb.replication;

import org.apache.qpid.server.model.VirtualHost;

/**
 * Represents a remote replication node in a BDB group.
 */
public class RemoteReplicationNode extends AbstractReplicationNode
{

    public RemoteReplicationNode(String groupName, String nodeName, String hostPort, VirtualHost virtualHost)
    {
        super(groupName, nodeName, hostPort, virtualHost);
    }

}
