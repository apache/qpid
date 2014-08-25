/*
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
 */
package org.apache.qpid.scripts;

import java.io.File;
import java.util.concurrent.TimeUnit;

import org.apache.qpid.test.utils.Piper;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.util.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QpidPasswdTest extends QpidTestCase
{
    private static final Logger LOGGER = LoggerFactory.getLogger(QpidPasswdTest.class);

    private static final String PASSWD_SCRIPT = "qpid-passwd";
    private static final String EXPECTED_OUTPUT = "user1:rL0Y20zC+Fzt72VPzMSk2A==";

    public void testRunScript() throws Exception
    {
        if(SystemUtils.isWindows())
        {
            return;
        }
        Process process = null;
        try
        {
            String scriptPath =
                    QpidTestCase.QPID_HOME + File.separatorChar
                    + "bin" + File.separatorChar
                    + PASSWD_SCRIPT;

            LOGGER.info("About to run script: " + scriptPath);

            ProcessBuilder pb = new ProcessBuilder(scriptPath, "user1", "foo");
            pb.redirectErrorStream(true);
            process = pb.start();

            Piper piper = new Piper(process.getInputStream(), System.out, EXPECTED_OUTPUT, EXPECTED_OUTPUT);
            piper.start();

            boolean finishedSuccessfully = piper.await(2, TimeUnit.SECONDS);
            assertTrue(
                    "Script should have completed with expected output " + EXPECTED_OUTPUT + ". Check standard output for actual output.",
                    finishedSuccessfully);
            process.waitFor();
            piper.join();

            assertEquals("Unexpected exit value from backup script", 0, process.exitValue());
        }
        finally
        {
            if (process != null)
            {
                process.getErrorStream().close();
                process.getInputStream().close();
                process.getOutputStream().close();
            }
        }

    }
}
