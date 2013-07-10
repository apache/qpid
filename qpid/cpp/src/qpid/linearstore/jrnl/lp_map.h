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
 * \file lp_map.h
 *
 * Qpid asynchronous store plugin library
 *
 * File containing code for class mrg::journal::lp_map (logical file map).
 * See class documentation for details.
 *
 * \author Kim van der Riet
 */

#ifndef QPID_LEGACYSTORE_JRNL_LP_MAP_H
#define QPID_LEGACYSTORE_JRNL_LP_MAP_H

#include <map>
#include <string>
#include <sys/types.h>
#include <vector>

namespace mrg
{
namespace journal
{
    /**
    * \class lp_map
    * \brief Maps the logical file id (lfid) to the physical file id (pfid) in the journal.
    *
    * NOTE: NOT THREAD SAFE
    */
    class lp_map
    {
    public:
        typedef std::map<u_int16_t, u_int16_t> lp_map_t;
        typedef lp_map_t::const_iterator lp_map_citr_t;
        typedef lp_map_t::const_reverse_iterator lp_map_critr_t;

    private:
        typedef std::pair<u_int16_t, u_int16_t> lfpair;
        typedef std::pair<lp_map_t::iterator, bool> lfret;
        lp_map_t _map;

    public:
        lp_map();
        virtual ~lp_map();

        void insert(u_int16_t lfid, u_int16_t pfid);
        inline u_int16_t size() const { return u_int16_t(_map.size()); }
        inline bool empty() const { return _map.empty(); }
        inline lp_map_citr_t begin() { return _map.begin(); }
        inline lp_map_citr_t end() { return _map.end(); }
        inline lp_map_critr_t rbegin() { return _map.rbegin(); }
        inline lp_map_critr_t rend() { return _map.rend(); }
        void get_pfid_list(std::vector<u_int16_t>& pfid_list);

        // debug aid
        std::string to_string();
    };

} // namespace journal
} // namespace mrg

#endif // ifndef QPID_LEGACYSTORE_JRNL_LP_MAP_H
