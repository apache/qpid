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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.apache.qpid.server.configuration.BrokerProperties;
import org.apache.qpid.server.store.StoreException;
import org.apache.qpid.server.util.BaseAction;
import org.apache.qpid.server.util.FileHelper;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;
import org.codehaus.jackson.type.TypeReference;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.model.AbstractConfiguredObject;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.ManagedAttributeField;
import org.apache.qpid.server.model.ManagedObjectFactoryConstructor;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.StateTransition;


public class FileSystemPreferencesProviderImpl
        extends AbstractConfiguredObject<FileSystemPreferencesProviderImpl> implements FileSystemPreferencesProvider<FileSystemPreferencesProviderImpl>
{
    private static final Logger LOGGER = Logger.getLogger(FileSystemPreferencesProviderImpl.class);

    private final AuthenticationProvider<? extends AuthenticationProvider> _authenticationProvider;

    private FileSystemPreferencesStore _store;

    @ManagedAttributeField( afterSet = "openNewStore" )
    private String _path;

    private boolean _open;

    @ManagedObjectFactoryConstructor
    public FileSystemPreferencesProviderImpl(Map<String, Object> attributes,
                                             AuthenticationProvider<? extends AuthenticationProvider> authenticationProvider)
    {
        super(parentsMap(authenticationProvider), attributes);
        _authenticationProvider = authenticationProvider;
    }

    @Override
    protected void validateOnCreate()
    {
        super.validateOnCreate();
        File storeFile  = new File(_path);
        if (storeFile.exists() )
        {
            if (!storeFile.canRead())
            {
                throw new IllegalConfigurationException(String.format("Cannot read preferences file '%s'. Please check permissions.", _path));
            }

            FileSystemPreferencesStore store = null;
            try
            {
                store = new FileSystemPreferencesStore(storeFile);
                store.open();
            }
            catch (RuntimeException e)
            {
                if (e instanceof IllegalConfigurationException)
                {
                    throw e;
                }
                throw new IllegalConfigurationException(String.format("Cannot open preferences store at '%s'", _path), e);
            }
            finally
            {
                if (store != null)
                {
                    store.close();
                }
            }
        }
    }

    @Override
    protected void onOpen()
    {
        FileSystemPreferencesStore store = new FileSystemPreferencesStore(new File(_path));

        // we need to check and create file if it does not exist every time on open
        store.createIfNotExist(getContextValue(String.class, BrokerProperties.POSIX_FILE_PERMISSIONS));
        store.open();
        _store = store;
        _open = true;
    }

    @StateTransition( currentState = {State.UNINITIALIZED, State.ERRORED}, desiredState = State.ACTIVE )
    private void activate()
    {
        if (_store != null)
        {
            setState(State.ACTIVE);
        }
        else
        {
            throw new IllegalStateException("Cannot open preferences provider " + getName() + " in state " + getState() );
        }
    }

    @Override
    public void onValidate()
    {
        super.onValidate();
        if(!isDurable())
        {
            throw new IllegalArgumentException(getClass().getSimpleName() + " must be durable");
        }
    }

    @Override
    public String getPath()
    {
        return _path;
    }

    @Override
    public <C extends ConfiguredObject> Collection<C> getChildren(Class<C> clazz)
    {
        return Collections.emptySet();
    }

    protected void onClose()
    {
        if(_store != null)
        {
            _store.close();
        }
    }

    @StateTransition(currentState = { State.ACTIVE }, desiredState = State.QUIESCED)
    private void doQuiesce()
    {
        if(_store != null)
        {
            _store.close();
        }
        setState(State.QUIESCED);
    }

    @StateTransition(currentState = { State.ACTIVE, State.QUIESCED, State.ERRORED }, desiredState = State.DELETED )
    private void doDelete()
    {
        close();

        if(_store != null)
        {
            _store.close();
            _store.delete();
            deleted();
            _authenticationProvider.setPreferencesProvider(null);

        }
        setState(State.DELETED);
    }

    @StateTransition(currentState = State.QUIESCED, desiredState = State.ACTIVE )
    private void restart()
    {
        if (_store == null)
        {
            throw new IllegalStateException("Cannot open preferences provider " + getName() + " in state " + getState() );
        }

        _store.open();
        setState(State.ACTIVE);
    }

    @Override
    public Map<String, Object> getPreferences(String userId)
    {
        return _store == null? Collections.<String, Object>emptyMap() : _store.getPreferences(userId);
    }

    @Override
    public Map<String, Object> setPreferences(String userId, Map<String, Object> preferences)
    {
        if (_store == null)
        {
            throw new IllegalStateException("Cannot set preferences with preferences provider " + getName() + " in state " + getState() );
        }

        return _store.setPreferences(userId, preferences);
    }

    @Override
    public String[] deletePreferences(String... userIDs)
    {
        if (_store == null)
        {
            throw new IllegalStateException("Cannot delete preferences with preferences provider " + getName() + " in state " + getState() );
        }

        return _store.deletePreferences(userIDs);
    }

    @Override
    public Set<String> listUserIDs()
    {
        if (_store == null)
        {
            return Collections.emptySet();
        }

        return _store.listUserIDs();
    }

    public AuthenticationProvider<? extends AuthenticationProvider> getAuthenticationProvider()
    {
        return _authenticationProvider;
    }



    @Override
    protected void changeAttributes(Map<String, Object> attributes)
    {

        super.changeAttributes(attributes);

        // if provider was previously in ERRORED state then set its state to ACTIVE
        if(getState() == State.ERRORED)
        {
            onOpen();
        }
    }

    /* Note this method is used: it is referenced by the annotation on _path to be called after _path is set */
    private void openNewStore()
    {
        if(_open)
        {
            if (_store != null)
            {
                _store.close();
            }

            if (_path == null)
            {
                _store = null;
            }
            else
            {
                FileSystemPreferencesStore store = new FileSystemPreferencesStore(new File(_path));
                store.createIfNotExist(getContextValue(String.class, BrokerProperties.POSIX_FILE_PERMISSIONS));
                store.open();
                _store = store;
            }
        }
    }

    @Override
    protected void validateChange(final ConfiguredObject<?> updatedObject, final Set<String> changedAttributes)
    {
        super.validateChange(updatedObject, changedAttributes);
        FileSystemPreferencesProvider<?> updated = (FileSystemPreferencesProvider<?>) updatedObject;

        if (changedAttributes.contains(NAME) && !getName().equals(updated.getName()))
        {
            throw new IllegalConfigurationException("Changing the name of preferences provider is not supported");
        }

        if (changedAttributes.contains(TYPE) && getType() != null && !getType().equals(updated.getType()))
        {
            throw new IllegalConfigurationException("Changing the type of preferences provider is not supported");
        }

        if (changedAttributes.contains(PATH))
        {
            if(updated.getPath() == null || updated.getPath().equals(""))
            {
                throw new IllegalConfigurationException("Path to preferences file is not specified");
            }
            else if(!updated.getPath().equals(getPath()))
            {
                File storeFile = new File(updated.getPath());

                if (!storeFile.exists())
                {
                    throw new IllegalConfigurationException("Path to preferences file does not exist!");
                }

            }
        }

        if(changedAttributes.contains(DURABLE) && !updated.isDurable())
        {
            throw new IllegalArgumentException(getClass().getSimpleName() + " must be durable");
        }



    }

    public static class FileSystemPreferencesStore
    {
        private final ObjectMapper _objectMapper;
        private final Map<String, Map<String, Object>> _preferences;
        private final FileHelper _fileHelper;
        private File _storeFile;
        private FileLock _storeLock;

        public FileSystemPreferencesStore(File preferencesFile)
        {
            _storeFile = preferencesFile;
            _objectMapper = new ObjectMapper();
            _objectMapper.configure(SerializationConfig.Feature.INDENT_OUTPUT, true);
            _objectMapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
            _preferences = new TreeMap<String, Map<String, Object>>();
            _fileHelper = new FileHelper();
        }

        public void createIfNotExist(String filePermissions)
        {
            if (!_storeFile.exists())
            {
                File parent = _storeFile.getParentFile();
                if (!parent.exists() && !parent.mkdirs())
                {
                    throw new IllegalConfigurationException(String.format("Cannot create preferences store folder at '%s'", _storeFile.getAbsolutePath()));
                }
                try
                {
                    Path path = _fileHelper.createNewFile(_storeFile, filePermissions);
                    if (!Files.exists(path))
                    {
                        throw new IllegalConfigurationException(String.format("Cannot create preferences store file at '%s'", _storeFile.getAbsolutePath()));
                    }
                }
                catch (IOException e)
                {
                    throw new IllegalConfigurationException(String.format("Cannot create preferences store file at '%s'", _storeFile.getAbsolutePath()), e);
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
                getFileLock(_storeFile.getPath() + ".lck");
                if (_storeFile.length() > 0)
                {
                    Map<String, Map<String, Object>> preferencesMap = _objectMapper.readValue(_storeFile,
                            new TypeReference<Map<String, Map<String, Object>>>()
                            {
                            });
                    _preferences.putAll(preferencesMap);
                }
            }
            catch (JsonProcessingException e)
            {
                throw new IllegalConfigurationException("Cannot parse preferences json in " + _storeFile.getName(), e);
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
                        _storeLock.channel().close();
                    }
                }
                catch (IOException e)
                {
                    LOGGER.error("Cannot release file lock for preferences file store", e);
                }
                finally
                {
                    _storeLock = null;
                    _preferences.clear();
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
                _fileHelper.writeFileSafely(_storeFile.toPath(), new BaseAction<File, IOException>()
                {
                    @Override
                    public void performAction(File file) throws IOException
                    {
                        _objectMapper.writeValue(file, _preferences);
                    }
                });
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

        private void getFileLock(String lockFilePath)
        {
            File lockFile = new File(lockFilePath);
            try
            {
                lockFile.createNewFile();
                lockFile.deleteOnExit();

                @SuppressWarnings("resource")
                FileOutputStream out = new FileOutputStream(lockFile);
                FileChannel channel = out.getChannel();
                _storeLock = channel.tryLock();
            }
            catch (IOException ioe)
            {
                throw new IllegalStateException("Cannot create the lock file " + lockFile.getName(), ioe);
            }
            catch(OverlappingFileLockException e)
            {
                _storeLock = null;
            }

            if(_storeLock == null)
            {
                throw new IllegalStateException("Cannot get lock on file " + lockFile.getAbsolutePath() + ". Is another instance running?");
            }
        }
    }
}
