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
package org.apache.qpid.server.store;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Model;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.util.ServerScopedRuntimeException;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;

public class JsonFileConfigStore implements DurableConfigurationStore
{
    private static final Model MODEL = Model.getInstance();

    private static final Map<String,Class<? extends ConfiguredObject>> CLASS_NAME_MAPPING = generateClassNameMap(VirtualHost.class);
    public static final String TYPE = "JSON";

    private final Map<UUID, ConfiguredObjectRecord> _objectsById = new HashMap<UUID, ConfiguredObjectRecord>();
    private final Map<String, List<UUID>> _idsByType = new HashMap<String, List<UUID>>();
    private final ObjectMapper _objectMapper = new ObjectMapper();

    private String _directoryName;
    private String _name;
    private FileLock _fileLock;
    private String _configFileName;
    private String _backupFileName;
    private int _configVersion;

    public JsonFileConfigStore()
    {
        _objectMapper.enable(SerializationConfig.Feature.INDENT_OUTPUT);
    }

    @Override
    public void configureConfigStore(final VirtualHost virtualHost, final ConfigurationRecoveryHandler recoveryHandler)
    {
        _name = virtualHost.getName();

        Object storePathAttr = virtualHost.getAttribute(VirtualHost.CONFIG_STORE_PATH);
        if(!(storePathAttr instanceof String))
        {
            throw new ServerScopedRuntimeException("Cannot determine path for configuration storage");
        }
        _directoryName = (String) storePathAttr;
        _configFileName = _name + ".json";
        _backupFileName = _name + ".bak";
        checkDirectoryIsWritable(_directoryName);
        getFileLock();

        if(!fileExists(_configFileName))
        {
            if(!fileExists(_backupFileName))
            {
                File newFile = new File(_directoryName, _configFileName);
                try
                {
                    _objectMapper.writeValue(newFile, Collections.emptyMap());
                }
                catch (IOException e)
                {
                    throw new ServerScopedRuntimeException("Could not write configuration file " + newFile, e);
                }
            }
            else
            {
                renameFile(_backupFileName, _configFileName);
            }
        }


        load();
        recoveryHandler.beginConfigurationRecovery(this,_configVersion);
        List<ConfiguredObjectRecord> records = new ArrayList<ConfiguredObjectRecord>(_objectsById.values());
        for(ConfiguredObjectRecord record : records)
        {
            recoveryHandler.configuredObject(record.getId(), record.getType(), record.getAttributes());
        }
        int oldConfigVersion = _configVersion;
        _configVersion = recoveryHandler.completeConfigurationRecovery();
        if(oldConfigVersion != _configVersion)
        {
            save();
        }
    }

    private void renameFile(String fromFileName, String toFileName)
    {
        File toFile = new File(_directoryName, toFileName);
        if(toFile.exists())
        {
            if(!toFile.delete())
            {
                throw new ServerScopedRuntimeException("Cannot delete file " + toFile.getAbsolutePath());
            }
        }
        File fromFile = new File(_directoryName, fromFileName);

        if(!fromFile.renameTo(toFile))
        {
            throw new ServerScopedRuntimeException("Cannot rename file " + fromFile.getAbsolutePath() + " to " + toFile.getAbsolutePath());
        }
    }

    private boolean fileExists(String fileName)
    {
        File file = new File(_directoryName, fileName);
        return file.exists();
    }

    private void getFileLock()
    {
        File lockFile = new File(_directoryName, _name + ".lck");
        try
        {
            lockFile.createNewFile();
            lockFile.deleteOnExit();

            @SuppressWarnings("resource")
            FileOutputStream out = new FileOutputStream(lockFile);
            FileChannel channel = out.getChannel();
            _fileLock = channel.tryLock();
        }
        catch (IOException ioe)
        {
            throw new ServerScopedRuntimeException("Cannot create the lock file " + lockFile.getName(), ioe);
        }
        catch(OverlappingFileLockException e)
        {
            _fileLock = null;
        }

        if(_fileLock == null)
        {
            throw new ServerScopedRuntimeException("Cannot get lock on file " + lockFile.getAbsolutePath() + ". Is another instance running?");
        }
    }

    private void checkDirectoryIsWritable(String directoryName)
    {
        File dir = new File(directoryName);
        if(dir.exists())
        {
            if(dir.isDirectory())
            {
                if(!dir.canWrite())
                {
                    throw new ServerScopedRuntimeException("Configuration path " + directoryName + " exists, but is not writable");
                }

            }
            else
            {
                throw new ServerScopedRuntimeException("Configuration path " + directoryName + " exists, but is not a directory");
            }
        }
        else if(!dir.mkdirs())
        {
            throw new ServerScopedRuntimeException("Cannot create directory " + directoryName);
        }
    }

    private void load()
    {
        final File configFile = new File(_directoryName, _configFileName);
        try
        {
            Map data = _objectMapper.readValue(configFile,Map.class);
            Collection<Class<? extends ConfiguredObject>> childClasses =
                    MODEL.getChildTypes(VirtualHost.class);
            data.remove("modelVersion");
            Object configVersion;
            if((configVersion = data.remove("configVersion")) instanceof Integer)
            {
                _configVersion = (Integer) configVersion;
            }
            for(Class<? extends ConfiguredObject> childClass : childClasses)
            {
                final String type = childClass.getSimpleName();
                String attrName = type.toLowerCase() + "s";
                Object children = data.remove(attrName);
                if(children != null)
                {
                    if(children instanceof Collection)
                    {
                        for(Object child : (Collection)children)
                        {
                            if(child instanceof Map)
                            {
                                loadChild(childClass, (Map)child, VirtualHost.class, null);
                            }
                        }
                    }
                }
            }
        }
        catch (JsonMappingException e)
        {
            throw new ServerScopedRuntimeException("Cannot parse the configuration file " + configFile, e);
        }
        catch (JsonParseException e)
        {
            throw new ServerScopedRuntimeException("Cannot parse the configuration file " + configFile, e);
        }
        catch (IOException e)
        {
            throw new ServerScopedRuntimeException("Could not load the configuration file " + configFile, e);
        }

    }

    private void loadChild(final Class<? extends ConfiguredObject> clazz,
                           final Map<String,Object> data,
                           final Class<? extends ConfiguredObject> parentClass,
                           final UUID parentId)
    {
        Collection<Class<? extends ConfiguredObject>> childClasses =
                MODEL.getChildTypes(clazz);
        String idStr = (String) data.remove("id");
        final UUID id = UUID.fromString(idStr);
        final String type = clazz.getSimpleName();

        for(Class<? extends ConfiguredObject> childClass : childClasses)
        {
            final String childType = childClass.getSimpleName();
            String attrName = childType.toLowerCase() + "s";
            Object children = data.remove(attrName);
            if(children != null)
            {
                if(children instanceof Collection)
                {
                    for(Object child : (Collection)children)
                    {
                        if(child instanceof Map)
                        {
                            loadChild(childClass, (Map)child, clazz, id);
                        }
                    }
                }
            }

        }
        if(parentId != null)
        {
            data.put(parentClass.getSimpleName().toLowerCase(),parentId);
            for(Class<? extends ConfiguredObject> otherParent : MODEL.getParentTypes(clazz))
            {
                if(otherParent != parentClass)
                {
                    final String otherParentAttr = otherParent.getSimpleName().toLowerCase();
                    Object otherParentId = data.get(otherParentAttr);
                    if(otherParentId instanceof String)
                    {
                        try
                        {
                            data.put(otherParentAttr, UUID.fromString((String) otherParentId));
                        }
                        catch(IllegalArgumentException e)
                        {
                            //
                        }
                    }
                }

            }
        }

        _objectsById.put(id, new ConfiguredObjectRecord(id, type, data));
        List<UUID> idsForType = _idsByType.get(type);
        if(idsForType == null)
        {
            idsForType = new ArrayList<UUID>();
            _idsByType.put(type, idsForType);
        }
        idsForType.add(id);

    }

    @Override
    public synchronized void create(final UUID id, final String type, final Map<String, Object> attributes) throws AMQStoreException
    {
        if(_objectsById.containsKey(id))
        {
            throw new AMQStoreException("Object with id " + id + " already exists");
        }
        else if(!CLASS_NAME_MAPPING.containsKey(type))
        {
            throw new AMQStoreException("Cannot create object of unknown type " + type);
        }
        else
        {
            ConfiguredObjectRecord record = new ConfiguredObjectRecord(id, type, attributes);
            _objectsById.put(id, record);
            List<UUID> idsForType = _idsByType.get(type);
            if(idsForType == null)
            {
                idsForType = new ArrayList<UUID>();
                _idsByType.put(type, idsForType);
            }
            idsForType.add(id);
            save();
        }
    }

    private void save()
    {
        Collection<Class<? extends ConfiguredObject>> childClasses =
                MODEL.getChildTypes(VirtualHost.class);

        Map<String, Object> virtualHostMap = new LinkedHashMap<String, Object>();
        virtualHostMap.put("modelVersion", Model.MODEL_VERSION);
        virtualHostMap.put("configVersion", _configVersion);

        for(Class<? extends ConfiguredObject> childClass : childClasses)
        {
            final String type = childClass.getSimpleName();
            String attrName = type.toLowerCase() + "s";
            List<UUID> childIds = _idsByType.get(type);
            if(childIds != null && !childIds.isEmpty())
            {
                List<Map<String,Object>> entities = new ArrayList<Map<String, Object>>();
                for(UUID id : childIds)
                {
                    entities.add(build(childClass,id));
                }
                virtualHostMap.put(attrName, entities);
            }
        }

        try
        {

            File tmpFile = File.createTempFile("cfg","tmp", new File(_directoryName));
            tmpFile.deleteOnExit();
            _objectMapper.writeValue(tmpFile,virtualHostMap);
            renameFile(_configFileName,_backupFileName);
            renameFile(tmpFile.getName(),_configFileName);
            tmpFile.delete();
            File backupFile = new File(_directoryName, _backupFileName);
            backupFile.delete();

        }
        catch (IOException e)
        {
            throw new ServerScopedRuntimeException("Cannot save to store", e);
        }
    }

    private Map<String, Object> build(final Class<? extends ConfiguredObject> type, final UUID id)
    {
        ConfiguredObjectRecord record = _objectsById.get(id);
        Map<String,Object> map = new LinkedHashMap<String, Object>();
        map.put("id", id);
        map.putAll(record.getAttributes());
        map.remove(MODEL.getParentTypes(type).iterator().next().getSimpleName().toLowerCase());

        Collection<Class<? extends ConfiguredObject>> childClasses =
                new ArrayList<Class<? extends ConfiguredObject>>(MODEL.getChildTypes(type));

        for(Class<? extends ConfiguredObject> childClass : childClasses)
        {
            // only add if this is the "first" parent
            if(MODEL.getParentTypes(childClass).iterator().next() == type)
            {
                String attrName = childClass.getSimpleName().toLowerCase() + "s";
                List<UUID> childIds = _idsByType.get(childClass.getSimpleName());
                if(childIds != null)
                {
                    List<Map<String,Object>> entities = new ArrayList<Map<String, Object>>();
                    for(UUID childId : childIds)
                    {
                        ConfiguredObjectRecord childRecord = _objectsById.get(childId);
                        final String parentArg = type.getSimpleName().toLowerCase();
                        if(id.toString().equals(String.valueOf(childRecord.getAttributes().get(parentArg))))
                        {
                            entities.add(build(childClass,childId));
                        }
                    }
                    if(!entities.isEmpty())
                    {
                        map.put(attrName,entities);
                    }
                }
            }
        }

        return map;
    }

    @Override
    public void remove(final UUID id, final String type) throws AMQStoreException
    {
        removeConfiguredObjects(id);
    }

    @Override
    public synchronized UUID[] removeConfiguredObjects(final UUID... objects) throws AMQStoreException
    {
        List<UUID> removedIds = new ArrayList<UUID>();
        for(UUID id : objects)
        {
            ConfiguredObjectRecord record = _objectsById.remove(id);
            if(record != null)
            {
                removedIds.add(id);
                _idsByType.get(record.getType()).remove(id);
            }
        }
        save();
        return removedIds.toArray(new UUID[removedIds.size()]);
    }

    @Override
    public void update(final UUID id, final String type, final Map<String, Object> attributes) throws AMQStoreException
    {
        update(false, new ConfiguredObjectRecord(id, type, attributes));
    }

    @Override
    public void update(final ConfiguredObjectRecord... records) throws AMQStoreException
    {
        update(false, records);
    }

    @Override
    public void update(final boolean createIfNecessary, final ConfiguredObjectRecord... records)
            throws AMQStoreException
    {
        for(ConfiguredObjectRecord record : records)
        {
            final UUID id = record.getId();
            final String type = record.getType();

            if(_objectsById.containsKey(id))
            {
                final ConfiguredObjectRecord existingRecord = _objectsById.get(id);
                if(!type.equals(existingRecord.getType()))
                {
                    throw new AMQStoreException("Cannot change the type of record " + id + " from type "
                                                + existingRecord.getType() + " to type " + type);
                }
            }
            else if(!createIfNecessary)
            {
                throw new AMQStoreException("Cannot update record with id " + id
                                        + " of type " + type + " as it does not exist");
            }
            else if(!CLASS_NAME_MAPPING.containsKey(type))
            {
                throw new AMQStoreException("Cannot update record of unknown type " + type);
            }
        }
        for(ConfiguredObjectRecord record : records)
        {
            final UUID id = record.getId();
            final String type = record.getType();
            if(_objectsById.put(id, record) == null)
            {
                List<UUID> idsForType = _idsByType.get(type);
                if(idsForType == null)
                {
                    idsForType = new ArrayList<UUID>();
                    _idsByType.put(type, idsForType);
                }
                idsForType.add(id);
            }
        }

        save();
    }

    public void close() throws Exception
    {
        try
        {
            releaseFileLock();
        }
        finally
        {
            _fileLock = null;
            _idsByType.clear();
            _objectsById.clear();
        }

    }

    private void releaseFileLock() throws IOException
    {
        _fileLock.release();
        _fileLock.channel().close();
    }


    private static Map<String,Class<? extends ConfiguredObject>> generateClassNameMap(final Class<? extends ConfiguredObject> clazz)
    {
        Map<String,Class<? extends ConfiguredObject>>map = new HashMap<String, Class<? extends ConfiguredObject>>();
        map.put(clazz.getSimpleName().toString(), clazz);
        Collection<Class<? extends ConfiguredObject>> childClasses = MODEL.getChildTypes(clazz);
        if(childClasses != null)
        {
            for(Class<? extends ConfiguredObject> childClass : childClasses)
            {
                map.putAll(generateClassNameMap(childClass));
            }
        }
        return map;
    }


}
