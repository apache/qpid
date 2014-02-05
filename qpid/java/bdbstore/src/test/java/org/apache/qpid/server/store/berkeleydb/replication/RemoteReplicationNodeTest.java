package org.apache.qpid.server.store.berkeleydb.replication;

import static org.apache.qpid.server.model.ReplicationNode.COALESCING_SYNC;
import static org.apache.qpid.server.model.ReplicationNode.DURABILITY;
import static org.apache.qpid.server.model.ReplicationNode.GROUP_NAME;
import static org.apache.qpid.server.model.ReplicationNode.HELPER_HOST_PORT;
import static org.apache.qpid.server.model.ReplicationNode.HOST_PORT;
import static org.apache.qpid.server.model.ReplicationNode.JOIN_TIME;
import static org.apache.qpid.server.model.ReplicationNode.LAST_KNOWN_REPLICATION_TRANSACTION_ID;
import static org.apache.qpid.server.model.ReplicationNode.NAME;
import static org.apache.qpid.server.model.ReplicationNode.PARAMETERS;
import static org.apache.qpid.server.model.ReplicationNode.REPLICATION_PARAMETERS;
import static org.apache.qpid.server.model.ReplicationNode.ROLE;
import static org.apache.qpid.server.model.ReplicationNode.STORE_PATH;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.test.utils.QpidTestCase;

import com.sleepycat.je.rep.NodeState;
import com.sleepycat.je.rep.ReplicatedEnvironment.State;
import com.sleepycat.je.rep.ReplicationNode;

public class RemoteReplicationNodeTest extends QpidTestCase
{

    private RemoteReplicationNode _node;
    private String _groupName;
    private VirtualHost _virtualHost;
    private TaskExecutor _taskExecutor;
    private ReplicationNode _replicationNode;
    private String _nodeName;
    private int _port;
    private ReplicatedEnvironmentFacade _replicatedEnvironmentFacade;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        _groupName = getTestName();
        _nodeName = getTestName() + "Name";
        _port = 5000;
        _replicationNode = mock(ReplicationNode.class);
        _virtualHost = mock(VirtualHost.class);
        _taskExecutor = mock(TaskExecutor.class);
        _replicatedEnvironmentFacade = mock(ReplicatedEnvironmentFacade.class);
        when(_replicatedEnvironmentFacade.getGroupName()).thenReturn(_groupName);

        when(_taskExecutor.isTaskExecutorThread()).thenReturn(true);
        when(_replicationNode.getName()).thenReturn(_nodeName);
        when(_replicationNode.getHostName()).thenReturn("localhost");
        when(_replicationNode.getPort()).thenReturn(_port);

        _node = new RemoteReplicationNode(_replicationNode, _virtualHost, _taskExecutor, _replicatedEnvironmentFacade);
    }

    public void testGetAttribute() throws Exception
    {
        State state = State.MASTER;
        long joinTime = System.currentTimeMillis();
        long currentTxnEndVLSN = 3;

        updateNodeState(state, joinTime, currentTxnEndVLSN);

        assertEquals("Unexpected name", _nodeName, _node.getAttribute(NAME));
        assertEquals("Unexpected group name", _groupName, _node.getAttribute(GROUP_NAME));
        assertEquals("Unexpected state", state.name(), _node.getAttribute(ROLE));
        assertEquals("Unexpected transaction id", currentTxnEndVLSN, _node.getAttribute(LAST_KNOWN_REPLICATION_TRANSACTION_ID));
        assertEquals("Unexpected join time", joinTime, _node.getAttribute(JOIN_TIME));
    }

    public void testSetRoleAttribute() throws Exception
    {
        updateNodeState();
        _node.setAttributes(Collections.<String, Object>singletonMap(ROLE, State.MASTER.name()));

        verify(_replicatedEnvironmentFacade).transferMasterAsynchronously(_nodeName);
    }

    public void testSetRoleAttributeDisallowedIfAlreadyMaster() throws Exception
    {
        updateNodeState(State.MASTER, System.currentTimeMillis(), 0L);
        try
        {
            _node.setAttributes(Collections.<String, Object>singletonMap(ROLE, State.MASTER.name()));
            fail("Exception not thrown");
        }
        catch (IllegalConfigurationException ice)
        {
            // pass
        }

        verify(_replicatedEnvironmentFacade, never()).transferMasterAsynchronously(_nodeName);
    }

    public void testSetImmutableAttributesThrowException() throws Exception
    {
        Map<String, Object> changeAttributeMap = new HashMap<String, Object>();
        changeAttributeMap.put(GROUP_NAME, "newGroupName");
        changeAttributeMap.put(HELPER_HOST_PORT, "newhost:1234");
        changeAttributeMap.put(HOST_PORT, "newhost:1234");
        changeAttributeMap.put(COALESCING_SYNC, Boolean.FALSE);
        changeAttributeMap.put(DURABILITY, "durability");
        changeAttributeMap.put(JOIN_TIME, 1000l);
        changeAttributeMap.put(LAST_KNOWN_REPLICATION_TRANSACTION_ID, 10001l);
        changeAttributeMap.put(NAME, "newName");
        changeAttributeMap.put(STORE_PATH, "/not/used");
        changeAttributeMap.put(PARAMETERS, Collections.emptyMap());
        changeAttributeMap.put(REPLICATION_PARAMETERS, Collections.emptyMap());

        for (Entry<String, Object> entry : changeAttributeMap.entrySet())
        {
            assertSetAttributesThrowsException(entry.getKey(), entry.getValue());
        }
    }

    private void assertSetAttributesThrowsException(String attributeName, Object attributeValue) throws Exception
    {
        updateNodeState();

        try
        {
            _node.setAttributes(Collections.<String, Object>singletonMap(attributeName, attributeValue));
            fail("Operation to change attribute '" + attributeName + "' should fail");
        }
        catch(IllegalConfigurationException e)
        {
            // pass
        }
    }

    private void updateNodeState() throws Exception
    {
        updateNodeState( State.REPLICA, System.currentTimeMillis(), 3);
    }

    private void updateNodeState(State state, long joinTime, long currentTxnEndVLSN) throws Exception
    {
        NodeState nodeState = new NodeState(_nodeName, _groupName, state, null, null, joinTime, currentTxnEndVLSN, 2, 1, 0, null, 0.0);
        when(_replicatedEnvironmentFacade.getRemoteNodeState(_replicationNode)).thenReturn(nodeState);
        _node.updateNodeState();
    }
}
