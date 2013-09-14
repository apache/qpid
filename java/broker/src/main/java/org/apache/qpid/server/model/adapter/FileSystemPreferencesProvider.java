package org.apache.qpid.server.model.adapter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.log4j.Logger;
import org.apache.qpid.AMQStoreException;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.IllegalStateTransitionException;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.PreferencesProvider;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.Statistics;
import org.apache.qpid.server.util.MapValueConverter;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;
import org.codehaus.jackson.type.TypeReference;

public class FileSystemPreferencesProvider extends AbstractAdapter implements PreferencesProvider
{
    private static final Logger LOGGER = Logger.getLogger(FileSystemPreferencesProvider.class);
    public static String PATH = "path";
    public static final String PROVIDER_TYPE = "FileSystemPreferences";

    // TODO: use resolver to resolve path from
    // '${qpid.work_dir}/preferences/${authenticationProviderName}'
    @SuppressWarnings("serial")
    private static final Map<String, Object> DEFAULTS = Collections.unmodifiableMap(new HashMap<String, Object>()
    {{
            put(PATH, System.getProperty("user.home") + File.separator + ".qpid" + File.separator + "preferences.json");
            put(TYPE, FileSystemPreferencesProvider.class.getSimpleName());
    }});

    @SuppressWarnings("serial")
    private static final Map<String, Type> ATTRIBUTE_TYPES = Collections.unmodifiableMap(new HashMap<String, Type>()
    {{
            put(NAME, String.class);
            put(PATH, String.class);
            put(TYPE, String.class);
    }});

    @SuppressWarnings("serial")
    private static Collection<String> AVAILABLE_ATTRIBUTES = Collections.unmodifiableList(new ArrayList<String>(
            PreferencesProvider.AVAILABLE_ATTRIBUTES)
    {{
            add(PATH);
    }});

    private final AuthenticationProvider _authenticationProvider;
    private AtomicReference<State> _state;

    private final ObjectMapper _objectMapper;
    private final Map<String, Map<String, Object>> _preferences;
    private File _preferencesLocation;
    private FileLock _fileLock;

    protected FileSystemPreferencesProvider(UUID id, Map<String, Object> attributes, AuthenticationProvider authenticationProvider, TaskExecutor taskExecutor)
    {
        super(id, DEFAULTS, MapValueConverter.convert(attributes, ATTRIBUTE_TYPES), taskExecutor);
        State state = MapValueConverter.getEnumAttribute(State.class, STATE, attributes, State.INITIALISING);
        _state = new AtomicReference<State>(state);
        addParent(AuthenticationProvider.class, authenticationProvider);
        _authenticationProvider = authenticationProvider;
        _objectMapper = new ObjectMapper();
        _objectMapper.configure(SerializationConfig.Feature.INDENT_OUTPUT, true);
        _objectMapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
        _preferences = new TreeMap<String, Map<String, Object>>();
        _preferencesLocation = new File(MapValueConverter.getStringAttribute(PATH, attributes));
        _preferences.putAll(load(_objectMapper, _preferencesLocation));
    }

    @Override
    public Collection<String> getAttributeNames()
    {
        return AVAILABLE_ATTRIBUTES;
    }

    @Override
    public String getName()
    {
        return (String) getAttribute(AuthenticationProvider.NAME);
    }

    @Override
    public String setName(String currentName, String desiredName) throws IllegalStateException, AccessControlException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public State getActualState()
    {
        return _state.get();
    }

    @Override
    public boolean isDurable()
    {
        return true;
    }

    @Override
    public void setDurable(boolean durable) throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public LifetimePolicy getLifetimePolicy()
    {
        return LifetimePolicy.PERMANENT;
    }

    @Override
    public LifetimePolicy setLifetimePolicy(LifetimePolicy expected, LifetimePolicy desired) throws IllegalStateException,
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
    public long setTimeToLive(long expected, long desired) throws IllegalStateException, AccessControlException,
            IllegalArgumentException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Statistics getStatistics()
    {
        return NoStatistics.getInstance();
    }

    @Override
    public <C extends ConfiguredObject> Collection<C> getChildren(Class<C> clazz)
    {
        return Collections.emptySet();
    }

    @Override
    public Object getAttribute(String name)
    {
        if (CREATED.equals(name))
        {
            // TODO
        }
        else if (DURABLE.equals(name))
        {
            return true;
        }
        else if (ID.equals(name))
        {
            return getId();
        }
        else if (LIFETIME_POLICY.equals(name))
        {
            return LifetimePolicy.PERMANENT;
        }
        else if (STATE.equals(name))
        {
            return getActualState();
        }
        else if (TIME_TO_LIVE.equals(name))
        {
            // TODO
        }
        else if (UPDATED.equals(name))
        {
            // TODO
        }
        return super.getAttribute(name);
    }

    @Override
    public boolean setState(State currentState, State desiredState) throws IllegalStateTransitionException,
            AccessControlException
    {
        State state = _state.get();
        if (desiredState == State.DELETED)
        {
            if ((state == State.INITIALISING || state == State.ACTIVE || state == State.STOPPED || state == State.QUIESCED || state == State.ERRORED)
                    && _state.compareAndSet(state, State.DELETED))
            {
                try
                {
                    close();
                }
                finally
                {
                    _preferencesLocation.delete();
                    _authenticationProvider.setPreferencesProvider(null);
                }
                return true;
            }
            else
            {
                throw new IllegalStateException("Cannot delete preferences provider in state: " + state);
            }
        }
        else if (desiredState == State.ACTIVE)
        {
            if ((state == State.INITIALISING || state == State.QUIESCED || state == State.STOPPED)
                    && _state.compareAndSet(state, State.ACTIVE))
            {
                try
                {
                    getFileLock();
                    Map<String, Map<String, Object>> preferences = load(_objectMapper, _preferencesLocation);
                    setPreferences(preferences);
                    return true;
                }
                catch (Exception e)
                {
                    _state.compareAndSet(State.ACTIVE, State.ERRORED);
                    Broker broker = getAuthenticationProvider().getParent(Broker.class);
                    if (broker != null && broker.isManagementMode())
                    {
                        LOGGER.warn("Failed to activate preferences provider: " + getName(), e);
                    }
                    else
                    {
                        throw new RuntimeException(e);
                    }
                }
            }
            else
            {
                throw new IllegalStateException("Cannot activate preferences provider in state: " + state);
            }
        }
        else if (desiredState == State.QUIESCED)
        {
            if (state == State.INITIALISING && _state.compareAndSet(state, State.QUIESCED))
            {
                close();
                return true;
            }
        }
        else if (desiredState == State.STOPPED)
        {
            if (_state.compareAndSet(state, State.STOPPED))
            {
                close();
                return true;
            }
            else
            {
                throw new IllegalStateException("Cannot stop authentication preferences in state: " + state);
            }
        }

        return false;
    }

    @Override
    public Map<String, Object> getPreferences(String userId)
    {
        Map<String, Object> userPreferences = null;
        synchronized (_preferences)
        {
            userPreferences = _preferences.get(userId);
        }
        if (userPreferences != null)
        {
            return new HashMap<String, Object>(userPreferences);
        }
        return Collections.emptyMap();
    }

    @Override
    public Map<String, Object> setPreferences(String userId, Map<String, Object> preferences)
    {
        Map<String, Object> userPreferences = null;
        synchronized (_preferences)
        {
            userPreferences = _preferences.get(userId);
            if (userPreferences == null)
            {
                userPreferences = new HashMap<String, Object>(preferences);
                _preferences.put(userId, userPreferences);
            }
            else
            {
                userPreferences.putAll(preferences);
            }
            savePreferences();
        }
        return userPreferences;
    }

    @Override
    public Map<String, Object> deletePreferences(String userId)
    {
        Map<String, Object> userPreferences = null;
        synchronized (_preferences)
        {
            if (_preferences.containsKey(userId))
            {
                userPreferences = _preferences.remove(userId);
                savePreferences();
            }
        }
        return userPreferences;
    }

    @Override
    public Set<String> listUserIDs()
    {
        synchronized (_preferences)
        {
            return Collections.unmodifiableSet(_preferences.keySet());
        }
    }

    public AuthenticationProvider getAuthenticationProvider()
    {
        return _authenticationProvider;
    }

    @Override
    protected void changeAttributes(Map<String, Object> attributes)
    {
        Map<String, Object> effectiveAttributes = MapValueConverter.convert(super.generateEffectiveAttributes(attributes),
                ATTRIBUTE_TYPES);
        validateAttributes(effectiveAttributes);
        String effectivePath = (String) effectiveAttributes.get(PATH);
        String currentPath = (String) getAttribute(PATH);
        Map<String, Map<String, Object>> newPreferences = null;
        File storeFile = new File(effectivePath);
        if (!effectivePath.equals(currentPath))
        {
            if (!storeFile.exists())
            {
                throw new IllegalConfigurationException("Path to preferences file does not exist!");
            }
            newPreferences = load(_objectMapper, storeFile);
        }
        super.changeAttributes(attributes);

        if (newPreferences != null)
        {
            setPreferences(newPreferences);
            _preferencesLocation = storeFile;
        }

        // if provider was previously in ERRORED state then set its state to
        // ACTIVE
        _state.compareAndSet(State.ERRORED, State.ACTIVE);
    }

    private void setPreferences(Map<String, Map<String, Object>> preferences)
    {
        synchronized (_preferences)
        {
            _preferences.clear();
            _preferences.putAll(preferences);
        }
    }

    private void validateAttributes(Map<String, Object> attributes)
    {
        super.validateChangeAttributes(attributes);

        String newName = (String) attributes.get(NAME);
        String currentName = getName();
        if (!currentName.equals(newName))
        {
            throw new IllegalConfigurationException("Changing the name of preferences provider is not supported");
        }
        String newType = (String) attributes.get(TYPE);
        String currentType = (String) getAttribute(TYPE);
        if (!currentType.equals(newType))
        {
            throw new IllegalConfigurationException("Changing the type of preferences provider is not supported");
        }
        String path = (String) attributes.get(PATH);
        if (path == null || path.equals("") || !(path instanceof String))
        {
            throw new IllegalConfigurationException("Path to preferences file is not specified");
        }
    }

    public File createStoreIfNotExist()
    {
        String path = (String)getAttribute(PATH);
        File preferencesLocation = new File(path);
        if (!preferencesLocation.exists())
        {
            File parent = preferencesLocation.getParentFile();
            if (!parent.exists() && !parent.mkdirs())
            {
                throw new IllegalConfigurationException("Cannot store preferences at " + path);
            }
            try
            {
                preferencesLocation.createNewFile();
            }
            catch (IOException e)
            {
                throw new IllegalConfigurationException("Cannot store preferences at " + path);
            }
        }
        return preferencesLocation;
    }

    private Map<String, Map<String, Object>> load(ObjectMapper mapper, File file)
    {
        if (!file.exists() || file.length() == 0)
        {
            return Collections.emptyMap();
        }

        try
        {
            return mapper.readValue(file, new TypeReference<Map<String, Map<String, Object>>>()
            {
            });
        }
        catch (JsonProcessingException e)
        {
            throw new IllegalConfigurationException("Cannot parse json", e);
        }
        catch (IOException e)
        {
            throw new IllegalConfigurationException("Cannot read json", e);
        }
    }

    private void savePreferences()
    {
        save(_objectMapper, _preferencesLocation, _preferences);
    }

    private void save(ObjectMapper mapper, File file, Map<String, Map<String, Object>> preferences)
    {
        try
        {
            RandomAccessFile raf = new RandomAccessFile(file, "rw");
            try
            {
                FileChannel channel = raf.getChannel();
                try
                {
                    FileLock lock = null;
                    try
                    {
                        lock = channel.tryLock();
                        if (lock == null)
                        {
                            throw new IllegalConfigurationException("Cannot aquire exclusive lock on preferences file for "
                                    + getName());
                        }
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        mapper.writeValue(baos, preferences);
                        channel.write(ByteBuffer.wrap(baos.toByteArray()));
                    }
                    catch (OverlappingFileLockException e)
                    {
                        throw new IllegalConfigurationException("Cannot aquire exclusive lock on preferences file for "
                                + getName(), e);
                    }
                    finally
                    {
                        if (lock != null)
                        {
                            lock.release();
                        }
                    }
                }
                finally
                {
                    channel.close();
                }
            }
            finally
            {
                raf.close();
            }
        }
        catch (FileNotFoundException e)
        {
            throw new IllegalConfigurationException("Cannot find preferences file for " + getName(), e);
        }
        catch (IOException e)
        {
            throw new IllegalConfigurationException("Cannot store preferences file for " + getName(), e);
        }
    }

    private void getFileLock() throws IOException, AMQStoreException
    {
        File lockFile = new File(_preferencesLocation.getAbsolutePath() + ".lck");
        lockFile.createNewFile();

        FileOutputStream out = new FileOutputStream(lockFile);
        FileChannel channel = out.getChannel();
        try
        {
            _fileLock = channel.tryLock();
        }
        catch(OverlappingFileLockException e)
        {
            _fileLock = null;
        }
        if(_fileLock == null)
        {
            throw new AMQStoreException("Cannot get lock on file " + lockFile.getAbsolutePath() + " is another instance running?");
        }
        lockFile.deleteOnExit();
    }

    public void close()
    {
        try
        {
            releaseFileLock();
        }
        catch(IOException e)
        {
            LOGGER.error("Cannot close file system preferences provider", e);
        }
        finally
        {
            new File(_preferencesLocation.getAbsolutePath() + ".lck").delete();
            _fileLock = null;
            _preferences.clear();
        }
    }

    private void releaseFileLock() throws IOException
    {
        _fileLock.release();
        _fileLock.channel().close();
    }
}
