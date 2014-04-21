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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.log4j.Logger;
import org.apache.qpid.server.message.EnqueueableMessage;
import org.apache.qpid.server.model.BrokerModel;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.plugin.MessageMetaDataType;
import org.apache.qpid.server.store.handler.ConfiguredObjectRecordHandler;
import org.apache.qpid.server.store.handler.DistributedTransactionHandler;
import org.apache.qpid.server.store.handler.MessageHandler;
import org.apache.qpid.server.store.handler.MessageInstanceHandler;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.Version;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.JsonSerializer;
import org.codehaus.jackson.map.Module;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializerProvider;
import org.codehaus.jackson.map.module.SimpleModule;

abstract public class AbstractJDBCMessageStore implements MessageStore, DurableConfigurationStore
{
    private static final String DB_VERSION_TABLE_NAME = "QPID_DB_VERSION";
    private static final String CONFIGURATION_VERSION_TABLE_NAME = "QPID_CONFIG_VERSION";

    private static final String QUEUE_ENTRY_TABLE_NAME = "QPID_QUEUE_ENTRIES";

    private static final String META_DATA_TABLE_NAME = "QPID_MESSAGE_METADATA";
    private static final String MESSAGE_CONTENT_TABLE_NAME = "QPID_MESSAGE_CONTENT";


    private static final String XID_TABLE_NAME = "QPID_XIDS";
    private static final String XID_ACTIONS_TABLE_NAME = "QPID_XID_ACTIONS";

    private static final String CONFIGURED_OBJECTS_TABLE_NAME = "QPID_CONFIGURED_OBJECTS";
    private static final String CONFIGURED_OBJECT_HIERARCHY_TABLE_NAME = "QPID_CONFIGURED_OBJECT_HIERARCHY";

    private static final int DEFAULT_CONFIG_VERSION = 0;

    public static final Set<String> CONFIGURATION_STORE_TABLE_NAMES = new HashSet<String>(Arrays.asList(CONFIGURED_OBJECTS_TABLE_NAME, CONFIGURED_OBJECT_HIERARCHY_TABLE_NAME));
    public static final Set<String> MESSAGE_STORE_TABLE_NAMES = new HashSet<String>(Arrays.asList(DB_VERSION_TABLE_NAME,
                                                                                                  META_DATA_TABLE_NAME, MESSAGE_CONTENT_TABLE_NAME,
                                                                                                    QUEUE_ENTRY_TABLE_NAME,
                                                                                                    XID_TABLE_NAME, XID_ACTIONS_TABLE_NAME));

    private static final int DB_VERSION = 8;

    private final AtomicLong _messageId = new AtomicLong(0);

    private static final String CREATE_DB_VERSION_TABLE = "CREATE TABLE "+ DB_VERSION_TABLE_NAME + " ( version int not null )";
    private static final String INSERT_INTO_DB_VERSION = "INSERT INTO "+ DB_VERSION_TABLE_NAME + " ( version ) VALUES ( ? )";
    private static final String SELECT_FROM_DB_VERSION = "SELECT version FROM " + DB_VERSION_TABLE_NAME;
    private static final String UPDATE_DB_VERSION = "UPDATE " + DB_VERSION_TABLE_NAME + " SET version = ?";


    private static final String SELECT_FROM_CONFIG_VERSION = "SELECT version FROM " + CONFIGURATION_VERSION_TABLE_NAME;
    private static final String DROP_CONFIG_VERSION_TABLE = "DROP TABLE "+ CONFIGURATION_VERSION_TABLE_NAME;


    private static final String INSERT_INTO_QUEUE_ENTRY = "INSERT INTO " + QUEUE_ENTRY_TABLE_NAME + " (queue_id, message_id) values (?,?)";
    private static final String DELETE_FROM_QUEUE_ENTRY = "DELETE FROM " + QUEUE_ENTRY_TABLE_NAME + " WHERE queue_id = ? AND message_id =?";
    private static final String SELECT_FROM_QUEUE_ENTRY = "SELECT queue_id, message_id FROM " + QUEUE_ENTRY_TABLE_NAME + " ORDER BY queue_id, message_id";
    private static final String INSERT_INTO_MESSAGE_CONTENT = "INSERT INTO " + MESSAGE_CONTENT_TABLE_NAME
            + "( message_id, content ) values (?, ?)";
    private static final String SELECT_FROM_MESSAGE_CONTENT = "SELECT content FROM " + MESSAGE_CONTENT_TABLE_NAME
            + " WHERE message_id = ?";
    private static final String DELETE_FROM_MESSAGE_CONTENT = "DELETE FROM " + MESSAGE_CONTENT_TABLE_NAME
            + " WHERE message_id = ?";

    private static final String INSERT_INTO_META_DATA = "INSERT INTO " + META_DATA_TABLE_NAME + "( message_id , meta_data ) values (?, ?)";
    private static final String SELECT_FROM_META_DATA =
            "SELECT meta_data FROM " + META_DATA_TABLE_NAME + " WHERE message_id = ?";
    private static final String DELETE_FROM_META_DATA = "DELETE FROM " + META_DATA_TABLE_NAME + " WHERE message_id = ?";
    private static final String SELECT_ALL_FROM_META_DATA = "SELECT message_id, meta_data FROM " + META_DATA_TABLE_NAME;

    private static final String INSERT_INTO_XIDS =
            "INSERT INTO "+ XID_TABLE_NAME +" ( format, global_id, branch_id ) values (?, ?, ?)";
    private static final String DELETE_FROM_XIDS = "DELETE FROM " + XID_TABLE_NAME
                                                      + " WHERE format = ? and global_id = ? and branch_id = ?";
    private static final String SELECT_ALL_FROM_XIDS = "SELECT format, global_id, branch_id FROM " + XID_TABLE_NAME;
    private static final String INSERT_INTO_XID_ACTIONS =
            "INSERT INTO "+ XID_ACTIONS_TABLE_NAME +" ( format, global_id, branch_id, action_type, " +
            "queue_id, message_id ) values (?,?,?,?,?,?) ";
    private static final String DELETE_FROM_XID_ACTIONS = "DELETE FROM " + XID_ACTIONS_TABLE_NAME
                                                   + " WHERE format = ? and global_id = ? and branch_id = ?";
    private static final String SELECT_ALL_FROM_XID_ACTIONS =
            "SELECT action_type, queue_id, message_id FROM " + XID_ACTIONS_TABLE_NAME +
            " WHERE format = ? and global_id = ? and branch_id = ?";
    private static final String INSERT_INTO_CONFIGURED_OBJECTS = "INSERT INTO " + CONFIGURED_OBJECTS_TABLE_NAME
            + " ( id, object_type, attributes) VALUES (?,?,?)";
    private static final String UPDATE_CONFIGURED_OBJECTS = "UPDATE " + CONFIGURED_OBJECTS_TABLE_NAME
            + " set object_type =?, attributes = ? where id = ?";
    private static final String DELETE_FROM_CONFIGURED_OBJECTS = "DELETE FROM " + CONFIGURED_OBJECTS_TABLE_NAME
            + " where id = ?";
    private static final String FIND_CONFIGURED_OBJECT = "SELECT object_type, attributes FROM " + CONFIGURED_OBJECTS_TABLE_NAME
            + " where id = ?";
    private static final String SELECT_FROM_CONFIGURED_OBJECTS = "SELECT id, object_type, attributes FROM " + CONFIGURED_OBJECTS_TABLE_NAME;


    private static final String INSERT_INTO_CONFIGURED_OBJECT_HIERARCHY = "INSERT INTO " + CONFIGURED_OBJECT_HIERARCHY_TABLE_NAME
                                                                          + " ( child_id, parent_type, parent_id) VALUES (?,?,?)";

    private static final String DELETE_FROM_CONFIGURED_OBJECT_HIERARCHY = "DELETE FROM " + CONFIGURED_OBJECT_HIERARCHY_TABLE_NAME
                                                                          + " where child_id = ?";
    private static final String SELECT_FROM_CONFIGURED_OBJECT_HIERARCHY = "SELECT child_id, parent_type, parent_id FROM " + CONFIGURED_OBJECT_HIERARCHY_TABLE_NAME;

    protected static final Charset UTF8_CHARSET = Charset.forName("UTF-8");


    private static final Module _module;
    static
    {
        SimpleModule module= new SimpleModule("ConfiguredObjectSerializer", new Version(1,0,0,null));

        final JsonSerializer<ConfiguredObject> serializer = new JsonSerializer<ConfiguredObject>()
        {
            @Override
            public void serialize(final ConfiguredObject value,
                                  final JsonGenerator jgen,
                                  final SerializerProvider provider)
                    throws IOException, JsonProcessingException
            {
                jgen.writeString(value.getId().toString());
            }
        };
        module.addSerializer(ConfiguredObject.class, serializer);

        _module = module;
    }

    protected final EventManager _eventManager = new EventManager();

    private final AtomicBoolean _messageStoreOpen = new AtomicBoolean();
    private final AtomicBoolean _configurationStoreOpen = new AtomicBoolean();

    private boolean _initialized;


    @Override
    public void openConfigurationStore(ConfiguredObject<?> parent, Map<String, Object> storeSettings)
    {
        if (_configurationStoreOpen.compareAndSet(false,  true))
        {
            initialiseIfNecessary(parent.getName(), storeSettings);
            try
            {
                createOrOpenConfigurationStoreDatabase();
                upgradeIfVersionTableExists(parent);
            }
            catch(SQLException e)
            {
                throw new StoreException("Cannot create databases or upgrade", e);
            }
        }
    }

    private void initialiseIfNecessary(String virtualHostName, Map<String, Object> storeSettings)
    {
        if (!_initialized)
        {
            try
            {
                implementationSpecificConfiguration(virtualHostName, storeSettings);
            }
            catch (ClassNotFoundException e)
            {
               throw new StoreException("Cannot find driver class", e);
            }
            catch (SQLException e)
            {
                throw new StoreException("Unexpected exception occured", e);
            }
            _initialized = true;
        }
    }

    @Override
    public void visitConfiguredObjectRecords(ConfiguredObjectRecordHandler handler)
    {
        checkConfigurationStoreOpen();

        try
        {
            handler.begin();
            doVisitAllConfiguredObjectRecords(handler);
            handler.end();
        }
        catch (SQLException e)
        {
            throw new StoreException("Cannot visit configured object records", e);
        }

    }

    private void doVisitAllConfiguredObjectRecords(ConfiguredObjectRecordHandler handler) throws SQLException
    {
        Connection conn = newAutoCommitConnection();
        Map<UUID, ConfiguredObjectRecordImpl> configuredObjects = new HashMap<UUID, ConfiguredObjectRecordImpl>();
        final ObjectMapper objectMapper = new ObjectMapper();
        try
        {
            PreparedStatement stmt = conn.prepareStatement(SELECT_FROM_CONFIGURED_OBJECTS);
            try
            {
                ResultSet rs = stmt.executeQuery();
                try
                {
                    while (rs.next())
                    {
                        String id = rs.getString(1);
                        String objectType = rs.getString(2);
                        String attributes = getBlobAsString(rs, 3);
                        final ConfiguredObjectRecordImpl configuredObjectRecord =
                                new ConfiguredObjectRecordImpl(UUID.fromString(id), objectType,
                                                               objectMapper.readValue(attributes, Map.class));
                        configuredObjects.put(configuredObjectRecord.getId(),configuredObjectRecord);

                    }
                }
                catch (JsonMappingException e)
                {
                    throw new StoreException("Error recovering persistent state: " + e.getMessage(), e);
                }
                catch (JsonParseException e)
                {
                    throw new StoreException("Error recovering persistent state: " + e.getMessage(), e);
                }
                catch (IOException e)
                {
                    throw new StoreException("Error recovering persistent state: " + e.getMessage(), e);
                }
                finally
                {
                    rs.close();
                }
            }
            finally
            {
                stmt.close();
            }
            stmt = conn.prepareStatement(SELECT_FROM_CONFIGURED_OBJECT_HIERARCHY);
            try
            {
                ResultSet rs = stmt.executeQuery();
                try
                {
                    while (rs.next())
                    {
                        UUID childId = UUID.fromString(rs.getString(1));
                        String parentType = rs.getString(2);
                        UUID parentId = UUID.fromString(rs.getString(3));

                        ConfiguredObjectRecordImpl child = configuredObjects.get(childId);
                        ConfiguredObjectRecordImpl parent = configuredObjects.get(parentId);

                        if(child != null && parent != null)
                        {
                            child.addParent(parentType, parent);
                        }
                        else if(child != null && child.getType().endsWith("Binding") && parentType.equals("Exchange"))
                        {
                            // TODO - remove this hack for amq. exchanges
                            child.addParent(parentType, new ConfiguredObjectRecordImpl(parentId, parentType, Collections.<String,Object>emptyMap()));
                        }
                    }
                }
                finally
                {
                    rs.close();
                }
            }
            finally
            {
                stmt.close();
            }

        }
        finally
        {
            conn.close();
        }

        for(ConfiguredObjectRecord record : configuredObjects.values())
        {
            boolean shoudlContinue = handler.handle(record);
            if (!shoudlContinue)
            {
                break;
            }
        }
    }

    private void checkConfigurationStoreOpen()
    {
        if (!_configurationStoreOpen.get())
        {
            throw new IllegalStateException("Configuration store is not open");
        }
    }

    private void checkMessageStoreOpen()
    {
        if (!_messageStoreOpen.get())
        {
            throw new IllegalStateException("Message store is not open");
        }
    }

    private void upgradeIfVersionTableExists(ConfiguredObject<?> parent)
            throws SQLException {
        Connection conn = newAutoCommitConnection();
        try
        {
            if (tableExists(DB_VERSION_TABLE_NAME, conn))
            {
                upgradeIfNecessary(parent);
            }
        }
        finally
        {
            if (conn != null)
            {
                conn.close();
            }
        }
    }

    @Override
    public void openMessageStore(ConfiguredObject<?> parent, Map<String, Object> messageStoreSettings)
    {
        if (_messageStoreOpen.compareAndSet(false,  true))
        {
            initialiseIfNecessary(parent.getName(), messageStoreSettings);
            try
            {
                createOrOpenMessageStoreDatabase();
                upgradeIfNecessary(parent);

                visitMessages(new MessageHandler()
                {
                    @Override
                    public boolean handle(StoredMessage<?> storedMessage)
                    {
                        long id = storedMessage.getMessageNumber();
                        if (_messageId.get() < id)
                        {
                            _messageId.set(id);
                        }
                        return true;
                    }
                });
            }
            catch (SQLException e)
            {
                throw new StoreException("Unable to activate message store ", e);
            }
        }
    }

    protected void upgradeIfNecessary(ConfiguredObject<?> parent) throws SQLException
    {
        Connection conn = newAutoCommitConnection();
        try
        {

            PreparedStatement statement = conn.prepareStatement(SELECT_FROM_DB_VERSION);
            try
            {
                ResultSet rs = statement.executeQuery();
                try
                {
                    if(!rs.next())
                    {
                        throw new StoreException(DB_VERSION_TABLE_NAME + " does not contain the database version");
                    }
                    int version = rs.getInt(1);
                    switch (version)
                    {
                        case 6:
                            upgradeFromV6();
                        case 7:
                            upgradeFromV7(parent);
                        case DB_VERSION:
                            return;
                        default:
                            throw new StoreException("Unknown database version: " + version);
                    }
                }
                finally
                {
                    rs.close();
                }
            }
            finally
            {
                statement.close();
            }
        }
        finally
        {
            conn.close();
        }

    }

    private void upgradeFromV6() throws SQLException
    {
        updateDbVersion(7);
    }


    private void upgradeFromV7(ConfiguredObject<?> parent) throws SQLException
    {
        Connection connection = newConnection();
        try
        {
            UUID virtualHostId = UUIDGenerator.generateVhostUUID(parent.getName());

            String stringifiedConfigVersion = BrokerModel.MODEL_VERSION;
            boolean tableExists = tableExists(CONFIGURATION_VERSION_TABLE_NAME, connection);
            if(tableExists)
            {
                int configVersion = getConfigVersion(connection);
                if (getLogger().isDebugEnabled())
                {
                    getLogger().debug("Upgrader read existing config version " + configVersion);
                }

                stringifiedConfigVersion = "0." + configVersion;
            }

            Map<String, Object> virtualHostAttributes = new HashMap<String, Object>();
            virtualHostAttributes.put("modelVersion", stringifiedConfigVersion);

            ConfiguredObjectRecord configuredObject = new ConfiguredObjectRecordImpl(virtualHostId, "VirtualHost", virtualHostAttributes);
            insertConfiguredObject(configuredObject, connection);

            if (getLogger().isDebugEnabled())
            {
                getLogger().debug("Upgrader created VirtualHost configuration entry with config version " + stringifiedConfigVersion);
            }

            Map<UUID,Map<String,Object>> bindingsToUpdate = new HashMap<UUID, Map<String, Object>>();
            List<UUID> others = new ArrayList<UUID>();
            final ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.registerModule(_module);

            PreparedStatement stmt = connection.prepareStatement(SELECT_FROM_CONFIGURED_OBJECTS);
            try
            {
                ResultSet rs = stmt.executeQuery();
                try
                {
                    while (rs.next())
                    {
                        UUID id = UUID.fromString(rs.getString(1));
                        String objectType = rs.getString(2);
                        Map<String,Object> attributes = objectMapper.readValue(getBlobAsString(rs, 3),Map.class);
                        if(objectType.endsWith("Binding"))
                        {
                            bindingsToUpdate.put(id,attributes);
                        }
                        else
                        {
                            others.add(id);
                        }
                    }
                }
                catch (JsonMappingException e)
                {
                    throw new StoreException("Error recovering persistent state: " + e.getMessage(), e);
                }
                catch (JsonParseException e)
                {
                    throw new StoreException("Error recovering persistent state: " + e.getMessage(), e);
                }
                catch (IOException e)
                {
                    throw new StoreException("Error recovering persistent state: " + e.getMessage(), e);
                }
                finally
                {
                    rs.close();
                }
            }
            finally
            {
                stmt.close();
            }

            stmt = connection.prepareStatement(INSERT_INTO_CONFIGURED_OBJECT_HIERARCHY);
            try
            {
                for (UUID id : others)
                {
                    stmt.setString(1, id.toString());
                    stmt.setString(2, "VirtualHost");
                    stmt.setString(3, virtualHostId.toString());
                    stmt.execute();
                }
                for(Map.Entry<UUID, Map<String,Object>> bindingEntry : bindingsToUpdate.entrySet())
                {
                    stmt.setString(1, bindingEntry.getKey().toString());
                    stmt.setString(2,"Queue");
                    stmt.setString(3, bindingEntry.getValue().remove("queue").toString());
                    stmt.execute();

                    stmt.setString(1, bindingEntry.getKey().toString());
                    stmt.setString(2,"Exchange");
                    stmt.setString(3, bindingEntry.getValue().remove("exchange").toString());
                    stmt.execute();
                }
            }
            finally
            {
                stmt.close();
            }
            stmt = connection.prepareStatement(UPDATE_CONFIGURED_OBJECTS);
            try
            {
                for(Map.Entry<UUID, Map<String,Object>> bindingEntry : bindingsToUpdate.entrySet())
                {
                    stmt.setString(1, "Binding");
                    byte[] attributesAsBytes = objectMapper.writeValueAsBytes(bindingEntry.getValue());

                    ByteArrayInputStream bis = new ByteArrayInputStream(attributesAsBytes);
                    stmt.setBinaryStream(2, bis, attributesAsBytes.length);
                    stmt.setString(3, bindingEntry.getKey().toString());
                    stmt.execute();
                }
            }
            catch (JsonMappingException e)
            {
                throw new StoreException("Error recovering persistent state: " + e.getMessage(), e);
            }
            catch (JsonGenerationException e)
            {
                throw new StoreException("Error recovering persistent state: " + e.getMessage(), e);
            }
            catch (IOException e)
            {
                throw new StoreException("Error recovering persistent state: " + e.getMessage(), e);
            }
            finally
            {
                stmt.close();
            }
            stmt = connection.prepareStatement(UPDATE_DB_VERSION);
            try
            {
                stmt.setInt(1, 8);
                stmt.execute();
            }
            finally
            {
                stmt.close();
            }
            connection.commit();

            if (tableExists)
            {
                dropConfigVersionTable(connection);
            }
        }
        finally
        {
            connection.close();
        }
    }

    private void updateDbVersion(int newVersion) throws SQLException
    {
        Connection conn = newAutoCommitConnection();
        try
        {

            PreparedStatement statement = conn.prepareStatement(UPDATE_DB_VERSION);
            try
            {
                statement.setInt(1,newVersion);
                statement.execute();
            }
            finally
            {
                statement.close();
            }
        }
        finally
        {
            conn.close();
        }
    }

    protected abstract void implementationSpecificConfiguration(String name, Map<String, Object> messageStoreSettings) throws ClassNotFoundException, SQLException;

    abstract protected Logger getLogger();

    abstract protected String getSqlBlobType();

    abstract protected String getSqlVarBinaryType(int size);

    abstract protected String getSqlBigIntType();

    protected void createOrOpenMessageStoreDatabase() throws SQLException
    {
        Connection conn = newAutoCommitConnection();

        createVersionTable(conn);
        createQueueEntryTable(conn);
        createMetaDataTable(conn);
        createMessageContentTable(conn);
        createXidTable(conn);
        createXidActionTable(conn);
        conn.close();
    }

    protected void createOrOpenConfigurationStoreDatabase() throws SQLException
    {
        Connection conn = newAutoCommitConnection();

        createConfiguredObjectsTable(conn);
        createConfiguredObjectHierarchyTable(conn);

        conn.close();
    }

    private void createVersionTable(final Connection conn) throws SQLException
    {
        if(!tableExists(DB_VERSION_TABLE_NAME, conn))
        {
            Statement stmt = conn.createStatement();
            try
            {
                stmt.execute(CREATE_DB_VERSION_TABLE);
            }
            finally
            {
                stmt.close();
            }

            PreparedStatement pstmt = conn.prepareStatement(INSERT_INTO_DB_VERSION);
            try
            {
                pstmt.setInt(1, DB_VERSION);
                pstmt.execute();
            }
            finally
            {
                pstmt.close();
            }
        }
    }

    private void dropConfigVersionTable(final Connection conn) throws SQLException
    {
        if(!tableExists(CONFIGURATION_VERSION_TABLE_NAME, conn))
        {
            Statement stmt = conn.createStatement();
            try
            {
                stmt.execute(DROP_CONFIG_VERSION_TABLE);
            }
            finally
            {
                stmt.close();
            }
        }
    }

    private void createConfiguredObjectsTable(final Connection conn) throws SQLException
    {
        if(!tableExists(CONFIGURED_OBJECTS_TABLE_NAME, conn))
        {
            Statement stmt = conn.createStatement();
            try
            {
                stmt.execute("CREATE TABLE " + CONFIGURED_OBJECTS_TABLE_NAME
                        + " ( id VARCHAR(36) not null, object_type varchar(255), attributes "+getSqlBlobType()+",  PRIMARY KEY (id))");
            }
            finally
            {
                stmt.close();
            }
        }
    }

    private void createConfiguredObjectHierarchyTable(final Connection conn) throws SQLException
    {
        if(!tableExists(CONFIGURED_OBJECT_HIERARCHY_TABLE_NAME, conn))
        {
            Statement stmt = conn.createStatement();
            try
            {
                stmt.execute("CREATE TABLE " + CONFIGURED_OBJECT_HIERARCHY_TABLE_NAME
                             + " ( child_id VARCHAR(36) not null, parent_type varchar(255), parent_id VARCHAR(36),  PRIMARY KEY (child_id, parent_type))");
            }
            finally
            {
                stmt.close();
            }
        }
    }

    private void createQueueEntryTable(final Connection conn) throws SQLException
    {
        if(!tableExists(QUEUE_ENTRY_TABLE_NAME, conn))
        {
            Statement stmt = conn.createStatement();
            try
            {
                stmt.execute("CREATE TABLE "+ QUEUE_ENTRY_TABLE_NAME +" ( queue_id varchar(36) not null, message_id "
                        + getSqlBigIntType() + " not null, PRIMARY KEY (queue_id, message_id) )");
            }
            finally
            {
                stmt.close();
            }
        }

    }

    private void createMetaDataTable(final Connection conn) throws SQLException
    {
        if(!tableExists(META_DATA_TABLE_NAME, conn))
        {
            Statement stmt = conn.createStatement();
            try
            {
                stmt.execute("CREATE TABLE "
                             + META_DATA_TABLE_NAME
                             + " ( message_id "
                             + getSqlBigIntType()
                             + " not null, meta_data "
                             + getSqlBlobType()
                             + ", PRIMARY KEY ( message_id ) )");
            }
            finally
            {
                stmt.close();
            }
        }

    }

    private void createMessageContentTable(final Connection conn) throws SQLException
    {
        if(!tableExists(MESSAGE_CONTENT_TABLE_NAME, conn))
        {
            Statement stmt = conn.createStatement();
            try
            {
                stmt.execute("CREATE TABLE "
                             + MESSAGE_CONTENT_TABLE_NAME
                             + " ( message_id "
                             + getSqlBigIntType()
                             + " not null, content "
                             + getSqlBlobType()
                             + ", PRIMARY KEY (message_id) )");
            }
            finally
            {
                stmt.close();
            }
        }

    }

    private void createXidTable(final Connection conn) throws SQLException
    {
        if(!tableExists(XID_TABLE_NAME, conn))
        {
            Statement stmt = conn.createStatement();
            try
            {
                stmt.execute("CREATE TABLE "
                             + XID_TABLE_NAME
                             + " ( format " + getSqlBigIntType() + " not null,"
                             + " global_id "
                             + getSqlVarBinaryType(64)
                             + ", branch_id "
                             + getSqlVarBinaryType(64)
                             + " ,  PRIMARY KEY ( format, "
                             +
                             "global_id, branch_id ))");
            }
            finally
            {
                stmt.close();
            }
        }
    }

    private void createXidActionTable(final Connection conn) throws SQLException
    {
        if(!tableExists(XID_ACTIONS_TABLE_NAME, conn))
        {
            Statement stmt = conn.createStatement();
            try
            {
                stmt.execute("CREATE TABLE " + XID_ACTIONS_TABLE_NAME + " ( format " + getSqlBigIntType() + " not null,"
                             + " global_id " + getSqlVarBinaryType(64) + " not null, branch_id " + getSqlVarBinaryType(
                        64) + " not null, " +
                             "action_type char not null, queue_id varchar(36) not null, message_id " + getSqlBigIntType() + " not null" +
                             ",  PRIMARY KEY ( " +
                             "format, global_id, branch_id, action_type, queue_id, message_id))");
            }
            finally
            {
                stmt.close();
            }
        }
    }

    protected boolean tableExists(final String tableName, final Connection conn) throws SQLException
    {
        DatabaseMetaData metaData = conn.getMetaData();
        ResultSet rs = metaData.getTables(null, null, "%", null);

        try
        {

            while(rs.next())
            {
                final String table = rs.getString(3);
                if(tableName.equalsIgnoreCase(table))
                {
                    return true;
                }
            }
            return false;
        }
        finally
        {
            rs.close();
        }
    }

    private int getConfigVersion(Connection conn) throws SQLException
    {
        Statement stmt = conn.createStatement();
        try
        {
            ResultSet rs = stmt.executeQuery(SELECT_FROM_CONFIG_VERSION);
            try
            {

                if(rs.next())
                {
                    return rs.getInt(1);
                }
                return DEFAULT_CONFIG_VERSION;
            }
            finally
            {
                rs.close();
            }

        }
        finally
        {
            stmt.close();
        }

    }

    @Override
    public void closeMessageStore()
    {
        if (_messageStoreOpen.compareAndSet(true, false))
        {
            if (!_configurationStoreOpen.get())
            {
                doClose();
            }
        }
    }

    @Override
    public void closeConfigurationStore()
    {
        if (_configurationStoreOpen.compareAndSet(true, false))
        {
            if (!_messageStoreOpen.get())
            {
                doClose();
            }
        }
    }

    protected abstract void doClose();

    @Override
    public StoredMessage addMessage(StorableMessageMetaData metaData)
    {
        checkMessageStoreOpen();

        if(metaData.isPersistent())
        {
            return new StoredJDBCMessage(_messageId.incrementAndGet(), metaData);
        }
        else
        {
            return new StoredMemoryMessage(_messageId.incrementAndGet(), metaData);
        }
    }

    private void removeMessage(long messageId)
    {
        try
        {
            Connection conn = newConnection();
            try
            {
                PreparedStatement stmt = conn.prepareStatement(DELETE_FROM_META_DATA);
                try
                {
                    stmt.setLong(1,messageId);
                    int results = stmt.executeUpdate();
                    stmt.close();

                    if (results == 0)
                    {
                        getLogger().warn("Message metadata not found for message id " + messageId);
                    }

                    if (getLogger().isDebugEnabled())
                    {
                        getLogger().debug("Deleted metadata for message " + messageId);
                    }

                    stmt = conn.prepareStatement(DELETE_FROM_MESSAGE_CONTENT);
                    stmt.setLong(1,messageId);
                    results = stmt.executeUpdate();
                }
                finally
                {
                    stmt.close();
                }
                conn.commit();
            }
            catch(SQLException e)
            {
                try
                {
                    conn.rollback();
                }
                catch(SQLException t)
                {
                    // ignore - we are re-throwing underlying exception
                }

                throw e;

            }
            finally
            {
                conn.close();
            }
        }
        catch (SQLException e)
        {
            throw new StoreException("Error removing message with id " + messageId + " from database: " + e.getMessage(), e);
        }

    }


    @Override
    public void create(ConfiguredObjectRecord object) throws StoreException
    {
        checkConfigurationStoreOpen();
        try
        {
            Connection conn = newConnection();
            try
            {
                insertConfiguredObject(object, conn);
                conn.commit();
            }
            finally
            {
                conn.close();
            }
        }
        catch (SQLException e)
        {
            throw new StoreException("Error creating ConfiguredObject " + object);
        }
    }

    /**
     * Convenience method to create a new Connection configured for TRANSACTION_READ_COMMITED
     * isolation and with auto-commit transactions enabled.
     */
    protected Connection newAutoCommitConnection() throws SQLException
    {
        final Connection connection = newConnection();
        try
        {
            connection.setAutoCommit(true);
        }
        catch (SQLException sqlEx)
        {

            try
            {
                connection.close();
            }
            finally
            {
                throw sqlEx;
            }
        }

        return connection;
    }

    /**
     * Convenience method to create a new Connection configured for TRANSACTION_READ_COMMITED
     * isolation and with auto-commit transactions disabled.
     */
    protected Connection newConnection() throws SQLException
    {
        final Connection connection = getConnection();
        try
        {
            connection.setAutoCommit(false);
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        }
        catch (SQLException sqlEx)
        {
            try
            {
                connection.close();
            }
            finally
            {
                throw sqlEx;
            }
        }
        return connection;
    }

    protected abstract Connection getConnection() throws SQLException;

    @Override
    public Transaction newTransaction()
    {
        checkMessageStoreOpen();

        return new JDBCTransaction();
    }

    private void enqueueMessage(ConnectionWrapper connWrapper, final TransactionLogResource queue, Long messageId) throws StoreException
    {
        Connection conn = connWrapper.getConnection();


        try
        {
            if (getLogger().isDebugEnabled())
            {
                getLogger().debug("Enqueuing message "
                                   + messageId
                                   + " on queue "
                                   + queue.getName()
                                   + " with id " + queue.getId()
                                   + " [Connection"
                                   + conn
                                   + "]");
            }

            PreparedStatement stmt = conn.prepareStatement(INSERT_INTO_QUEUE_ENTRY);
            try
            {
                stmt.setString(1, queue.getId().toString());
                stmt.setLong(2,messageId);
                stmt.executeUpdate();
            }
            finally
            {
                stmt.close();
            }

        }
        catch (SQLException e)
        {
            getLogger().error("Failed to enqueue: " + e.getMessage(), e);
            throw new StoreException("Error writing enqueued message with id " + messageId + " for queue " + queue.getName() + " with id " + queue.getId()
                + " to database", e);
        }

    }

    private void dequeueMessage(ConnectionWrapper connWrapper, final TransactionLogResource  queue, Long messageId) throws StoreException
    {

        Connection conn = connWrapper.getConnection();


        try
        {
            PreparedStatement stmt = conn.prepareStatement(DELETE_FROM_QUEUE_ENTRY);
            try
            {
                stmt.setString(1, queue.getId().toString());
                stmt.setLong(2, messageId);
                int results = stmt.executeUpdate();



                if(results != 1)
                {
                    throw new StoreException("Unable to find message with id " + messageId + " on queue " + queue.getName()
                           + " with id " + queue.getId());
                }

                if (getLogger().isDebugEnabled())
                {
                    getLogger().debug("Dequeuing message " + messageId + " on queue " + queue.getName()
                                       + " with id " + queue.getId());
                }
            }
            finally
            {
                stmt.close();
            }

        }
        catch (SQLException e)
        {
            getLogger().error("Failed to dequeue: " + e.getMessage(), e);
            throw new StoreException("Error deleting enqueued message with id " + messageId + " for queue " + queue.getName()
                    + " with id " + queue.getId() + " from database", e);
        }

    }

    private void removeXid(ConnectionWrapper connWrapper, long format, byte[] globalId, byte[] branchId)
            throws StoreException
    {
        Connection conn = connWrapper.getConnection();


        try
        {
            PreparedStatement stmt = conn.prepareStatement(DELETE_FROM_XIDS);
            try
            {
                stmt.setLong(1,format);
                stmt.setBytes(2,globalId);
                stmt.setBytes(3,branchId);
                int results = stmt.executeUpdate();



                if(results != 1)
                {
                    throw new StoreException("Unable to find message with xid");
                }
            }
            finally
            {
                stmt.close();
            }

            stmt = conn.prepareStatement(DELETE_FROM_XID_ACTIONS);
            try
            {
                stmt.setLong(1,format);
                stmt.setBytes(2,globalId);
                stmt.setBytes(3,branchId);
                int results = stmt.executeUpdate();

            }
            finally
            {
                stmt.close();
            }

        }
        catch (SQLException e)
        {
            getLogger().error("Failed to dequeue: " + e.getMessage(), e);
            throw new StoreException("Error deleting enqueued message with xid", e);
        }

    }

    private void recordXid(ConnectionWrapper connWrapper, long format, byte[] globalId, byte[] branchId,
                           Transaction.Record[] enqueues, Transaction.Record[] dequeues) throws StoreException
    {
        Connection conn = connWrapper.getConnection();


        try
        {

            PreparedStatement stmt = conn.prepareStatement(INSERT_INTO_XIDS);
            try
            {
                stmt.setLong(1,format);
                stmt.setBytes(2, globalId);
                stmt.setBytes(3, branchId);
                stmt.executeUpdate();
            }
            finally
            {
                stmt.close();
            }

            stmt = conn.prepareStatement(INSERT_INTO_XID_ACTIONS);

            try
            {
                stmt.setLong(1,format);
                stmt.setBytes(2, globalId);
                stmt.setBytes(3, branchId);

                if(enqueues != null)
                {
                    stmt.setString(4, "E");
                    for(Transaction.Record record : enqueues)
                    {
                        stmt.setString(5, record.getResource().getId().toString());
                        stmt.setLong(6, record.getMessage().getMessageNumber());
                        stmt.executeUpdate();
                    }
                }

                if(dequeues != null)
                {
                    stmt.setString(4, "D");
                    for(Transaction.Record record : dequeues)
                    {
                        stmt.setString(5, record.getResource().getId().toString());
                        stmt.setLong(6, record.getMessage().getMessageNumber());
                        stmt.executeUpdate();
                    }
                }

            }
            finally
            {
                stmt.close();
            }

        }
        catch (SQLException e)
        {
            getLogger().error("Failed to enqueue: " + e.getMessage(), e);
            throw new StoreException("Error writing xid ", e);
        }

    }

    private static final class ConnectionWrapper
    {
        private final Connection _connection;

        public ConnectionWrapper(Connection conn)
        {
            _connection = conn;
        }

        public Connection getConnection()
        {
            return _connection;
        }
    }


    private void commitTran(ConnectionWrapper connWrapper) throws StoreException
    {

        try
        {
            Connection conn = connWrapper.getConnection();
            conn.commit();

            if (getLogger().isDebugEnabled())
            {
                getLogger().debug("commit tran completed");
            }

            conn.close();
        }
        catch (SQLException e)
        {
            throw new StoreException("Error commit tx: " + e.getMessage(), e);
        }
        finally
        {

        }
    }

    private StoreFuture commitTranAsync(ConnectionWrapper connWrapper) throws StoreException
    {
        commitTran(connWrapper);
        return StoreFuture.IMMEDIATE_FUTURE;
    }

    private void abortTran(ConnectionWrapper connWrapper) throws StoreException
    {
        if (connWrapper == null)
        {
            throw new StoreException("Fatal internal error: transactional context is empty at abortTran");
        }

        if (getLogger().isDebugEnabled())
        {
            getLogger().debug("abort tran called: " + connWrapper.getConnection());
        }

        try
        {
            Connection conn = connWrapper.getConnection();
            conn.rollback();
            conn.close();
        }
        catch (SQLException e)
        {
            throw new StoreException("Error aborting transaction: " + e.getMessage(), e);
        }

    }

    private void storeMetaData(Connection conn, long messageId, StorableMessageMetaData metaData)
        throws SQLException
    {
        if(getLogger().isDebugEnabled())
        {
            getLogger().debug("Adding metadata for message " + messageId);
        }

        PreparedStatement stmt = conn.prepareStatement(INSERT_INTO_META_DATA);
        try
        {
            stmt.setLong(1,messageId);

            final int bodySize = 1 + metaData.getStorableSize();
            byte[] underlying = new byte[bodySize];
            underlying[0] = (byte) metaData.getType().ordinal();
            ByteBuffer buf = ByteBuffer.wrap(underlying);
            buf.position(1);
            buf = buf.slice();

            metaData.writeToBuffer(buf);
            ByteArrayInputStream bis = new ByteArrayInputStream(underlying);
            try
            {
                stmt.setBinaryStream(2,bis,underlying.length);
                int result = stmt.executeUpdate();

                if(result == 0)
                {
                    throw new StoreException("Unable to add meta data for message " +messageId);
                }
            }
            finally
            {
                try
                {
                    bis.close();
                }
                catch (IOException e)
                {

                    throw new SQLException(e);
                }
            }

        }
        finally
        {
            stmt.close();
        }

    }


    private static class RecordImpl implements Transaction.Record, TransactionLogResource, EnqueueableMessage
    {

        private long _messageNumber;
        private UUID _queueId;

        public RecordImpl(UUID queueId, long messageNumber)
        {
            _messageNumber = messageNumber;
            _queueId = queueId;
        }

        @Override
        public TransactionLogResource getResource()
        {
            return this;
        }

        @Override
        public EnqueueableMessage getMessage()
        {
            return this;
        }

        @Override
        public long getMessageNumber()
        {
            return _messageNumber;
        }

        @Override
        public boolean isPersistent()
        {
            return true;
        }

        @Override
        public StoredMessage getStoredMessage()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getName()
        {
            return _queueId.toString();
        }

        @Override
        public UUID getId()
        {
            return _queueId;
        }

        @Override
        public boolean isDurable()
        {
            return true;
        }
    }

    private StorableMessageMetaData getMetaData(long messageId) throws SQLException
    {

        Connection conn = newAutoCommitConnection();
        try
        {
            PreparedStatement stmt = conn.prepareStatement(SELECT_FROM_META_DATA);
            try
            {
                stmt.setLong(1,messageId);
                ResultSet rs = stmt.executeQuery();
                try
                {

                    if(rs.next())
                    {
                        byte[] dataAsBytes = getBlobAsBytes(rs, 1);
                        ByteBuffer buf = ByteBuffer.wrap(dataAsBytes);
                        buf.position(1);
                        buf = buf.slice();
                        MessageMetaDataType type = MessageMetaDataTypeRegistry.fromOrdinal(dataAsBytes[0]);
                        StorableMessageMetaData metaData = type.createMetaData(buf);

                        return metaData;
                    }
                    else
                    {
                        throw new StoreException("Meta data not found for message with id " + messageId);
                    }
                }
                finally
                {
                    rs.close();
                }
            }
            finally
            {
                stmt.close();
            }
        }
        finally
        {
            conn.close();
        }
    }

    protected abstract byte[] getBlobAsBytes(ResultSet rs, int col) throws SQLException;

    private void addContent(Connection conn, long messageId, ByteBuffer src)
    {
        if(getLogger().isDebugEnabled())
        {
            getLogger().debug("Adding content for message " + messageId);
        }
        PreparedStatement stmt = null;

        try
        {
            src = src.slice();

            byte[] chunkData = new byte[src.limit()];
            src.duplicate().get(chunkData);

            stmt = conn.prepareStatement(INSERT_INTO_MESSAGE_CONTENT);
            stmt.setLong(1,messageId);

            ByteArrayInputStream bis = new ByteArrayInputStream(chunkData);
            stmt.setBinaryStream(2, bis, chunkData.length);
            stmt.executeUpdate();
        }
        catch (SQLException e)
        {
            closeConnection(conn);
            throw new StoreException("Error adding content for message " + messageId + ": " + e.getMessage(), e);
        }
        finally
        {
            closePreparedStatement(stmt);
        }

    }

    private int getContent(long messageId, int offset, ByteBuffer dst)
    {
        Connection conn = null;
        PreparedStatement stmt = null;

        try
        {
            conn = newAutoCommitConnection();

            stmt = conn.prepareStatement(SELECT_FROM_MESSAGE_CONTENT);
            stmt.setLong(1,messageId);
            ResultSet rs = stmt.executeQuery();

            int written = 0;

            if (rs.next())
            {

                byte[] dataAsBytes = getBlobAsBytes(rs, 1);
                int size = dataAsBytes.length;

                if (offset > size)
                {
                    throw new StoreException("Offset " + offset + " is greater than message size " + size
                            + " for message id " + messageId + "!");

                }

                written = size - offset;
                if(written > dst.remaining())
                {
                    written = dst.remaining();
                }

                dst.put(dataAsBytes, offset, written);
            }

            return written;

        }
        catch (SQLException e)
        {
            throw new StoreException("Error retrieving content from offset " + offset + " for message " + messageId + ": " + e.getMessage(), e);
        }
        finally
        {
            closePreparedStatement(stmt);
            closeConnection(conn);
        }


    }

    @Override
    public boolean isPersistent()
    {
        return true;
    }


    protected class JDBCTransaction implements Transaction
    {
        private final ConnectionWrapper _connWrapper;
        private int _storeSizeIncrease;


        protected JDBCTransaction()
        {
            try
            {
                _connWrapper = new ConnectionWrapper(newConnection());
            }
            catch (SQLException e)
            {
                throw new StoreException(e);
            }
        }

        @Override
        public void enqueueMessage(TransactionLogResource queue, EnqueueableMessage message)
        {
            checkMessageStoreOpen();

            final StoredMessage storedMessage = message.getStoredMessage();
            if(storedMessage instanceof StoredJDBCMessage)
            {
                try
                {
                    ((StoredJDBCMessage) storedMessage).store(_connWrapper.getConnection());
                }
                catch (SQLException e)
                {
                    throw new StoreException("Exception on enqueuing message into message store" + _messageId, e);
                }
            }
            _storeSizeIncrease += storedMessage.getMetaData().getContentSize();
            AbstractJDBCMessageStore.this.enqueueMessage(_connWrapper, queue, message.getMessageNumber());

        }

        @Override
        public void dequeueMessage(TransactionLogResource queue, EnqueueableMessage message)
        {
            checkMessageStoreOpen();

            AbstractJDBCMessageStore.this.dequeueMessage(_connWrapper, queue, message.getMessageNumber());
        }

        @Override
        public void commitTran()
        {
            checkMessageStoreOpen();

            AbstractJDBCMessageStore.this.commitTran(_connWrapper);
            storedSizeChange(_storeSizeIncrease);
        }

        @Override
        public StoreFuture commitTranAsync()
        {
            checkMessageStoreOpen();

            StoreFuture storeFuture = AbstractJDBCMessageStore.this.commitTranAsync(_connWrapper);
            storedSizeChange(_storeSizeIncrease);
            return storeFuture;
        }

        @Override
        public void abortTran()
        {
            checkMessageStoreOpen();

            AbstractJDBCMessageStore.this.abortTran(_connWrapper);
        }

        @Override
        public void removeXid(long format, byte[] globalId, byte[] branchId)
        {
            checkMessageStoreOpen();

            AbstractJDBCMessageStore.this.removeXid(_connWrapper, format, globalId, branchId);
        }

        @Override
        public void recordXid(long format, byte[] globalId, byte[] branchId, Record[] enqueues, Record[] dequeues)
        {
            checkMessageStoreOpen();

            AbstractJDBCMessageStore.this.recordXid(_connWrapper, format, globalId, branchId, enqueues, dequeues);
        }
    }

    private class StoredJDBCMessage implements StoredMessage
    {

        private final long _messageId;
        private final boolean _isRecovered;

        private StorableMessageMetaData _metaData;
        private volatile SoftReference<StorableMessageMetaData> _metaDataRef;
        private byte[] _data;
        private volatile SoftReference<byte[]> _dataRef;


        StoredJDBCMessage(long messageId, StorableMessageMetaData metaData)
        {
            this(messageId, metaData, false);
        }


        StoredJDBCMessage(long messageId,
                          StorableMessageMetaData metaData, boolean isRecovered)
        {
            _messageId = messageId;
            _isRecovered = isRecovered;

            if(!_isRecovered)
            {
                _metaData = metaData;
            }
            _metaDataRef = new SoftReference<StorableMessageMetaData>(metaData);
        }

        @Override
        public StorableMessageMetaData getMetaData()
        {
            StorableMessageMetaData metaData = _metaData == null ? _metaDataRef.get() : _metaData;
            if(metaData == null)
            {
                checkMessageStoreOpen();
                try
                {
                    metaData = AbstractJDBCMessageStore.this.getMetaData(_messageId);
                }
                catch (SQLException e)
                {
                    throw new StoreException(e);
                }
                _metaDataRef = new SoftReference<StorableMessageMetaData>(metaData);
            }

            return metaData;
        }

        @Override
        public long getMessageNumber()
        {
            return _messageId;
        }

        @Override
        public void addContent(int offsetInMessage, ByteBuffer src)
        {
            src = src.slice();

            if(_data == null)
            {
                _data = new byte[src.remaining()];
                _dataRef = new SoftReference<byte[]>(_data);
                src.duplicate().get(_data);
            }
            else
            {
                byte[] oldData = _data;
                _data = new byte[oldData.length + src.remaining()];
                _dataRef = new SoftReference<byte[]>(_data);

                System.arraycopy(oldData,0,_data,0,oldData.length);
                src.duplicate().get(_data, oldData.length, src.remaining());
            }

        }

        @Override
        public int getContent(int offsetInMessage, ByteBuffer dst)
        {
            byte[] data = _dataRef == null ? null : _dataRef.get();
            if(data != null)
            {
                int length = Math.min(dst.remaining(), data.length - offsetInMessage);
                dst.put(data, offsetInMessage, length);
                return length;
            }
            else
            {
                checkMessageStoreOpen();
                return AbstractJDBCMessageStore.this.getContent(_messageId, offsetInMessage, dst);
            }
        }


        @Override
        public ByteBuffer getContent(int offsetInMessage, int size)
        {
            ByteBuffer buf = ByteBuffer.allocate(size);
            int length = getContent(offsetInMessage, buf);
            buf.position(0);
            buf.limit(length);
            return  buf;
        }

        @Override
        public synchronized StoreFuture flushToStore()
        {
            checkMessageStoreOpen();

            Connection conn = null;
            try
            {
                if(!stored())
                {
                    conn = newConnection();

                    store(conn);

                    conn.commit();
                    storedSizeChange(getMetaData().getContentSize());
                }
            }
            catch (SQLException e)
            {
                if(getLogger().isDebugEnabled())
                {
                    getLogger().debug("Error when trying to flush message " + _messageId + " to store: " + e);
                }
                throw new StoreException(e);
            }
            finally
            {
                closeConnection(conn);
            }
            return StoreFuture.IMMEDIATE_FUTURE;
        }

        @Override
        public void remove()
        {
            checkMessageStoreOpen();

            int delta = getMetaData().getContentSize();
            AbstractJDBCMessageStore.this.removeMessage(_messageId);
            storedSizeChange(-delta);
        }

        private synchronized void store(final Connection conn) throws SQLException
        {
            if (!stored())
            {
                try
                {
                    storeMetaData(conn, _messageId, _metaData);
                    AbstractJDBCMessageStore.this.addContent(conn, _messageId,
                                                      _data == null ? ByteBuffer.allocate(0) : ByteBuffer.wrap(_data));
                }
                finally
                {
                    _metaData = null;
                    _data = null;
                }

                if(getLogger().isDebugEnabled())
                {
                    getLogger().debug("Storing message " + _messageId + " to store");
                }
            }
        }

        private boolean stored()
        {
            return _metaData == null || _isRecovered;
        }
    }

    protected void closeConnection(final Connection conn)
    {
        if(conn != null)
        {
           try
           {
               conn.close();
           }
           catch (SQLException e)
           {
               getLogger().error("Problem closing connection", e);
           }
        }
    }

    protected void closePreparedStatement(final PreparedStatement stmt)
    {
        if (stmt != null)
        {
            try
            {
                stmt.close();
            }
            catch(SQLException e)
            {
                getLogger().error("Problem closing prepared statement", e);
            }
        }
    }

    @Override
    public void addEventListener(EventListener eventListener, Event... events)
    {
        _eventManager.addEventListener(eventListener, events);
    }

    private void insertConfiguredObject(ConfiguredObjectRecord configuredObject, final Connection conn) throws StoreException
    {
        try
        {
            PreparedStatement stmt = conn.prepareStatement(FIND_CONFIGURED_OBJECT);
            try
            {
                stmt.setString(1, configuredObject.getId().toString());
                ResultSet rs = stmt.executeQuery();
                boolean exists;
                try
                {
                    exists = rs.next();

                }
                finally
                {
                    rs.close();
                }
                // If we don't have any data in the result set then we can add this configured object
                if (!exists)
                {
                    PreparedStatement insertStmt = conn.prepareStatement(INSERT_INTO_CONFIGURED_OBJECTS);
                    try
                    {
                        insertStmt.setString(1, configuredObject.getId().toString());
                        insertStmt.setString(2, configuredObject.getType());
                        if(configuredObject.getAttributes() == null)
                        {
                            insertStmt.setNull(3, Types.BLOB);
                        }
                        else
                        {
                            final Map<String, Object> attributes = configuredObject.getAttributes();
                            final ObjectMapper objectMapper = new ObjectMapper();
                            objectMapper.registerModule(_module);
                            byte[] attributesAsBytes = objectMapper.writeValueAsBytes(attributes);

                            ByteArrayInputStream bis = new ByteArrayInputStream(attributesAsBytes);
                            insertStmt.setBinaryStream(3, bis, attributesAsBytes.length);
                        }
                        insertStmt.execute();
                    }
                    finally
                    {
                        insertStmt.close();
                    }

                    writeHierarchy(configuredObject, conn);
                }

            }
            finally
            {
                stmt.close();
            }

        }
        catch (JsonMappingException e)
        {
            throw new StoreException("Error inserting of configured object " + configuredObject + " into database: " + e.getMessage(), e);
        }
        catch (JsonGenerationException e)
        {
            throw new StoreException("Error inserting of configured object " + configuredObject + " into database: " + e.getMessage(), e);
        }
        catch (IOException e)
        {
            throw new StoreException("Error inserting of configured object " + configuredObject + " into database: " + e.getMessage(), e);
        }
        catch (SQLException e)
        {
            throw new StoreException("Error inserting of configured object " + configuredObject + " into database: " + e.getMessage(), e);
        }
    }

    @Override
    public UUID[] remove(ConfiguredObjectRecord... objects) throws StoreException
    {
        checkConfigurationStoreOpen();

        Collection<UUID> removed = new ArrayList<UUID>(objects.length);
        try
        {

            Connection conn = newAutoCommitConnection();
            try
            {
                for(ConfiguredObjectRecord record : objects)
                {
                    if(removeConfiguredObject(record.getId(), conn) != 0)
                    {
                        removed.add(record.getId());
                    }
                }
            }
            finally
            {
                conn.close();
            }
        }
        catch (SQLException e)
        {
            throw new StoreException("Error deleting of configured objects " + Arrays.asList(objects) + " from database: " + e.getMessage(), e);
        }
        return removed.toArray(new UUID[removed.size()]);
    }

    private int removeConfiguredObject(final UUID id, final Connection conn) throws SQLException
    {
        final int results;
        PreparedStatement stmt = conn.prepareStatement(DELETE_FROM_CONFIGURED_OBJECTS);
        try
        {
            stmt.setString(1, id.toString());
            results = stmt.executeUpdate();
        }
        finally
        {
            stmt.close();
        }
        stmt = conn.prepareStatement(DELETE_FROM_CONFIGURED_OBJECT_HIERARCHY);
        try
        {
            stmt.setString(1, id.toString());
            stmt.executeUpdate();
        }
        finally
        {
            stmt.close();
        }

        return results;
    }

    public void update(boolean createIfNecessary, ConfiguredObjectRecord... records) throws StoreException
    {
        checkConfigurationStoreOpen();
        try
        {
            Connection conn = newConnection();
            try
            {
                for(ConfiguredObjectRecord record : records)
                {
                    updateConfiguredObject(record, createIfNecessary, conn);
                }
                conn.commit();
            }
            finally
            {
                conn.close();
            }
        }
        catch (SQLException e)
        {
            throw new StoreException("Error updating configured objects in database: " + e.getMessage(), e);
        }
    }

    private void updateConfiguredObject(ConfiguredObjectRecord configuredObject,
                                        boolean createIfNecessary,
                                        Connection conn)
            throws SQLException, StoreException
    {
        PreparedStatement stmt = conn.prepareStatement(FIND_CONFIGURED_OBJECT);
        try
        {
            stmt.setString(1, configuredObject.getId().toString());
            ResultSet rs = stmt.executeQuery();
            try
            {
                final ObjectMapper objectMapper = new ObjectMapper();
                objectMapper.registerModule(_module);
                if (rs.next())
                {
                    PreparedStatement stmt2 = conn.prepareStatement(UPDATE_CONFIGURED_OBJECTS);
                    try
                    {
                        stmt2.setString(1, configuredObject.getType());
                        if (configuredObject.getAttributes() != null)
                        {
                            byte[] attributesAsBytes = objectMapper.writeValueAsBytes(
                                    configuredObject.getAttributes());
                            ByteArrayInputStream bis = new ByteArrayInputStream(attributesAsBytes);
                            stmt2.setBinaryStream(2, bis, attributesAsBytes.length);
                        }
                        else
                        {
                            stmt2.setNull(2, Types.BLOB);
                        }
                        stmt2.setString(3, configuredObject.getId().toString());
                        stmt2.execute();
                    }
                    finally
                    {
                        stmt2.close();
                    }
                }
                else if(createIfNecessary)
                {
                    PreparedStatement insertStmt = conn.prepareStatement(INSERT_INTO_CONFIGURED_OBJECTS);
                    try
                    {
                        insertStmt.setString(1, configuredObject.getId().toString());
                        insertStmt.setString(2, configuredObject.getType());
                        if(configuredObject.getAttributes() == null)
                        {
                            insertStmt.setNull(3, Types.BLOB);
                        }
                        else
                        {
                            final Map<String, Object> attributes = configuredObject.getAttributes();
                            byte[] attributesAsBytes = objectMapper.writeValueAsBytes(attributes);
                            ByteArrayInputStream bis = new ByteArrayInputStream(attributesAsBytes);
                            insertStmt.setBinaryStream(3, bis, attributesAsBytes.length);
                        }
                        insertStmt.execute();
                    }
                    finally
                    {
                        insertStmt.close();
                    }
                    writeHierarchy(configuredObject, conn);
                }
            }
            finally
            {
                rs.close();
            }
        }
        catch (JsonMappingException e)
        {
            throw new StoreException("Error updating configured object " + configuredObject + " in database: " + e.getMessage(), e);
        }
        catch (JsonGenerationException e)
        {
            throw new StoreException("Error updating configured object " + configuredObject + " in database: " + e.getMessage(), e);
        }
        catch (IOException e)
        {
            throw new StoreException("Error updating configured object " + configuredObject + " in database: " + e.getMessage(), e);
        }
        finally
        {
            stmt.close();
        }
    }

    private void writeHierarchy(final ConfiguredObjectRecord configuredObject, final Connection conn) throws SQLException, StoreException
    {
        PreparedStatement insertStmt = conn.prepareStatement(INSERT_INTO_CONFIGURED_OBJECT_HIERARCHY);
        try
        {
            for(Map.Entry<String,ConfiguredObjectRecord> parentEntry : configuredObject.getParents().entrySet())
            {
                insertStmt.setString(1, configuredObject.getId().toString());
                insertStmt.setString(2, parentEntry.getKey());
                insertStmt.setString(3, parentEntry.getValue().getId().toString());

                insertStmt.execute();
            }
        }
        finally
        {
            insertStmt.close();
        }
    }

    @Override
    public void visitMessages(MessageHandler handler) throws StoreException
    {
        checkMessageStoreOpen();

        Connection conn = null;
        try
        {
            conn = newAutoCommitConnection();
            Statement stmt = conn.createStatement();
            try
            {
                ResultSet rs = stmt.executeQuery(SELECT_ALL_FROM_META_DATA);
                try
                {
                    while (rs.next())
                    {
                        long messageId = rs.getLong(1);
                        byte[] dataAsBytes = getBlobAsBytes(rs, 2);
                        ByteBuffer buf = ByteBuffer.wrap(dataAsBytes);
                        buf.position(1);
                        buf = buf.slice();
                        MessageMetaDataType<?> type = MessageMetaDataTypeRegistry.fromOrdinal(dataAsBytes[0]);
                        StorableMessageMetaData metaData = type.createMetaData(buf);
                        StoredJDBCMessage message = new StoredJDBCMessage(messageId, metaData, true);
                        if (!handler.handle(message))
                        {
                            break;
                        }
                    }
                }
                finally
                {
                    rs.close();
                }
            }
            finally
            {
                stmt.close();
            }
        }
        catch (SQLException e)
        {
            throw new StoreException("Error encountered when visiting messages", e);
        }
        finally
        {
            closeConnection(conn);
        }
    }

    @Override
    public void visitMessageInstances(MessageInstanceHandler handler) throws StoreException
    {
        checkMessageStoreOpen();

        Connection conn = null;
        try
        {
            conn = newAutoCommitConnection();
            Statement stmt = conn.createStatement();
            try
            {
                ResultSet rs = stmt.executeQuery(SELECT_FROM_QUEUE_ENTRY);
                try
                {
                    while(rs.next())
                    {
                        String id = rs.getString(1);
                        long messageId = rs.getLong(2);
                        if (!handler.handle(UUID.fromString(id), messageId))
                        {
                            break;
                        }
                    }
                }
                finally
                {
                    rs.close();
                }
            }
            finally
            {
                stmt.close();
            }
        }
        catch(SQLException e)
        {
            throw new StoreException("Error encountered when visiting message instances", e);
        }
        finally
        {
            closeConnection(conn);
        }
    }

    @Override
    public void visitDistributedTransactions(DistributedTransactionHandler handler) throws StoreException
    {
        checkMessageStoreOpen();

        Connection conn = null;
        try
        {
            conn = newAutoCommitConnection();
            List<Xid> xids = new ArrayList<Xid>();

            Statement stmt = conn.createStatement();
            try
            {
                ResultSet rs = stmt.executeQuery(SELECT_ALL_FROM_XIDS);
                try
                {
                    while(rs.next())
                    {

                        long format = rs.getLong(1);
                        byte[] globalId = rs.getBytes(2);
                        byte[] branchId = rs.getBytes(3);
                        xids.add(new Xid(format, globalId, branchId));
                    }
                }
                finally
                {
                    rs.close();
                }
            }
            finally
            {
                stmt.close();
            }



            for(Xid xid : xids)
            {
                List<RecordImpl> enqueues = new ArrayList<RecordImpl>();
                List<RecordImpl> dequeues = new ArrayList<RecordImpl>();

                PreparedStatement pstmt = conn.prepareStatement(SELECT_ALL_FROM_XID_ACTIONS);

                try
                {
                    pstmt.setLong(1, xid.getFormat());
                    pstmt.setBytes(2, xid.getGlobalId());
                    pstmt.setBytes(3, xid.getBranchId());

                    ResultSet rs = pstmt.executeQuery();
                    try
                    {
                        while(rs.next())
                        {

                            String actionType = rs.getString(1);
                            UUID queueId = UUID.fromString(rs.getString(2));
                            long messageId = rs.getLong(3);

                            RecordImpl record = new RecordImpl(queueId, messageId);
                            List<RecordImpl> records = "E".equals(actionType) ? enqueues : dequeues;
                            records.add(record);
                        }
                    }
                    finally
                    {
                        rs.close();
                    }
                }
                finally
                {
                    pstmt.close();
                }

                if (!handler.handle(xid.getFormat(), xid.getGlobalId(), xid.getBranchId(),
                                enqueues.toArray(new RecordImpl[enqueues.size()]),
                                dequeues.toArray(new RecordImpl[dequeues.size()])))
                {
                    break;
                }
            }

        }
        catch (SQLException e)
        {
            throw new StoreException("Error encountered when visiting distributed transactions", e);

        }
        finally
        {
            closeConnection(conn);
        }
    }


    protected abstract String getBlobAsString(ResultSet rs, int col) throws SQLException;

    protected abstract void storedSizeChange(int storeSizeIncrease);


    @Override
    public void onDelete()
    {
        // TODO should probably check we are closed
        try
        {
            Connection conn = newAutoCommitConnection();
            try
            {
                List<String> tables = new ArrayList<String>();
                tables.addAll(CONFIGURATION_STORE_TABLE_NAMES);
                tables.addAll(MESSAGE_STORE_TABLE_NAMES);

                for (String tableName : tables)
                {
                    Statement stmt = conn.createStatement();
                    try
                    {
                        stmt.execute("DROP TABLE " +  tableName);
                    }
                    catch(SQLException e)
                    {
                        getLogger().warn("Failed to drop table '" + tableName + "' :" + e);
                    }
                    finally
                    {
                        stmt.close();
                    }
                }
            }
            finally
            {
                conn.close();
            }
        }
        catch(SQLException e)
        {
            getLogger().error("Exception while deleting store tables", e);
        }
    }


    private static final class ConfiguredObjectRecordImpl implements ConfiguredObjectRecord
    {

        private final UUID _id;
        private final String _type;
        private final Map<String, Object> _attributes;
        private final Map<String, ConfiguredObjectRecord> _parents = new HashMap<String, ConfiguredObjectRecord>();

        private ConfiguredObjectRecordImpl(final UUID id,
                                           final String type,
                                           final Map<String, Object> attributes)
        {
            _id = id;
            _type = type;
            _attributes = Collections.unmodifiableMap(attributes);
        }

        @Override
        public UUID getId()
        {
            return _id;
        }

        @Override
        public String getType()
        {
            return _type;
        }

        private void addParent(String parentType, ConfiguredObjectRecord parent)
        {
            _parents.put(parentType, parent);
        }

        @Override
        public Map<String, Object> getAttributes()
        {
            return _attributes;
        }

        @Override
        public Map<String, ConfiguredObjectRecord> getParents()
        {
            return Collections.unmodifiableMap(_parents);
        }

        @Override
        public String toString()
        {
            return "ConfiguredObjectRecordImpl [_id=" + _id + ", _type=" + _type + ", _attributes=" + _attributes + ", _parents="
                    + _parents + "]";
        }
    }
}
