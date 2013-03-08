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
#include "qpid/broker/SelectorValue.h"
#include "qpid/sys/IntegerTypes.h"

#include <string>
#include <memory>
#include <ostream>

#include <boost/scoped_ptr.hpp>
#include <boost/lexical_cast.hpp>

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
 * LiteralExactNumeric ::= Digit+
 * Exponent ::= ['+'|'-'] LiteralExactNumeric
 * LiteralApproxNumeric ::= ( Digit "." Digit* [ "E" Exponent ] ) |
 *                          ( "." Digit+ [ "E" Exponent ] ) |
 *                          ( Digit+ "E" Exponent )
 * LiteralBool ::= "TRUE" | "FALSE"
 *
 * Literal ::= LiteralBool | LiteralString | LiteralApproxNumeric | LiteralExactNumeric
 *
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
 * ComparisonExpression ::= PrimaryExpression "IS" "NULL" |
 *                        PrimaryExpression "IS" "NOT" "NULL" |
 *                        PrimaryExpression ComparisonOpsOps PrimaryExpression |
 *                        "NOT" ComparisonExpression |
 *                        "(" OrExpression ")"
 *
 * PrimaryExpression :: = Identifier |
 *                        Literal
 *
 */

namespace qpid {
namespace broker {

using std::string;
using std::ostream;

class Expression {
public:
    virtual ~Expression() {}
    virtual void repr(std::ostream&) const = 0;
    virtual Value eval(const SelectorEnv&) const = 0;
};

class BoolExpression : public Expression {
public:
    virtual ~BoolExpression() {}
    virtual void repr(std::ostream&) const = 0;
    virtual BoolOrNone eval_bool(const SelectorEnv&) const = 0;

    Value eval(const SelectorEnv& env) const {
        return eval_bool(env);
    }
};

// Operators

class ComparisonOperator {
public:
    virtual ~ComparisonOperator() {}
    virtual void repr(ostream&) const = 0;
    virtual BoolOrNone eval(Expression&, Expression&, const SelectorEnv&) const = 0;
};

template <typename T>
class UnaryBooleanOperator {
public:
    virtual ~UnaryBooleanOperator() {}
    virtual void repr(ostream&) const = 0;
    virtual BoolOrNone eval(T&, const SelectorEnv&) const = 0;
};

////////////////////////////////////////////////////

// Convenience outputters

ostream& operator<<(ostream& os, const Expression& e)
{
    e.repr(os);
    return os;
}

ostream& operator<<(ostream& os, const ComparisonOperator& e)
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

class ComparisonExpression : public BoolExpression {
    ComparisonOperator* op;
    boost::scoped_ptr<Expression> e1;
    boost::scoped_ptr<Expression> e2;

public:
    ComparisonExpression(ComparisonOperator* o, Expression* e, Expression* e_):
        op(o),
        e1(e),
        e2(e_)
    {}

    void repr(ostream& os) const {
        os << "(" << *e1 << *op << *e2 << ")";
    }

    BoolOrNone eval_bool(const SelectorEnv& env) const {
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

    BoolOrNone eval_bool(const SelectorEnv& env) const {
        BoolOrNone bn1(e1->eval_bool(env));
        if (bn1==BN_TRUE) {
            return BN_TRUE;
        } else {
            BoolOrNone bn2(e2->eval_bool(env));
            if (bn2==BN_TRUE) {
                return BN_TRUE;
            } else {
                if (bn1==BN_FALSE && bn2==BN_FALSE) return BN_FALSE;
                else return BN_UNKNOWN;
            }
        }
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

    BoolOrNone eval_bool(const SelectorEnv& env) const {
        BoolOrNone bn1(e1->eval_bool(env));
        if (bn1==BN_FALSE) {
            return BN_FALSE;
        } else {
            BoolOrNone bn2(e2->eval_bool(env));
            if (bn2==BN_FALSE) {
                return BN_FALSE;
            } else {
                if (bn1==BN_TRUE && bn2==BN_TRUE) return BN_TRUE;
                else return BN_UNKNOWN;
            }
        }
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

    virtual BoolOrNone eval_bool(const SelectorEnv& env) const {
        return op->eval(*e1, env);
    }
};

// Expression types...

class Literal : public Expression {
    const Value value;

public:
    template <typename T>
    Literal(const T& v) :
        value(v)
    {}

    void repr(ostream& os) const {
        os << value;
    }

    Value eval(const SelectorEnv&) const {
        return value;
    }
};

class StringLiteral : public Expression {
    const string value;

public:
    StringLiteral(const string& v) :
        value(v)
    {}

    void repr(ostream& os) const {
        os << "'" << value << "'";
    }

    Value eval(const SelectorEnv&) const {
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

    Value eval(const SelectorEnv& env) const {
        return env.value(identifier);
    }
};

////////////////////////////////////////////////////

// Some operators...

typedef bool BoolOp(const Value&, const Value&);

BoolOrNone booleval(BoolOp* op, Expression& e1, Expression& e2, const SelectorEnv& env) {
    const Value v1(e1.eval(env));
    if (!unknown(v1)) {
        const Value v2(e2.eval(env));
        if (!unknown(v2)) {
            return BoolOrNone(op(v1, v2));
        }
    }
    return BN_UNKNOWN;
}

// "="
class Eq : public ComparisonOperator {
    void repr(ostream& os) const {
        os << "=";
    }

    BoolOrNone eval(Expression& e1, Expression& e2, const SelectorEnv& env) const {
        return booleval(&operator==, e1, e2, env);
    }
};

// "<>"
class Neq : public ComparisonOperator {
    void repr(ostream& os) const {
        os << "<>";
    }

    BoolOrNone eval(Expression& e1, Expression& e2, const SelectorEnv& env) const {
        return booleval(&operator!=, e1, e2, env);
    }
};

// "<"
class Ls : public ComparisonOperator {
    void repr(ostream& os) const {
        os << "<";
    }

    BoolOrNone eval(Expression& e1, Expression& e2, const SelectorEnv& env) const {
        return booleval(&operator<, e1, e2, env);
    }
};

// ">"
class Gr : public ComparisonOperator {
    void repr(ostream& os) const {
        os << ">";
    }

    BoolOrNone eval(Expression& e1, Expression& e2, const SelectorEnv& env) const {
        return booleval(&operator>, e1, e2, env);
    }
};

// "<="
class Lseq : public ComparisonOperator {
    void repr(ostream& os) const {
        os << "<=";
    }

    BoolOrNone eval(Expression& e1, Expression& e2, const SelectorEnv& env) const {
        return booleval(&operator<=, e1, e2, env);
    }
};

// ">="
class Greq : public ComparisonOperator {
    void repr(ostream& os) const {
        os << ">=";
    }

    BoolOrNone eval(Expression& e1, Expression& e2, const SelectorEnv& env) const {
        return booleval(&operator>=, e1, e2, env);
    }
};

// "IS NULL"
class IsNull : public UnaryBooleanOperator<Expression> {
    void repr(ostream& os) const {
        os << "IsNull";
    }

    BoolOrNone eval(Expression& e, const SelectorEnv& env) const {
        return BoolOrNone(unknown(e.eval(env)));
    }
};

// "IS NOT NULL"
class IsNonNull : public UnaryBooleanOperator<Expression> {
    void repr(ostream& os) const {
        os << "IsNonNull";
    }

    BoolOrNone eval(Expression& e, const SelectorEnv& env) const {
        return BoolOrNone(!unknown(e.eval(env)));
    }
};

// "NOT"
class Not : public UnaryBooleanOperator<BoolExpression> {
    void repr(ostream& os) const {
        os << "NOT";
    }

    BoolOrNone eval(BoolExpression& e, const SelectorEnv& env) const {
        BoolOrNone bn = e.eval_bool(env);
        if (bn==BN_UNKNOWN) return bn;
        else return BoolOrNone(!bn);
    }
};

Eq eqOp;
Neq neqOp;
Ls lsOp;
Gr grOp;
Lseq lseqOp;
Greq greqOp;
IsNull isNullOp;
IsNonNull isNonNullOp;
Not notOp;

////////////////////////////////////////////////////

BoolExpression* parseOrExpression(Tokeniser&);
BoolExpression* parseAndExpression(Tokeniser&);
BoolExpression* parseComparisonExpression(Tokeniser&);
Expression* parsePrimaryExpression(Tokeniser&);

// Top level parser
class TopBoolExpression : public TopExpression {
    boost::scoped_ptr<BoolExpression> expression;

    void repr(ostream& os) const {
        expression->repr(os);
    }

    bool eval(const SelectorEnv& env) const {
        BoolOrNone bn = expression->eval_bool(env);
        if (bn==BN_TRUE) return true;
        else return false;
    }

public:
    TopBoolExpression(BoolExpression* be) :
        expression(be)
    {}
};

TopExpression* TopExpression::parse(const string& exp)
{
    string::const_iterator s = exp.begin();
    string::const_iterator e = exp.end();
    Tokeniser tokeniser(s,e);
    std::auto_ptr<BoolExpression> b(parseOrExpression(tokeniser));
    if (!b.get()) throw std::range_error("Illegal selector: couldn't parse");
    if (tokeniser.nextToken().type != T_EOS) throw std::range_error("Illegal selector: too much input");
    return new TopBoolExpression(b.release());
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
    std::auto_ptr<BoolExpression> e(parseComparisonExpression(tokeniser));
    if (!e.get()) return 0;
    while ( tokeniser.nextToken().type==T_AND ) {
        std::auto_ptr<BoolExpression> e1(e);
        std::auto_ptr<BoolExpression> e2(parseComparisonExpression(tokeniser));
        if (!e2.get()) return 0;
        e.reset(new AndExpression(e1.release(), e2.release()));
    }
    tokeniser.returnTokens();
    return e.release();
}

BoolExpression* parseComparisonExpression(Tokeniser& tokeniser)
{
    const Token t = tokeniser.nextToken();
    if ( t.type==T_LPAREN ) {
        std::auto_ptr<BoolExpression> e(parseOrExpression(tokeniser));
        if (!e.get()) return 0;
        if ( tokeniser.nextToken().type!=T_RPAREN ) return 0;
        return e.release();
    } else if ( t.type==T_NOT ) {
        std::auto_ptr<BoolExpression> e(parseComparisonExpression(tokeniser));
        if (!e.get()) return 0;
        return new UnaryBooleanExpression<BoolExpression>(&notOp, e.release());
    }

    tokeniser.returnTokens();
    std::auto_ptr<Expression> e1(parsePrimaryExpression(tokeniser));
    if (!e1.get()) return 0;

    // Check for "IS NULL" and "IS NOT NULL"
    if ( tokeniser.nextToken().type==T_IS ) {
        // The rest must be T_NULL or T_NOT, T_NULL
        switch (tokeniser.nextToken().type) {
            case T_NULL:
                return new UnaryBooleanExpression<Expression>(&isNullOp, e1.release());
            case T_NOT:
                if ( tokeniser.nextToken().type == T_NULL)
                    return new UnaryBooleanExpression<Expression>(&isNonNullOp, e1.release());
            default:
                return 0;
        }
    }
    tokeniser.returnTokens();

    const Token op = tokeniser.nextToken();
    if (op.type != T_OPERATOR) {
        return 0;
    }

    std::auto_ptr<Expression> e2(parsePrimaryExpression(tokeniser));
    if (!e2.get()) return 0;

    if (op.val == "=") return new ComparisonExpression(&eqOp, e1.release(), e2.release());
    if (op.val == "<>") return new ComparisonExpression(&neqOp, e1.release(), e2.release());
    if (op.val == "<") return new ComparisonExpression(&lsOp, e1.release(), e2.release());
    if (op.val == ">") return new ComparisonExpression(&grOp, e1.release(), e2.release());
    if (op.val == "<=") return new ComparisonExpression(&lseqOp, e1.release(), e2.release());
    if (op.val == ">=") return new ComparisonExpression(&greqOp, e1.release(), e2.release());

    return 0;
}

Expression* parsePrimaryExpression(Tokeniser& tokeniser)
{
    const Token& t = tokeniser.nextToken();
    switch (t.type) {
        case T_IDENTIFIER:
            return new Identifier(t.val);
        case T_STRING:
            return new StringLiteral(t.val);
        case T_FALSE:
            return new Literal(false);
        case T_TRUE:
            return new Literal(true);
        case T_NUMERIC_EXACT:
            return new Literal(boost::lexical_cast<int64_t>(t.val));
        case T_NUMERIC_APPROX:
            return new Literal(boost::lexical_cast<double>(t.val));
        default:
            return 0;
    }
}

}}
