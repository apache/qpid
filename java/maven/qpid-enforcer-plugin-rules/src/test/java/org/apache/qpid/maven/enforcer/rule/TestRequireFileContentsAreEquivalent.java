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

import org.apache.commons.io.FileUtils;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.plugins.enforcer.EnforcerTestUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

public class TestRequireFileContentsAreEquivalent
{
    final RequireFileContentsAreEquivalent rule = new RequireFileContentsAreEquivalent();

    final static String TEST_TEXT = "Lorem ipsum dolor sit amet, consectetur adipiscing elit.";
    final static String TEST_TEXT_WHITESPACE = "Lorem  ipsum dolor  sit amet,\n   consectetur adipiscing  \t elit.  ";
    final static String ALTERNATE_TEST_TEXT_WHITESPACE = "   Lorem  \t   ip sum  dolor \n  sit amet,\n   consectetur adipiscing  \t elit.";
    final static String DIFFERENT_TEST_TEXT = "Donec velit felis, semper dapibus mattis vitae";

    @Test
    public void testDifferentContentFiles() throws Exception
    {
        final File f1 = createTestFile(1, TEST_TEXT);
        final File f2 = createTestFile(2, DIFFERENT_TEST_TEXT);

        rule.files = new File[]
        { f1, f2 };

        try
        {
            rule.execute(EnforcerTestUtils.getHelper());
            Assert.fail("Files with different content should have failed enforcer rule");
        }
        catch (final EnforcerRuleException ere)
        {
            // do nothing
        }
    }

    @Test
    public void testIdenticalContentFiles() throws Exception
    {
        final File f1 = createTestFile(1, TEST_TEXT);
        final File f2 = createTestFile(2, TEST_TEXT);

        rule.files = new File[]
        { f1, f2 };

        rule.execute(EnforcerTestUtils.getHelper());
    }

    @Test
    public void testUsingOneFileTwice() throws Exception
    {
        final File f1 = createTestFile(1, TEST_TEXT);

        rule.files = new File[]
        { f1, f1 };

        rule.execute(EnforcerTestUtils.getHelper());
    }

    @Test
    public void testSimilarFiles() throws Exception
    {
        final File f1 = createTestFile(1, TEST_TEXT);
        final File f2 = createTestFile(2, TEST_TEXT_WHITESPACE);

        rule.files = new File[]
        { f1, f2 };

        rule.execute(EnforcerTestUtils.getHelper());
    }

    @Test
    public void testMultipleFilesOneDifferent() throws Exception
    {
        final File f1 = createTestFile(1, TEST_TEXT);
        final File f2 = createTestFile(2, TEST_TEXT_WHITESPACE);
        final File f3 = createTestFile(3, ALTERNATE_TEST_TEXT_WHITESPACE);
        final File f4 = createTestFile(4, DIFFERENT_TEST_TEXT);

        rule.files = new File[]
        { f1, f2, f3, f4 };

        try
        {
            rule.execute(EnforcerTestUtils.getHelper());
            Assert.fail("Files with different content should have failed enforcer rule");
        }
        catch (final EnforcerRuleException ere)
        {
            // do nothing
        }
    }

    @Test
    public void testMultipleFilesAllSimilar() throws Exception
    {
        final File f1 = createTestFile(1, TEST_TEXT);
        final File f2 = createTestFile(2, TEST_TEXT);
        final File f3 = createTestFile(3, TEST_TEXT_WHITESPACE);
        final File f4 = createTestFile(4, ALTERNATE_TEST_TEXT_WHITESPACE);

        rule.files = new File[]
        { f1, f2, f3, f4 };

        rule.execute(EnforcerTestUtils.getHelper());
    }

    @After
    public void deleteTestFiles() throws Exception
    {
        for (File file : rule.files)
        {
            if (file.exists())
            {
                file.delete();
            }
        }
    }

    private File createTestFile(final int id, final String content) throws IOException
    {
        final File file = File.createTempFile(TestRequireFileContentsAreEquivalent.class.getName() + 
                                                "-testfile" + id, "tmp");
        file.deleteOnExit();
        FileUtils.writeStringToFile(file, content);
        return file;
    }
}
