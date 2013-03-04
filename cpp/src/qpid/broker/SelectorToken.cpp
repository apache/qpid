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

namespace qpid {
namespace broker {

// Tokeniserss always take string const_iterators to mark the beginning and end of the string being tokenised
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

// Not much of a parser...
void skipWS(std::string::const_iterator& s, std::string::const_iterator& e)
{
    while ( s!=e && std::isspace(*s) ) {
        ++s;
    }
}

bool tokeniseEos(std::string::const_iterator& s, std::string::const_iterator& e, Token& tok)
{
    if ( s!=e ) return false;

    tok = Token(T_EOS, "");
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

bool tokeniseIdentifier(std::string::const_iterator& s, std::string::const_iterator& e, Token& tok)
{
    // Be sure that first char is alphanumeric or _ or $
    if ( s==e || !isIdentifierStart(*s) ) return false;

    std::string::const_iterator t = s;

    while ( s!=e && isIdentifierPart(*++s) );

    tok = Token(T_IDENTIFIER, t, s);

    return true;
}

// Lexically, reserved words are a subset of identifiers
// so we parse an identifier first then check if it is a reserved word and
// convert it if it is a reserved word
namespace {

struct RWEntry {
    const char* word;
    TokenType type;
};

bool caseless(const char* s1, const char* s2)
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

bool operator<(const RWEntry& r, const char* rhs) {
    return caseless(r.word, rhs);
}

bool operator<(const char* rhs, const RWEntry& r) {
    return caseless(rhs, r.word);
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

    std::pair<const RWEntry*, const RWEntry*> entry = std::equal_range(&reserved[0], &reserved[reserved_size], tok.val.c_str());

    if ( entry.first==entry.second ) return false;

    tok.type = entry.first->type;
    return true;
}

// This is really only used for testing
bool tokeniseReservedWord(std::string::const_iterator& s, std::string::const_iterator& e, Token& tok)
{
    std::string::const_iterator p = s;
    bool r = tokeniseIdentifier(p, e, tok) && tokeniseReservedWord(tok);
    if (r) s = p;
    return r;
}

bool tokeniseIdentifierOrReservedWord(std::string::const_iterator& s, std::string::const_iterator& e, Token& tok)
{
    bool r = tokeniseIdentifier(s, e, tok);
    if (r) (void) tokeniseReservedWord(tok);
    return r;
}

// parsing strings is complicated by the need to allow "''" as an embedded single quote
bool tokeniseString(std::string::const_iterator& s, std::string::const_iterator& e, Token& tok)
{
    if ( s==e || *s != '\'' ) return false;

    std::string::const_iterator q = std::find(s+1, e, '\'');
    if ( q==e ) return false;

    std::string content(s+1, q);
    ++q;

    while ( q!=e && *q=='\'' ) {
        std::string::const_iterator p = q;
        q = std::find(p+1, e, '\'');
        if ( q==e ) return false;
        content += std::string(p, q);
        ++q;
    }

    s = q;
    tok = Token(T_STRING, content);
    return true;
}

bool tokeniseParens(std::string::const_iterator& s, std::string::const_iterator& e, Token& tok)
{
    if ( s==e) return false;
    if ( *s=='(' ) {
        tok = Token (T_LPAREN, s, s+1);
        ++s;
        return true;
    }
    if ( *s==')' ) {
        tok = Token (T_RPAREN, s, s+1);
        ++s;
        return true;
    }
    return false;
}

inline bool isOperatorPart(char c)
{
    return !std::isalnum(c) && !std::isspace(c) && c!='_' && c!='$' && c!='(' && c!=')' && c!= '\'';
}

// These lexical tokens contain no alphanumerics - this is broader than actual operators but
// works.
bool tokeniseOperator(std::string::const_iterator& s, std::string::const_iterator& e, Token& tok)
{
    if ( s==e || !isOperatorPart(*s) ) return false;

    std::string::const_iterator t = s;

    while (s!=e && isOperatorPart(*++s));

    tok = Token(T_OPERATOR, t, s);
    return true;
}

bool tokeniseNumeric(std::string::const_iterator& s, std::string::const_iterator& e, Token& tok)
{
    std::string::const_iterator t = s;

    // Hand constructed state machine recogniser
    enum {
        START,
        REJECT,
        DIGIT,
        DECIMAL_START,
        DECIMAL,
        EXPONENT_SIGN,
        EXPONENT_START,
        EXPONENT,
        ACCEPT_EXACT,
        ACCEPT_INEXACT
    } state = START;

    while (true)
    switch (state) {
    case START:
        if (t==e) {state = REJECT;}
        else if (std::isdigit(*t)) {++t; state = DIGIT;}
        else if (*t=='.') {++t; state = DECIMAL_START;}
        else state = REJECT;
        break;
    case DECIMAL_START:
        if (t==e) {state = REJECT;}
        else if (std::isdigit(*t)) {++t; state = DECIMAL;}
        else state = REJECT;
        break;
    case EXPONENT_SIGN:
        if (t==e) {state = REJECT;}
        else if (*t=='-' || *t=='+') {++t; state = EXPONENT_START;}
        else if (std::isdigit(*t)) {++t; state = EXPONENT;}
        else state = REJECT;
        break;
    case EXPONENT_START:
        if (t==e) {state = REJECT;}
        else if (std::isdigit(*t)) {++t; state = EXPONENT;}
        else state = REJECT;
        break;
    case DIGIT:
        if (t==e) {state = ACCEPT_EXACT;}
        else if (std::isdigit(*t)) {++t; state = DIGIT;}
        else if (*t=='.') {++t; state = DECIMAL;}
        else if (*t=='e' || *t=='E') {++t; state = EXPONENT_SIGN;}
        else state = ACCEPT_EXACT;
        break;
    case DECIMAL:
        if (t==e) {state = ACCEPT_INEXACT;}
        else if (std::isdigit(*t)) {++t; state = DECIMAL;}
        else if (*t=='e' || *t=='E') {++t; state = EXPONENT_SIGN;}
        else state = ACCEPT_INEXACT;
        break;
    case EXPONENT:
        if (t==e) {state = ACCEPT_INEXACT;}
        else if (std::isdigit(*t)) {++t; state = EXPONENT;}
        else state = ACCEPT_INEXACT;
        break;
    case ACCEPT_EXACT:
        tok = Token(T_NUMERIC_EXACT, s, t);
        s = t;
        return true;
    case ACCEPT_INEXACT:
        tok = Token(T_NUMERIC_APPROX, s, t);
        s = t;
        return true;
    case REJECT:
        return false;
    };
}

Tokeniser::Tokeniser(const std::string::const_iterator& s, const std::string::const_iterator& e) :
    tokp(0),
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

    skipWS(inp, inEnd);

    tokens.push_back(Token());
    Token& tok = tokens[tokp++];

    if (tokeniseEos(inp, inEnd, tok)) return tok;
    if (tokeniseIdentifierOrReservedWord(inp, inEnd, tok)) return tok;
    if (tokeniseNumeric(inp, inEnd, tok)) return tok;
    if (tokeniseString(inp, inEnd, tok)) return tok;
    if (tokeniseParens(inp, inEnd, tok)) return tok;
    if (tokeniseOperator(inp, inEnd, tok)) return tok;

    throw TokenException("Found illegal character");
}

void Tokeniser::returnTokens(unsigned int n)
{
    assert( n<=tokp );
    tokp-=n;
}


}}
