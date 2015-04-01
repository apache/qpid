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
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

public class FileHelper
{

    public void writeFileSafely(Path targetFile, BaseAction<File, IOException> operation) throws IOException
    {
        String name = targetFile.toFile().getName();
        writeFileSafely(targetFile,
                targetFile.resolveSibling(name + ".bak"),
                targetFile.resolveSibling(name + ".tmp"),
                operation);
    }

    public void writeFileSafely(Path targetFile, Path backupFile, Path tmpFile, BaseAction<File, IOException> write) throws IOException
    {
        Files.deleteIfExists(tmpFile);
        Files.deleteIfExists(backupFile);

        Set<PosixFilePermission> permissions = null;
        if (Files.exists(targetFile) && isPosixFileSystem(targetFile))
        {
            permissions =  Files.getPosixFilePermissions(targetFile);
        }

        tmpFile = createNewFile(tmpFile, permissions);

        write.performAction(tmpFile.toFile());

        atomicFileMoveOrReplace(targetFile, backupFile);

        if (permissions != null)
        {
            Files.setPosixFilePermissions(backupFile, permissions);
        }

        atomicFileMoveOrReplace(tmpFile, targetFile);

        Files.deleteIfExists(tmpFile);
        Files.deleteIfExists(backupFile);
    }

    public Path createNewFile(File newFile, String posixFileAttributes) throws IOException
    {
        return createNewFile(newFile.toPath(), posixFileAttributes);
    }

    public Path createNewFile(Path newFile, String posixFileAttributes) throws IOException
    {
        Set<PosixFilePermission> permissions = posixFileAttributes == null ? null : PosixFilePermissions.fromString(posixFileAttributes);
        return createNewFile(newFile, permissions );
    }

    public Path createNewFile(Path newFile, Set<PosixFilePermission> permissions) throws IOException
    {
        if (!Files.exists(newFile))
        {
            newFile = Files.createFile(newFile);
        }

        if (permissions != null && isPosixFileSystem(newFile))
        {
            Files.setPosixFilePermissions(newFile, permissions);
        }

        return newFile;
    }

    public boolean isPosixFileSystem(Path path) throws IOException
    {
        while (!Files.exists(path))
        {
            path = path.getParent();

            if (path == null)
            {
                return false;
            }
        }
        return Files.getFileStore(path).supportsFileAttributeView(PosixFileAttributeView.class);
    }

    public Path atomicFileMoveOrReplace(Path sourceFile, Path targetFile) throws IOException
    {
        try
        {
            return Files.move(sourceFile, targetFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
        catch(AtomicMoveNotSupportedException e)
        {
            if (sourceFile.toFile().renameTo(targetFile.toFile()))
            {
                return targetFile;
            }
            else
            {
                throw new RuntimeException("Atomic move is unsupported and rename from : '"
                + sourceFile + "' to: '" + targetFile + "' failed.");
            }
        }
    }

    public boolean isWritableDirectory(String path)
    {
        File storePath = new File(path);
        if (storePath.exists())
        {
            if (!storePath.isDirectory())
            {
                return false;
            }
        }
        else
        {
            do
            {
                storePath = storePath.getParentFile();
                if (storePath == null)
                {
                    return false;
                }
            }
            while (!storePath.exists());
        }
        return storePath.canWrite();
    }

}
