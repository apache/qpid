package org.apache.qpid.server.store.berkeleydb;

import java.io.File;
import java.net.InetAddress;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.qpid.server.configuration.ServerConfiguration;
import org.apache.qpid.server.logging.SystemOutMessageLogger;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.actors.TestLogActor;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.util.TestApplicationRegistry;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.util.FileUtils;

import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.rep.ReplicatedEnvironment;
import com.sleepycat.je.rep.ReplicationConfig;

public class BDBHAMessageStoreTest extends QpidTestCase
{
    private static final String TEST_LOG_FILE_MAX = "1000000";
    private static final String TEST_ELECTION_RETRIES = "1000";
    private static final String TEST_NUMBER_OF_THREADS = "10";
    private static final String TEST_ENV_CONSISTENCY_TIMEOUT = "9999999";
    private String _groupName;
    private String _workDir;
    private int _masterPort;
    private String _host;
    private XMLConfiguration _configXml;

    public void setUp() throws Exception
    {
        super.setUp();

        _workDir = TMP_FOLDER + File.separator + getName();
        _host = InetAddress.getByName("localhost").getHostAddress();
        _groupName = "group" + getName();
        _masterPort = -1;

        FileUtils.delete(new File(_workDir), true);
        _configXml = new XMLConfiguration();
    }

    public void tearDown() throws Exception
    {
        FileUtils.delete(new File(_workDir), true);
        super.tearDown();
    }

    public void testSetSystemConfiguration() throws Exception
    {
        // create virtual host configuration, registry and host instance
        addVirtualHostConfiguration();
        TestApplicationRegistry registry = initialize();
        try
        {
            VirtualHost virtualhost = registry.getVirtualHostRegistry().getVirtualHost("test" + _masterPort);
            BDBHAMessageStore store = (BDBHAMessageStore) virtualhost.getMessageStore();

            // test whether JVM system settings were applied
            Environment env = store.getEnvironment();
            assertEquals("Unexpected number of cleaner threads", TEST_NUMBER_OF_THREADS, env.getConfig().getConfigParam(EnvironmentConfig.CLEANER_THREADS));
            assertEquals("Unexpected log file max", TEST_LOG_FILE_MAX, env.getConfig().getConfigParam(EnvironmentConfig.LOG_FILE_MAX));

            ReplicatedEnvironment repEnv = store.getReplicatedEnvironment();
            assertEquals("Unexpected number of elections primary retries", TEST_ELECTION_RETRIES,
                    repEnv.getConfig().getConfigParam(ReplicationConfig.ELECTIONS_PRIMARY_RETRIES));
            assertEquals("Unexpected number of elections primary retries", TEST_ENV_CONSISTENCY_TIMEOUT,
                    repEnv.getConfig().getConfigParam(ReplicationConfig.ENV_CONSISTENCY_TIMEOUT));
        }
        finally
        {
            ApplicationRegistry.remove();
        }
    }

    private void addVirtualHostConfiguration() throws Exception
    {
        int port = findFreePort();
        if (_masterPort == -1)
        {
            _masterPort = port;
        }
        String nodeName = getNodeNameForNodeAt(port);

        String vhostName = "test" + port;
        String vhostPrefix = "virtualhosts.virtualhost." + vhostName;

        _configXml.addProperty("virtualhosts.virtualhost.name", vhostName);
        _configXml.addProperty(vhostPrefix + ".store.class", BDBHAMessageStore.class.getName());
        _configXml.addProperty(vhostPrefix + ".store.environment-path", _workDir + File.separator
                + port);
        _configXml.addProperty(vhostPrefix + ".store.highAvailability.groupName", _groupName);
        _configXml.addProperty(vhostPrefix + ".store.highAvailability.nodeName", nodeName);
        _configXml.addProperty(vhostPrefix + ".store.highAvailability.nodeHostPort",
                getNodeHostPortForNodeAt(port));
        _configXml.addProperty(vhostPrefix + ".store.highAvailability.helperHostPort",
                getHelperHostPort());

        _configXml.addProperty(vhostPrefix + ".store.envConfig(-1).name", EnvironmentConfig.CLEANER_THREADS);
        _configXml.addProperty(vhostPrefix + ".store.envConfig.value", TEST_NUMBER_OF_THREADS);

        _configXml.addProperty(vhostPrefix + ".store.envConfig(-1).name", EnvironmentConfig.LOG_FILE_MAX);
        _configXml.addProperty(vhostPrefix + ".store.envConfig.value", TEST_LOG_FILE_MAX);

        _configXml.addProperty(vhostPrefix + ".store.repConfig(-1).name", ReplicationConfig.ELECTIONS_PRIMARY_RETRIES);
        _configXml.addProperty(vhostPrefix + ".store.repConfig.value", TEST_ELECTION_RETRIES);

        _configXml.addProperty(vhostPrefix + ".store.repConfig(-1).name", ReplicationConfig.ENV_CONSISTENCY_TIMEOUT);
        _configXml.addProperty(vhostPrefix + ".store.repConfig.value", TEST_ENV_CONSISTENCY_TIMEOUT);
    }

    private String getNodeNameForNodeAt(final int bdbPort)
    {
        return "node" + getName() + bdbPort;
    }

    private String getNodeHostPortForNodeAt(final int bdbPort)
    {
        return _host + ":" + bdbPort;
    }

    private String getHelperHostPort()
    {
        if (_masterPort == -1)
        {
            throw new IllegalStateException("Helper port not yet assigned.");
        }
        return _host + ":" + _masterPort;
    }

    private TestApplicationRegistry initialize() throws Exception
    {
        CurrentActor.set(new TestLogActor(new SystemOutMessageLogger()));
        ServerConfiguration configuration = new ServerConfiguration(_configXml);
        TestApplicationRegistry registry = new TestApplicationRegistry(configuration);
        ApplicationRegistry.initialise(registry);
        registry.getVirtualHostRegistry().setDefaultVirtualHostName("test" + _masterPort);
        return registry;
    }
}
