package org.apache.qpid.server.configuration.store;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.configuration.ConfigurationEntry;
import org.apache.qpid.server.configuration.ConfigurationEntryStore;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.util.FileUtils;

public class JsonConfigurationEntryStore extends MemoryConfigurationEntryStore
{
    public static final String STORE_TYPE = "json";

    private File _storeFile;

    public JsonConfigurationEntryStore(String storeLocation, ConfigurationEntryStore initialStore)
    {
        super();
        _storeFile = new File(storeLocation);
        if ((!_storeFile.exists() || _storeFile.length() == 0))
        {
           initialiseStore(_storeFile, initialStore);
        }
        load(fileToURL(_storeFile));
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
                copyInitialStoreFile(initialStore.getStoreLocation(), storeFile);
            }
            else
            {
                ConfigurationEntry rootEntry = initialStore.getRootEntry();
                Map<UUID, ConfigurationEntry> entries = new HashMap<UUID, ConfigurationEntry>();
                copyEntry(rootEntry.getId(), initialStore, entries);
                saveAsTree(rootEntry.getId(), entries, getObjectMapper(), storeFile);
            }
        }
    }

    private void copyInitialStoreFile(String initialStoreLocation, File storeFile)
    {
        URL initialStoreURL = toURL(initialStoreLocation);
        InputStream in =  null;
        try
        {
            in = initialStoreURL.openStream();
            FileUtils.copy(in, storeFile);
        }
        catch (IOException e)
        {
            throw new IllegalConfigurationException("Cannot create store file " + storeFile + " by copying initial store from " + initialStoreLocation , e);
        }
        finally
        {
            if (in != null)
            {
                try
                {
                    in.close();
                }
                catch (IOException e)
                {
                    throw new IllegalConfigurationException("Cannot close initial store input stream: " + initialStoreLocation , e);
                }
            }
        }
    }

}
