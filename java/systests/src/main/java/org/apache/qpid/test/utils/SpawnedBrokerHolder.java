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
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.apache.qpid.server.BrokerOptions;
import org.apache.qpid.server.configuration.BrokerProperties;
import org.apache.qpid.server.logging.messages.BrokerMessages;
import org.apache.qpid.util.FileUtils;
import org.apache.qpid.util.SystemUtils;

public class SpawnedBrokerHolder implements BrokerHolder
{
    private static final Logger LOGGER = Logger.getLogger(SpawnedBrokerHolder.class);
    protected static final String BROKER_READY = System.getProperty("broker.ready", BrokerMessages.READY().toString());
    private static final String BROKER_STOPPED = System.getProperty("broker.stopped", BrokerMessages.STOPPED().toString());

    private final BrokerType _type;
    private final int _port;
    private final String _name;
    private final Map<String, String> _jvmOptions;
    private final Map<String, String> _environmentSettings;
    protected BrokerCommandHelper _brokerCommandHelper;

    private  Process _process;
    private  Integer _pid;
    private Set<Integer> _portsUsedByBroker;
    private String _brokerCommand;

    public SpawnedBrokerHolder(String brokerCommandTemplate, int port, String name, Map<String, String> jvmOptions, Map<String, String> environmentSettings, BrokerType type, Set<Integer> portsUsedByBroker)
    {
        _type = type;
        _portsUsedByBroker = portsUsedByBroker;
        _port = port;
        _name = name;
        _jvmOptions = jvmOptions;
        _environmentSettings = environmentSettings;
        _brokerCommandHelper = new BrokerCommandHelper(brokerCommandTemplate);
    }


    @Override
    public void start(BrokerOptions brokerOptions) throws Exception
    {
        // Add the port to QPID_WORK to ensure unique working dirs for multi broker tests
        final String qpidWork = getQpidWork(_type, _port);

        String[] cmd = _brokerCommandHelper.getBrokerCommand(_port, brokerOptions.getConfigurationStoreLocation(), brokerOptions.getConfigurationStoreType(),
                new File(brokerOptions.getLogConfigFileLocation()));
        if (brokerOptions.isManagementMode())
        {
            String[] newCmd = new String[cmd.length + 3];
            System.arraycopy(cmd, 0, newCmd, 0, cmd.length);
            newCmd[cmd.length] = "-mm";
            newCmd[cmd.length + 1] = "-mmpass";
            newCmd[cmd.length + 2] = brokerOptions.getManagementModePassword();
            cmd = newCmd;
        }
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Map<String, String> processEnv = pb.environment();
        String qpidHome = System.getProperty(BrokerProperties.PROPERTY_QPID_HOME);
        processEnv.put(BrokerProperties.PROPERTY_QPID_HOME, qpidHome);

        //Augment Path with bin directory in QPID_HOME.
        boolean foundPath = false;
        final String pathEntry = qpidHome + File.separator + "bin";
        for(Map.Entry<String,String> entry : processEnv.entrySet())
        {
            if(entry.getKey().equalsIgnoreCase("path"))
            {
                entry.setValue(entry.getValue().concat(File.pathSeparator + pathEntry));
                foundPath = true;
            }
        }
        if(!foundPath)
        {
            processEnv.put("PATH", pathEntry);
        }
        //Add the test name to the broker run.
        // DON'T change PNAME, qpid.stop needs this value.
        processEnv.put("QPID_PNAME", "-DPNAME=QPBRKR -DTNAME=\"" + _name + "\"");
        processEnv.put("QPID_WORK", qpidWork);

        // Use the environment variable to set amqj.logging.level for the broker
        // The value used is a 'server' value in the test configuration to
        // allow a differentiation between the client and broker logging levels.
        if (System.getProperty("amqj.server.logging.level") != null)
        {
            processEnv.put("AMQJ_LOGGING_LEVEL", System.getProperty("amqj.server.logging.level"));
        }

        // Add all the environment settings the test requested
        if (!_environmentSettings.isEmpty())
        {
            for (Map.Entry<String, String> entry : _environmentSettings.entrySet())
            {
                processEnv.put(entry.getKey(), entry.getValue());
            }
        }

        String qpidOpts = "";

        // Add all the specified system properties to QPID_OPTS
        if (!_jvmOptions.isEmpty())
        {
            for (String key : _jvmOptions.keySet())
            {
                qpidOpts += " -D" + key + "=" + _jvmOptions.get(key);
            }
        }

        if (processEnv.containsKey("QPID_OPTS"))
        {
            qpidOpts = processEnv.get("QPID_OPTS") + qpidOpts;
        }
        processEnv.put("QPID_OPTS", qpidOpts);

        // cpp broker requires that the work directory is created
        createBrokerWork(qpidWork);

        _process = pb.start();

        Piper standardOutputPiper = new Piper(_process.getInputStream(),
                BROKER_READY,
                BROKER_STOPPED,
                "STD", "BROKER-" + _port);

        standardOutputPiper.start();

        new Piper(_process.getErrorStream(), null, null, "ERROR", "BROKER-" + _port).start();

        StringBuilder cmdLine = new StringBuilder(cmd[0]);
        for(int i = 1; i< cmd.length; i++)
        {
            cmdLine.append(' ');
            cmdLine.append(cmd[i]);
        }

        _brokerCommand = cmdLine.toString();
        _pid = retrieveUnixPidIfPossible();

        if (!standardOutputPiper.await(30, TimeUnit.SECONDS))
        {
            LOGGER.info("broker failed to become ready (" + standardOutputPiper.getReady() + "):" + standardOutputPiper.getStopLine());
            String threadDump = dumpThreads();
            if (!threadDump.isEmpty())
            {
                LOGGER.info("the result of a try to capture thread dump:" + threadDump);
            }
            //Ensure broker has stopped
            _process.destroy();
            cleanBrokerWork(qpidWork);
            throw new RuntimeException("broker failed to become ready:"
                    + standardOutputPiper.getStopLine());
        }

        try
        {
            //test that the broker is still running and hasn't exited unexpectedly
            int exit = _process.exitValue();
            LOGGER.info("broker aborted: " + exit);
            cleanBrokerWork(qpidWork);
            throw new RuntimeException("broker aborted: " + exit);
        }
        catch (IllegalThreadStateException e)
        {
            // this is expect if the broker started successfully
        }

    }

    protected void createBrokerWork(final String qpidWork)
    {
        if (qpidWork != null)
        {
            final File dir = new File(qpidWork);
            dir.mkdirs();
            if (!dir.isDirectory())
            {
                throw new RuntimeException("Failed to created Qpid work directory : " + qpidWork);
            }
        }
    }

    private String getQpidWork(BrokerType broker, int port)
    {
        if (!broker.equals(BrokerType.EXTERNAL))
        {
            return System.getProperty(BrokerProperties.PROPERTY_QPID_WORK) + File.separator + port;
        }

        return System.getProperty(BrokerProperties.PROPERTY_QPID_WORK);
    }

    private void cleanBrokerWork(final String qpidWork)
    {
        if (qpidWork != null)
        {
            LOGGER.info("Cleaning broker work dir: " + qpidWork);

            File file = new File(qpidWork);
            if (file.exists())
            {
                final boolean success = FileUtils.delete(file, true);
                if(!success)
                {
                    throw new RuntimeException("Failed to recursively delete beneath : " + file);
                }
            }
        }
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
