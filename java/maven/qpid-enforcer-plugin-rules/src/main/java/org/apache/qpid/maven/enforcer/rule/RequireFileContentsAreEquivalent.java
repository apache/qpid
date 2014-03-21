/**
  Licensed to the Apache Software Foundation (ASF) under one or more
  contributor license agreements.  See the NOTICE file distributed with
  this work for additional information regarding copyright ownership.
  The ASF licenses this file to You under the Apache License, Version 2.0
  (the "License"); you may not use this file except in compliance with
  the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */
package org.apache.qpid.maven.enforcer.rule;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import org.apache.commons.io.FileUtils;
import org.apache.maven.enforcer.rule.api.EnforcerRule;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.enforcer.rule.api.EnforcerRuleHelper;
import org.apache.maven.plugins.enforcer.AbstractStandardEnforcerRule;

public class RequireFileContentsAreEquivalent extends AbstractStandardEnforcerRule
{

    final static String WHITESPACE_REGEX = "\\s+";
    final static String EMPTY_STRING = "";

    File[] files;

    @Override
    public void execute(final EnforcerRuleHelper enforcerRuleHelper) throws EnforcerRuleException
    {
        if (files.length < 2)
        {
            throw new EnforcerRuleException("The file list must contain at least two files for comparison.");
        }

        boolean success = true;
        boolean firstTime = true;
        String referenceContent = null;

        for (final File file : files)
        {
            try
            {
                final String fileContent = FileUtils.readFileToString(file);
                if (firstTime)
                {
                    referenceContent = fileContent;
                    firstTime = false;
                }
                else if (referenceContent != null && fileContent != null)
                {
                    final String strippedReferenceContent = referenceContent.replaceAll(WHITESPACE_REGEX, EMPTY_STRING);
                    final String strippedFileContent = fileContent.replaceAll(WHITESPACE_REGEX, EMPTY_STRING);
                    if (!strippedReferenceContent.equalsIgnoreCase(strippedFileContent))
                    {
                        success = false;
                        break;
                    }
                }
                else
                {
                    throw new EnforcerRuleException("Unable to read file contents");
                }
            }
            catch (final IOException ioe)
            {
                throw new EnforcerRuleException("Cannot process file : " + file.getName(), ioe);
            }
        }

        if (!success)
        {
            throw new EnforcerRuleException("Files specified are not equal in content");
        }
    }

    @Override
    public String getCacheId()
    {
        return Integer.toString(Arrays.hashCode(files));
    }

    @Override
    public boolean isCacheable()
    {
        return true;
    }

    @Override
    public boolean isResultValid(EnforcerRule arg0)
    {
        return true;
    }

}
