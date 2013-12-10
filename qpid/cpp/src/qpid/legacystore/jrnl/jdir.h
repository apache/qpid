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

/**
 * \file jdir.h
 *
 * Qpid asynchronous store plugin library
 *
 * File containing code for class mrg::journal::jdir (%journal data
 * directory), used for controlling and manipulating %journal data
 * directories and files. See class documentation for details.
 *
 * \author Kim van der Riet
 */

#ifndef QPID_LEGACYSTORE_JRNL_JDIR_H
#define QPID_LEGACYSTORE_JRNL_JDIR_H

namespace mrg
{
namespace journal
{
class jdir;
}
}

#include "qpid/legacystore/jrnl/jinf.h"
#include <dirent.h>

namespace mrg
{
namespace journal
{

    /**
    * \class jdir
    * \brief Class to manage the %journal directory
    */
    class jdir
    {
    private:
        std::string _dirname;
        std::string _base_filename;

    public:

        /**
        * \brief Sole constructor
        *
        * \param dirname Name of directory to be managed.
        * \param base_filename Filename root used in the creation of %journal files
        *     and sub-directories.
        */
        jdir(const std::string& dirname, const std::string& base_filename);

        virtual ~jdir();


        /**
        * \brief Create %journal directory as set in the dirname parameter of the constructor.
        *     Recursive creation is supported.
        *
        * \exception jerrno::JERR_JDIR_MKDIR The creation of dirname failed.
        */
        void create_dir();

        /**
        * \brief Static function to create a directory. Recursive creation is supported.
        *
        * \param dirname C-string containing name of directory.
        *
        * \exception jerrno::JERR_JDIR_MKDIR The creation of dirname failed.
        */
        static void create_dir(const char* dirname);

        /**
        * \brief Static function to create a directory. Recursive creation is supported.
        *
        * \param dirname String containing name of directory.
        *
        * \exception jerrno::JERR_JDIR_MKDIR The creation of dirname failed.
        */
        static void create_dir(const std::string& dirname);


        /**
        * \brief Clear the %journal directory of files matching the base filename
        *     by moving them into a subdirectory. This fn uses the dirname and base_filename
        *     that were set on construction.
        *
        * \param create_flag If set, create dirname if it is non-existent, otherwise throw
        *     exception.
        *
        * \exception jerrno::JERR_JDIR_OPENDIR The %journal directory could not be opened.
        * \exception jerrno::JERR_JDIR_FMOVE Moving the files from the %journal directory to the created backup
        *     directory failed.
        * \exception jerrno::JERR_JDIR_CLOSEDIR The directory handle could not be closed.
        */
        void clear_dir(const bool create_flag = true);

        /**
        * \brief Clear the directory dirname of %journal files matching base_filename
        *     by moving them into a subdirectory.
        *
        * \param dirname C-string containing name of %journal directory.
        * \param base_filename C-string containing base filename of %journal files to be matched
        *     for moving into subdirectory.
        * \param create_flag If set, create dirname if it is non-existent, otherwise throw
        *     exception
        *
        * \exception jerrno::JERR_JDIR_OPENDIR The %journal directory could not be opened.
        * \exception jerrno::JERR_JDIR_FMOVE Moving the files from the %journal directory to the created backup
        *     directory failed.
        * \exception jerrno::JERR_JDIR_CLOSEDIR The directory handle could not be closed.
        */
        static void clear_dir(const char* dirname, const char* base_filename,
                const bool create_flag = true);

        /**
        * \brief Clear the directory dirname of %journal files matching base_filename
        *     by moving them into a subdirectory.
        *
        * \param dirname String containing name of %journal directory.
        * \param base_filename String containing base filename of %journal files to be matched
        *     for moving into subdirectory.
        * \param create_flag If set, create dirname if it is non-existent, otherwise throw
        *     exception
        *
        * \exception jerrno::JERR_JDIR_OPENDIR The %journal directory could not be opened.
        * \exception jerrno::JERR_JDIR_FMOVE Moving the files from the %journal directory to the created backup
        *     directory failed.
        * \exception jerrno::JERR_JDIR_CLOSEDIR The directory handle could not be closed.
        */
        static void clear_dir(const std::string& dirname, const std::string& base_filename,
                const bool create_flag = true);



        /**
         * \brief Move (push down) the directory target_dir located in directory dirname into a backup directory
         * named _bak_dir_base.XXXX (note prepended underscore), where XXXX is an increasing hex serial number
         * starting at 0000.
         *
         * \param dirname Full path to directory containing directory to be pushed down.
         * \param target_dir Name of directory in dirname to be pushed down.
         * \param bak_dir_base Base name for backup directory to be created in dirname, into which target_dir will be moved.
         * \return Name of backup dir into which target_dir was pushed.
         */
        static std::string push_down(const std::string& dirname, const std::string& target_dir, const std::string& bak_dir_base);


        /**
        * \brief Verify that dirname is a valid %journal directory.
        *
        * The validation reads the .%jinf file, and using this information verifies that all the expected %journal
        * (.jdat) files are present.
        *
        * \exception jerrno::JERR_JDIR_NOTDIR dirname is not a directory
        * \exception jerrno::JERR_JDIR_STAT Could not stat dirname
        * \exception jerrno::JERR__FILEIO Error reading %jinf file
        * \exception jerrno::JERR_JINF_CVALIDFAIL Error validating %jinf file
        * \exception jerrno::JERR_JDIR_NOSUCHFILE Expected jdat file is missing
        */
        void verify_dir();

        /**
        * \brief Verify that dirname is a valid %journal directory.
        *
        * The validation reads the .%jinf file, and using this information verifies that all the expected %journal
        * (.jdat) files are present.
        *
        * \param dirname C-string containing name of %journal directory.
        * \param base_filename C-string containing base filename of %journal files to be matched for moving into sub-directory.
        *
        * \exception jerrno::JERR_JDIR_NOTDIR dirname is not a directory
        * \exception jerrno::JERR_JDIR_STAT Could not stat dirname
        * \exception jerrno::JERR__FILEIO Error reading %jinf file
        * \exception jerrno::JERR_JINF_CVALIDFAIL Error validating %jinf file
        * \exception jerrno::JERR_JDIR_NOSUCHFILE Expected jdat file is missing
        */
        static void verify_dir(const char* dirname, const char* base_filename);

        /**
        * \brief Verify that dirname is a valid %journal directory.
        *
        * The validation reads the .%jinf file, and using this information verifies that all the expected %journal
        * (.jdat) files are present.
        *
        * \param dirname String containing name of %journal directory.
        * \param base_filename String containing base filename of %journal files to be matched for moving into sub-directory.
        *
        * \exception jerrno::JERR_JDIR_NOTDIR dirname is not a directory
        * \exception jerrno::JERR_JDIR_STAT Could not stat dirname
        * \exception jerrno::JERR__FILEIO Error reading %jinf file
        * \exception jerrno::JERR_JINF_CVALIDFAIL Error validating %jinf file
        * \exception jerrno::JERR_JDIR_NOSUCHFILE Expected jdat file is missing
        */
        static void verify_dir(const std::string& dirname, const std::string& base_filename);

        /**
        * \brief Delete the %journal directory and all files and sub--directories that it may
        *     contain. This is equivilent of rm -rf.
        *
        * FIXME: links are not handled correctly.
        *
        * \param children_only If true, delete only children of dirname, but leave dirname itself.
        *
        * \exception jerrno::JERR_JDIR_OPENDIR The %journal directory could not be opened.
        * \exception jerrno::JERR_JDIR_STAT Could not stat dirname.
        * \exception jerrno::JERR_JDIR_UNLINK A file could not be deleted.
        * \exception jerrno::JERR_JDIR_BADFTYPE A dir entry is neiter a file nor a dir.
        * \exception jerrno::JERR_JDIR_CLOSEDIR The directory handle could not be closed.
        * \exception jerrno::JERR_JDIR_RMDIR A directory could not be deleted.
        */
        void delete_dir(bool children_only = false );

        /**
        * \brief Delete the %journal directory and all files and sub--directories that it may
        *     contain. This is equivilent of rm -rf.
        *
        * FIXME: links are not handled correctly.
        *
        * \param dirname C-string containing name of directory to be deleted.
        * \param children_only If true, delete only children of dirname, but leave dirname itself.
        *
        * \exception jerrno::JERR_JDIR_OPENDIR The %journal directory could not be opened.
        * \exception jerrno::JERR_JDIR_STAT Could not stat dirname.
        * \exception jerrno::JERR_JDIR_UNLINK A file could not be deleted.
        * \exception jerrno::JERR_JDIR_BADFTYPE A dir entry is neiter a file nor a dir.
        * \exception jerrno::JERR_JDIR_CLOSEDIR The directory handle could not be closed.
        * \exception jerrno::JERR_JDIR_RMDIR A directory could not be deleted.
        */
        static void delete_dir(const char* dirname, bool children_only = false);

        /**
        * \brief Delete the %journal directory and all files and sub--directories that it may
        *     contain. This is equivilent of rm -rf.
        *
        * FIXME: links are not handled correctly.
        *
        * \param dirname String containing name of directory to be deleted.
        * \param children_only If true, delete only children of dirname, but leave dirname itself.
        *
        * \exception jerrno::JERR_JDIR_OPENDIR The %journal directory could not be opened.
        * \exception jerrno::JERR_JDIR_STAT Could not stat dirname.
        * \exception jerrno::JERR_JDIR_UNLINK A file could not be deleted.
        * \exception jerrno::JERR_JDIR_BADFTYPE A dir entry is neiter a file nor a dir.
        * \exception jerrno::JERR_JDIR_CLOSEDIR The directory handle could not be closed.
        * \exception jerrno::JERR_JDIR_RMDIR A directory could not be deleted.
        */
        static void delete_dir(const std::string& dirname, bool children_only = false);

        /**
        * \brief Create bakup directory that is next in sequence and move all %journal files
        *     matching base_filename into it.
        *
        * In directory dirname, search for existing backup directory using pattern
        * "_basename.bak.XXXX" where XXXX is a hexadecimal sequence, and create next directory
        * based on highest number found. Move all %journal files which match the base_fileaname
        * parameter into this new backup directory.
        *
        * \param dirname String containing name of %journal directory.
        * \param base_filename String containing base filename of %journal files to be matched
        *     for moving into subdirectory.
        *
        * \exception jerrno::JERR_JDIR_OPENDIR The %journal directory could not be opened.
        * \exception jerrno::JERR_JDIR_CLOSEDIR The directory handle could not be closed.
        * \exception jerrno::JERR_JDIR_MKDIR The backup directory could not be deleted.
        */
        static std::string create_bak_dir(const std::string& dirname,
                const std::string& base_filename);

        /**
        * \brief Return the directory name as a string.
        */
        inline const std::string& dirname() const { return _dirname; }

        /**
        * \brief Return the %journal base filename name as a string.
        */
        inline const std::string& base_filename() const { return _base_filename; }

        /**
        * \brief Test whether the named file is a directory.
        *
        * \param name Name of file to be tested.
        * \return <b><i>true</i></b> if the named file is a directory; <b><i>false</i></b>
        *     otherwise.
        * \exception jerrno::JERR_JDIR_STAT Could not stat name.
        */
        static bool is_dir(const char* name);

        /**
        * \brief Test whether the named file is a directory.
        *
        * \param name Name of file to be tested.
        * \return <b><i>true</i></b> if the named file is a directory; <b><i>false</i></b>
        *     otherwise.
        * \exception jerrno::JERR_JDIR_STAT Could not stat name.
        */
        static bool is_dir(const std::string& name);


        /**
        * \brief Test whether the named entity exists on the filesystem.
        *
        * If stat() fails with error ENOENT, then this will return <b><i>false</i></b>. If
        * stat() succeeds, then <b><i>true</i></b> is returned, irrespective of the file type.
        * If stat() fails with any other error, an exception is thrown.
        *
        * \param name Name of entity to be tested.
        * \return <b><i>true</i></b> if the named entity exists; <b><i>false</i></b>
        *     otherwise.
        * \exception jerrno::JERR_JDIR_STAT Could not stat name.
        */
        static bool exists(const char* name);

        /**
        * \brief Test whether the named entity exists on the filesystem.
        *
        * If stat() fails with error ENOENT, then this will return <b><i>false</i></b>. If
        * stat() succeeds, then <b><i>true</i></b> is returned, irrespective of the file type.
        * If stat() fails with any other error, an exception is thrown.
        *
        * \param name Name of entity to be tested.
        * \return <b><i>true</i></b> if the named entity exists; <b><i>false</i></b>
        *     otherwise.
        * \exception jerrno::JERR_JDIR_STAT Could not stat name.
        */
        static bool exists(const std::string& name);

        /**
        * \brief Stream operator
        */
        friend std::ostream& operator<<(std::ostream& os, const jdir& jdir);

        /**
        * \brief Stream operator
        */
        friend std::ostream& operator<<(std::ostream& os, const jdir* jdirPtr);

    private:
        /**
         * \brief Check for error, if non-zero close DIR handle and throw JERR_JDIR_READDIR
         *
         * \exception jerrno::JERR_JDIR_READDIR Error while reading contents of dir.
         */
        static void check_err(const int err_num, DIR* dir, const std::string& dir_name, const std::string& fn_name);

        /**
         * \brief Close a DIR handle, throw JERR_JDIR_CLOSEDIR if error occurs during close
         *
         * \exception jerrno::JERR_JDIR_CLOSEDIR The directory handle could not be closed.
         */
        static void close_dir(DIR* dir, const std::string& dir_name, const std::string& fn_name);
    };

} // namespace journal
} // namespace mrg

#endif // ifndef QPID_LEGACYSTORE_JRNL_JDIR_H
