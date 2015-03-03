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

package org.apache.qpid.server.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

import org.apache.qpid.test.utils.QpidTestCase;

public class FileHelperTest extends QpidTestCase
{
    private static final String TEST_FILE_PERMISSIONS = "rwxr-x---";
    private File _testFile;
    private FileHelper _fileHelper;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        _testFile = new File(TMP_FOLDER, "test-" + System.currentTimeMillis());
        _fileHelper = new FileHelper();
    }

    @Override
    public void tearDown() throws Exception
    {
        try
        {
            super.tearDown();
        }
        finally
        {
            Files.deleteIfExists(_testFile.toPath());
        }
    }

    public void testCreateNewFile() throws Exception
    {
        assertFalse("File should not exist", _testFile.exists());
        Path path = _fileHelper.createNewFile(_testFile, TEST_FILE_PERMISSIONS);
        assertTrue("File was not created", path.toFile().exists());
        if (Files.getFileStore(path).supportsFileAttributeView(PosixFileAttributeView.class))
        {
            assertPermissions(path);
        }
    }

    public void testCreateNewFileUsingRelativePath() throws Exception
    {
        _testFile = new File("./tmp-" + System.currentTimeMillis());
        assertFalse("File should not exist", _testFile.exists());
        Path path = _fileHelper.createNewFile(_testFile, TEST_FILE_PERMISSIONS);
        assertTrue("File was not created", path.toFile().exists());
        if (Files.getFileStore(path).supportsFileAttributeView(PosixFileAttributeView.class))
        {
            assertPermissions(path);
        }
    }

    public void testWriteFileSafely() throws Exception
    {
        Path path = _fileHelper.createNewFile(_testFile, TEST_FILE_PERMISSIONS);
        _fileHelper.writeFileSafely(path, new BaseAction<File, IOException>()
        {
            @Override
            public void performAction(File file) throws IOException
            {
                Files.write(file.toPath(), "test".getBytes("UTF8"));
                assertEquals("Unexpected name", _testFile.getAbsolutePath() + ".tmp", file.getPath());
            }
        });

        assertTrue("File was not created", path.toFile().exists());

        if (Files.getFileStore(path).supportsFileAttributeView(PosixFileAttributeView.class))
        {
            assertPermissions(path);
        }

        String content =  new String(Files.readAllBytes(path), "UTF-8");
        assertEquals("Unexpected file content", "test", content);
    }

    public void testAtomicFileMoveOrReplace() throws Exception
    {
        Path path = _fileHelper.createNewFile(_testFile, TEST_FILE_PERMISSIONS);
        Files.write(path, "test".getBytes("UTF8"));
        _testFile = _fileHelper.atomicFileMoveOrReplace(path, path.resolveSibling(_testFile.getName() + ".target")).toFile();

        assertFalse("File was not moved", path.toFile().exists());
        assertTrue("Target file does not exist", _testFile.exists());

        if (Files.getFileStore(_testFile.toPath()).supportsFileAttributeView(PosixFileAttributeView.class))
        {
            assertPermissions(_testFile.toPath());
        }
    }


    private void assertPermissions(Path path) throws IOException
    {
        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path);
        assertTrue("Unexpected owner read permission", permissions.contains(PosixFilePermission.OWNER_READ));
        assertTrue("Unexpected owner write permission", permissions.contains(PosixFilePermission.OWNER_WRITE));
        assertTrue("Unexpected owner exec permission", permissions.contains(PosixFilePermission.OWNER_EXECUTE));
        assertTrue("Unexpected group read permission", permissions.contains(PosixFilePermission.GROUP_READ));
        assertFalse("Unexpected group write permission", permissions.contains(PosixFilePermission.GROUP_WRITE));
        assertTrue("Unexpected group exec permission", permissions.contains(PosixFilePermission.GROUP_EXECUTE));
        assertFalse("Unexpected others read permission", permissions.contains(PosixFilePermission.OTHERS_READ));
        assertFalse("Unexpected others write permission", permissions.contains(PosixFilePermission.OTHERS_WRITE));
        assertFalse("Unexpected others exec permission", permissions.contains(PosixFilePermission.OTHERS_EXECUTE));
    }
}
