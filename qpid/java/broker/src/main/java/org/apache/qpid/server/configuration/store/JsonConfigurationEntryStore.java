package org.apache.qpid.server.configuration.store;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.configuration.ConfigurationEntry;
import org.apache.qpid.server.configuration.ConfigurationEntryStore;
import org.apache.qpid.server.configuration.IllegalConfigurationException;

public class JsonConfigurationEntryStore extends MemoryConfigurationEntryStore
{
    public static final String STORE_TYPE = "json";

    private File _storeFile;

    public JsonConfigurationEntryStore(String storeLocation, ConfigurationEntryStore initialStore, boolean overwrite, Map<String, String> configProperties)
    {
        super(configProperties);
        _storeFile = new File(storeLocation);

        if(_storeFile.isDirectory())
        {
            throw new IllegalConfigurationException("A directory exists at the location for the broker configuration store file: " + storeLocation);
        }

        if(overwrite && _storeFile.exists())
        {
            if(!_storeFile.delete())
            {
                throw new RuntimeException("Unable to overwrite existing configuration store file as requested: " + storeLocation);
            }
        }

        if ((!_storeFile.exists() || _storeFile.length() == 0))
        {
           initialiseStore(_storeFile, initialStore);
        }
        load(getConfigurationEntryStoreUtil().fileToURL(_storeFile));
        if(isGeneratedObjectIdDuringLoad())
        {
            saveAsTree(_storeFile);
        }
    }

    @Override
    public synchronized UUID[] remove(UUID... entryIds)
    {
        UUID[] removedIds = super.remove(entryIds);
        if (removedIds.length > 0)
        {
            saveAsTree(_storeFile);
        }
        return removedIds;
    }

    @Override
    public synchronized void save(ConfigurationEntry... entries)
    {
        if (replaceEntries(entries))
        {
            saveAsTree(_storeFile);
        }
    }

    @Override
    public String getStoreLocation()
    {
        return _storeFile.getAbsolutePath();
    }

    @Override
    public String getType()
    {
        return STORE_TYPE;
    }

    @Override
    public String toString()
    {
        return "JsonConfigurationEntryStore [_storeFile=" + _storeFile + ", _rootId=" + getRootEntry().getId() + "]";
    }

    private void initialiseStore(File storeFile, ConfigurationEntryStore initialStore)
    {
        createFileIfNotExist(storeFile);
        if (initialStore == null)
        {
           throw new IllegalConfigurationException("Cannot create new store without an initial store");
        }
        else
        {
            if (initialStore instanceof MemoryConfigurationEntryStore && initialStore.getStoreLocation() != null)
            {
                getConfigurationEntryStoreUtil().copyInitialConfigFile(initialStore.getStoreLocation(), storeFile);
            }
            else
            {
                ConfigurationEntry rootEntry = initialStore.getRootEntry();
                Map<UUID, ConfigurationEntry> entries = new HashMap<UUID, ConfigurationEntry>();
                copyEntry(rootEntry.getId(), initialStore, entries);
                saveAsTree(rootEntry.getId(), entries, getObjectMapper(), storeFile, getVersion());
            }
        }
    }

}
