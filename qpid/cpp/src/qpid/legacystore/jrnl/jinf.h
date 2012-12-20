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
 * \file jinf.h
 *
 * Qpid asynchronous store plugin library
 *
 * This file contains the code for the mrg::journal::jinf class.
 *
 * \author Kim van der Riet
 */

#ifndef QPID_LEGACYSTORE_JRNL_JINF_H
#define QPID_LEGACYSTORE_JRNL_JINF_H

#include <ctime>
#include <string>
#include <sys/types.h>
#include <vector>

namespace mrg
{
namespace journal
{
    /**
    * \class jinf
    * \brief Class to handle the journal information file &lt;basename&gt;.jinf.
    */
    class jinf
    {
    public:
        typedef std::vector<u_int16_t> pfid_list; // pfids
        typedef pfid_list::const_iterator pfidl_citr;

    private:
        u_int8_t _jver;
        std::string _jid;
        std::string _jdir;
        std::string _base_filename;
        std::string _filename;
        timespec _ts;
        u_int16_t _num_jfiles;
        bool _ae;
        u_int32_t _ae_max_jfiles;
        u_int32_t _jfsize_sblks;
        u_int16_t _sblk_size_dblks;
        u_int32_t _dblk_size;
        u_int32_t _wcache_pgsize_sblks;
        u_int16_t _wcache_num_pages;
        u_int32_t _rcache_pgsize_sblks;
        u_int16_t _rcache_num_pages;
        std::tm* _tm_ptr;
        bool _valid_flag;
        bool _analyzed_flag;
        pfid_list _pfid_list;
        bool _initial_owi;
        bool _frot;

    public:
        // constructor for reading existing jinf file
        jinf(const std::string& jinf_filename, bool validate_flag);
        // constructor for writing jinf file
        jinf(const std::string& jid, const std::string& jdir, const std::string& base_filename,
                const u_int16_t num_jfiles, const bool auto_expand, const u_int16_t ae_max_jfiles,
                const u_int32_t jfsize_sblks, const u_int32_t wcache_pgsize_sblks, const u_int16_t wcache_num_pages,
                const timespec& ts);
        virtual ~jinf();

        void validate();
        void analyze();
        void write();

        inline u_int8_t jver() const { return _jver; }
        inline const std::string& jid() const { return _jid; }
        inline const std::string& jdir() const { return _jdir; }
        inline void set_jdir(const std::string& jdir) { _jdir = jdir; }
        inline const std::string& base_filename() const { return _base_filename; }
        inline const timespec& ts() const { return _ts; }
        inline u_int16_t num_jfiles() const { return _num_jfiles; }
        u_int16_t incr_num_jfiles();
        inline bool is_ae() const { return _ae; }
        inline u_int16_t ae_max_jfiles() const { return _ae_max_jfiles; }
        inline u_int32_t jfsize_sblks() const { return _jfsize_sblks; }
        inline u_int16_t sblk_size_dblks() const { return _sblk_size_dblks; }
        inline u_int32_t dblk_size() const { return _dblk_size; }
        inline u_int32_t wcache_pgsize_sblks() const { return _wcache_pgsize_sblks; }
        inline u_int16_t wcache_num_pages() const { return _wcache_num_pages; }
        inline u_int32_t rcache_pgsize_sblks() const { return _rcache_pgsize_sblks; }
        inline u_int16_t rcache_num_pages() const { return _rcache_num_pages; }
        u_int16_t get_first_pfid();
        u_int16_t get_last_pfid();
        pfid_list& get_pfid_list();
        void get_normalized_pfid_list(pfid_list& pfid_list);
        bool get_initial_owi();
        bool get_frot();

        std::string to_string() const;
        std::string xml_str() const;

    private:
        void set_filename();
        void read(const std::string& jinf_filename);
        bool bool_value(char* line) const;
        u_int16_t u_int16_value(char* line) const;
        u_int32_t u_int32_value(char* line) const;
        std::string& string_value(std::string& str, char* line) const;
        char* find_value(char* line) const;
        u_int32_t get_filesize(const std::string& file_name) const;
    };

} // namespace journal
} // namespace mrg

#endif // ifndef QPID_LEGACYSTORE_JRNL_JINF_H
