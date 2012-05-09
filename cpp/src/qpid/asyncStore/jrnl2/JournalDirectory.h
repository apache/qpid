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

/**
 * \file JournalDirectory.h
 */

#ifndef qpid_asyncStore_jrnl2_JournalDirectory_h_
#define qpid_asyncStore_jrnl2_JournalDirectory_h_

#include <string>

// --- Helper macros ---

/**
 * \brief Macro to handle the throwing of JournalExceptions.
 *
 * \param name Directory name
 * \param err Static JournalError instance corresponding to the error to be thrown.
 * \param fn C-string conaining throwing function name
 */
#define HANDLE_ERROR(name, err, fn) \
    std::ostringstream oss; \
    oss << "name=\"" << name << "\""; \
    throw qpid::asyncStore::jrnl2::JournalError(err, oss.str(), "JournalDirectory", fn)

/**
 * \brief Macro to both check a result \a ret for a non-zero value and then throw a JournalException if so.
 *
 * \param ret Int result value to be checked for non-zero value.
 * \param name Directory name.
 * \param err Static JournalError instance corresponding to the error to be thrown.
 * \param fn C-string conaining throwing function name
 */
#define CHK_ERROR(ret, name, err, fn) \
    if (ret) { HANDLE_ERROR(name, err, fn); }

/**
 * \brief Macro to handle the throwing of JournalException in which errno contains the error code.
 *
 * \param name Directory name
 * \param err Static JournalError instance corresponding to the error to be thrown.
 * \param fn C-string conaining throwing function name
 */
#define HANDLE_SYS_ERROR(name, err, fn) \
    std::ostringstream oss; \
    oss << "name=\"" << name << "\": " << FORMAT_SYSERR(errno); \
    throw qpid::asyncStore::jrnl2::JournalError(err, oss.str(), "JournalDirectory", fn)

/**
 * \brief Macro to both check a result \a ret for a non-zero value, then throw a JournalException if so. The value
 * of errno is read and incorporated into the error text.
 *
 * \param ret Int result value to be checked for non-zero value.
 * \param name Directory name
 * \param err Static JournalError instance corresponding to the error to be thrown.
 * \param fn C-string conaining throwing function name
 */
#define CHK_SYS_ERROR(ret, name, err, fn) \
    if (ret) { HANDLE_SYS_ERROR(name, err, fn); }


namespace qpid {
namespace asyncStore {
namespace jrnl2 {

/**
 * \brief Class which manages and controls the directory where a journal instance will be created and operated.
 *
 * This class can check for the existence of the target directory, and if it is not present, create it. It can
 * also delete the directory (with all its contents).
 */
class JournalDirectory
{
public:
    /**
     * \brief Constructor.
     *
     * \param fqName Name of directory to be checked for. May be an absolute or relative path.
     * \throws JournalException with error JournalErrors::JERR_STAT if ::lstat() fails.
     * \throws JournalException with error JournalErrors::JERR_NOTADIR if \a name exists, but is not a directory.
     * \throws JournalException with error JournalErrors::JERR_UNLINK if ::unlink() fails
     * \throws JournalException with error JournalErrors::JERR_DIRNOTEMPTY if \a recursiveDelete is \b false and
     * directory \a name contains one or more directories.
     * \throws JournalException with error JournalErrors::JERR_BADFTYPE if \a name is not a regular file, symlink
     * or directory.
     * \throws JournalException with error JournalErrors::JERR_CLOSEDIR if ::closedir() fails.
     * \throws JournalException with error JournalErrors::JERR_RMDIR if ::rmdir() fails.
     * \throws JournalException with error JournalErrors::JERR_OPENDIR if ::opendir() fails.
     */
    JournalDirectory(const std::string& fqName);

    /**
     * \brief Returns the name of the journal directory.
     *
     * \returns Name of the joruanl directory.
     */
    const std::string getFqName() const;

    /**
     * \brief Set a (new) journal directory location. The previous location is assumed to have been set in the
     * constructor or a previous call to getName().
     *
     * \param newFqName New directory (fully qualified name)
     * \param createNew If \b true, will create the new directory immediately and setting m_verified to \b true,
     * otherwise the name will be set, but no action taken to create or verify the name.
     * \param destroyExisting If \b true, will destroy the old journal directory, deleting all its contents. If
     * \b false, the existing directory will be left in tact.
     * \throws JournalException with error JournalErrors::JERR_STAT if ::lstat() fails.
     * \throws JournalException with error JournalErrors::JERR_NOTADIR if \a name exists, but is not a directory.
     * \throws JournalException with error JournalErrors::JERR_UNLINK if ::unlink() fails
     * \throws JournalException with error JournalErrors::JERR_BADFTYPE if \a name is not a regular file, symlink
     * or directory.
     * \throws JournalException with error JournalErrors::JERR_CLOSEDIR if ::closedir() fails.
     * \throws JournalException with error JournalErrors::JERR_RMDIR if ::rmdir() fails.
     * \throws JournalException with error JournalErrors::JERR_OPENDIR if ::opendir() fails.
     */
    void setFqName(const std::string newFqName,
                   const bool createNew = true,
                   const bool destroyExisting = false);

    /**
     * \brief Static helper class to check for the existence of the directory \a name.
     *
     * \param fqName Name of directory to be checked for. May be an absolute or relative path.
     * \param checkIsWritable Also check if the directory is writable. If the directory exists but is not writable
     * and this parameter is set \b true, then this test will fail.
     * \returns \b true if the directory exists and if \b checkIsWritable is set to \b true, then also writable,
     * \b false otherwise.
     * \throws JournalException with error JournalErrors::JERR_STAT if ::lstat() fails.
     * \throws JournalException with error JournalErrors::JERR_NOTADIR if \a name exists, but is not a directory.
     */
    static bool s_exists(const std::string& fqName,
                         const bool checkIsWritable = true);

    /**
     * \brief Static helper function to create a directory \a name. If parameter \a name is a path which includes
     * one or more non-existent directories, then these will be recursively created as needed.
     *
     * \param fqName Name of directory to be created. May be an absolute or relative path.
     * \throws JournalException with error JournalErrors::JERR_STAT if ::lstat() fails.
     * \throws JournalException with error JournalErrors::JERR_NOTADIR if part of \a name exists, but is not a
     * directory.
     * \throws JournalException with error JournalErrors::JERR_MKDIR if ::mkdir() fails.
     */
    static void s_create(const std::string& fqName);

    /**
     * \brief Create the directory \a name supplied to the constructor.
     *
     * \throws JournalException with error JournalErrors::JERR_STAT if ::lstat() fails.
     * \throws JournalException with error JournalErrors::JERR_NOTADIR if part of \a name exists, but is not a
     * directory.
     * \throws JournalException with error JournalErrors::JERR_MKDIR if ::mkdir() fails.
     */
    void create();

    /**
     * \brief Static helper function to delete only the contents of directory \a name.
     *
     * \param fqName Name of directory whose contents are to be deleted. May be an absolute or relative path.
     * \param recursiveDelete If \b true, then all subdirectories (if they exist) will also be deleted.
     * \throws JournalException with error JournalErrors::JERR_STAT if ::lstat() fails.
     * \throws JournalException with error JournalErrors::JERR_NOTADIR if \a name exists, but is not a directory.
     * \throws JournalException with error JournalErrors::JERR_UNLINK if ::unlink() fails
     * \throws JournalException with error JournalErrors::JERR_DIRNOTEMPTY if \a recursiveDelete is \b false and
     * directory \a name contains one or more directories.
     * \throws JournalException with error JournalErrors::JERR_BADFTYPE if \a name is not a regular file, symlink
     * or directory.
     * \throws JournalException with error JournalErrors::JERR_CLOSEDIR if ::closedir() fails.
     * \throws JournalException with error JournalErrors::JERR_RMDIR if ::rmdir() fails.
     * \throws JournalException with error JournalErrors::JERR_OPENDIR if ::opendir() fails.
     */
    static void s_clear(const std::string& fqName,
                        const bool recursiveDelete = true);

    /**
     * \brief Delete only the contents of directory \a name.
     *
     * \param recursiveDelete If \b true, then all subdirectories (if they exist) will also be deleted.
     * \throws JournalException with error JournalErrors::JERR_STAT if ::lstat() fails.
     * \throws JournalException with error JournalErrors::JERR_NOTADIR if \a name exists, but is not a directory.
     * \throws JournalException with error JournalErrors::JERR_UNLINK if ::unlink() fails
     * \throws JournalException with error JournalErrors::JERR_DIRNOTEMPTY if \a recursiveDelete is \b false and
     * directory \a name contains one or more directories.
     * \throws JournalException with error JournalErrors::JERR_BADFTYPE if \a name is not a regular file, symlink
     * or directory.
     * \throws JournalException with error JournalErrors::JERR_CLOSEDIR if ::closedir() fails.
     * \throws JournalException with error JournalErrors::JERR_RMDIR if ::rmdir() fails.
     * \throws JournalException with error JournalErrors::JERR_OPENDIR if ::opendir() fails.
     */
    void clear(const bool recursiveDelete = true);

    /**
     * \brief Static helper function to delete the contents of directory \a name, and by default, also the
     * directory itself.
     *
     * \param fqName Name of directory to be deleted. May be an absolute or relative path.
     * \param recursiveDelete If \b true, then all subdirectories (if they exist) will also be deleted.
     * \param childrenOnly If \b true, then only the contents of directory \a name will be deleted and directory
     * \a name will remain. Otherwise \a name itself will also be deleted.
     * \throws JournalException with error JournalErrors::JERR_STAT if ::lstat() fails.
     * \throws JournalException with error JournalErrors::JERR_NOTADIR if \a name exists, but is not a directory.
     * \throws JournalException with error JournalErrors::JERR_UNLINK if ::unlink() fails
     * \throws JournalException with error JournalErrors::JERR_DIRNOTEMPTY if \a recursiveDelete is \b false and
     * directory \a name contains one or more directories.
     * \throws JournalException with error JournalErrors::JERR_BADFTYPE if \a name is not a regular file, symlink
     * or directory.
     * \throws JournalException with error JournalErrors::JERR_CLOSEDIR if ::closedir() fails.
     * \throws JournalException with error JournalErrors::JERR_RMDIR if ::rmdir() fails.
     * \throws JournalException with error JournalErrors::JERR_OPENDIR if ::opendir() fails.
     */
    static void s_destroy(const std::string& fqName,
                          const bool recursiveDelete = true,
                          const bool childrenOnly = false);

    /**
     * \brief Delete the contents of directory \a name, and by default, also the directory itself.
     *
     * \param recursiveDelete If \b true, then all subdirectories (if they exist) will also be deleted.
     * \param childrenOnly If \b true, then only the contents of directory \a name will be deleted and directory
     * \a name will remain. Otherwise \a name itself will also be deleted.
     * \throws JournalException with error JournalErrors::JERR_STAT if ::lstat() fails.
     * \throws JournalException with error JournalErrors::JERR_NOTADIR if \a name exists, but is not a directory.
     * \throws JournalException with error JournalErrors::JERR_UNLINK if ::unlink() fails
     * \throws JournalException with error JournalErrors::JERR_DIRNOTEMPTY if \a recursiveDelete is \b false and
     * directory \a name contains one or more directories.
     * \throws JournalException with error JournalErrors::JERR_BADFTYPE if \a name is not a regular file, symlink
     * or directory.
     * \throws JournalException with error JournalErrors::JERR_CLOSEDIR if ::closedir() fails.
     * \throws JournalException with error JournalErrors::JERR_RMDIR if ::rmdir() fails.
     * \throws JournalException with error JournalErrors::JERR_OPENDIR if ::opendir() fails.
     */
    void destroy(const bool recursiveDelete = true,
                 const bool childrenOnly = false);

    /**
     * \brief Return the verified status of the store directory.
     *
     * \returns \b true if the directory exists and is available for use as a journal directory, \b false otherwise.
     */
    bool isVerified() const;

protected:
    std::string m_fqName;   ///< Name of directory (fully qualified name)
    bool m_verified;        ///< True when verified that it exists or is created

};

}}} // namespace qpid::asyncStore::jrnl2

#endif // qpid_asyncStore_jrnl2_JournalDirectory_h_

