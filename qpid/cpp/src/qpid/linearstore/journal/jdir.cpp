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

#include "qpid/linearstore/journal/jdir.h"

#include <cstring>
#include <cerrno>
#include <iomanip>
#include "qpid/linearstore/journal/jexception.h"
#include <sys/stat.h>
#include <unistd.h>

namespace qpid {
namespace linearstore {
namespace journal {

jdir::jdir(const std::string& dirname/*, const std::string& _base_filename*/):
        _dirname(dirname)/*,
        _base_filename(_base_filename)*/
{}

jdir::~jdir()
{}

// === create_dir ===

void
jdir::create_dir()
{
    create_dir(_dirname);
}


void
jdir::create_dir(const char* dirname)
{
    create_dir(std::string(dirname));
}


void
jdir::create_dir(const std::string& dirname)
{
    std::size_t fdp = dirname.find_last_of('/');
    if (fdp != std::string::npos)
    {
        std::string parent_dir = dirname.substr(0, fdp);
        if (!exists(parent_dir))
            create_dir(parent_dir);
    }
    if (::mkdir(dirname.c_str(), S_IRWXU | S_IRWXG | S_IROTH | S_IXOTH))
    {
        if (errno != EEXIST) // Dir exists, ignore
        {
            std::ostringstream oss;
            oss << "dir=\"" << dirname << "\"" << FORMAT_SYSERR(errno);
            throw jexception(jerrno::JERR_JDIR_MKDIR, oss.str(), "jdir", "create_dir");
        }
    }
}


// === clear_dir ===

void
jdir::clear_dir(const bool create_flag)
{
    clear_dir(_dirname/*, _base_filename*/, create_flag);
}

void
jdir::clear_dir(const char* dirname/*, const char* base_filename*/, const bool create_flag)
{
    clear_dir(std::string(dirname)/*, std::string(base_filename)*/, create_flag);
}


void
jdir::clear_dir(const std::string& dirname/*, const std::string&
#ifndef RHM_JOWRITE
        base_filename
#endif
*/
        , const bool create_flag)
{
    DIR* dir = open_dir(dirname, "clear_dir", true);
    if (!dir && create_flag) {
        create_dir(dirname);
        dir = open_dir(dirname, "clear_dir", true);
    }
//#ifndef RHM_JOWRITE
    struct dirent* entry;
    bool found = false;
    std::string bak_dir;
    while ((entry = ::readdir(dir)) != 0)
    {
        // Ignore . and ..
        if (std::strcmp(entry->d_name, ".") != 0 && std::strcmp(entry->d_name, "..") != 0)
        {
            if (std::strlen(entry->d_name) >= 3) // 'bak'
            {
                if (std::strncmp(entry->d_name, "bak", 3) == 0)
                {
                    if (!found)
                    {
                        bak_dir = create_bak_dir(dirname/*, base_filename*/);
                        found = true;
                    }
                    std::ostringstream oldname;
                    oldname << dirname << "/" << entry->d_name;
                    std::ostringstream newname;
                    newname << bak_dir << "/" << entry->d_name;
                    if (::rename(oldname.str().c_str(), newname.str().c_str()))
                    {
                        ::closedir(dir);
                        std::ostringstream oss;
                        oss << "file=\"" << oldname.str() << "\" dest=\"" <<
                                newname.str() << "\"" << FORMAT_SYSERR(errno);
                        throw jexception(jerrno::JERR_JDIR_FMOVE, oss.str(), "jdir", "clear_dir");
                    }
                }
            }
        }
    }
// FIXME: Find out why this fails with false alarms/errors from time to time...
// While commented out, there is no error capture from reading dir entries.
//    check_err(errno, dir, dirname, "clear_dir");
//#endif
    close_dir(dir, dirname, "clear_dir");
}

// === push_down ===

std::string
jdir::push_down(const std::string& dirname, const std::string& target_dir/*, const std::string& bak_dir_base*/)
{
    std::string bak_dir_name = create_bak_dir(dirname/*, bak_dir_base*/);

    DIR* dir = open_dir(dirname, "push_down", false);
    // Copy contents of targetDirName into bak dir
    struct dirent* entry;
    while ((entry = ::readdir(dir)) != 0)
    {
        // Search for targetDirName in storeDirName
        if (std::strcmp(entry->d_name, target_dir.c_str()) == 0)
        {
            std::ostringstream oldname;
            oldname << dirname << "/" << target_dir;
            std::ostringstream newname;
            newname << bak_dir_name << "/" << target_dir;
            if (::rename(oldname.str().c_str(), newname.str().c_str()))
            {
                ::closedir(dir);
                std::ostringstream oss;
                oss << "file=\"" << oldname.str() << "\" dest=\"" <<  newname.str() << "\"" << FORMAT_SYSERR(errno);
                throw jexception(jerrno::JERR_JDIR_FMOVE, oss.str(), "jdir", "push_down");
            }
            break;
        }
    }
    close_dir(dir, dirname, "push_down");
    return bak_dir_name;
}

// === verify_dir ===

void
jdir::verify_dir()
{
    verify_dir(_dirname/*, _base_filename*/);
}

void
jdir::verify_dir(const char* dirname/*, const char* base_filename*/)
{
    verify_dir(std::string(dirname)/*, std::string(base_filename)*/);
}


void
jdir::verify_dir(const std::string& dirname/*, const std::string& base_filename*/)
{
    if (!is_dir(dirname))
    {
        std::ostringstream oss;
        oss << "dir=\"" << dirname << "\"";
        throw jexception(jerrno::JERR_JDIR_NOTDIR, oss.str(), "jdir", "verify_dir");
    }

    // Read jinf file, then verify all journal files are present
//    jinf ji(dirname + "/" + base_filename + "." + QLS_JRNL_FILE_EXTENSION, true);
//    for (uint16_t fnum=0; fnum < ji.num_jfiles(); fnum++)
//    {
//        std::ostringstream oss;
//        oss << dirname << "/" << base_filename << ".";
//        oss << std::setw(4) << std::setfill('0') << std::hex << fnum;
//        oss << "." << QLS_JRNL_FILE_EXTENSION;
//        if (!exists(oss.str()))
//            throw jexception(jerrno::JERR_JDIR_NOSUCHFILE, oss.str(), "jdir", "verify_dir");
//    }
}


// === delete_dir ===

void
jdir::delete_dir(bool children_only)
{
    delete_dir(_dirname, children_only);
}

void
jdir::delete_dir(const char* dirname, bool children_only)
{
    delete_dir(std::string(dirname), children_only);
}

void
jdir::delete_dir(const std::string& dirname, bool children_only)
{
    struct dirent* entry;
    struct stat s;
    DIR* dir = open_dir(dirname, "delete_dir", true); // true = allow dir does not exist, return 0
    if (!dir) return;
    while ((entry = ::readdir(dir)) != 0)
    {
        // Ignore . and ..
        if (std::strcmp(entry->d_name, ".") != 0 && std::strcmp(entry->d_name, "..") != 0)
        {
            std::string full_name(dirname + "/" + entry->d_name);
            if (::lstat(full_name.c_str(), &s))
            {
                ::closedir(dir);
                std::ostringstream oss;
                oss << "stat: file=\"" << full_name << "\"" << FORMAT_SYSERR(errno);
                throw jexception(jerrno::JERR_JDIR_STAT, oss.str(), "jdir", "delete_dir");
            }
            if (S_ISREG(s.st_mode) || S_ISLNK(s.st_mode)) // This is a file or slink
            {
                if(::unlink(full_name.c_str()))
                {
                    ::closedir(dir);
                    std::ostringstream oss;
                    oss << "unlink: file=\"" << entry->d_name << "\"" << FORMAT_SYSERR(errno);
                    throw jexception(jerrno::JERR_JDIR_UNLINK, oss.str(), "jdir", "delete_dir");
                }
            }
            else if (S_ISDIR(s.st_mode)) // This is a dir
            {
                delete_dir(full_name);
            }
            else // all other types, throw up!
            {
                ::closedir(dir);
                std::ostringstream oss;
                oss << "file=\"" << entry->d_name << "\" is not a dir, file or slink.";
                oss << " (mode=0x" << std::hex << s.st_mode << std::dec << ")";
                throw jexception(jerrno::JERR_JDIR_BADFTYPE, oss.str(), "jdir", "delete_dir");
            }
        }
    }

// FIXME: Find out why this fails with false alarms/errors from time to time...
// While commented out, there is no error capture from reading dir entries.
//        check_err(errno, dir, dirname, "delete_dir");
    // Now dir is empty, close and delete it
    close_dir(dir, dirname, "delete_dir");

    if (!children_only)
        if (::rmdir(dirname.c_str()))
        {
            std::ostringstream oss;
            oss << "dir=\"" << dirname << "\"" << FORMAT_SYSERR(errno);
            throw jexception(jerrno::JERR_JDIR_RMDIR, oss.str(), "jdir", "delete_dir");
        }
}


std::string
jdir::create_bak_dir(const std::string& dirname)
{
    DIR* dir = open_dir(dirname, "create_bak_dir", false);
    long dir_num = 0L;
    struct dirent* entry;
    while ((entry = ::readdir(dir)) != 0)
    {
        // Ignore . and ..
        if (std::strcmp(entry->d_name, ".") != 0 && std::strcmp(entry->d_name, "..") != 0)
        {
            if (std::strlen(entry->d_name) == 9) // Format: _bak.XXXX
            {
                if (std::strncmp(entry->d_name, "_bak.", 5) == 0)
                {
                    long this_dir_num = std::strtol(entry->d_name + 5, 0, 16);
                    if (this_dir_num > dir_num)
                        dir_num = this_dir_num;
                }
            }
        }
    }
// FIXME: Find out why this fails with false alarms/errors from time to time...
// While commented out, there is no error capture from reading dir entries.
//    check_err(errno, dir, dirname, "create_bak_dir");
    close_dir(dir, dirname, "create_bak_dir");

    std::ostringstream dn;
    dn << dirname << "/_bak." << std::hex << std::setw(4) << std::setfill('0') << ++dir_num;
    if (::mkdir(dn.str().c_str(), S_IRWXU | S_IRWXG | S_IROTH))
    {
        std::ostringstream oss;
        oss << "dir=\"" << dn.str() << "\"" << FORMAT_SYSERR(errno);
        throw jexception(jerrno::JERR_JDIR_MKDIR, oss.str(), "jdir", "create_bak_dir");
    }
    return std::string(dn.str());
}

bool
jdir::is_dir(const char* name)
{
    struct stat s;
    if (::stat(name, &s))
    {
        std::ostringstream oss;
        oss << "file=\"" << name << "\"" << FORMAT_SYSERR(errno);
        throw jexception(jerrno::JERR_JDIR_STAT, oss.str(), "jdir", "is_dir");
    }
    return S_ISDIR(s.st_mode);
}

bool
jdir::is_dir(const std::string& name)
{
    return is_dir(name.c_str());
}

bool
jdir::exists(const char* name)
{
    struct stat s;
    if (::stat(name, &s))
    {
        if (errno == ENOENT) // No such dir or file
            return false;
        // Throw for any other condition
        std::ostringstream oss;
        oss << "file=\"" << name << "\"" << FORMAT_SYSERR(errno);
        throw jexception(jerrno::JERR_JDIR_STAT, oss.str(), "jdir", "exists");
    }
    return true;
}

bool
jdir::exists(const std::string& name)
{
    return exists(name.c_str());
}

void
jdir::read_dir(const std::string& name, std::vector<std::string>& dir_list, const bool incl_dirs, const bool incl_files, const bool incl_links, const bool return_fqfn) {
    struct stat s;
    if (is_dir(name)) {
        DIR* dir = open_dir(name, "read_dir", false);
        struct dirent* entry;
        while ((entry = ::readdir(dir)) != 0) {
            if (std::strcmp(entry->d_name, ".") != 0 && std::strcmp(entry->d_name, "..") != 0) { // Ignore . and ..
                std::string full_name(name + "/" + entry->d_name);
                if (::stat(full_name.c_str(), &s))
                {
                    ::closedir(dir);
                    std::ostringstream oss;
                    oss << "stat: file=\"" << full_name << "\"" << FORMAT_SYSERR(errno);
                    throw jexception(jerrno::JERR_JDIR_STAT, oss.str(), "jdir", "delete_dir");
                }
                if ((S_ISREG(s.st_mode) && incl_files) || (S_ISDIR(s.st_mode) && incl_dirs) || (S_ISLNK(s.st_mode) && incl_links)) {
                    if (return_fqfn) {
                        dir_list.push_back(name + "/" + entry->d_name);
                    } else {
                        dir_list.push_back(entry->d_name);
                    }
                }
            }
        }
        close_dir(dir, name, "read_dir");
    }
}

void
jdir::check_err(const int err_num, DIR* dir, const std::string& dir_name, const std::string& fn_name)
{
    if (err_num)
    {
        std::ostringstream oss;
        oss << "dir=\"" << dir_name << "\"" << FORMAT_SYSERR(err_num);
        ::closedir(dir); // Try to close, it makes no sense to trap errors here...
        throw jexception(jerrno::JERR_JDIR_READDIR, oss.str(), "jdir", fn_name);
    }
}

void
jdir::close_dir(DIR* dir, const std::string& dir_name, const std::string& fn_name)
{
    if (::closedir(dir))
    {
        std::ostringstream oss;
        oss << "dir=\"" << dir_name << "\"" << FORMAT_SYSERR(errno);
        throw jexception(jerrno::JERR_JDIR_CLOSEDIR, oss.str(), "jdir", fn_name);
    }
}

DIR*
jdir::open_dir(const std::string& dir_name, const std::string& fn_name, const bool test_enoent)
{
    DIR* dir = ::opendir(dir_name.c_str());
    if (!dir) {
        if (test_enoent && errno == ENOENT) {
            return 0;
        }
        std::ostringstream oss;
        oss << "dir=\"" << dir_name << "\"" << FORMAT_SYSERR(errno);
        throw jexception(jerrno::JERR_JDIR_OPENDIR, oss.str(), "jdir", fn_name);
    }
    return dir;
}

std::ostream&
operator<<(std::ostream& os, const jdir& jdir)
{
    os << jdir._dirname;
    return os;
}

std::ostream&
operator<<(std::ostream& os, const jdir* jdirPtr)
{
    os << jdirPtr->_dirname;
    return os;
}

}}}
