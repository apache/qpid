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
import java.sql.Connection;
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

import org.apache.log4j.Logger;
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

import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.store.handler.ConfiguredObjectRecordHandler;

public abstract class AbstractJDBCConfigurationStore implements MessageStoreProvider, DurableConfigurationStore
{
    private static final String CONFIGURATION_VERSION_TABLE_NAME = "QPID_CONFIG_VERSION";

    private static final String CONFIGURED_OBJECTS_TABLE_NAME = "QPID_CONFIGURED_OBJECTS";
    private static final String CONFIGURED_OBJECT_HIERARCHY_TABLE_NAME = "QPID_CONFIGURED_OBJECT_HIERARCHY";

    private static final int DEFAULT_CONFIG_VERSION = 0;

    public static final Set<String> CONFIGURATION_STORE_TABLE_NAMES = new HashSet<String>(Arrays.asList(CONFIGURED_OBJECTS_TABLE_NAME, CONFIGURED_OBJECT_HIERARCHY_TABLE_NAME));

    private static final String SELECT_FROM_CONFIG_VERSION = "SELECT version FROM " + CONFIGURATION_VERSION_TABLE_NAME;
    private static final String DROP_CONFIG_VERSION_TABLE = "DROP TABLE "+ CONFIGURATION_VERSION_TABLE_NAME;

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
            boolean shouldContinue = handler.handle(record);
            if (!shouldContinue)
            {
                break;
            }
        }
    }

    protected abstract void checkConfigurationStoreOpen();

    protected void upgradeIfNecessary(ConfiguredObject<?> parent) throws StoreException
    {
        Connection connection = null;
        try
        {
            connection = newAutoCommitConnection();

            boolean tableExists = tableExists(CONFIGURATION_VERSION_TABLE_NAME, connection);
            if(tableExists)
            {
                int configVersion = getConfigVersion(connection);
                if (getLogger().isDebugEnabled())
                {
                    getLogger().debug("Upgrader read existing config version " + configVersion);
                }

                switch(configVersion)
                {

                    case 7:
                        upgradeFromV7(parent);
                        break;
                    default:
                        throw new UnsupportedOperationException("Cannot upgrade from configuration version : "
                                                                + configVersion);
                }
            }
        }
        catch (SQLException se)
        {
            throw new StoreException("Failed to upgrade database", se);
        }
        finally
        {
            JdbcUtils.closeConnection(connection, getLogger());
        }

    }

    private void upgradeFromV7(ConfiguredObject<?> parent) throws SQLException
    {
        @SuppressWarnings("serial")
        Map<String, String> defaultExchanges = new HashMap<String, String>()
        {{
            put("amq.direct", "direct");
            put("amq.topic", "topic");
            put("amq.fanout", "fanout");
            put("amq.match", "headers");
        }};

        Connection connection = newConnection();
        try
        {
            String virtualHostName = parent.getName();
            UUID virtualHostId = UUIDGenerator.generateVhostUUID(virtualHostName);

            String stringifiedConfigVersion = "0." + DEFAULT_CONFIG_VERSION;

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
            virtualHostAttributes.put("name", virtualHostName);

            ConfiguredObjectRecord virtualHostRecord = new ConfiguredObjectRecordImpl(virtualHostId, "VirtualHost", virtualHostAttributes);
            insertConfiguredObject(virtualHostRecord, connection);

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
                try (ResultSet rs = stmt.executeQuery())
                {
                    while (rs.next())
                    {
                        UUID id = UUID.fromString(rs.getString(1));
                        String objectType = rs.getString(2);
                        if ("VirtualHost".equals(objectType))
                        {
                            continue;
                        }
                        Map<String, Object> attributes = objectMapper.readValue(getBlobAsString(rs, 3), Map.class);

                        if (objectType.endsWith("Binding"))
                        {
                            bindingsToUpdate.put(id, attributes);
                        }
                        else
                        {
                            if (objectType.equals("Exchange"))
                            {
                                defaultExchanges.remove((String) attributes.get("name"));
                            }
                            others.add(id);
                        }
                    }
                }
                catch (IOException e)
                {
                    throw new StoreException("Error recovering persistent state: " + e.getMessage(), e);
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

            for (Map.Entry<String, String> defaultExchangeEntry : defaultExchanges.entrySet())
            {
                UUID id = UUIDGenerator.generateExchangeUUID(defaultExchangeEntry.getKey(), virtualHostName);
                Map<String, Object> exchangeAttributes = new HashMap<String, Object>();
                exchangeAttributes.put("name", defaultExchangeEntry.getKey());
                exchangeAttributes.put("type", defaultExchangeEntry.getValue());
                exchangeAttributes.put("lifetimePolicy", "PERMANENT");
                Map<String, UUID> parents = Collections.singletonMap("VirtualHost", virtualHostRecord.getId());
                ConfiguredObjectRecord exchangeRecord = new org.apache.qpid.server.store.ConfiguredObjectRecordImpl(id, "Exchange", exchangeAttributes, parents);
                insertConfiguredObject(exchangeRecord, connection);
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
            catch (IOException e)
            {
                throw new StoreException("Error recovering persistent state: " + e.getMessage(), e);
            }
            finally
            {
                stmt.close();
            }

            if (tableExists)
            {
                dropConfigVersionTable(connection);
            }

            connection.commit();
        }
        catch(SQLException e)
        {
            try
            {
                connection.rollback();
            }
            catch(SQLException re)
            {
            }
            throw e;
        }
        finally
        {
            connection.close();
        }
    }

    protected abstract Logger getLogger();

    protected abstract String getSqlBlobType();

    protected abstract String getSqlVarBinaryType(int size);

    protected abstract String getSqlBigIntType();


    protected void createOrOpenConfigurationStoreDatabase(final boolean clear) throws StoreException
    {
        Connection conn = null;
        try
        {
            conn = newAutoCommitConnection();

            createConfiguredObjectsTable(conn, clear);
            createConfiguredObjectHierarchyTable(conn, clear);
        }
        catch (SQLException e)
        {
            throw new StoreException("Unable to open configuration tables", e);
        }
        finally
        {
            JdbcUtils.closeConnection(conn, getLogger());
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

    private void createConfiguredObjectsTable(final Connection conn, final boolean clear) throws SQLException
    {
        if(!tableExists(CONFIGURED_OBJECTS_TABLE_NAME, conn))
        {
            try (Statement stmt = conn.createStatement())
            {
                stmt.execute("CREATE TABLE "
                             + CONFIGURED_OBJECTS_TABLE_NAME
                             + " ( id VARCHAR(36) not null, object_type varchar(255), attributes "
                             + getSqlBlobType()
                             + ",  PRIMARY KEY (id))");
            }
        }
        else if(clear)
        {
            try (Statement stmt = conn.createStatement())
            {
                stmt.execute("DELETE FROM " + CONFIGURED_OBJECTS_TABLE_NAME);
            }
        }
    }

    private void createConfiguredObjectHierarchyTable(final Connection conn, final boolean clear) throws SQLException
    {
        if(!tableExists(CONFIGURED_OBJECT_HIERARCHY_TABLE_NAME, conn))
        {
            try (Statement stmt = conn.createStatement())
            {
                stmt.execute("CREATE TABLE " + CONFIGURED_OBJECT_HIERARCHY_TABLE_NAME
                             + " ( child_id VARCHAR(36) not null, parent_type varchar(255), parent_id VARCHAR(36),  PRIMARY KEY (child_id, parent_type))");
            }
        }
        else if(clear)
        {
            try (Statement stmt = conn.createStatement())
            {
                stmt.execute("DELETE FROM " + CONFIGURED_OBJECT_HIERARCHY_TABLE_NAME);
            }
        }
    }

    protected boolean tableExists(final String tableName, final Connection conn) throws SQLException
    {
        return JdbcUtils.tableExists(tableName, conn);
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
            throw new StoreException("Error creating ConfiguredObject " + object, e);
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

    protected boolean hasNoConfigurationEntries()
    {
        ConfiguredObjectRecordPresenceDetector recordPresenceDetector = new ConfiguredObjectRecordPresenceDetector();
        visitConfiguredObjectRecords(recordPresenceDetector);

        return !recordPresenceDetector.isRecordsPresent();
    }

    protected abstract Connection getConnection() throws SQLException;

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

    @Override
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
            for(Map.Entry<String,UUID> parentEntry : configuredObject.getParents().entrySet())
            {
                insertStmt.setString(1, configuredObject.getId().toString());
                insertStmt.setString(2, parentEntry.getKey());
                insertStmt.setString(3, parentEntry.getValue().toString());

                insertStmt.execute();
            }
        }
        finally
        {
            insertStmt.close();
        }
    }

    protected abstract String getBlobAsString(ResultSet rs, int col) throws SQLException;

    @Override
    public void onDelete(ConfiguredObject<?> parent)
    {
        // TODO should probably check we are closed
        try
        {
            Connection conn = newAutoCommitConnection();
            try
            {
                List<String> tables = new ArrayList<String>();
                tables.addAll(CONFIGURATION_STORE_TABLE_NAMES);

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
        private final Map<String, UUID> _parents = new HashMap<>();

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
            _parents.put(parentType, parent.getId());
        }

        @Override
        public Map<String, Object> getAttributes()
        {
            return _attributes;
        }

        @Override
        public Map<String, UUID> getParents()
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

    private static class ConfiguredObjectRecordPresenceDetector implements ConfiguredObjectRecordHandler
    {
        private boolean _recordsPresent;

        @Override
        public void begin()
        {

        }

        @Override
        public boolean handle(final ConfiguredObjectRecord record)
        {
            _recordsPresent = true;
            return false;
        }

        @Override
        public void end()
        {

        }

        public boolean isRecordsPresent()
        {
            return _recordsPresent;
        }
    }
}
