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
#include "qpid/broker/SelectorValue.h"

#include "unit_test.h"

#include <string>
#include <map>
#include <boost/ptr_container/ptr_vector.hpp>

using std::string;
using std::map;
using std::vector;

namespace qb = qpid::broker;

using qpid::broker::Token;
using qpid::broker::TokenType;
using qpid::broker::Tokeniser;
using qpid::broker::tokenise;

namespace qpid {
namespace tests {

QPID_AUTO_TEST_SUITE(SelectorSuite)

typedef bool (*TokeniseF)(string::const_iterator&,string::const_iterator&,Token&);

bool tokeniseEos(std::string::const_iterator& s, std::string::const_iterator& e, Token& tok)
{
    Token t1;
    std::string::const_iterator t = s;
    bool r = tokenise(t, e, t1);
    if (r && (t1.type==qb::T_EOS)) {tok = t1; s = t; return true;}
    return false;
}

bool tokeniseParens(std::string::const_iterator& s, std::string::const_iterator& e, Token& tok)
{
    Token t1;
    std::string::const_iterator t = s;
    bool r = tokenise(t, e, t1);
    if (r && (t1.type==qb::T_LPAREN || t1.type==qb::T_RPAREN)) {tok = t1; s = t; return true;}
    return false;
}

bool tokeniseOperator(std::string::const_iterator& s, std::string::const_iterator& e, Token& tok)
{
    Token t1;
    std::string::const_iterator t = s;
    bool r = tokenise(t, e, t1);
    if (r && (t1.type>=qb::T_PLUS && t1.type<=qb::T_GREQ)) {tok = t1; s = t; return true;}
    return false;
}

bool tokeniseString(std::string::const_iterator& s, std::string::const_iterator& e, Token& tok)
{
    Token t1;
    std::string::const_iterator t = s;
    bool r = tokenise(t, e, t1);
    if (r && (t1.type==qb::T_STRING)) {tok = t1; s = t; return true;}
    return false;
}

bool tokeniseIdentifier(std::string::const_iterator& s, std::string::const_iterator& e, Token& tok)
{
    Token t1;
    std::string::const_iterator t = s;
    bool r = tokenise(t, e, t1);
    if (r && (t1.type==qb::T_IDENTIFIER)) {tok = t1; s = t; return true;}
    return false;
}

bool tokeniseReservedWord(std::string::const_iterator& s, std::string::const_iterator& e, Token& tok)
{
    std::string::const_iterator t = s;
    Token t1;
    if (tokenise(t, e, t1)) {
    switch (t1.type) {
    case qb::T_AND:
    case qb::T_BETWEEN:
    case qb::T_ESCAPE:
    case qb::T_FALSE:
    case qb::T_IN:
    case qb::T_IS:
    case qb::T_LIKE:
    case qb::T_NOT:
    case qb::T_NULL:
    case qb::T_OR:
    case qb::T_TRUE:
        tok = t1;
        s = t;
        return true;
    default:
        break;
    }
    }
    return false;
}

bool tokeniseNumeric(std::string::const_iterator& s, std::string::const_iterator& e, Token& tok)
{
    Token t1;
    std::string::const_iterator t = s;
    bool r = tokenise(t, e, t1);
    if (r && (t1.type==qb::T_NUMERIC_EXACT || t1.type==qb::T_NUMERIC_APPROX)) {tok = t1; s = t; return true;}
    return false;
}


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
    verifyTokeniserSuccess(&tokenise, "", qb::T_EOS, "", "");
    verifyTokeniserSuccess(&tokenise, " ", qb::T_EOS, "", "");
    verifyTokeniserSuccess(&tokenise, "null_123+blah", qb::T_IDENTIFIER, "null_123", "+blah");
    verifyTokeniserSuccess(&tokenise, "\"null-123\"+blah", qb::T_IDENTIFIER, "null-123", "+blah");
    verifyTokeniserSuccess(&tokenise, "\"This is an \"\"odd!\"\" identifier\"+blah", qb::T_IDENTIFIER, "This is an \"odd!\" identifier", "+blah");
    verifyTokeniserSuccess(&tokenise, "null+blah", qb::T_NULL, "null", "+blah");
    verifyTokeniserSuccess(&tokenise, "null+blah", qb::T_NULL, "null", "+blah");
    verifyTokeniserSuccess(&tokenise, "Is nOt null", qb::T_IS, "Is", " nOt null");
    verifyTokeniserSuccess(&tokenise, "nOt null", qb::T_NOT, "nOt", " null");
    verifyTokeniserSuccess(&tokenise, "Is nOt null", qb::T_IS, "Is", " nOt null");
    verifyTokeniserSuccess(&tokenise, "'Hello World'", qb::T_STRING, "Hello World", "");
    verifyTokeniserSuccess(&tokenise, "'Hello World''s end'a bit more", qb::T_STRING, "Hello World's end", "a bit more");
    verifyTokeniserSuccess(&tokenise, "=blah", qb::T_EQUAL, "=", "blah");
    verifyTokeniserSuccess(&tokenise, "<> Identifier", qb::T_NEQ, "<>", " Identifier");
    verifyTokeniserSuccess(&tokenise, "(a and b) not c", qb::T_LPAREN, "(", "a and b) not c");
    verifyTokeniserSuccess(&tokenise, ") not c", qb::T_RPAREN, ")", " not c");
    verifyTokeniserSuccess(&tokenise, "017kill", qb::T_NUMERIC_EXACT, "017", "kill");
    verifyTokeniserSuccess(&tokenise, "019kill", qb::T_NUMERIC_EXACT, "01", "9kill");
    verifyTokeniserSuccess(&tokenise, "0kill", qb::T_NUMERIC_EXACT, "0", "kill");
    verifyTokeniserSuccess(&tokenise, "0.kill", qb::T_NUMERIC_APPROX, "0.", "kill");
    verifyTokeniserSuccess(&tokenise, "3.1415=pi", qb::T_NUMERIC_APPROX, "3.1415", "=pi");
    verifyTokeniserSuccess(&tokenise, ".25.kill", qb::T_NUMERIC_APPROX, ".25", ".kill");
    verifyTokeniserSuccess(&tokenise, "2e5.kill", qb::T_NUMERIC_APPROX, "2e5", ".kill");
    verifyTokeniserSuccess(&tokenise, "3.e50easy to kill", qb::T_NUMERIC_APPROX, "3.e50", "easy to kill");
    verifyTokeniserSuccess(&tokenise, "34.25e+50easy to kill", qb::T_NUMERIC_APPROX, "34.25e+50", "easy to kill");
    verifyTokeniserSuccess(&tokenise, "34de", qb::T_NUMERIC_APPROX, "34d", "e");
    verifyTokeniserSuccess(&tokenise, "34fuller", qb::T_NUMERIC_APPROX, "34f", "uller");
    verifyTokeniserSuccess(&tokenise, "34Longer", qb::T_NUMERIC_EXACT, "34L", "onger");
    verifyTokeniserSuccess(&tokenise, "34littler", qb::T_NUMERIC_EXACT, "34l", "ittler");
    verifyTokeniserSuccess(&tokenise, "034Longer", qb::T_NUMERIC_EXACT, "034L", "onger");
    verifyTokeniserSuccess(&tokenise, "034littler", qb::T_NUMERIC_EXACT, "034l", "ittler");
    verifyTokeniserSuccess(&tokenise, "0X34littler", qb::T_NUMERIC_EXACT, "0X34l", "ittler");
    verifyTokeniserSuccess(&tokenise, "0X3456_fffflittler", qb::T_NUMERIC_EXACT, "0X3456_ffffl", "ittler");
    verifyTokeniserSuccess(&tokenise, "0xdead_beafittler", qb::T_NUMERIC_EXACT, "0xdead_beaf", "ittler");
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
    verifyTokeniserFail(&tokeniseNumeric, "kill");
    verifyTokeniserFail(&tokeniseNumeric, "e3");
    verifyTokeniserFail(&tokeniseNumeric, "1.e.5");
    verifyTokeniserFail(&tokeniseNumeric, ".e5");
    verifyTokeniserFail(&tokeniseNumeric, "34e");
    verifyTokeniserFail(&tokeniseNumeric, ".3e+");
    verifyTokeniserFail(&tokeniseNumeric, ".3e-.");
    verifyTokeniserFail(&tokenise, "0b34Longer");
    verifyTokeniserFail(&tokenise, "0X_34Longer");
}

QPID_AUTO_TEST_CASE(tokenString)
{

    string exp("  a =b");
    string::const_iterator s = exp.begin();
    string::const_iterator e = exp.end();
    Tokeniser t(s, e);

    BOOST_CHECK_EQUAL(t.nextToken(), Token(qb::T_IDENTIFIER, "a"));
    BOOST_CHECK_EQUAL(t.nextToken(), Token(qb::T_EQUAL, "="));
    BOOST_CHECK_EQUAL(t.nextToken(), Token(qb::T_IDENTIFIER, "b"));
    BOOST_CHECK_EQUAL(t.nextToken(), Token(qb::T_EOS, ""));

    exp = " not 'hello kitty''s friend' = Is null       ";
    s = exp.begin();
    e = exp.end();
    Tokeniser u(s, e);

    BOOST_CHECK_EQUAL(u.nextToken(), Token(qb::T_NOT, "not"));
    BOOST_CHECK_EQUAL(u.nextToken(), Token(qb::T_STRING, "hello kitty's friend"));
    BOOST_CHECK_EQUAL(u.nextToken(), Token(qb::T_EQUAL, "="));
    BOOST_CHECK_EQUAL(u.nextToken(), Token(qb::T_IS, "Is"));
    BOOST_CHECK_EQUAL(u.nextToken(), Token(qb::T_NULL, "null"));
    BOOST_CHECK_EQUAL(u.nextToken(), Token(qb::T_EOS, ""));
    BOOST_CHECK_EQUAL(u.nextToken(), Token(qb::T_EOS, ""));

    u.returnTokens(3);
    BOOST_CHECK_EQUAL(u.nextToken(), Token(qb::T_IS, "Is"));
    BOOST_CHECK_EQUAL(u.nextToken(), Token(qb::T_NULL, "null"));
    BOOST_CHECK_EQUAL(u.nextToken(), Token(qb::T_EOS, ""));
    BOOST_CHECK_EQUAL(u.nextToken(), Token(qb::T_EOS, ""));

    exp = "(a+6)*7.5/1e6";
    s = exp.begin();
    e = exp.end();
    Tokeniser v(s, e);

    BOOST_CHECK_EQUAL(v.nextToken(), Token(qb::T_LPAREN, "("));
    BOOST_CHECK_EQUAL(v.nextToken(), Token(qb::T_IDENTIFIER, "a"));
    BOOST_CHECK_EQUAL(v.nextToken(), Token(qb::T_PLUS, "+"));
    BOOST_CHECK_EQUAL(v.nextToken(), Token(qb::T_NUMERIC_EXACT, "6"));
    BOOST_CHECK_EQUAL(v.nextToken(), Token(qb::T_RPAREN, ")"));
    BOOST_CHECK_EQUAL(v.nextToken(), Token(qb::T_MULT, "*"));
    BOOST_CHECK_EQUAL(v.nextToken(), Token(qb::T_NUMERIC_APPROX, "7.5"));
    BOOST_CHECK_EQUAL(v.nextToken(), Token(qb::T_DIV, "/"));
    BOOST_CHECK_EQUAL(v.nextToken(), Token(qb::T_NUMERIC_APPROX, "1e6"));
}

QPID_AUTO_TEST_CASE(parseStringFail)
{
    BOOST_CHECK_THROW(qb::Selector e("hello world"), std::range_error);
    BOOST_CHECK_THROW(qb::Selector e("hello ^ world"), std::range_error);
    BOOST_CHECK_THROW(qb::Selector e("A is null not"), std::range_error);
    BOOST_CHECK_THROW(qb::Selector e("A is null or not"), std::range_error);
    BOOST_CHECK_THROW(qb::Selector e("A is null or and"), std::range_error);
    BOOST_CHECK_THROW(qb::Selector e("A is null and (B='hello out there'"), std::range_error);
    BOOST_CHECK_THROW(qb::Selector e("in='hello kitty'"), std::range_error);
    BOOST_CHECK_THROW(qb::Selector e("A like 234"), std::range_error);
    BOOST_CHECK_THROW(qb::Selector e("A not 234 escape"), std::range_error);
    BOOST_CHECK_THROW(qb::Selector e("A not like 'eclecti_' escape 'happy'"), std::range_error);
    BOOST_CHECK_THROW(qb::Selector e("A not like 'eclecti_' escape happy"), std::range_error);
    BOOST_CHECK_THROW(qb::Selector e("A not like 'eclecti_' escape '%'"), std::range_error);
    BOOST_CHECK_THROW(qb::Selector e("A BETWEEN AND 'true'"), std::range_error);
    BOOST_CHECK_THROW(qb::Selector e("A NOT BETWEEN 34 OR 3.9"), std::range_error);
    BOOST_CHECK_THROW(qb::Selector e("A IN ()"), std::range_error);
    BOOST_CHECK_THROW(qb::Selector e("A NOT IN ()"), std::range_error);
    BOOST_CHECK_THROW(qb::Selector e("A IN 'hello', 'there', 1, true, (1-17))"), std::range_error);
    BOOST_CHECK_THROW(qb::Selector e("A IN ('hello', 'there' 1, true, (1-17))"), std::range_error);
}

QPID_AUTO_TEST_CASE(parseString)
{
    BOOST_CHECK_NO_THROW(qb::Selector e("'Daft' is not null"));
    BOOST_CHECK_NO_THROW(qb::Selector e("42 is null"));
    BOOST_CHECK_NO_THROW(qb::Selector e("A is not null"));
    BOOST_CHECK_NO_THROW(qb::Selector e("A is null"));
    BOOST_CHECK_NO_THROW(qb::Selector e("A = C"));
    BOOST_CHECK_NO_THROW(qb::Selector e("A <> C"));
    BOOST_CHECK_NO_THROW(qb::Selector e("A='hello kitty'"));
    BOOST_CHECK_NO_THROW(qb::Selector e("A<>'hello kitty'"));
    BOOST_CHECK_NO_THROW(qb::Selector e("A=B"));
    BOOST_CHECK_NO_THROW(qb::Selector e("A<>B"));
    BOOST_CHECK_NO_THROW(qb::Selector e("A='hello kitty' OR B='Bye, bye cruel world'"));
    BOOST_CHECK_NO_THROW(qb::Selector e("B='hello kitty' AnD A='Bye, bye cruel world'"));
    BOOST_CHECK_NO_THROW(qb::Selector e("A is null or A='Bye, bye cruel world'"));
    BOOST_CHECK_NO_THROW(qb::Selector e("Z is null OR A is not null and A<>'Bye, bye cruel world'"));
    BOOST_CHECK_NO_THROW(qb::Selector e("(Z is null OR A is not null) and A<>'Bye, bye cruel world'"));
    BOOST_CHECK_NO_THROW(qb::Selector e("NOT C is not null OR C is null"));
    BOOST_CHECK_NO_THROW(qb::Selector e("Not A='' or B=z"));
    BOOST_CHECK_NO_THROW(qb::Selector e("Not A=17 or B=5.6"));
    BOOST_CHECK_NO_THROW(qb::Selector e("A<>17 and B=5.6e17"));
    BOOST_CHECK_NO_THROW(qb::Selector e("A LIKE 'excep%ional'"));
    BOOST_CHECK_NO_THROW(qb::Selector e("B NOT LIKE 'excep%ional'"));
    BOOST_CHECK_NO_THROW(qb::Selector e("A LIKE 'excep%ional' EScape '\'"));
    BOOST_CHECK_NO_THROW(qb::Selector e("A BETWEEN 13 AND 'true'"));
    BOOST_CHECK_NO_THROW(qb::Selector e("A NOT BETWEEN 100 AND 3.9"));
    BOOST_CHECK_NO_THROW(qb::Selector e("true"));
    BOOST_CHECK_NO_THROW(qb::Selector e("-354"));
    BOOST_CHECK_NO_THROW(qb::Selector e("-(X or Y)"));
    BOOST_CHECK_NO_THROW(qb::Selector e("-687 or 567"));
    BOOST_CHECK_NO_THROW(qb::Selector e("(354.6)"));
    BOOST_CHECK_NO_THROW(qb::Selector e("A is null and 'hello out there'"));
    BOOST_CHECK_NO_THROW(qb::Selector e("17/4>4"));
    BOOST_CHECK_NO_THROW(qb::Selector e("17/4>+4"));
    BOOST_CHECK_NO_THROW(qb::Selector e("17/4>-4"));
    BOOST_CHECK_NO_THROW(qb::Selector e("A IN ('hello', 'there', 1 , true, (1-17))"));
}

class TestSelectorEnv : public qpid::broker::SelectorEnv {
    mutable map<string, qb::Value> values;
    boost::ptr_vector<string> strings;
    static const qb::Value EMPTY;

    const qb::Value& value(const string& v) const {
        const qb::Value& r = values.find(v)!=values.end() ? values[v] : EMPTY;
        return r;
    }

public:
    void set(const string& id, const char* value) {
        strings.push_back(new string(value));
        values[id] = strings[strings.size()-1];
    }

    void set(const string& id, const qb::Value& value) {
        if (value.type==qb::Value::T_STRING) {
            strings.push_back(new string(*value.s));
            values[id] = strings[strings.size()-1];
        } else {
            values[id] = value;
        }
    }
};

const qb::Value TestSelectorEnv::EMPTY;

QPID_AUTO_TEST_CASE(simpleEval)
{
    TestSelectorEnv env;
    env.set("A", "Bye, bye cruel world");
    env.set("B", "hello kitty");

    BOOST_CHECK(qb::Selector("").eval(env));
    BOOST_CHECK(qb::Selector(" ").eval(env));
    BOOST_CHECK(qb::Selector("A is not null").eval(env));
    BOOST_CHECK(!qb::Selector("A is null").eval(env));
    BOOST_CHECK(!qb::Selector("A = C").eval(env));
    BOOST_CHECK(!qb::Selector("A <> C").eval(env));
    BOOST_CHECK(!qb::Selector("C is not null").eval(env));
    BOOST_CHECK(qb::Selector("C is null").eval(env));
    BOOST_CHECK(qb::Selector("A='Bye, bye cruel world'").eval(env));
    BOOST_CHECK(!qb::Selector("A<>'Bye, bye cruel world'").eval(env));
    BOOST_CHECK(!qb::Selector("A='hello kitty'").eval(env));
    BOOST_CHECK(qb::Selector("A<>'hello kitty'").eval(env));
    BOOST_CHECK(!qb::Selector("A=B").eval(env));
    BOOST_CHECK(qb::Selector("A<>B").eval(env));
    BOOST_CHECK(!qb::Selector("A='hello kitty' OR B='Bye, bye cruel world'").eval(env));
    BOOST_CHECK(qb::Selector("B='hello kitty' OR A='Bye, bye cruel world'").eval(env));
    BOOST_CHECK(qb::Selector("B='hello kitty' AnD A='Bye, bye cruel world'").eval(env));
    BOOST_CHECK(!qb::Selector("B='hello kitty' AnD B='Bye, bye cruel world'").eval(env));
    BOOST_CHECK(qb::Selector("A is null or A='Bye, bye cruel world'").eval(env));
    BOOST_CHECK(qb::Selector("Z is null OR A is not null and A<>'Bye, bye cruel world'").eval(env));
    BOOST_CHECK(!qb::Selector("(Z is null OR A is not null) and A<>'Bye, bye cruel world'").eval(env));
    BOOST_CHECK(qb::Selector("NOT C is not null OR C is null").eval(env));
    BOOST_CHECK(qb::Selector("Not A='' or B=z").eval(env));
    BOOST_CHECK(qb::Selector("Not A=17 or B=5.6").eval(env));
    BOOST_CHECK(!qb::Selector("A<>17 and B=5.6e17").eval(env));
    BOOST_CHECK(!qb::Selector("C=D").eval(env));
    BOOST_CHECK(qb::Selector("13 is not null").eval(env));
    BOOST_CHECK(!qb::Selector("'boo!' is null").eval(env));
    BOOST_CHECK(qb::Selector("A LIKE '%cru_l%'").eval(env));
    BOOST_CHECK(qb::Selector("'_%%_hello.th_re%' LIKE 'z_%.%z_%z%' escape 'z'").eval(env));
    BOOST_CHECK(qb::Selector("A NOT LIKE 'z_%.%z_%z%' escape 'z'").eval(env));
    BOOST_CHECK(qb::Selector("'{}[]<>,.!\"$%^&*()_-+=?/|\\' LIKE '{}[]<>,.!\"$z%^&*()z_-+=?/|\\' escape 'z'").eval(env));
}

QPID_AUTO_TEST_CASE(numericEval)
{
    TestSelectorEnv env;
    env.set("A", 42.0);
    env.set("B", 39);

    BOOST_CHECK(qb::Selector("A>B").eval(env));
    BOOST_CHECK(qb::Selector("A=42").eval(env));
    BOOST_CHECK(qb::Selector("B=39.0").eval(env));
    BOOST_CHECK(qb::Selector("Not A=17 or B=5.6").eval(env));
    BOOST_CHECK(!qb::Selector("A<>17 and B=5.6e17").eval(env));
    BOOST_CHECK(qb::Selector("3 BETWEEN -17 and 98.5").eval(env));
    BOOST_CHECK(qb::Selector("A BETWEEN B and 98.5").eval(env));
    BOOST_CHECK(!qb::Selector("B NOT BETWEEN 35 AND 100").eval(env));
    BOOST_CHECK(!qb::Selector("A BETWEEN B and 40").eval(env));
    BOOST_CHECK(!qb::Selector("A BETWEEN C and 40").eval(env));
    BOOST_CHECK(!qb::Selector("A BETWEEN 45 and C").eval(env));
    BOOST_CHECK(qb::Selector("(A BETWEEN 40 and C) IS NULL").eval(env));
    BOOST_CHECK(qb::Selector("(A BETWEEN C and 45) IS NULL").eval(env));
    BOOST_CHECK(qb::Selector("17/4=4").eval(env));
    BOOST_CHECK(!qb::Selector("A/0=0").eval(env));
    BOOST_CHECK(qb::Selector("A*B+19<A*(B+19)").eval(env));
    BOOST_CHECK(qb::Selector("-A=0-A").eval(env));
}

QPID_AUTO_TEST_CASE(numericLiterals)
{
    TestSelectorEnv env;

    BOOST_CHECK(qb::Selector(" 9223372036854775807>0").eval(env));
    BOOST_CHECK(qb::Selector("-9223372036854775807<0").eval(env));
    BOOST_CHECK_THROW(qb::Selector(" 9223372036854775808>0").eval(env), std::range_error);
    BOOST_CHECK(qb::Selector("0x8000_0000_0000_0001=-9223372036854775807").eval(env));
    BOOST_CHECK_THROW(qb::Selector("-9223372036854775809<0").eval(env), std::range_error);
    BOOST_CHECK(qb::Selector(" 9223372036854775807L<>0").eval(env));
    BOOST_CHECK(qb::Selector("-9223372036854775807L<>0").eval(env));
    BOOST_CHECK(qb::Selector("-9223372036854775808<>0").eval(env));
    BOOST_CHECK(qb::Selector("-9223372036854775808=0x8000_0000_0000_0000").eval(env));
    BOOST_CHECK(qb::Selector("0x8000_0000_0000_0000<9223372036854775807").eval(env));
    BOOST_CHECK(qb::Selector(" 0.4f>0.3d").eval(env));
    BOOST_CHECK(qb::Selector(" 1000_020.4f>0.3d").eval(env));
    BOOST_CHECK(qb::Selector(" 1000_020.4f>0x800p-3").eval(env));
    BOOST_CHECK(qb::Selector(" 0x1000_0000=0x1000_0000p0").eval(env));
    BOOST_CHECK(qb::Selector(" 0xFF=255L").eval(env));
    BOOST_CHECK(qb::Selector(" 077L=0b111_111").eval(env));
    BOOST_CHECK(qb::Selector(" 077L=63").eval(env));
}

QPID_AUTO_TEST_CASE(comparisonEval)
{
    TestSelectorEnv env;

    BOOST_CHECK(!qb::Selector("17 > 19.0").eval(env));
    BOOST_CHECK(!qb::Selector("'hello' > 19.0").eval(env));
    BOOST_CHECK(!qb::Selector("'hello' < 19.0").eval(env));
    BOOST_CHECK(!qb::Selector("'hello' = 19.0").eval(env));
    BOOST_CHECK(!qb::Selector("'hello'>42 and 'hello'<42 and 'hello'=42 and 'hello'<>42").eval(env));
    BOOST_CHECK(qb::Selector("20 >= 19.0 and 20 > 19").eval(env));
    BOOST_CHECK(qb::Selector("42 <= 42.0 and 37.0 >= 37").eval(env));
    BOOST_CHECK(qb::Selector("(A IN ('hello', 'there', 1 , true, (1-17))) IS NULL").eval(env));
    BOOST_CHECK(qb::Selector("(-16 IN ('hello', A, 'there', true)) IS NULL").eval(env));
    BOOST_CHECK(qb::Selector("(-16 NOT IN ('hello', 'there', A, true)) IS NULL").eval(env));
    BOOST_CHECK(qb::Selector("(-16 IN ('hello', 'there', true)) IS NOT NULL").eval(env));
    BOOST_CHECK(!qb::Selector("-16 IN ('hello', 'there', true)").eval(env));
    BOOST_CHECK(qb::Selector("(-16 NOT IN ('hello', 'there', true)) IS NOT NULL").eval(env));
    BOOST_CHECK(!qb::Selector("-16 NOT IN ('hello', 'there', true)").eval(env));
    BOOST_CHECK(qb::Selector("(-16 NOT IN ('hello', 'there', A, 1 , true)) IS NULL").eval(env));
    BOOST_CHECK(qb::Selector("'hello' IN ('hello', 'there', 1 , true, (1-17))").eval(env));
    BOOST_CHECK(qb::Selector("TRUE IN ('hello', 'there', 1 , true, (1-17))").eval(env));
    BOOST_CHECK(qb::Selector("-16 IN ('hello', 'there', 1 , true, (1-17))").eval(env));
    BOOST_CHECK(!qb::Selector("-16 NOT IN ('hello', 'there', 1 , true, (1-17))").eval(env));
    BOOST_CHECK(!qb::Selector("1 IN ('hello', 'there', 'polly')").eval(env));
    BOOST_CHECK(!qb::Selector("1 NOT IN ('hello', 'there', 'polly')").eval(env));
    BOOST_CHECK(!qb::Selector("'hell' IN ('hello', 'there', 1 , true, (1-17))").eval(env));
    BOOST_CHECK(qb::Selector("('hell' IN ('hello', 'there', 1 , true, (1-17), A)) IS NULL").eval(env));
    BOOST_CHECK(qb::Selector("('hell' NOT IN ('hello', 'there', 1 , true, (1-17), A)) IS NULL").eval(env));
    BOOST_CHECK(!qb::Selector("'hello kitty' BETWEEN 30 and 40").eval(env));
    BOOST_CHECK(qb::Selector("'hello kitty' NOT BETWEEN 30 and 40").eval(env));
    BOOST_CHECK(!qb::Selector("14 BETWEEN 'aardvark' and 'zebra'").eval(env));
    BOOST_CHECK(qb::Selector("14 NOT BETWEEN 'aardvark' and 'zebra'").eval(env));
    BOOST_CHECK(!qb::Selector("TRUE BETWEEN 'aardvark' and 'zebra'").eval(env));
    BOOST_CHECK(qb::Selector("TRUE NOT BETWEEN 'aardvark' and 'zebra'").eval(env));
    BOOST_CHECK(qb::Selector("(A BETWEEN 'aardvark' and 14) IS NULL").eval(env));
    BOOST_CHECK(qb::Selector("(A NOT BETWEEN 'aardvark' and 14) IS NULL").eval(env));
    BOOST_CHECK(qb::Selector("(14 BETWEEN A and 17) IS NULL").eval(env));
    BOOST_CHECK(qb::Selector("(14 NOT BETWEEN A and 17) IS NULL").eval(env));
    BOOST_CHECK(qb::Selector("(14 BETWEEN 11 and A) IS NULL").eval(env));
    BOOST_CHECK(qb::Selector("(14 NOT BETWEEN 11 and A) IS NULL").eval(env));
    BOOST_CHECK(qb::Selector("14 NOT BETWEEN 11 and 9").eval(env));
    BOOST_CHECK(qb::Selector("14 BETWEEN -11 and 54367").eval(env));
}

QPID_AUTO_TEST_CASE(NullEval)
{
    TestSelectorEnv env;

    BOOST_CHECK(qb::Selector("P > 19.0 or (P is null)").eval(env));
    BOOST_CHECK(qb::Selector("P is null or P=''").eval(env));
    BOOST_CHECK(!qb::Selector("P=Q").eval(env));
    BOOST_CHECK(!qb::Selector("not P=Q").eval(env));
    BOOST_CHECK(!qb::Selector("not P=Q and not P=Q").eval(env));
    BOOST_CHECK(!qb::Selector("P=Q or not P=Q").eval(env));
    BOOST_CHECK(!qb::Selector("P > 19.0 or P <= 19.0").eval(env));
    BOOST_CHECK(qb::Selector("P > 19.0 or 17 <= 19.0").eval(env));
}

QPID_AUTO_TEST_SUITE_END()

}}
