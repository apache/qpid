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

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.apache.qpid.util.SystemUtils;

public class SpawnedBrokerHolder implements BrokerHolder
{
    private static final Logger LOGGER = Logger.getLogger(SpawnedBrokerHolder.class);

    private final Process _process;
    private final Integer _pid;
    private final String _workingDirectory;
    private Set<Integer> _portsUsedByBroker;
    private final String _brokerCommand;

    public SpawnedBrokerHolder(final Process process, final String workingDirectory, Set<Integer> portsUsedByBroker,
                               String brokerCmd)
    {
        if(process == null)
        {
            throw new IllegalArgumentException("Process must not be null");
        }

        _process = process;
        _pid = retrieveUnixPidIfPossible();
        _workingDirectory = workingDirectory;
        _portsUsedByBroker = portsUsedByBroker;
        _brokerCommand = brokerCmd;
    }

    @Override
    public String getWorkingDirectory()
    {
        return _workingDirectory;
    }

    public void shutdown()
    {
        if(SystemUtils.isWindows())
        {
            doWindowsKill();
        }

        LOGGER.info("Destroying broker process");
        _process.destroy();

        reapChildProcess();

        waitUntilPortsAreFree();
    }

    private void doWindowsKill()
    {
        try
        {
            Process p = Runtime.getRuntime().exec(new String[] {"wmic", "process", "list"});
            try(BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream())))
            {
                String line;
                String headers = reader.readLine();
                int processIdOffset = headers.indexOf(" ProcessId") + 1;
                int parentProcessIdOffset = headers.indexOf(" ParentProcessId") + 1;
                String parentProcess = null;
                Map<String, List<String>> parentProcessMap = new HashMap<String, List<String>>();

                while ((line = reader.readLine()) != null)
                {
                    if (line.length() > processIdOffset)
                    {
                        String processIdStr = line.substring(processIdOffset);
                        processIdStr = processIdStr.substring(0, processIdStr.indexOf(' '));
                        processIdStr = processIdStr.trim();

                        String parentProcessIdStr = line.substring(parentProcessIdOffset);
                        parentProcessIdStr = parentProcessIdStr.substring(0, parentProcessIdStr.indexOf(' '));
                        parentProcessIdStr = parentProcessIdStr.trim();
                        if (parentProcessIdStr.length() > 0 && (parentProcess == null || parentProcess.equals(
                                parentProcessIdStr)))
                        {
                            List<String> children = parentProcessMap.get(parentProcessIdStr);
                            if (children == null)
                            {
                                children = new ArrayList<String>();
                                parentProcessMap.put(parentProcessIdStr, children);
                            }
                            children.add(processIdStr);
                        }
                        if (line.substring(0, _brokerCommand.length() + 7)
                                .toLowerCase()
                                .contains(_brokerCommand.toLowerCase()))
                        {
                            parentProcess = processIdStr;
                        }

                    }
                    if (parentProcess != null)
                    {
                        List<String> children = parentProcessMap.get(parentProcess);
                        if (children != null)
                        {
                            for (String child : children)
                            {
                                p = Runtime.getRuntime().exec(new String[]{"taskkill", "/PID", child, "/T", "/F"});
                                consumeAllOutput(p);
                            }
                        }
                        p = Runtime.getRuntime().exec(new String[]{"taskkill", "/PID", parentProcess, "/T", "/F"});
                        consumeAllOutput(p);
                    }

                }
            }
        }
        catch (IOException e)
        {
            LOGGER.error("Error whilst killing process " + _brokerCommand, e);
        }
    }

    private static void consumeAllOutput(Process p) throws IOException
    {
        try(InputStreamReader inputStreamReader = new InputStreamReader(p.getInputStream()))
        {
            try (BufferedReader reader = new BufferedReader(inputStreamReader))
            {
                while (reader.readLine() != null)
                {
                }
            }
        }
    }

    @Override
    public void kill()
    {
        if (_pid == null)
        {
            if(SystemUtils.isWindows())
            {
                doWindowsKill();
            }
            LOGGER.info("Destroying broker process (no PID)");
            _process.destroy();
        }
        else
        {
            LOGGER.info("Killing broker process with PID " + _pid);
            sendSigkillForImmediateShutdown(_pid);
        }

        reapChildProcess();

        waitUntilPortsAreFree();
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
        if(!SystemUtils.isWindows())
        {
            try
            {
                Integer pid = ReflectionUtils.getDeclaredField(_process, "pid");
                LOGGER.info("PID " + pid);
                return pid;
            }
            catch (ReflectionUtilsException e)
            {
                LOGGER.warn("Could not get pid for process, Broker process shutdown will be graceful");
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

    private void waitUntilPortsAreFree()
    {
        new PortHelper().waitUntilPortsAreFree(_portsUsedByBroker);
    }

    @Override
    public String dumpThreads()
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try
        {
            Process process = Runtime.getRuntime().exec("jstack " + _pid);
            InputStream is = process.getInputStream();
            byte[] buffer = new byte[1024];
            int length = -1;
            while ((length = is.read(buffer)) != -1)
            {
                baos.write(buffer, 0, length);
            }
         }
        catch (Exception e)
        {
            LOGGER.error("Error whilst collecting thread dump for " + _pid, e);
        }
        return new String(baos.toByteArray());
    }

    @Override
    public String toString()
    {
        return "SpawnedBrokerHolder [_pid=" + _pid + ", _portsUsedByBroker="
                + _portsUsedByBroker + "]";
    }
}
