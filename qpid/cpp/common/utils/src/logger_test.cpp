/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
#include <iostream>
#include <string>
#include "logger.h"

using namespace qpid::utils;

void run_sequence(Logger& log)
{
    bool b = true;
    short s = -5;
    unsigned short us = 12;
    int i = -2345;
    unsigned int ui = 34567;
    long l = -12345678;
    unsigned long ul = 23456789;
    long long ll = -1234567890;
    unsigned long long ull = 1234567890;
    float f = -123.45678;
    double d = 123.45678901;
    long double ld = 23456.789012345678;
    char* cstr = "This is a test C string.";
    char* cr = "\n";
    std::string str("This is a test std::string");
    log << "bool = " << b << cr;
    log << "short = " << s << cr;
    log << "unsigned sort = " << us << cr;
    log << "int = " << i << cr;
    log << "unsigned int = " << ui << cr;
    log << "long = " << l << cr;
    log << "unsigned long = " << ul << cr;
    log << "long long = " << ll << cr;
    log << "unsigned long long = " << ull << cr;
    log << "float = " << f << cr;
    log << "double = " << d << cr;
    log << "long double = " << ld << cr;
    log << "char* = " << cstr << cr;
    log << "std::string = " << str << cr;
    log << "String 1\n";
    log << "String 2\n" << "String 3 " << "String 4\n";
    log << "Literal bool = " << false << cr;
    log << "Literal unsigned int = " << 15 << cr;
    log << "Literal double = " << (double)15 << cr;
}

int main(int argc, char** argv)
{
    Logger log("test_log.txt", false);
    std::cout << "****** Initial state (echo off, timestamp on)" << std::endl;
    run_sequence(log);
    std::cout << std::endl << "****** (echo off, timestamp off)" << std::endl;
    log.setTimestampFlag(false);
    run_sequence(log);
    std::cout << std::endl << "****** (echo on, timestamp on)" << std::endl;
    log.setEchoFlag(true);
    log.setTimestampFlag(true);
    run_sequence(log);
    std::cout << std::endl << "****** (echo on, timestamp off)" << std::endl;
    log.setTimestampFlag(false);
    run_sequence(log);
    return 0;
}
