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

#include "qpid/broker/SelectorToken.h"

#include <string>
#include <algorithm>
#include <iostream>
#include <cassert>
#include <cctype>

namespace qpid {
namespace broker {

// Tokenisers always take string const_iterators to mark the beginning and end of the string being tokenised
// if the tokenise is successful then the start iterator is advanced, if the tokenise fails then the start
// iterator is unchanged.

std::ostream& operator<<(std::ostream& os, const Token& t)
{
    os << "T<" << t.type << ", " << t.val << ">";
    return os;
}

TokenException::TokenException(const std::string& msg) :
    range_error(msg)
{}

// Lexically, reserved words are a subset of identifiers
// so we parse an identifier first then check if it is a reserved word and
// convert it if it is a reserved word
namespace {

struct RWEntry {
    const char* word;
    TokenType type;
};

inline bool caseless(const char* s1, const char* s2)
{
    do {
        char ls1 = std::tolower(*s1);
        char ls2 = std::tolower(*s2);
        if (ls1<ls2)
            return true;
        else if (ls1>ls2)
            return false;
    } while ( *s1++ && *s2++ );
    // Equal
    return false;
}

inline bool operator<(const RWEntry& lhs, const RWEntry& rhs) {
    return caseless(lhs.word, rhs.word);
}

}

bool tokeniseReservedWord(Token& tok)
{
    // This must be sorted!!
    static const RWEntry reserved[] = {
        {"and", T_AND},
        {"between", T_BETWEEN},
        {"escape", T_ESCAPE},
        {"false", T_FALSE},
        {"in", T_IN},
        {"is", T_IS},
        {"like", T_LIKE},
        {"not", T_NOT},
        {"null", T_NULL},
        {"or", T_OR},
        {"true", T_TRUE}
    };

    const int reserved_size = sizeof(reserved)/sizeof(RWEntry);

    if ( tok.type != T_IDENTIFIER ) return false;

    RWEntry rw;
    rw.word = tok.val.c_str();
    std::pair<const RWEntry*, const RWEntry*> entry = std::equal_range(&reserved[0], &reserved[reserved_size], rw);

    if ( entry.first==entry.second ) return false;

    tok.type = entry.first->type;
    return true;
}

// parsing strings is complicated by the need to allow embedded quotes by doubling the quote character
bool processString(std::string::const_iterator& s, std::string::const_iterator& e, char quoteChar, TokenType type, Token& tok)
{
    // We only get here once the tokeniser recognises the initial quote for a string
    // so we don't need to check for it again.
    std::string::const_iterator q = std::find(s+1, e, quoteChar);
    if ( q==e ) return false;

    std::string content(s+1, q);
    ++q;

    while ( q!=e && *q==quoteChar ) {
        std::string::const_iterator p = q;
        q = std::find(p+1, e, quoteChar);
        if ( q==e ) return false;
        content += std::string(p, q);
        ++q;
    }

    tok = Token(type, s, content);
    s = q;
    return true;
}

inline bool isIdentifierStart(char c)
{
    return std::isalpha(c) || c=='_' || c=='$';
}

inline bool isIdentifierPart(char c)
{
    return std::isalnum(c) || c=='_' || c=='$' || c=='.';
}

static const std::string END("<END>");
bool tokenise(std::string::const_iterator& s, std::string::const_iterator& e, Token& tok)
{
    std::string::const_iterator t = s;

    // Hand constructed state machine recogniser
    enum {
        START,
        REJECT,
        IDENTIFIER,
        ZERO,
        DIGIT,
        HEXDIGIT_START,
        HEXDIGIT,
        OCTDIGIT,
        BINDIGIT_START,
        BINDIGIT,
        DECIMAL_START,
        DECIMAL,
        EXPONENT_SIGN,
        EXPONENT_START,
        EXPONENT,
        ACCEPT_IDENTIFIER,
        ACCEPT_INC,
        ACCEPT_NOINC
    } state = START;

    TokenType tokType = T_EOS;
    while (true)
    switch (state) {
    case START:
        if (t==e) {tok = Token(T_EOS, s, END); return true;}
        else if (std::isspace(*t)) {++t; ++s; continue;}
        else switch (*t) {
        case '(': tokType = T_LPAREN; state = ACCEPT_INC; continue;
        case ')': tokType = T_RPAREN; state = ACCEPT_INC; continue;
        case ',': tokType = T_COMMA; state = ACCEPT_INC; continue;
        case '+': tokType = T_PLUS; state = ACCEPT_INC; continue;
        case '-': tokType = T_MINUS; state = ACCEPT_INC; continue;
        case '*': tokType = T_MULT; state = ACCEPT_INC; continue;
        case '/': tokType = T_DIV; state = ACCEPT_INC; continue;
        case '=': tokType = T_EQUAL; state = ACCEPT_INC; continue;
        case '<':
            ++t;
            if (t==e || (*t!='>' && *t!='='))
                {tokType = T_LESS; state = ACCEPT_NOINC; continue; }
            else
                {tokType = (*t=='>') ? T_NEQ : T_LSEQ; state = ACCEPT_INC; continue; }
        case '>':
            ++t;
            if (t==e || *t!='=')
                {tokType = T_GRT; state = ACCEPT_NOINC; continue;}
            else
                {tokType = T_GREQ; state = ACCEPT_INC; continue;}
        default:
            break;
        }
        if (isIdentifierStart(*t)) {++t; state = IDENTIFIER;}
        else if (*t=='\'') {return processString(s, e, '\'', T_STRING, tok);}
        else if (*t=='\"') {return processString(s, e, '\"', T_IDENTIFIER, tok);}
        else if (*t=='0') {++t; state = ZERO;}
        else if (std::isdigit(*t)) {++t; state = DIGIT;}
        else if (*t=='.') {++t; state = DECIMAL_START;}
        else state = REJECT;
        continue;
    case IDENTIFIER:
        if (t==e) {state = ACCEPT_IDENTIFIER;}
        else if (isIdentifierPart(*t)) {++t; state = IDENTIFIER;}
        else state = ACCEPT_IDENTIFIER;
        continue;
    case DECIMAL_START:
        if (t==e) {state = REJECT;}
        else if (std::isdigit(*t)) {++t; state = DECIMAL;}
        else state = REJECT;
        continue;
    case EXPONENT_SIGN:
        if (t==e) {state = REJECT;}
        else if (*t=='-' || *t=='+') {++t; state = EXPONENT_START;}
        else if (std::isdigit(*t)) {++t; state = EXPONENT;}
        else state = REJECT;
        continue;
    case EXPONENT_START:
        if (t==e) {state = REJECT;}
        else if (std::isdigit(*t)) {++t; state = EXPONENT;}
        else state = REJECT;
        continue;
    case ZERO:
        if (t==e) {tokType = T_NUMERIC_EXACT; state = ACCEPT_NOINC;}
        else if (*t=='.') {++t; state = DECIMAL;}
        else if (*t=='x' || *t=='X') {++t; state = HEXDIGIT_START;}
        else if (*t=='b' || *t=='B') {++t; state = BINDIGIT_START;}
        else state = OCTDIGIT;
        continue;
    case HEXDIGIT_START:
        if (t==e) {state = REJECT;}
        else if (std::isxdigit(*t)) {++t; state = HEXDIGIT;}
        else state = REJECT;
        continue;
    case HEXDIGIT:
        if (t==e) {tokType = T_NUMERIC_EXACT; state = ACCEPT_NOINC;}
        else if (*t=='l' || *t=='L') {tokType = T_NUMERIC_EXACT; state = ACCEPT_INC;}
        else if (std::isxdigit(*t) || *t=='_') {++t; state = HEXDIGIT;}
        else if (*t=='p' || *t=='P') {++t; state = EXPONENT_SIGN;}
        else {tokType = T_NUMERIC_EXACT; state = ACCEPT_NOINC;}
        continue;
    case BINDIGIT_START:
        if (t==e) {state = REJECT;}
        else if (*t=='0' || *t=='1') {++t; state = BINDIGIT;}
        else state = REJECT;
        continue;
    case BINDIGIT:
        if (t==e) {tokType = T_NUMERIC_EXACT; state = ACCEPT_NOINC;}
        else if (*t=='l' || *t=='L') {tokType = T_NUMERIC_EXACT; state = ACCEPT_INC;}
        else if (*t=='0' || *t=='1' || *t=='_') {++t; state = BINDIGIT;}
        else {tokType = T_NUMERIC_EXACT; state = ACCEPT_NOINC;}
        continue;
    case OCTDIGIT:
        if (t==e) {tokType = T_NUMERIC_EXACT; state = ACCEPT_NOINC;}
        else if (*t=='l' || *t=='L') {tokType = T_NUMERIC_EXACT; state = ACCEPT_INC;}
        else if ((std::isdigit(*t) && *t<'8') || *t=='_') {++t; state = OCTDIGIT;}
        else {tokType = T_NUMERIC_EXACT; state = ACCEPT_NOINC;}
        continue;
    case DIGIT:
        if (t==e) {tokType = T_NUMERIC_EXACT; state = ACCEPT_NOINC;}
        else if (*t=='l' || *t=='L') {tokType = T_NUMERIC_EXACT; state = ACCEPT_INC;}
        else if (*t=='f' || *t=='F' || *t=='d' || *t=='D') {tokType = T_NUMERIC_APPROX; state = ACCEPT_INC;}
        else if (std::isdigit(*t) || *t=='_') {++t; state = DIGIT;}
        else if (*t=='.') {++t; state = DECIMAL;}
        else if (*t=='e' || *t=='E') {++t; state = EXPONENT_SIGN;}
        else {tokType = T_NUMERIC_EXACT; state = ACCEPT_NOINC;}
        continue;
    case DECIMAL:
        if (t==e) {tokType = T_NUMERIC_APPROX; state = ACCEPT_NOINC;}
        else if (std::isdigit(*t) || *t=='_') {++t; state = DECIMAL;}
        else if (*t=='e' || *t=='E') {++t; state = EXPONENT_SIGN;}
        else if (*t=='f' || *t=='F' || *t=='d' || *t=='D') {tokType = T_NUMERIC_APPROX; state = ACCEPT_INC;}
        else {tokType = T_NUMERIC_APPROX; state = ACCEPT_NOINC;}
        continue;
    case EXPONENT:
        if (t==e) {tokType = T_NUMERIC_APPROX; state = ACCEPT_NOINC;}
        else if (std::isdigit(*t)) {++t; state = EXPONENT;}
        else if (*t=='f' || *t=='F' || *t=='d' || *t=='D') {tokType = T_NUMERIC_APPROX; state = ACCEPT_INC;}
        else {tokType = T_NUMERIC_APPROX; state = ACCEPT_NOINC;}
        continue;
    case ACCEPT_INC:
        ++t;
    case ACCEPT_NOINC:
        tok = Token(tokType, s, t);
        s = t;
        return true;
    case ACCEPT_IDENTIFIER:
        tok = Token(T_IDENTIFIER, s, t);
        s = t;
        tokeniseReservedWord(tok);
        return true;
    case REJECT:
        return false;
    };
}

Tokeniser::Tokeniser(const std::string::const_iterator& s, const std::string::const_iterator& e) :
    tokp(0),
    inStart(s),
    inp(s),
    inEnd(e)
{
}

/**
 * Skip any whitespace then look for a token, throwing an exception if no valid token
 * is found.
 *
 * Advance the string iterator past the parsed token on success. On failure the string iterator is 
 * in an undefined location.
 */
const Token& Tokeniser::nextToken()
{
    if ( tokens.size()>tokp ) return tokens[tokp++];

    // Don't extend stream of tokens further than the end of stream;
    if ( tokp>0 && tokens[tokp-1].type==T_EOS ) return tokens[tokp-1];

    tokens.push_back(Token());
    Token& tok = tokens[tokp++];

    if (tokenise(inp, inEnd, tok)) return tok;

    throw TokenException("Found illegal character");
}

void Tokeniser::returnTokens(unsigned int n)
{
    assert( n<=tokp );
    tokp-=n;
}

std::string Tokeniser::remaining()
{
    Token& currentTok = tokens[tokp];
    return std::string(currentTok.tokenStart, inEnd);
}


}}
