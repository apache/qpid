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
package org.apache.qpid.server.store.derby;


import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.store.*;

public abstract class AbstractDerbyMessageStore extends AbstractJDBCMessageStore
{
    private final AtomicBoolean _messageStoreOpen = new AtomicBoolean(false);

    private long _persistentSizeLowThreshold;
    private long _persistentSizeHighThreshold;
    private long _totalStoreSize;
    private boolean _limitBusted;

    private ConfiguredObject<?> _parent;

    @Override
    public final void openMessageStore(final ConfiguredObject<?> parent)
    {
        if (_messageStoreOpen.compareAndSet(false, true))
        {
            _parent = parent;

            DerbyUtils.loadDerbyDriver();

            doOpen(parent);

            final SizeMonitorSettings sizeMonitorSettings = (SizeMonitorSettings) parent;
            _persistentSizeHighThreshold = sizeMonitorSettings.getStoreOverfullSize();
            _persistentSizeLowThreshold = sizeMonitorSettings.getStoreUnderfullSize();

            if (_persistentSizeLowThreshold > _persistentSizeHighThreshold || _persistentSizeLowThreshold < 0l)
            {
                _persistentSizeLowThreshold = _persistentSizeHighThreshold;
            }

            createOrOpenMessageStoreDatabase();
            setInitialSize();
            setMaximumMessageId();
        }
    }

    protected abstract void doOpen(final ConfiguredObject<?> parent);

    @Override
    public final void upgradeStoreStructure() throws StoreException
    {
        checkMessageStoreOpen();

        upgrade(_parent);
    }

    @Override
    public final void closeMessageStore()
    {
        if (_messageStoreOpen.compareAndSet(true,  false))
        {
            doClose();
        }
    }

    protected abstract void doClose();

    @Override
    protected boolean isMessageStoreOpen()
    {
        return _messageStoreOpen.get();
    }

    @Override
    protected void checkMessageStoreOpen()
    {
        if (!_messageStoreOpen.get())
        {
            throw new IllegalStateException("Message store is not open");
        }
    }

    @Override
    protected String getSqlBlobType()
    {
        return "blob";
    }

    @Override
    protected String getSqlVarBinaryType(int size)
    {
        return "varchar("+size+") for bit data";
    }

    @Override
    protected String getSqlBigIntType()
    {
        return "bigint";
    }

    @Override
    protected byte[] getBlobAsBytes(ResultSet rs, int col) throws SQLException
    {
        return DerbyUtils.getBlobAsBytes(rs, col);
    }

    @Override
    protected boolean tableExists(final String tableName, final Connection conn) throws SQLException
    {
        return DerbyUtils.tableExists(tableName, conn);
    }

    @Override
    protected void storedSizeChange(final int delta)
    {
        if(getPersistentSizeHighThreshold() > 0)
        {
            synchronized(this)
            {
                // the delta supplied is an approximation of a store size change. we don;t want to check the statistic every
                // time, so we do so only when there's been enough change that it is worth looking again. We do this by
                // assuming the total size will change by less than twice the amount of the message data change.
                long newSize = _totalStoreSize += 3*delta;

                Connection conn = null;
                try
                {

                    if(!_limitBusted &&  newSize > getPersistentSizeHighThreshold())
                    {
                        conn = newAutoCommitConnection();
                        _totalStoreSize = getSizeOnDisk(conn);
                        if(_totalStoreSize > getPersistentSizeHighThreshold())
                        {
                            _limitBusted = true;
                            _eventManager.notifyEvent(Event.PERSISTENT_MESSAGE_SIZE_OVERFULL);
                        }
                    }
                    else if(_limitBusted && newSize < getPersistentSizeLowThreshold())
                    {
                        long oldSize = _totalStoreSize;
                        conn = newAutoCommitConnection();
                        _totalStoreSize = getSizeOnDisk(conn);
                        if(oldSize <= _totalStoreSize)
                        {

                            reduceSizeOnDisk(conn);

                            _totalStoreSize = getSizeOnDisk(conn);
                        }

                        if(_totalStoreSize < getPersistentSizeLowThreshold())
                        {
                            _limitBusted = false;
                            _eventManager.notifyEvent(Event.PERSISTENT_MESSAGE_SIZE_UNDERFULL);
                        }


                    }
                }
                catch (SQLException e)
                {
                    JdbcUtils.closeConnection(conn, getLogger());
                    throw new StoreException("Exception while processing store size change", e);
                }
            }
        }
    }

    private void setInitialSize()
    {
        Connection conn = null;
        try
        {
            conn = newAutoCommitConnection();
            _totalStoreSize = getSizeOnDisk(conn);
        }
        catch (SQLException e)
        {
            getLogger().error("Unable to set initial store size", e);
        }
        finally
        {
            JdbcUtils.closeConnection(conn, getLogger());
        }
    }

    private long getSizeOnDisk(Connection conn)
    {
        PreparedStatement stmt = null;
        try
        {
            String sizeQuery = "SELECT SUM(T2.NUMALLOCATEDPAGES * T2.PAGESIZE) TOTALSIZE" +
                    "    FROM " +
                    "        SYS.SYSTABLES systabs," +
                    "        TABLE (SYSCS_DIAG.SPACE_TABLE(systabs.tablename)) AS T2" +
                    "    WHERE systabs.tabletype = 'T'";

            stmt = conn.prepareStatement(sizeQuery);

            ResultSet rs = null;
            long size = 0l;

            try
            {
                rs = stmt.executeQuery();
                while(rs.next())
                {
                    size = rs.getLong(1);
                }
            }
            finally
            {
                if(rs != null)
                {
                    rs.close();
                }
            }

            return size;

        }
        catch (SQLException e)
        {
            throw new StoreException("Error establishing on disk size", e);
        }
        finally
        {
            JdbcUtils.closePreparedStatement(stmt, getLogger());
        }
    }

    private void reduceSizeOnDisk(Connection conn)
    {
        CallableStatement cs = null;
        PreparedStatement stmt = null;
        try
        {
            String tableQuery =
                    "SELECT S.SCHEMANAME, T.TABLENAME FROM SYS.SYSSCHEMAS S, SYS.SYSTABLES T WHERE S.SCHEMAID = T.SCHEMAID AND T.TABLETYPE='T'";
            stmt = conn.prepareStatement(tableQuery);
            ResultSet rs = null;

            List<String> schemas = new ArrayList<String>();
            List<String> tables = new ArrayList<String>();

            try
            {
                rs = stmt.executeQuery();
                while(rs.next())
                {
                    schemas.add(rs.getString(1));
                    tables.add(rs.getString(2));
                }
            }
            finally
            {
                if(rs != null)
                {
                    rs.close();
                }
            }


            cs = conn.prepareCall
                    ("CALL SYSCS_UTIL.SYSCS_COMPRESS_TABLE(?, ?, ?)");

            for(int i = 0; i < schemas.size(); i++)
            {
                cs.setString(1, schemas.get(i));
                cs.setString(2, tables.get(i));
                cs.setShort(3, (short) 0);
                cs.execute();
            }
        }
        catch (SQLException e)
        {
            throw new StoreException("Error reducing on disk size", e);
        }
        finally
        {
            JdbcUtils.closePreparedStatement(stmt, getLogger());
            JdbcUtils.closePreparedStatement(cs, getLogger());
        }
    }

    private long getPersistentSizeLowThreshold()
    {
        return _persistentSizeLowThreshold;
    }

    private long getPersistentSizeHighThreshold()
    {
        return _persistentSizeHighThreshold;
    }

}
