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
package org.apache.qpid.test.utils;

import java.io.IOException;

import org.apache.log4j.Logger;

public class SpawnedBrokerHolder implements BrokerHolder
{
    private static final Logger LOGGER = Logger.getLogger(SpawnedBrokerHolder.class);

    private final boolean _isWindows = String.valueOf(System.getProperty("os.name")).toLowerCase().contains("windows");
    private final Process _process;
    private final Integer _pid;
    private final String _workingDirectory;

    public SpawnedBrokerHolder(final Process process, final String workingDirectory)
    {
        if(process == null)
        {
            throw new IllegalArgumentException("Process must not be null");
        }

        _process = process;
        _pid = retrieveUnixPidIfPossible();
        _workingDirectory = workingDirectory;
    }

    @Override
    public String getWorkingDirectory()
    {
        return _workingDirectory;
    }

    public void shutdown()
    {
        LOGGER.info("Destroying broker process");
        _process.destroy();

        reapChildProcess();
    }

    @Override
    public void kill()
    {
        if (_pid == null)
        {
            LOGGER.info("Destroying broker process");
            _process.destroy();
        }
        else
        {
            LOGGER.info("Killing broker process with PID " + _pid);
            sendSigkillForImmediateShutdown(_pid);
        }

        reapChildProcess();
    }

    private void sendSigkillForImmediateShutdown(Integer pid)
    {
        boolean killSuccessful = false;
        try
        {
            final Process killProcess = Runtime.getRuntime().exec("kill -KILL " + pid);
            killProcess.waitFor();
            killSuccessful = killProcess.exitValue() == 0;
        }
        catch (IOException e)
        {
            LOGGER.error("Error whilst killing process " + _pid, e);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
        finally
        {
            if (!killSuccessful)
            {
                _process.destroy();
            }
        }
    }

    private Integer retrieveUnixPidIfPossible()
    {
        if(!_isWindows)
        {
            try
            {
                Integer pid = ReflectionUtils.getDeclaredField(_process, "pid");
                LOGGER.info("PID " + pid);
                return pid;
            }
            catch (ReflectionUtilsException e)
            {
                LOGGER.warn("Could not get pid for process, Broker process shutdown will be ungraceful");
            }
        }
        return null;
    }

    private void reapChildProcess()
    {
        try
        {
            _process.waitFor();
            LOGGER.info("broker exited: " + _process.exitValue());
        }
        catch (InterruptedException e)
        {
            LOGGER.error("Interrupted whilst waiting for process shutdown");
            Thread.currentThread().interrupt();
        }
        finally
        {
            try
            {
                _process.getInputStream().close();
                _process.getErrorStream().close();
                _process.getOutputStream().close();
            }
            catch (IOException e)
            {
            }
        }
    }

}
