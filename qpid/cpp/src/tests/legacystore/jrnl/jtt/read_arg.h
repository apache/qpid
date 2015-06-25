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

#ifndef mrg_jtt_read_arg_hpp
#define mrg_jtt_read_arg_hpp

#include <string>
#include <map>

namespace mrg
{
namespace jtt
{

class read_arg
{
    public:
        enum read_mode_t { NONE, ALL, RANDOM, LAZYLOAD};
    private:
        static std::map<std::string, read_mode_t> _map;
        static std::string _description;
        static const bool init;
        static bool __init();
        read_mode_t _rm;
    public:
        inline read_arg() : _rm(NONE) {}
        inline read_arg(read_mode_t rm) : _rm(rm) {}

        inline read_mode_t val() const { return _rm; }
        inline void set_val(const read_mode_t rm) { _rm = rm; }
        void parse(const std::string& str);

        inline const std::string& str() const { return str(_rm); }
        static const std::string& str(const read_mode_t rm);
        static const std::string& descr();

        friend std::ostream& operator<<(std::ostream& os, const read_arg& ra);
        friend std::istream& operator>>(std::istream& is, read_arg& ra);
};

} // namespace jtt
} // namespace mrg

#endif // ifndef mrg_jtt_read_arg_hpp
