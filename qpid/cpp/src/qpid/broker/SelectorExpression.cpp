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

#include "qpid/broker/SelectorExpression.h"

#include "qpid/broker/Selector.h"
#include "qpid/broker/SelectorToken.h"

#include <string>
#include <memory>
#include <ostream>

#include <boost/scoped_ptr.hpp>

/*
 * Syntax for JMS style selector expressions (informal):
 *
 * Alpha ::= "a".."z" | "A".."Z"
 * Digit ::= "0".."9"
 * IdentifierInitial ::= Alpha | "_" | "$"
 * IdentifierPart ::= IdentifierInitial | Digit | "."
 * Identifier ::= IdentifierInitial IdentifierPart*
 * Constraint : Identifier NOT IN ("NULL", "TRUE", "FALSE", "NOT", "AND", "OR", "BETWEEN", "LIKE", "IN", "IS") // Case insensitive
 *
 * LiteralString ::= ("'" ~[']* "'")+ // Repeats to cope with embedded single quote
 *
 * // Currently no numerics at all
 * //LiteralExactNumeric ::= Digit+
 * //LiteralApproxNumeric ::= ( Digit "." Digit* [ "E" LiteralExactNumeric ] ) |
 * //                         ( "." Digit+ [ "E" LiteralExactNumeric ] ) |
 * //                         ( Digit+ "E" LiteralExactNumeric )
 * //LiteralBool ::= "TRUE" | "FALSE"
 * //
 *
 * Literal ::= LiteralBool | LiteralString | LiteralApproxNumeric | LiteralExactNumeric
 *
 * // Currently only simple string comparison expressions + IS NULL or IS NOT NULL
 * EqOps ::= "=" | "<>"
 *
 * ComparisonOps ::= EqOps | ">" | ">=" | "<" | "<="
 *
 * BoolExpression ::= OrExpression
 *
 * OrExpression ::= AndExpression  ( "OR" AndExpression )*
 *
 * AndExpression :: = EqualityExpression ( "AND" EqualityExpression  )*
 *
 * EqualityExpression ::= Identifier "IS" "NULL" |
 *                        Identifier "IS "NOT" "NULL" |
 *                        PrimaryExpression EqOps PrimaryExpression |
 *                        "NOT" EqualityExpression |
 *                        "(" OrExpression ")"
 *
 * PrimaryExpression :: = Identifier |
 *                        LiteralString
 *
 */

namespace qpid {
namespace broker {

class Expression;

using std::string;
using std::ostream;

// Operators

class EqualityOperator {
public:
    virtual void repr(ostream&) const = 0;
    virtual bool eval(Expression&, Expression&, const SelectorEnv&) const = 0;
};

template <typename T>
class UnaryBooleanOperator {
public:
    virtual void repr(ostream&) const = 0;
    virtual bool eval(T&, const SelectorEnv&) const = 0;
};

////////////////////////////////////////////////////

// Convenience outputters

ostream& operator<<(ostream& os, const Expression& e)
{
    e.repr(os);
    return os;
}

ostream& operator<<(ostream& os, const BoolExpression& e)
{
    e.repr(os);
    return os;
}

ostream& operator<<(ostream& os, const EqualityOperator& e)
{
    e.repr(os);
    return os;
}

template <typename T>
ostream& operator<<(ostream& os, const UnaryBooleanOperator<T>& e)
{
    e.repr(os);
    return os;
}

// Boolean Expression types...

class EqualityExpression : public BoolExpression {
    EqualityOperator* op;
    boost::scoped_ptr<Expression> e1;
    boost::scoped_ptr<Expression> e2;

public:
    EqualityExpression(EqualityOperator* o, Expression* e, Expression* e_):
        op(o),
        e1(e),
        e2(e_)
    {}

    void repr(ostream& os) const {
        os << "(" << *e1 << *op << *e2 << ")";
    }

    bool eval(const SelectorEnv& env) const {
        return op->eval(*e1, *e2, env);
    }
};

class OrExpression : public BoolExpression {
    boost::scoped_ptr<BoolExpression> e1;
    boost::scoped_ptr<BoolExpression> e2;

public:
    OrExpression(BoolExpression* e, BoolExpression* e_):
        e1(e),
        e2(e_)
    {}

    void repr(ostream& os) const {
        os << "(" << *e1 << " OR " << *e2 << ")";
    }

    // We can use the regular C++ short-circuiting operator||
    bool eval(const SelectorEnv& env) const {
        return e1->eval(env) || e2->eval(env);
    }
};

class AndExpression : public BoolExpression {
    boost::scoped_ptr<BoolExpression> e1;
    boost::scoped_ptr<BoolExpression> e2;

public:
    AndExpression(BoolExpression* e, BoolExpression* e_):
        e1(e),
        e2(e_)
    {}

    void repr(ostream& os) const {
        os << "(" << *e1 << " AND " << *e2 << ")";
    }

    // We can use the regular C++ short-circuiting operator&&
    bool eval(const SelectorEnv& env) const {
        return e1->eval(env) && e2->eval(env);
    }
};

template <typename T>
class UnaryBooleanExpression : public BoolExpression {
    UnaryBooleanOperator<T>* op;
    boost::scoped_ptr<T> e1;

public:
    UnaryBooleanExpression(UnaryBooleanOperator<T>* o, T* e) :
        op(o),
        e1(e)
    {}

    void repr(ostream& os) const {
        os << *op << "(" << *e1 << ")";
    }

    virtual bool eval(const SelectorEnv& env) const {
        return op->eval(*e1, env);
    }
};

// Expression types...

class Literal : public Expression {
    string value;

public:
    Literal(const string& v) :
        value(v)
    {}

    void repr(ostream& os) const {
        os << "'" << value << "'";
    }

    string eval(const SelectorEnv&) const {
        return value;
    }
};

class Identifier : public Expression {
    string identifier;

public:
    Identifier(const string& i) :
        identifier(i)
    {}

    void repr(ostream& os) const {
        os << "I:" << identifier;
    }

    string eval(const SelectorEnv& env) const {
        return env.value(identifier);
    }

    bool present(const SelectorEnv& env) const {
        return env.present(identifier);
    }
};

////////////////////////////////////////////////////

// Some operators...

// "="
class Eq : public EqualityOperator {
    void repr(ostream& os) const {
        os << "=";
    }

    bool eval(Expression& e1, Expression& e2, const SelectorEnv& env) const {
        return e1.eval(env) == e2.eval(env);
    }
};

// "<>"
class Neq : public EqualityOperator {
    void repr(ostream& os) const {
        os << "<>";
    }

    bool eval(Expression& e1, Expression& e2, const SelectorEnv& env) const {
        return e1.eval(env) != e2.eval(env);
    }
};

// "IS NULL"
class IsNull : public UnaryBooleanOperator<Identifier> {
    void repr(ostream& os) const {
        os << "IsNull";
    }

    bool eval(Identifier& i, const SelectorEnv& env) const {
        return !i.present(env);
    }
};

// "IS NOT NULL"
class IsNonNull : public UnaryBooleanOperator<Identifier> {
    void repr(ostream& os) const {
        os << "IsNonNull";
    }

    bool eval(Identifier& i, const SelectorEnv& env) const {
        return i.present(env);
    }
};

// "NOT"
class Not : public UnaryBooleanOperator<BoolExpression> {
    void repr(ostream& os) const {
        os << "NOT";
    }

    bool eval(BoolExpression& e, const SelectorEnv& env) const {
        return !e.eval(env);
    }
};

Eq eqOp;
Neq neqOp;
IsNull isNullOp;
IsNonNull isNonNullOp;
Not notOp;

////////////////////////////////////////////////////

// Top level parser
BoolExpression* parseTopBoolExpression(const string& exp)
{
    string::const_iterator s = exp.begin();
    string::const_iterator e = exp.end();
    Tokeniser tokeniser(s,e);
    std::auto_ptr<BoolExpression> b(parseOrExpression(tokeniser));
    if (!b.get()) throw std::range_error("Illegal selector: couldn't parse");
    if (tokeniser.nextToken().type != T_EOS) throw std::range_error("Illegal selector: too much input");
    return b.release();
}

BoolExpression* parseOrExpression(Tokeniser& tokeniser)
{
    std::auto_ptr<BoolExpression> e(parseAndExpression(tokeniser));
    if (!e.get()) return 0;
    while ( tokeniser.nextToken().type==T_OR ) {
        std::auto_ptr<BoolExpression> e1(e);
        std::auto_ptr<BoolExpression> e2(parseAndExpression(tokeniser));
        if (!e2.get()) return 0;
        e.reset(new OrExpression(e1.release(), e2.release()));
    }
    tokeniser.returnTokens();
    return e.release();
}

BoolExpression* parseAndExpression(Tokeniser& tokeniser)
{
    std::auto_ptr<BoolExpression> e(parseEqualityExpression(tokeniser));
    if (!e.get()) return 0;
    while ( tokeniser.nextToken().type==T_AND ) {
        std::auto_ptr<BoolExpression> e1(e);
        std::auto_ptr<BoolExpression> e2(parseEqualityExpression(tokeniser));
        if (!e2.get()) return 0;
        e.reset(new AndExpression(e1.release(), e2.release()));
    }
    tokeniser.returnTokens();
    return e.release();
}

BoolExpression* parseEqualityExpression(Tokeniser& tokeniser)
{
    const Token t = tokeniser.nextToken();
    if ( t.type==T_IDENTIFIER ) {
        // Check for "IS NULL" and "IS NOT NULL"
        if ( tokeniser.nextToken().type==T_IS ) {
            // The rest must be T_NULL or T_NOT, T_NULL
            switch (tokeniser.nextToken().type) {
            case T_NULL:
                return new UnaryBooleanExpression<Identifier>(&isNullOp, new Identifier(t.val));
            case T_NOT:
                if ( tokeniser.nextToken().type == T_NULL)
                    return new UnaryBooleanExpression<Identifier>(&isNonNullOp, new Identifier(t.val));
            default:
                return 0;
            }
        }
        tokeniser.returnTokens();
    } else if ( t.type==T_LPAREN ) {
        std::auto_ptr<BoolExpression> e(parseOrExpression(tokeniser));
        if (!e.get()) return 0;
        if ( tokeniser.nextToken().type!=T_RPAREN ) return 0;
        return e.release();
    } else if ( t.type==T_NOT ) {
        std::auto_ptr<BoolExpression> e(parseEqualityExpression(tokeniser));
        if (!e.get()) return 0;
        return new UnaryBooleanExpression<BoolExpression>(&notOp, e.release());
    }

    tokeniser.returnTokens();
    std::auto_ptr<Expression> e1(parsePrimaryExpression(tokeniser));
    if (!e1.get()) return 0;

    const Token op = tokeniser.nextToken();
    if (op.type != T_OPERATOR) {
        return 0;
    }

    std::auto_ptr<Expression> e2(parsePrimaryExpression(tokeniser));
    if (!e2.get()) return 0;

    if (op.val == "=") return new EqualityExpression(&eqOp, e1.release(), e2.release());
    if (op.val == "<>") return new EqualityExpression(&neqOp, e1.release(), e2.release());

    return 0;
}

Expression* parsePrimaryExpression(Tokeniser& tokeniser)
{
    const Token& t = tokeniser.nextToken();
    switch (t.type) {
        case T_IDENTIFIER:
            return new Identifier(t.val);
        case T_STRING:
            return new Literal(t.val);
        default:
            return 0;
    }
}

}}
