package org.apache.qpid.server.configuration.startup;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.configuration.ConfigurationEntry;
import org.apache.qpid.server.configuration.ConfigurationEntryStore;
import org.apache.qpid.server.configuration.RecovererProvider;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.PreferencesProvider;
import org.apache.qpid.server.model.adapter.FileSystemPreferencesProvider;
import org.apache.qpid.server.model.adapter.PreferencesProviderCreator;
import org.apache.qpid.server.util.BrokerTestHelper;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.test.utils.TestFileUtils;

public class PreferencesProviderRecovererTest extends QpidTestCase
{
    private AuthenticationProvider _authenticationProvider;
    private Broker _broker;

    public void setUp() throws Exception
    {
        super.setUp();
        BrokerTestHelper.setUp();
        _authenticationProvider = mock(AuthenticationProvider.class);
        _broker = BrokerTestHelper.createBrokerMock();
        when(_authenticationProvider.getParent(Broker.class)).thenReturn(_broker);
    }

    public void tearDown() throws Exception
    {
        try
        {
            BrokerTestHelper.tearDown();
        }
        finally
        {
            super.tearDown();
        }
    }

    public void testRecoverFileSystemPreferencesProvider()
    {
        PreferencesProviderRecoverer recoverer = new PreferencesProviderRecoverer(new PreferencesProviderCreator());

        Map<String, Object> attributes = new HashMap<String, Object>();
        UUID id = UUID.randomUUID();
        attributes.put(PreferencesProvider.TYPE, FileSystemPreferencesProvider.class);
        attributes.put(PreferencesProvider.NAME, "test-provider");
        File file = TestFileUtils.createTempFile(this, ".prefs.json", "{\"test_user\":{\"pref1\": \"pref1Value\", \"pref2\": 1.0} }");
        try
        {
            attributes.put(FileSystemPreferencesProvider.PATH, file.getAbsolutePath());
            ConfigurationEntry entry = new ConfigurationEntry(id, PreferencesProvider.class.getSimpleName(), attributes, Collections.<UUID>emptySet(), mock(ConfigurationEntryStore.class));
            PreferencesProvider provider = recoverer.create(mock(RecovererProvider.class), entry, _authenticationProvider);
            assertNotNull("Preferences provider was not recovered", provider);
            assertEquals("Unexpected name", "test-provider", provider.getName());
            assertEquals("Unexpected id", id, provider.getId());
            assertEquals("Unexpected path", file.getAbsolutePath(), provider.getAttribute(FileSystemPreferencesProvider.PATH));
        }
        finally
        {
            file.delete();
        }
    }

}
