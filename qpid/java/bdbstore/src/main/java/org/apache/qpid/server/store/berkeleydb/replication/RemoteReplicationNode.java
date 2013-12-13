package org.apache.qpid.server.store.berkeleydb.replication;

import java.security.AccessControlException;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.model.ConfigurationChangeListener;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.IllegalStateTransitionException;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.ReplicationNode;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.Statistics;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.model.VirtualHost;

/**
 * Represents a remote replication node in a BDB group.
 */
public class RemoteReplicationNode implements ReplicationNode
{

    private final UUID _id;
    private final String _groupName;
    private final String _nodeName;
    private final String _host;
    private final int _port;
    private final VirtualHost _virtualHost;

    public RemoteReplicationNode(String groupName, String nodeName, String host, int port, VirtualHost virtualHost)
    {
        super();
        _id = UUIDGenerator.generateReplicationNodeId(groupName, nodeName);
        _groupName = groupName;
        _nodeName = nodeName;
        _host = host;
        _port = port;
        _virtualHost = virtualHost;
    }

    @Override
    public UUID getId()
    {
        return _id;
    }

    @Override
    public String getName()
    {
        return _nodeName;
    }

    @Override
    public String setName(String currentName, String desiredName)
            throws IllegalStateException, AccessControlException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public State getDesiredState()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public State setDesiredState(State currentState, State desiredState)
            throws IllegalStateTransitionException, AccessControlException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public State getActualState()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addChangeListener(ConfigurationChangeListener listener)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeChangeListener(ConfigurationChangeListener listener)
    {
        throw new UnsupportedOperationException();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends ConfiguredObject> T getParent(Class<T> clazz)
    {
        if (clazz == VirtualHost.class)
        {
            return (T) _virtualHost;
        }
        throw new IllegalArgumentException();
    }

    @Override
    public boolean isDurable()
    {
        return true;
    }

    @Override
    public void setDurable(boolean durable) throws IllegalStateException,
            AccessControlException, IllegalArgumentException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public LifetimePolicy getLifetimePolicy()
    {
        return LifetimePolicy.PERMANENT;
    }

    @Override
    public LifetimePolicy setLifetimePolicy(LifetimePolicy expected,
            LifetimePolicy desired) throws IllegalStateException,
            AccessControlException, IllegalArgumentException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getTimeToLive()
    {
        return 0;
    }

    @Override
    public long setTimeToLive(long expected, long desired)
            throws IllegalStateException, AccessControlException,
            IllegalArgumentException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<String> getAttributeNames()
    {
        return ReplicationNode.AVAILABLE_ATTRIBUTES;
    }

    @Override
    public Object getAttribute(String name)
    {
        if (ReplicationNode.ID.equals(name))
        {
            return getId();
        }
        else if (ReplicationNode.NAME.equals(name))
        {
            return getName();
        }
        else if (ReplicationNode.LIFETIME_POLICY.equals(name))
        {
            return getLifetimePolicy();
        }
        else if (ReplicationNode.DURABLE.equals(name))
        {
            return isDurable();
        }
        else if (ReplicationNode.HOST_PORT.equals(name))
        {
            return _host + ":" + _port;
        }
        else if (ReplicationNode.GROUP_NAME.equals(name))
        {
            return _groupName;
        }
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, Object> getActualAttributes()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object setAttribute(String name, Object expected, Object desired)
            throws IllegalStateException, AccessControlException,
            IllegalArgumentException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Statistics getStatistics()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public <C extends ConfiguredObject> Collection<C> getChildren(Class<C> clazz)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public <C extends ConfiguredObject> C createChild(Class<C> childClass,
            Map<String, Object> attributes, ConfiguredObject... otherParents)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setAttributes(Map<String, Object> attributes)
            throws IllegalStateException, AccessControlException,
            IllegalArgumentException
    {
        throw new UnsupportedOperationException();
    }
}
