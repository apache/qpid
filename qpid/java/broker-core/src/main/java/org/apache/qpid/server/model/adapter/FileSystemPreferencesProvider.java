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

package org.apache.qpid.server.model.adapter;

import java.io.ByteArrayOutputStream;
import java.io.File;
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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.log4j.Logger;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.model.*;
import org.apache.qpid.server.util.MapValueConverter;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;
import org.codehaus.jackson.type.TypeReference;

public class FileSystemPreferencesProvider extends AbstractConfiguredObject<FileSystemPreferencesProvider> implements PreferencesProvider<FileSystemPreferencesProvider>
{
    private static final Logger LOGGER = Logger.getLogger(FileSystemPreferencesProvider.class);
    public static String PATH = "path";
    public static final String PROVIDER_TYPE = "FileSystemPreferences";

    @SuppressWarnings("serial")
    private static final Map<String, Object> DEFAULTS = Collections.unmodifiableMap(new HashMap<String, Object>()
    {{
            put(TYPE, FileSystemPreferencesProvider.class.getSimpleName());
    }});

    @SuppressWarnings("serial")
    private static final Map<String, Type> ATTRIBUTE_TYPES = Collections.unmodifiableMap(new HashMap<String, Type>()
    {{
            put(NAME, String.class);
            put(PATH, String.class);
            put(TYPE, String.class);
    }});

    private final AuthenticationProvider<? extends AuthenticationProvider> _authenticationProvider;
    private AtomicReference<State> _state;

    private FileSystemPreferencesStore _store;

    protected FileSystemPreferencesProvider(UUID id, Map<String, Object> attributes,
                                            AuthenticationProvider<? extends AuthenticationProvider> authenticationProvider,
                                            TaskExecutor taskExecutor)
    {
        super(id, DEFAULTS, MapValueConverter.convert(attributes, ATTRIBUTE_TYPES), taskExecutor);
        State state = MapValueConverter.getEnumAttribute(State.class, STATE, attributes, State.INITIALISING);
        _state = new AtomicReference<State>(state);
        addParent(AuthenticationProvider.class, authenticationProvider);
        _authenticationProvider = authenticationProvider;
        _store = new FileSystemPreferencesStore(new File(MapValueConverter.getStringAttribute(PATH, attributes)));
    }

    @Override
    public Collection<String> getAttributeNames()
    {
        return Attribute.getAttributeNames(FileSystemPreferencesProvider.class);
    }

    @Override
    public String getName()
    {
        return (String) getAttribute(AuthenticationProvider.NAME);
    }

    @ManagedAttribute
    public String getPath()
    {
        return (String) getAttribute(PATH);
    }

    @Override
    public String setName(String currentName, String desiredName) throws IllegalStateException, AccessControlException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public State getState()
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
            return getState();
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
    public boolean setState(State currentState, State desiredState) throws IllegalStateTransitionException, AccessControlException
    {
        State state = _state.get();
        if (desiredState == State.DELETED)
        {
            if ((state == State.INITIALISING || state == State.ACTIVE || state == State.STOPPED || state == State.QUIESCED || state == State.ERRORED)
                    && _state.compareAndSet(state, State.DELETED))
            {
                try
                {
                    _store.close();
                }
                finally
                {
                    _store.delete();
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
                    _store.open();
                    return true;
                }
                catch (RuntimeException e)
                {
                    _state.compareAndSet(State.ACTIVE, State.ERRORED);
                    Broker<?> broker = getAuthenticationProvider().getParent(Broker.class);
                    if (broker != null && broker.isManagementMode())
                    {
                        LOGGER.warn("Failed to activate preferences provider: " + getName(), e);
                    }
                    else
                    {
                        throw e;
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
                _store.close();
                return true;
            }
        }
        else if (desiredState == State.STOPPED)
        {
            if (_state.compareAndSet(state, State.STOPPED))
            {
                _store.close();
                return true;
            }
            else
            {
                throw new IllegalStateException("Cannot stop preferences preferences in state: " + state);
            }
        }

        return false;
    }

    @Override
    public Map<String, Object> getPreferences(String userId)
    {
        return _store.getPreferences(userId);
    }

    @Override
    public Map<String, Object> setPreferences(String userId, Map<String, Object> preferences)
    {
        return _store.setPreferences(userId, preferences);
    }

    @Override
    public String[] deletePreferences(String... userIDs)
    {
        return _store.deletePreferences(userIDs);
    }

    @Override
    public Set<String> listUserIDs()
    {
        return _store.listUserIDs();
    }

    public AuthenticationProvider<? extends AuthenticationProvider> getAuthenticationProvider()
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

        File storeFile = new File(effectivePath);
        FileSystemPreferencesStore newStore = null;
        if (!effectivePath.equals(currentPath))
        {
            if (!storeFile.exists())
            {
                throw new IllegalConfigurationException("Path to preferences file does not exist!");
            }
            newStore = new FileSystemPreferencesStore(storeFile);
            newStore.open();
        }

        try
        {
            super.changeAttributes(attributes);

            if (newStore != null)
            {
                _store.close();
                _store = newStore;
                newStore = null;
            }
        }
        finally
        {
            if (newStore != null)
            {
                newStore.close();
            }
        }
        // if provider was previously in ERRORED state then set its state to ACTIVE
        _state.compareAndSet(State.ERRORED, State.ACTIVE);
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
        if (path == null || path.equals(""))
        {
            throw new IllegalConfigurationException("Path to preferences file is not specified");
        }
    }

    public void createStoreIfNotExist()
    {
        _store.createIfNotExist();
    }

    public static class FileSystemPreferencesStore
    {
        private final ObjectMapper _objectMapper;
        private final Map<String, Map<String, Object>> _preferences;
        private File _storeFile;
        private FileLock _storeLock;
        private RandomAccessFile _storeRAF;

        public FileSystemPreferencesStore(File preferencesFile)
        {
            _storeFile = preferencesFile;
            _objectMapper = new ObjectMapper();
            _objectMapper.configure(SerializationConfig.Feature.INDENT_OUTPUT, true);
            _objectMapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
            _preferences = new TreeMap<String, Map<String, Object>>();
        }

        public void createIfNotExist()
        {
            if (!_storeFile.exists())
            {
                File parent = _storeFile.getParentFile();
                if (!parent.exists() && !parent.mkdirs())
                {
                    throw new IllegalConfigurationException("Cannot create preferences store folders");
                }
                try
                {
                    if (_storeFile.createNewFile() && !_storeFile.exists())
                    {
                        throw new IllegalConfigurationException("Preferences store file was not created:" + _storeFile.getAbsolutePath());
                    }
                }
                catch (IOException e)
                {
                    throw new IllegalConfigurationException("Cannot create preferences store file");
                }
            }
        }

        public void delete()
        {
            if (_storeFile.exists() && !_storeFile.delete())
            {
                LOGGER.warn("Failed to delete preferences provider file '" + _storeFile.getName() + "'");
            }
        }

        public void open()
        {
            if (!_storeFile.exists())
            {
                throw new IllegalConfigurationException("Preferences file does not exist");
            }

            if (_storeLock != null)
            {
                throw new IllegalStateException("Preferences store is already opened");
            }
            try
            {
                _storeRAF = new RandomAccessFile(_storeFile, "rw");
                FileChannel fileChannel = _storeRAF.getChannel();
                try
                {
                    _storeLock = fileChannel.tryLock();
                }
                catch (OverlappingFileLockException e)
                {
                    _storeLock = null;
                }
                if (_storeLock == null)
                {
                    throw new IllegalConfigurationException("Cannot get lock on store file " + _storeFile.getName()
                            + " is another instance running?");
                }
                long fileSize = fileChannel.size();
                if (fileSize > 0)
                {
                    ByteBuffer buffer = ByteBuffer.allocate((int) fileSize);
                    fileChannel.read(buffer);
                    buffer.rewind();
                    buffer.flip();
                    byte[] data = buffer.array();
                    try
                    {
                        Map<String, Map<String, Object>> preferencesMap = _objectMapper.readValue(data,
                                new TypeReference<Map<String, Map<String, Object>>>()
                                {
                                });
                        _preferences.putAll(preferencesMap);
                    }
                    catch (JsonProcessingException e)
                    {
                        throw new IllegalConfigurationException("Cannot parse preferences json in " + _storeFile.getName(), e);
                    }
                }
            }
            catch (IOException e)
            {
                throw new IllegalConfigurationException("Cannot load preferences from " + _storeFile.getName(), e);
            }
        }

        public void close()
        {
            synchronized (_preferences)
            {
                try
                {
                    if (_storeLock != null)
                    {
                        _storeLock.release();
                    }
                }
                catch (IOException e)
                {
                    LOGGER.error("Cannot release file lock for preferences file store", e);
                }
                finally
                {
                    _storeLock = null;
                    try
                    {
                        if (_storeRAF != null)
                        {
                            _storeRAF.close();
                        }
                    }
                    catch (IOException e)
                    {
                        LOGGER.error("Cannot close preferences file", e);
                    }
                    finally
                    {
                        _storeRAF = null;
                        _preferences.clear();
                    }
                }
            }
        }

        public Map<String, Object> getPreferences(String userId)
        {
            checkStoreOpened();
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

        public Map<String, Object> setPreferences(String userId, Map<String, Object> preferences)
        {
            checkStoreOpened();
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
                save();
            }
            return userPreferences;
        }

        public String[] deletePreferences(String... userIDs)
        {
            checkStoreOpened();
            Set<String> deletedUsers = new HashSet<String>();
            synchronized (_preferences)
            {
                for (String id : userIDs)
                {
                    if (_preferences.containsKey(id))
                    {
                        _preferences.remove(id);
                        deletedUsers.add(id);
                    }
                }
                if (!deletedUsers.isEmpty())
                {
                    save();
                }
            }
            return deletedUsers.toArray(new String[deletedUsers.size()]);
        }

        public Set<String> listUserIDs()
        {
            checkStoreOpened();
            synchronized (_preferences)
            {
                return Collections.unmodifiableSet(_preferences.keySet());
            }
        }

        private void save()
        {
            checkStoreOpened();
            try
            {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                _objectMapper.writeValue(baos, _preferences);
                FileChannel channel = _storeRAF.getChannel();
                long currentSize = channel.size();
                channel.position(0);
                channel.write(ByteBuffer.wrap(baos.toByteArray()));
                if (currentSize > baos.size())
                {
                    channel.truncate(baos.size());
                }
            }
            catch (IOException e)
            {
                throw new IllegalConfigurationException("Cannot store preferences", e);
            }
        }

        private void checkStoreOpened()
        {
            if (_storeLock == null)
            {
                throw new IllegalStateException("Preferences store is not opened");
            }
        }

    }
}
