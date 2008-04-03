#!/usr/bin/env ruby
$: << ".."                      # Include .. in load path
require 'cppgen'

class Specification < CppGen
  def initialize(outdir, amqp)
    super(outdir, amqp)
    @ns="qpid::amqp_#{@amqp.version.bars}"
    @dir="qpid/amqp_#{@amqp.version.bars}"
  end

  # domains

  def domain_h(d)
    genl
    typename=d.name.typename
    if d.enum
      scope("enum #{typename} {", "};") { 
        genl d.enum.choices.map { |c|
          "#{c.name.constname} = #{c.value}" }.join(",\n")
      }
      scope("inline SerializeAs<#{typename}, uint8_t> serializable(#{typename}& e) {") {
        genl "return SerializeAs<#{typename}, uint8_t>(e);"
      }
    else
      genl "typedef #{d.amqp2cpp} #{typename};"
    end
  end

  # class constants
  
  def class_h(c)
    genl "const uint8_t CODE=#{c.code};"
    genl "extern const char* NAME;"
  end
  
  def class_cpp(c)
    genl "const char* NAME=\"#{c.fqname}\";"
  end

  # Used by structs, commands and controls.
  def action_struct_h(x, base, consts, &block)
    genl
    struct(x.classname, "public #{base}") {
      x.fields.each { |f| genl "#{f.amqp2cpp} #{f.cppname};" }
      genl
      genl "static const char* NAME;"
      consts.each {
        |c| genl "static const uint8_t #{c.upcase}=#{(x.send c) or 0};"
      }
      genl "static const uint8_t CLASS_CODE=#{x.containing_class.nsname}::CODE;"
      genl "static const char* CLASS_NAME;"
      ctor_decl(x.classname,[])
      ctor_decl(x.classname, x.parameters) unless x.fields.empty?
      genl "void accept(Visitor&);" 
      genl "void accept(ConstVisitor&) const;"
      if (x.fields.empty?)
        genl "template <class S> void serialize(S&) {}"
      else
        scope("template <class S> void serialize(S& s) {") {
          gen "s"; x.fields.each { |f| gen "(#{f.cppname})"}; genl ";"
        }
      end
      genl
      yield if block
    }
    genl "std::ostream& operator << (std::ostream&, const #{x.classname}&);"
  end

  # FIXME aconway 2008-03-10: packing, coding
  def action_struct_cpp(x)
    genl
    genl "const char* #{x.classname}::NAME=\"#{x.fqname}\";"
    genl "const char* #{x.classname}::CLASS_NAME=#{x.containing_class.nsname}::NAME;"
    genl
    ctor=x.classname+"::"+x.classname
    ctor_defn(ctor) {}
    ctor_defn(ctor, x.parameters, x.initializers) {} if not x.fields.empty?
    # FIXME aconway 2008-03-04: struct visitors
    if x.is_a? AmqpStruct
      genl "void #{x.classname}::accept(Visitor&) { assert(0); }"
      genl "void #{x.classname}::accept(ConstVisitor&) const { assert(0); }"
    else
      genl "void #{x.classname}::accept(Visitor& v) {  v.visit(*this); }"
      genl "void #{x.classname}::accept(ConstVisitor& v) const { v.visit(*this); }"
    end
    genl
    scope("std::ostream& operator << (std::ostream& o, const #{x.classname}&#{"x" unless x.fields.empty?}) {") {
      genl "return o << \"[#{x.fqname}\";";
      x.fields.each{ |f| genl "o << \" #{f.name}=\" << x.#{f.cppname};" }
      genl "o << \"];\";"
    }
  end

  # structs

  def struct_h(s) action_struct_h(s, "Struct", ["size","pack","code"]); end
  def struct_cpp(s) action_struct_cpp(s) end

  # command and control
  
  def action_h(a)
    action_struct_h(a, a.base, ["code"]) {
      function_defn("template <class T> void invoke", ["T& target"]) {
        genl "target.#{a.funcname}(#{a.values.join(', ')});"
      }
    }
  end
  
  def action_cpp(a) action_struct_cpp(a); end

  # Types that must be generated early because they are used by other types.
  def pregenerate?(x) not @amqp.used_by[x.fqname].empty?;  end

  def pregenerate_class?(c)
    c.children.select{ |t| (t.is_a? AmqpStruct or t.is_a? AmqpDomain) and pregenerate? t} 
  end
  
  # Typedefs, enums and forward declarations for classes.
  def gen_specification_fwd()
    h_file("#{@dir}/specification_fwd") { 
      include "#{@dir}/built_in_types"
      namespace(@ns) {
        # Top level 
        @amqp.domains.each { |d|
          # segment-type and track are are built in
          domain_h d unless ["track","segment-type"].include?(d.name)
        }
        # Domains/structs that must be generated early because they are used by
        # other definitions:
        @amqp.classes.select{ |c| pregenerate_class?(c) }.each { |c|
          namespace(c.nsname) { 
            c.collect_all(AmqpDomain).each { |d| domain_h d if pregenerate? d }
            c.collect_all(AmqpStruct).each { |s| genl "class #{s.classname};" if pregenerate? s }
          }
        }
        # Now dependent domains/structs and actions
        each_class_ns { |c|
          class_h c
          c.collect_all(AmqpDomain).each { |d| domain_h d unless pregenerate? d}
          c.collect_all(AmqpStruct).each { |s| genl "class #{s.classname};" unless pregenerate? s }
          c.collect_all(AmqpAction).each { |a| genl "class #{a.classname};" unless pregenerate? a }
        }
      }
    }
  end
  
  # Generate the specification files
  def gen_specification()
    h_file("#{@dir}/specification") {
      include "#{@dir}/specification_fwd"
      include "#{@dir}/complex_types"
      include "#{@dir}/Map.h"
      include "<boost/call_traits.hpp>"
      include "<iosfwd>"
      genl "using boost::call_traits;"
      namespace(@ns) {
        # Structs that must be generated early because
        # they are used by other definitions:
        each_class_ns { |c|
           c.collect_all(AmqpStruct).each { |s| struct_h s if pregenerate? s }
        }
        # Now dependent domains/structs and actions
        each_class_ns { |c|
          c.collect_all(AmqpStruct).each { |s| struct_h s unless pregenerate? s}
          c.collect_all(AmqpAction).each { |a| action_h a }
        }
      }
    }

    cpp_file("#{@dir}/specification") { 
      include "#{@dir}/specification"
      include "<iostream>"
      # FIXME aconway 2008-03-04: add Struct visitors.
      ["Command","Control"].each { |x| include "#{@dir}/Apply#{x}" }
      namespace(@ns) { 
        each_class_ns { |c|
          class_cpp c
          c.actions.each { |a| action_cpp a}
          c.collect_all(AmqpStruct).each {  |s| struct_cpp(s) }
        }
      }
    }
  end
  
  def gen_proxy()
    h_file("#{@dir}/ProxyTemplate.h") { 
      include "#{@dir}/specification"
      namespace(@ns) { 
        genl "template <class F, class R=typename F::result_type>"
        cpp_class("ProxyTemplate") {
          public
          genl "ProxyTemplate(F f=F()) : functor(f) {}"
          @amqp.classes.each { |c|
            c.actions.each { |a|
              genl
              function_defn("R #{a.funcname}", a.parameters) { 
                var=a.name.funcname
                args = a.arguments.empty? ? "" : "("+a.arguments.join(", ")+")"
                genl("#{a.fqclassname} #{var}#{args};")
                genl "return functor(#{var});"
              }
            }
          }
          private
          genl "F functor;"
        }
      }
    }
  end

  def visitor_interface_h(base, subs, is_const)
    name="#{is_const ? 'Const' : ''}#{base}Visitor"
    const=is_const ? "const " : ""
    struct(name) {
      genl "virtual ~#{name}() {}"
      genl "typedef #{const}#{base} BaseType;"
      subs.each{ |s|
        genl "virtual void visit(#{const}#{s.fqclassname}&) = 0;"
      }}
  end

  def visitor_impl(base, subs, is_const)
    name="#{is_const ? 'Const' : ''}#{base}Visitor"
    const=is_const ? "const " : ""
    genl "template <class F>"
    struct("ApplyVisitor<#{name}, F>", "public ApplyVisitorBase<#{name}, F>") {
      subs.each{ |s|
        genl "virtual void visit(#{const}#{s.fqclassname}& x) { this->invoke(x); }" 
      }}
  end
  
  def gen_visitor(base, subs)
    h_file("#{@dir}/#{base}Visitor.h") { 
      include "#{@dir}/specification"
      namespace("#{@ns}") { 
        visitor_interface_h(base, subs, false)
        visitor_interface_h(base, subs, true)
      }}
    
    h_file("#{@dir}/Apply#{base}.h") {
      include "#{@dir}/#{base}Visitor.h"
      include "#{@dir}/apply.h"
      namespace("#{@ns}") { 
        visitor_impl(base, subs, false)
        visitor_impl(base, subs, true)
      }
    }
  end
  
  def gen_holder(base, subs)
    name=base.caps+"Holder"
    h_file("#{@dir}/#{name}") {
      include "#{@dir}/Apply#{base}"
      include "#{@dir}/Holder"
      namespace(@ns){
        namespace("#{base.downcase}_max") {
          gen "template <class M, class X> "
          struct("Max") {
            genl "static const size_t max=(M::max > sizeof(X)) ? M::max : sizeof(X);"
          }
          genl "struct Max000 { static const size_t max=0; };"
          last="Max000"
          subs.each { |s|
            genl "typedef Max<#{last}, #{s.fqclassname}> #{last.succ!};"
          }
          genl "static const int MAX=#{last}::max;"
        }
        holder_base="amqp_0_10::Holder<#{base}Holder, #{base}, #{base.downcase}_max::MAX>"
        struct("#{name}", "public #{holder_base}") {
          genl "#{name}() {}"
          genl "template <class T> #{name}(const T& t) : #{holder_base}(t) {}"
          genl "using #{holder_base}::operator=;"
          genl "void set(uint8_t classCode, uint8_t code);"
        }}}
    
    cpp_file("#{@dir}/#{name}") {
      include "#{@dir}/#{name}"
      namespace(@ns) {
        genl "using framing::in_place;"
        genl
        scope("void #{name}::set(uint8_t classCode, uint8_t code) {") {
          genl "uint16_t key=(classCode<<8)+code;"
          scope ("switch(key) {") {
            subs.each { |s|
              genl "case 0x#{s.full_code.to_s(16)}: *this=in_place<#{s.fqclassname}>(); break;"
            }
            genl "default: assert(0);"
          }}}}
  end

  def gen_visitable(base, subs)
    gen_holder(base, subs)
    gen_visitor(base, subs)
  end

  def generate
    gen_specification_fwd
    gen_specification
    gen_proxy
    gen_visitable("Command", @amqp.collect_all(AmqpCommand))
    gen_visitable("Control", @amqp.collect_all(AmqpControl))
    # FIXME aconway 2008-03-04: sort out visitable structs.
    # gen_visitable("Struct", @amqp.collect_all(AmqpStruct))
  end
end

Specification.new($outdir, $amqp).generate();

