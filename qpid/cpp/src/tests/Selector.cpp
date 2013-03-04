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
#include "qpid/broker/Selector.h"

#include "unit_test.h"

#include <string>
#include <map>

using std::string;
using std::map;

namespace qb = qpid::broker;

using qpid::broker::Token;
using qpid::broker::TokenType;
using qpid::broker::Tokeniser;
using qpid::broker::tokeniseEos;
using qpid::broker::tokeniseIdentifier;
using qpid::broker::tokeniseIdentifierOrReservedWord;
using qpid::broker::tokeniseReservedWord;
using qpid::broker::tokeniseOperator;
using qpid::broker::tokeniseParens;
using qpid::broker::tokeniseNumeric;
using qpid::broker::tokeniseString;

namespace qpid {
namespace tests {

QPID_AUTO_TEST_SUITE(SelectorSuite)

typedef bool (*TokeniseF)(string::const_iterator&,string::const_iterator&,Token&);

void verifyTokeniserSuccess(TokeniseF t, const char* ss, TokenType tt, const char* tv, const char* fs) {
    Token tok;
    string s(ss);
    string::const_iterator sb = s.begin();
    string::const_iterator se = s.end();
    BOOST_CHECK(t(sb, se, tok));
    BOOST_CHECK_EQUAL(tok, Token(tt, tv));
    BOOST_CHECK_EQUAL(string(sb, se), fs);
}

void verifyTokeniserFail(TokeniseF t, const char* c) {
    Token tok;
    string s(c);
    string::const_iterator sb = s.begin();
    string::const_iterator se = s.end();
    BOOST_CHECK(!t(sb, se, tok));
    BOOST_CHECK_EQUAL(string(sb, se), c);
}

QPID_AUTO_TEST_CASE(tokeniseSuccess)
{
    verifyTokeniserSuccess(&tokeniseEos, "", qb::T_EOS, "", "");
    verifyTokeniserSuccess(&tokeniseIdentifier, "null_123+blah", qb::T_IDENTIFIER, "null_123", "+blah");
    verifyTokeniserSuccess(&tokeniseIdentifierOrReservedWord, "null_123+blah", qb::T_IDENTIFIER, "null_123", "+blah");
    verifyTokeniserSuccess(&tokeniseIdentifierOrReservedWord, "null+blah", qb::T_NULL, "null", "+blah");
    verifyTokeniserSuccess(&tokeniseIdentifierOrReservedWord, "null+blah", qb::T_NULL, "null", "+blah");
    verifyTokeniserSuccess(&tokeniseIdentifierOrReservedWord, "Is nOt null", qb::T_IS, "Is", " nOt null");
    verifyTokeniserSuccess(&tokeniseIdentifierOrReservedWord, "nOt null", qb::T_NOT, "nOt", " null");
    verifyTokeniserSuccess(&tokeniseIdentifierOrReservedWord, "Is nOt null", qb::T_IS, "Is", " nOt null");
    verifyTokeniserSuccess(&tokeniseString, "'Hello World'", qb::T_STRING, "Hello World", "");
    verifyTokeniserSuccess(&tokeniseString, "'Hello World''s end'a bit more", qb::T_STRING, "Hello World's end", "a bit more");
    verifyTokeniserSuccess(&tokeniseOperator, "=blah", qb::T_OPERATOR, "=", "blah");
    verifyTokeniserSuccess(&tokeniseOperator, "<> Identifier", qb::T_OPERATOR, "<>", " Identifier");
    verifyTokeniserSuccess(&tokeniseParens, "(a and b) not c", qb::T_LPAREN, "(", "a and b) not c");
    verifyTokeniserSuccess(&tokeniseParens, ") not c", qb::T_RPAREN, ")", " not c");
}

QPID_AUTO_TEST_CASE(tokeniseFailure)
{
    verifyTokeniserFail(&tokeniseEos, "hb23");
    verifyTokeniserFail(&tokeniseIdentifier, "123");
    verifyTokeniserFail(&tokeniseIdentifier, "'Embedded 123'");
    verifyTokeniserFail(&tokeniseReservedWord, "1.2e5");
    verifyTokeniserFail(&tokeniseReservedWord, "'Stringy thing'");
    verifyTokeniserFail(&tokeniseReservedWord, "oR_andsomething");
    verifyTokeniserFail(&tokeniseString, "'Embedded 123");
    verifyTokeniserFail(&tokeniseString, "'This isn''t fair");
    verifyTokeniserFail(&tokeniseOperator, "123");
    verifyTokeniserFail(&tokeniseOperator, "'Stringy thing'");
    verifyTokeniserFail(&tokeniseOperator, "NoT");
    verifyTokeniserFail(&tokeniseOperator, "(a and b)");
    verifyTokeniserFail(&tokeniseOperator, ")");
    verifyTokeniserFail(&tokeniseParens, "=");
    verifyTokeniserFail(&tokeniseParens, "what ho!");
}

QPID_AUTO_TEST_CASE(tokenString)
{

    string exp("  a =b");
    string::const_iterator s = exp.begin();
    string::const_iterator e = exp.end();
    Tokeniser t(s, e);

    BOOST_CHECK_EQUAL(t.nextToken(), Token(qb::T_IDENTIFIER, "a"));
    BOOST_CHECK_EQUAL(t.nextToken(), Token(qb::T_OPERATOR, "="));
    BOOST_CHECK_EQUAL(t.nextToken(), Token(qb::T_IDENTIFIER, "b"));
    BOOST_CHECK_EQUAL(t.nextToken(), Token(qb::T_EOS, ""));

    exp = " not 'hello kitty''s friend' = Is null       ";
    s = exp.begin();
    e = exp.end();
    Tokeniser u(s, e);

    BOOST_CHECK_EQUAL(u.nextToken(), Token(qb::T_NOT, "not"));
    BOOST_CHECK_EQUAL(u.nextToken(), Token(qb::T_STRING, "hello kitty's friend"));
    BOOST_CHECK_EQUAL(u.nextToken(), Token(qb::T_OPERATOR, "="));
    BOOST_CHECK_EQUAL(u.nextToken(), Token(qb::T_IS, "Is"));
    BOOST_CHECK_EQUAL(u.nextToken(), Token(qb::T_NULL, "null"));
    BOOST_CHECK_EQUAL(u.nextToken(), Token(qb::T_EOS, ""));
    BOOST_CHECK_EQUAL(u.nextToken(), Token(qb::T_EOS, ""));

    u.returnTokens(3);
    BOOST_CHECK_EQUAL(u.nextToken(), Token(qb::T_IS, "Is"));
    BOOST_CHECK_EQUAL(u.nextToken(), Token(qb::T_NULL, "null"));
    BOOST_CHECK_EQUAL(u.nextToken(), Token(qb::T_EOS, ""));
    BOOST_CHECK_EQUAL(u.nextToken(), Token(qb::T_EOS, ""));
}

QPID_AUTO_TEST_CASE(parseStringFail)
{
    BOOST_CHECK_THROW(qb::Selector e("'Daft' is not null"), std::range_error);
    BOOST_CHECK_THROW(qb::Selector e("A is null not"), std::range_error);
    BOOST_CHECK_THROW(qb::Selector e("A is null or not"), std::range_error);
    BOOST_CHECK_THROW(qb::Selector e("A is null or and"), std::range_error);
    BOOST_CHECK_THROW(qb::Selector e("A is null and 'hello out there'"), std::range_error);
    BOOST_CHECK_THROW(qb::Selector e("A is null and (B='hello out there'"), std::range_error);
    BOOST_CHECK_THROW(qb::Selector e("in='hello kitty'"), std::range_error);
}

class TestSelectorEnv : public qpid::broker::SelectorEnv {
    map<string, string> values;

    bool present(const std::string& v) const {
        return values.find(v)!=values.end();
    }

    std::string value(const std::string& v) const {
        return present(v) ? values.at(v) : "";
    }

public:
    void set(const string& id, const string& value) {
        values[id] = value;
    }
};

QPID_AUTO_TEST_CASE(parseString)
{
    qb::Selector a("A is not null");
    qb::Selector a1("A is null");
    qb::Selector a2("A = C");
    qb::Selector a3("A <> C");
    qb::Selector c("C is not null");
    qb::Selector c1("C is null");
    qb::Selector d("A='hello kitty'");
    qb::Selector e("A<>'hello kitty'");
    qb::Selector f("A=B");
    qb::Selector g("A<>B");
    qb::Selector h("A='hello kitty' OR B='Bye, bye cruel world'");
    qb::Selector i("B='hello kitty' OR A='Bye, bye cruel world'");
    qb::Selector j("B='hello kitty' AnD A='Bye, bye cruel world'");
    qb::Selector k("B='hello kitty' AnD B='Bye, bye cruel world'");
    qb::Selector a4("A is null or A='Bye, bye cruel world'");
    qb::Selector a5("Z is null OR A is not null and A<>'Bye, bye cruel world'");
    qb::Selector a6("(Z is null OR A is not null) and A<>'Bye, bye cruel world'");
    qb::Selector t("NOT C is not null OR C is null");
    qb::Selector n("Not A='' or B=z");

    TestSelectorEnv env;
    env.set("A", "Bye, bye cruel world");
    env.set("B", "hello kitty");

    BOOST_CHECK(a.eval(env));
    BOOST_CHECK(!a1.eval(env));
    BOOST_CHECK(!a2.eval(env));
    BOOST_CHECK(a3.eval(env));
    BOOST_CHECK(!c.eval(env));
    BOOST_CHECK(c1.eval(env));
    BOOST_CHECK(!d.eval(env));
    BOOST_CHECK(e.eval(env));
    BOOST_CHECK(!f.eval(env));
    BOOST_CHECK(g.eval(env));
    BOOST_CHECK(!h.eval(env));
    BOOST_CHECK(i.eval(env));
    BOOST_CHECK(j.eval(env));
    BOOST_CHECK(!k.eval(env));
    BOOST_CHECK(a4.eval(env));
    BOOST_CHECK(a5.eval(env));
    BOOST_CHECK(!a6.eval(env));
    BOOST_CHECK(t.eval(env));
    BOOST_CHECK(n.eval(env));
}

QPID_AUTO_TEST_SUITE_END()

}}
