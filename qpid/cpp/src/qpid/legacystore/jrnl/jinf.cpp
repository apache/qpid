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
 * \file jinf.cpp
 *
 * Qpid asynchronous store plugin library
 *
 * This file contains the code for the mrg::journal::jinf class.
 *
 * See jinf.h comments for details of this class.
 *
 * \author Kim van der Riet
 */

#include "qpid/legacystore/jrnl/jinf.h"

#include <cstdlib>
#include <cstring>
#include <ctime>
#include <fstream>
#include "qpid/legacystore/jrnl/file_hdr.h"
#include "qpid/legacystore/jrnl/jcntl.h"
#include "qpid/legacystore/jrnl/jerrno.h"
#include "qpid/legacystore/jrnl/lp_map.h"
#include <sstream>
#include <sys/stat.h>

namespace mrg
{
namespace journal
{

jinf::jinf(const std::string& jinf_filename, bool validate_flag):
        _jver(0),
        _filename(jinf_filename),
        _num_jfiles(0),
        _ae(false),
        _ae_max_jfiles(0),
        _jfsize_sblks(0),
        _sblk_size_dblks(0),
        _dblk_size(0),
        _wcache_pgsize_sblks(0),
        _wcache_num_pages(0),
        _rcache_pgsize_sblks(0),
        _rcache_num_pages(0),
        _tm_ptr(0),
        _valid_flag(false),
        _analyzed_flag(false),
        _initial_owi(false),
        _frot(false)
{
    read(_filename);
    if (validate_flag)
        validate();
}

jinf::jinf(const std::string& jid, const std::string& jdir, const std::string& base_filename, const u_int16_t num_jfiles,
        const bool auto_expand, const u_int16_t ae_max_jfiles, const u_int32_t jfsize_sblks,
        const u_int32_t wcache_pgsize_sblks, const u_int16_t wcache_num_pages, const timespec& ts):
        _jver(RHM_JDAT_VERSION),
        _jid(jid),
        _jdir(jdir),
        _base_filename(base_filename),
        _ts(ts),
        _num_jfiles(num_jfiles),
        _ae(auto_expand),
        _ae_max_jfiles(ae_max_jfiles),
        _jfsize_sblks(jfsize_sblks),
        _sblk_size_dblks(JRNL_SBLK_SIZE),
        _dblk_size(JRNL_DBLK_SIZE),
        _wcache_pgsize_sblks(wcache_pgsize_sblks),
        _wcache_num_pages(wcache_num_pages),
        _rcache_pgsize_sblks(JRNL_RMGR_PAGE_SIZE),
        _rcache_num_pages(JRNL_RMGR_PAGES),
        _tm_ptr(std::localtime(&ts.tv_sec)),
        _valid_flag(false),
        _analyzed_flag(false),
        _initial_owi(false)
{
    set_filename();
}

jinf::~jinf()
{}

void
jinf::validate()
{
    bool err = false;
    std::ostringstream oss;
    if (_jver != RHM_JDAT_VERSION)
    {
        oss << "File \"" << _filename << "\": ";
        oss << "RHM_JDAT_VERSION mismatch: found=" << (int)_jver;
        oss << "; required=" << RHM_JDAT_VERSION << std::endl;
        err = true;
    }
    if (_num_jfiles < JRNL_MIN_NUM_FILES)
    {
        oss << "File \"" << _filename << "\": ";
        oss << "Number of journal files too small: found=" << _num_jfiles;
        oss << "; minimum=" << JRNL_MIN_NUM_FILES << std::endl;
        err = true;
    }
    if (_num_jfiles > JRNL_MAX_NUM_FILES)
    {
        oss << "File \"" << _filename << "\": ";
        oss << "Number of journal files too large: found=" << _num_jfiles;
        oss << "; maximum=" << JRNL_MAX_NUM_FILES << std::endl;
        err = true;
    }
    if (_ae)
    {
        if (_ae_max_jfiles < _num_jfiles)
        {
            oss << "File \"" << _filename << "\": ";
            oss << "Number of journal files exceeds auto-expansion limit: found=" << _num_jfiles;
            oss << "; maximum=" << _ae_max_jfiles;
            err = true;
        }
        if (_ae_max_jfiles > JRNL_MAX_NUM_FILES)
        {
            oss << "File \"" << _filename << "\": ";
            oss << "Auto-expansion file limit too large: found=" << _ae_max_jfiles;
            oss << "; maximum=" << JRNL_MAX_NUM_FILES;
            err = true;
        }
    }
    if (_jfsize_sblks < JRNL_MIN_FILE_SIZE)
    {
        oss << "File \"" << _filename << "\": ";
        oss << "Journal file size too small: found=" << _jfsize_sblks;
        oss << "; minimum=" << JRNL_MIN_FILE_SIZE << " (sblks)" << std::endl;
        err = true;
    }
    if (_sblk_size_dblks != JRNL_SBLK_SIZE)
    {
        oss << "File \"" << _filename << "\": ";
        oss << "JRNL_SBLK_SIZE mismatch: found=" << _sblk_size_dblks;
        oss << "; required=" << JRNL_SBLK_SIZE << std::endl;
        err = true;
    }
    if (_dblk_size != JRNL_DBLK_SIZE)
    {
        oss << "File \"" << _filename << "\": ";
        oss << "JRNL_DBLK_SIZE mismatch: found=" << _dblk_size;
        oss << "; required=" << JRNL_DBLK_SIZE << std::endl;
        err = true;
    }
    if (err)
        throw jexception(jerrno::JERR_JINF_CVALIDFAIL, oss.str(), "jinf", "validate");
    _valid_flag = true;
}

void
jinf::analyze()
{
    lp_map early_map;   // map for all owi flags same as pfid 0
    lp_map late_map;    // map for all owi flags opposite to pfid 0
    bool late_latch = false; // latch for owi switchover

    if (!_valid_flag)
        validate();
    bool done = false;
    for (u_int16_t pfid=0; pfid<_num_jfiles && !done; pfid++)
    {
        std::ostringstream oss;
        if (_jdir.at(_jdir.size() - 1) == '/')
            oss << _jdir << _base_filename << ".";
        else
            oss << _jdir << "/" << _base_filename << ".";
        oss << std::setw(4) << std::setfill('0') << std::hex << pfid;
        oss << "." << JRNL_DATA_EXTENSION;

        // Check size of each file is consistent and expected
        u_int32_t fsize = get_filesize(oss.str());
        if (fsize != (_jfsize_sblks + 1) * _sblk_size_dblks * _dblk_size)
        {
            std::ostringstream oss1;
            oss1 << "File \"" << oss.str() << "\": size=" << fsize << "; expected=" << ((_jfsize_sblks + 1) * _sblk_size_dblks * _dblk_size);
            throw jexception(jerrno::JERR_JINF_BADFILESIZE, oss1.str(), "jinf", "analyze");
        }

        std::ifstream jifs(oss.str().c_str());
        if (!jifs.good())
            throw jexception(jerrno::JERR__FILEIO, oss.str(), "jinf", "analyze");
        file_hdr fhdr;
        jifs.read((char*)&fhdr, sizeof(fhdr));
        if (fhdr._magic != RHM_JDAT_FILE_MAGIC) // No file header
        {
            if (fhdr._magic != 0)
                throw jexception(jerrno::JERR_JINF_INVALIDFHDR, oss.str(), "jinf", "analyze");
            if (!pfid) // pfid 0 == lid 0 cannot be empty
                throw jexception(jerrno::JERR_JINF_JDATEMPTY, oss.str(), "jinf", "analyze");
            _frot = true;
            done = true;
        }
        else
        {
            assert(pfid == fhdr._pfid);
            if (pfid == 0)
            {
                _initial_owi = fhdr.get_owi();
                early_map.insert(fhdr._lfid, pfid);
            }
            else
            {
                if (_initial_owi == fhdr.get_owi())
                {
                    early_map.insert(fhdr._lfid, pfid);
                    if (late_latch && (!_ae || _num_jfiles == JRNL_MIN_NUM_FILES))
                        throw jexception(jerrno::JERR_JINF_OWIBAD, oss.str(), "jinf", "analyze");
                }
                else
                {
                    late_map.insert(fhdr._lfid, pfid);
                    late_latch = true;
                }
            }
        }
        jifs.close();
    } // for (pfid)

    // If this is not the first rotation, all files should be in either early or late maps
    if (!_frot) assert(early_map.size() + late_map.size() == _num_jfiles);

    _pfid_list.clear();
    late_map.get_pfid_list(_pfid_list);
    early_map.get_pfid_list(_pfid_list);

    // Check OWI consistency
//    for (u_int16_t lfid=0; lfid<_num_jfiles && !done; lfid++)
//    {
//        throw jexception(jerrno::JERR_JINF_OWIBAD, oss.str(), "jinf", "analyze");
//    }

    _analyzed_flag = true;
}

void
jinf::write()
{
    std::ostringstream oss;
    oss << _jdir << "/" << _base_filename << "." << JRNL_INFO_EXTENSION;
    std::ofstream of(oss.str().c_str(), std::ofstream::out | std::ofstream::trunc);
    if (!of.good())
        throw jexception(jerrno::JERR__FILEIO, oss.str(), "jinf", "write");
    of << xml_str();
    of.close();
}

u_int16_t
jinf::incr_num_jfiles()
{
    if (_num_jfiles >= JRNL_MAX_NUM_FILES)
        throw jexception(jerrno::JERR_JINF_TOOMANYFILES, "jinf", "incr_num_jfiles");
    return ++_num_jfiles;
}

u_int16_t
jinf::get_first_pfid()
{
    if (!_analyzed_flag)
        analyze();
    return *_pfid_list.begin();
}

u_int16_t
jinf::get_last_pfid()
{
    if (!_analyzed_flag)
        analyze();
    return *_pfid_list.rbegin();
}

jinf::pfid_list&
jinf::get_pfid_list()
{
    if (!_analyzed_flag)
        analyze();
    return _pfid_list;
}

void
jinf::get_normalized_pfid_list(pfid_list& pfid_list)
{
    if (!_analyzed_flag)
        analyze();
    pfid_list.clear();
    u_int16_t s = _pfid_list.size();
    u_int16_t iz = 0; // index of 0 value
    while (_pfid_list[iz] && iz < s)
        iz++;
    assert(_pfid_list[iz] == 0);
    for (u_int16_t i = iz; i < iz + s; i++)
        pfid_list.push_back(_pfid_list[i % s]);
    assert(pfid_list[0] == 0);
    assert(pfid_list.size() == s);
}

bool
jinf::get_initial_owi()
{
    if (!_analyzed_flag)
        analyze();
    return _initial_owi;
}

bool
jinf::get_frot()
{
    if (!_analyzed_flag)
        analyze();
    return _frot;
}

std::string
jinf::to_string() const
{
    std::ostringstream oss;
    oss << std::setfill('0');
    oss << "Journal ID \"" << _jid << "\" initialized " << (_tm_ptr->tm_year + 1900) << "/";
    oss << std::setw(2) << (_tm_ptr->tm_mon + 1) << "/" << std::setw(2) << _tm_ptr->tm_mday << " ";
    oss << std::setw(2) << _tm_ptr->tm_hour << ":" << std::setw(2) << _tm_ptr->tm_min << ":";
    oss << std::setw(2) << _tm_ptr->tm_sec << "." << std::setw(9) << _ts.tv_nsec << ":" << std::endl;
    oss << "  Journal directory: \"" << _jdir << "\"" << std::endl;
    oss << "  Journal base filename: \"" << _base_filename << "\"" << std::endl;
    oss << "  Journal version: " << (unsigned)_jver << std::endl;
    oss << "  Number of journal files: " << _num_jfiles << std::endl;
// TODO: Uncomment these lines when auto-expand is enabled.
//    oss << "  Auto-expand mode: " << (_ae ? "enabled" : "disabled") << std::endl;
//    if (_ae) oss << "  Max. number of journal files (in auto-expand mode): " << _ae_max_jfiles << std::endl;
    oss << "  Journal file size: " << _jfsize_sblks << " sblks" << std::endl;
    oss << "  Softblock size (JRNL_SBLK_SIZE): " << _sblk_size_dblks << " dblks" << std::endl;
    oss << "  Datablock size (JRNL_DBLK_SIZE): " << _dblk_size << " bytes" << std::endl;
    oss << "  Write page size: " << _wcache_pgsize_sblks << " sblks" << std::endl;
    oss << "  Number of write pages: " << _wcache_num_pages << std::endl;
    oss << "  Read page size (JRNL_RMGR_PAGE_SIZE): " << _rcache_pgsize_sblks << " sblks" << std::endl;
    oss << "  Number of read pages (JRNL_RMGR_PAGES): " << _rcache_num_pages << std::endl;
    return oss.str();
}

std::string
jinf::xml_str() const
{
    // TODO: This is *not* an XML writer, rather for simplicity, it uses literals. I'm sure a more elegant way can be
    // found to do this using the real thing...

    std::ostringstream oss;
    oss << std::setfill('0');
    oss << "<?xml version=\"1.0\" ?>" << std::endl;
    oss << "<jrnl>" << std::endl;
    oss << "  <journal_version value=\"" << (unsigned)_jver << "\" />" << std::endl;
    oss << "  <journal_id>" << std::endl;
    oss << "    <id_string value=\"" << _jid << "\" />" << std::endl;
    oss << "    <directory value=\"" << _jdir << "\" />" << std::endl;
    oss << "    <base_filename value=\"" << _base_filename << "\" />" << std::endl;
    oss << "  </journal_id>" << std::endl;
    oss << "  <creation_time>" << std::endl;
    oss << "    <seconds value=\"" << _ts.tv_sec << "\" />" << std::endl;
    oss << "    <nanoseconds value=\"" << _ts.tv_nsec << "\" />" << std::endl;
    oss << "    <string value=\"" << (_tm_ptr->tm_year + 1900) << "/";
    oss << std::setw(2) << (_tm_ptr->tm_mon + 1) << "/" << std::setw(2) << _tm_ptr->tm_mday << " ";
    oss << std::setw(2) << _tm_ptr->tm_hour << ":" << std::setw(2) << _tm_ptr->tm_min << ":";
    oss << std::setw(2) << _tm_ptr->tm_sec << "." << std::setw(9) << _ts.tv_nsec;
    oss << "\" />" << std::endl;
    oss << "  </creation_time>" << std::endl;
    oss << "  <journal_file_geometry>" << std::endl;
    oss << "    <number_jrnl_files value=\"" << _num_jfiles << "\" />" << std::endl;
    oss << "    <auto_expand value=\"" << (_ae ? "true" : "false") << "\" />" << std::endl;
    if (_ae) oss << "    <auto_expand_max_jrnl_files value=\"" << _ae_max_jfiles << "\" />" << std::endl;
    oss << "    <jrnl_file_size_sblks value=\"" << _jfsize_sblks << "\" />" << std::endl;
    oss << "    <JRNL_SBLK_SIZE value=\"" << _sblk_size_dblks << "\" />" << std::endl;
    oss << "    <JRNL_DBLK_SIZE value=\"" << _dblk_size << "\" />" << std::endl;
    oss << "  </journal_file_geometry>" << std::endl;
    oss << "  <cache_geometry>" << std::endl;
    oss << "    <wcache_pgsize_sblks value=\"" << _wcache_pgsize_sblks << "\" />" << std::endl;
    oss << "    <wcache_num_pages value=\"" << _wcache_num_pages << "\" />" << std::endl;
    oss << "    <JRNL_RMGR_PAGE_SIZE value=\"" << _rcache_pgsize_sblks << "\" />" << std::endl;
    oss << "    <JRNL_RMGR_PAGES value=\"" << _rcache_num_pages << "\" />" << std::endl;
    oss << "  </cache_geometry>" << std::endl;
    oss << "</jrnl>" << std::endl;
    return oss.str();
}

void
jinf::set_filename()
{
    std::ostringstream oss;
    oss << _jdir << "/" << _base_filename << "." << JRNL_INFO_EXTENSION;
    _filename = oss.str().c_str();
}

void
jinf::read(const std::string& jinf_filename)
{
    // TODO: This is *not* an XML reader, rather for simplicity, it is a brute-force line reader which relies on string
    // recognition. It relies on the format of xml_str() above; it will not handle a XML restructuring.
    // *** Can it be replaced cheaply by a real XML reader? Should it be, or is this sufficient? ***

    char buff[1024]; // limit of line input length
    std::ifstream jinfs(jinf_filename.c_str());
    if (!jinfs.good())
        throw jexception(jerrno::JERR__FILEIO, jinf_filename.c_str(), "jinf", "read");
    u_int32_t charcnt = 0;
    while (jinfs.good())
    {
        jinfs.getline(buff, 1023);
        charcnt += std::strlen(buff);
        if (std::strstr(buff, "journal_version"))
            _jver = u_int16_value(buff);
        else if(std::strstr(buff, "id_string"))
            string_value(_jid, buff);
        else if(std::strstr(buff, "directory"))
            string_value(_jdir, buff);
        else if(std::strstr(buff, "base_filename"))
            string_value(_base_filename, buff);
        else if(std::strstr(buff, "number_jrnl_files"))
            _num_jfiles = u_int16_value(buff);
        else if(std::strstr(buff, "auto_expand_max_jrnl_files"))
            _ae_max_jfiles = u_int16_value(buff);
        else if(std::strstr(buff, "auto_expand"))
            _ae = bool_value(buff);
        else if(std::strstr(buff, "jrnl_file_size_sblks"))
            _jfsize_sblks = u_int32_value(buff);
        else if(std::strstr(buff, "JRNL_SBLK_SIZE"))
            _sblk_size_dblks = u_int16_value(buff);
        else if(std::strstr(buff, "JRNL_DBLK_SIZE"))
            _dblk_size = u_int32_value(buff);
        else if(std::strstr(buff, "wcache_pgsize_sblks"))
            _wcache_pgsize_sblks = u_int32_value(buff);
        else if(std::strstr(buff, "wcache_num_pages"))
            _wcache_num_pages = u_int32_value(buff);
        else if(std::strstr(buff, "JRNL_RMGR_PAGE_SIZE"))
            _rcache_pgsize_sblks = u_int32_value(buff);
        else if(std::strstr(buff, "JRNL_RMGR_PAGES"))
            _rcache_num_pages = u_int32_value(buff);
        else if(std::strstr(buff, "nanoseconds"))
            _ts.tv_nsec = u_int32_value(buff);
        else if(std::strstr(buff, "seconds"))
        {
            _ts.tv_sec = u_int32_value(buff);
            _tm_ptr = std::localtime(&_ts.tv_sec);
        }
    }
    jinfs.close();
    if (charcnt == 0)
        throw jexception(jerrno::JERR_JINF_ZEROLENFILE, jinf_filename.c_str(), "jinf", "read");
}

bool
jinf::bool_value(char* line) const
{
    return std::strcmp(find_value(line), "true") == 0;
}

u_int16_t
jinf::u_int16_value(char* line) const
{
    return std::atoi(find_value(line));
}

u_int32_t
jinf::u_int32_value(char* line) const
{
    return std::atol(find_value(line));
}

std::string&
jinf::string_value(std::string& str, char* line) const
{
    str.assign(find_value(line));
    return str;
}

char*
jinf::find_value(char* line) const
{
    const char* target1_str = "value=\"";
    int target2_char = '\"';
    char* t1 = std::strstr(line, target1_str);
    if (t1 == 0)
    {
        std::ostringstream oss;
        oss << "File \"" << _filename << "\": line=" << line;
        throw jexception(jerrno::JERR_JINF_NOVALUESTR, oss.str(), "jinf", "find_value");
    }
    t1 += std::strlen(target1_str);

    char* t2 = std::strchr(t1, target2_char);
    if (t2 == 0)
    {
        std::ostringstream oss;
        oss << "File \"" << _filename << "\": line=" << line;
        throw jexception(jerrno::JERR_JINF_BADVALUESTR, oss.str(), "jinf", "find_value");
    }
    *t2 = '\0';
    return t1;
}

u_int32_t
jinf::get_filesize(const std::string& file_name) const
{
    struct stat s;
    if (::stat(file_name.c_str(), &s))
    {
        std::ostringstream oss;
        oss << "stat: file=\"" << file_name << "\"" << FORMAT_SYSERR(errno);
        throw jexception(jerrno::JERR_JINF_STAT, oss.str(), "jinf", "get_filesize");
    }
    if (!S_ISREG(s.st_mode)) // not a regular file,
    {
        std::ostringstream oss;
        oss << "File \"" << file_name << "\" is not a regular file: mode=0x" << std::hex << s.st_mode;
        throw jexception(jerrno::JERR_JINF_NOTREGFILE, oss.str(), "jinf", "get_filesize");
    }
    return u_int32_t(s.st_size);
}

} // namespace journal
} // namespace mrg
