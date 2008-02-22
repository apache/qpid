// Visitors
template <class Base> struct Visitor;
template <class Base, class F, class R> FunctorVisitor;

/** Template base implementation for visitables. */
template <class Base, class Derived>
struct VisitableBase : public Base {
    virtual void accept(Visitor<Derived>& v) {
        v.visit(static_cast<Derived>&(*this));
    }
    virtual void accept(Visitor<Derived>& v) const {
        v.visit(static_cast<const Derived>&(*this));
    }
};

