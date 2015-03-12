/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.    
 *
 * 
 */
package org.apache.qpid.server.jmx.mbeans;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.management.JMException;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;
import javax.management.openmbean.TabularType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.management.common.mbeans.LoggingManagement;
import org.apache.qpid.management.common.mbeans.annotations.MBeanDescription;
import org.apache.qpid.server.jmx.AMQManagedObject;
import org.apache.qpid.server.jmx.ManagedObject;
import org.apache.qpid.server.jmx.ManagedObjectRegistry;
import org.apache.qpid.server.logging.log4j.LoggingFacadeException;
import org.apache.qpid.server.logging.log4j.LoggingManagementFacade;
import org.apache.qpid.server.util.ConnectionScopedRuntimeException;


/** MBean class for LoggingManagement. It implements all the management features exposed for managing logging. */
@MBeanDescription("Logging Management Interface")
public class LoggingManagementMBean extends AMQManagedObject implements LoggingManagement
{
    public static final String INHERITED_PSUEDO_LOG_LEVEL = "INHERITED";
    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingManagementMBean.class);
    private static final TabularType LOGGER_LEVEL_TABULAR_TYE;
    private static final CompositeType LOGGER_LEVEL_COMPOSITE_TYPE;

    private final LoggingManagementFacade _loggingManagementFacade;
    private final String[] _allAvailableLogLevels;

    static
    {
        try
        {
            OpenType[] loggerLevelItemTypes = new OpenType[]{SimpleType.STRING, SimpleType.STRING};

            LOGGER_LEVEL_COMPOSITE_TYPE = new CompositeType("LoggerLevelList", "Logger Level Data",
                                                         COMPOSITE_ITEM_NAMES.toArray(new String[COMPOSITE_ITEM_NAMES.size()]),
                                                         COMPOSITE_ITEM_DESCRIPTIONS.toArray(new String[COMPOSITE_ITEM_DESCRIPTIONS.size()]),
                                                         loggerLevelItemTypes);

            LOGGER_LEVEL_TABULAR_TYE = new TabularType("LoggerLevel", "List of loggers with levels",
                                                       LOGGER_LEVEL_COMPOSITE_TYPE,
                                                       TABULAR_UNIQUE_INDEX.toArray(new String[TABULAR_UNIQUE_INDEX.size()]));
        }
        catch (OpenDataException e)
        {
            throw new ExceptionInInitializerError(e);
        }
    }

    public LoggingManagementMBean(LoggingManagementFacade loggingManagementFacade, ManagedObjectRegistry registry) throws JMException
    {
        super(LoggingManagement.class, LoggingManagement.TYPE, registry);
        register();
        _loggingManagementFacade = loggingManagementFacade;
        _allAvailableLogLevels = buildAllAvailableLoggerLevelsWithInheritedPsuedoLogLevel(_loggingManagementFacade);
    }

    @Override
    public String getObjectInstanceName()
    {
        return LoggingManagement.TYPE;
    }

    @Override
    public ManagedObject getParentObject()
    {
        return null;
    }

    @Override
    public Integer getLog4jLogWatchInterval()
    {
        return _loggingManagementFacade.getLog4jLogWatchInterval();
    }
    
    @Override
    public String[] getAvailableLoggerLevels()
    {
        return _allAvailableLogLevels;
    }

    @Override
    public TabularData viewEffectiveRuntimeLoggerLevels()
    {
        Map<String, String> levels = _loggingManagementFacade.retrieveRuntimeLoggersLevels();
        return createTabularDataFromLevelsMap(levels);
    }

    @Override
    public String getRuntimeRootLoggerLevel()
    {
        return _loggingManagementFacade.retrieveRuntimeRootLoggerLevel();
    }

    @Override
    public boolean setRuntimeRootLoggerLevel(String level)
    {
        try
        {
            validateLevelNotAllowingInherited(level);
        }
        catch (IllegalArgumentException iae)
        {
            LOGGER.warn(level + " is not a known level");
            return false;
        }

        _loggingManagementFacade.setRuntimeRootLoggerLevel(level);
        return true;
    }

    @Override
    public boolean setRuntimeLoggerLevel(String logger, String level)
    {
        String validatedLevel;
        try
        {
            validatedLevel = getValidateLevelAllowingInherited(level);
        }
        catch (IllegalArgumentException iae)
        {
            LOGGER.warn(level + " is not a known level");
            return false;
        }

        try
        {
            _loggingManagementFacade.setRuntimeLoggerLevel(logger, validatedLevel);
        }
        catch (LoggingFacadeException e)
        {
            LOGGER.error("Cannot set runtime logging level", e);
           return false;
        }
        return true;
    }

    @Override
    public TabularData viewConfigFileLoggerLevels()
    {
        Map<String,String> levels;
        try
        {
            levels = _loggingManagementFacade.retrieveConfigFileLoggersLevels();
        }
        catch (LoggingFacadeException e)
        {
            LOGGER.error("Cannot determine logging levels", e);
           return null;
        }

        return createTabularDataFromLevelsMap(levels);
    }

    @Override
    public String getConfigFileRootLoggerLevel()throws IOException
    {
        try
        {
            return _loggingManagementFacade.retrieveConfigFileRootLoggerLevel().toUpperCase();
        }
        catch (LoggingFacadeException e)
        {
            LOGGER.warn("The log4j configuration get config request was aborted: ", e);
            throw new IOException("The log4j configuration get config request was aborted: " + e.getMessage());
        }
    }

    @Override
    public boolean setConfigFileLoggerLevel(String logger, String level)
    {
        String validatedLevel;
        try
        {
            validatedLevel = getValidateLevelAllowingInherited(level);
        }
        catch (IllegalArgumentException iae)
        {
            LOGGER.warn(level + " is not a known level");
            return false;
        }

        try
        {
            _loggingManagementFacade.setConfigFileLoggerLevel(logger, validatedLevel);
        }
        catch (LoggingFacadeException e)
        {
            LOGGER.warn("The log4j configuration set config request was aborted: ", e);
            return false;
        }
        return true;
    }
    
    @Override
    public boolean setConfigFileRootLoggerLevel(String level)
    {
        try
        {
            validateLevelNotAllowingInherited(level);
        }
        catch (IllegalArgumentException iae)
        {
            LOGGER.warn(level + " is not a known level");
            return false;
        }

        try
        {
            _loggingManagementFacade.setConfigFileRootLoggerLevel(level);
            return true;
        }
        catch (LoggingFacadeException e)
        {
            LOGGER.warn("The log4j configuration set config request was aborted: ", e);
            return false;
        }
    }

    @Override
    public void reloadConfigFile() throws IOException
    {
        try
        {

            _loggingManagementFacade.reload();
        }
        catch (LoggingFacadeException e)
        {
            LOGGER.warn("The log4j configuration reload request was aborted: ", e);
            throw new IOException("The log4j configuration reload request was aborted: " + e.getMessage());
        }
    }

    private String getValidateLevelAllowingInherited(String level)
    {
        if(level == null
            || "null".equalsIgnoreCase(level)
            || INHERITED_PSUEDO_LOG_LEVEL.equalsIgnoreCase(level))
        {
            //the string "null" or "inherited" signals to inherit from a parent logger,
            //using a null Level reference for the logger.
            return null;
        }

        validateLevelNotAllowingInherited(level);
        return level;
    }

    private void validateLevelNotAllowingInherited(String level)
    {
        final List<String> availableLoggerLevels = _loggingManagementFacade.getAvailableLoggerLevels();
        if (level == null || !availableLoggerLevels.contains(level.toUpperCase()))
        {
            throw new IllegalArgumentException(level + " not known");
        }
    }

    private TabularData createTabularDataFromLevelsMap(Map<String, String> levels)
    {
        TabularData loggerLevelList = new TabularDataSupport(LOGGER_LEVEL_TABULAR_TYE);
        for (Map.Entry<String,String> entry : levels.entrySet())
        {
            String loggerName = entry.getKey();
            String level = entry.getValue();

            CompositeData loggerData = createRow(loggerName, level);
            loggerLevelList.put(loggerData);
        }
        return loggerLevelList;
    }

    private CompositeData createRow(String loggerName, String level)
    {
        Object[] itemData = {loggerName, level.toUpperCase()};
        try
        {
            CompositeData loggerData = new CompositeDataSupport(LOGGER_LEVEL_COMPOSITE_TYPE,
                    COMPOSITE_ITEM_NAMES.toArray(new String[COMPOSITE_ITEM_NAMES.size()]), itemData);
            return loggerData;
        }
        catch (OpenDataException ode)
        {
            // Should not happen
            throw new ConnectionScopedRuntimeException(ode);
        }
    }

    private String[] buildAllAvailableLoggerLevelsWithInheritedPsuedoLogLevel(LoggingManagementFacade loggingManagementFacade)
    {
        List<String> levels = loggingManagementFacade.getAvailableLoggerLevels();
        List<String> mbeanLevels = new ArrayList<String>(levels);
        mbeanLevels.add(INHERITED_PSUEDO_LOG_LEVEL);

        return mbeanLevels.toArray(new String[mbeanLevels.size()]);
    }
}
