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
      consts.each { |c| genl "static const uint8_t #{c.upcase}=#{x.send c or 0};"}
      genl "static const uint8_t CLASS_CODE=#{x.containing_class.nsname}::CODE;"
      ctor_decl(x.classname,[])
      ctor_decl(x.classname, x.parameters) unless x.fields.empty?
      function_decl("void accept", ["Visitor&"], "const")
      genl
      yield if block
    }
  end

  def action_struct_cpp(x)
    genl
    genl "const char* #{x.classname}::NAME=\"#{x.fqname}\";"
    genl
    ctor=x.classname+"::"+x.classname
    ctor_defn(ctor) {}
    ctor_defn(ctor, x.parameters, x.initializers) {} if not x.fields.empty?
    function_defn("void #{x.classname}::accept", ["Visitor& v"], "const") { 
      genl "v.visit(*this);"
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
      function_defn("template <class S> void serialize", ["S& s"]) { 
        gen "s"
        a.fields.each { |f| gen "(#{f.cppname})"}
        genl ";"
      } unless a.fields.empty?
    }
  end
  
  def action_cpp(a) action_struct_cpp(a); end

  # Types that must be generated early because they are used by other types.
  def pregenerate?(x) not @amqp.used_by[x.fqname].empty?;  end

  # Generate the log
  def gen_specification()
    h_file("#{@dir}/specification") {
      include "#{@dir}/built_in_types"
      include "#{@dir}/helpers"
      include "<boost/call_traits.hpp>"
      genl "using boost::call_traits;"
      namespace(@ns) {
        # Top level 
        @amqp.domains.each { |d|
          # segment-type and track are are built in
          domain_h d unless ["track","segment-type"].include?(d.name)
        }
        # Domains and structs that must be generated early because
        # they are used by other definitions:
        each_class_ns { |c|
          class_h c
          c.collect_all(AmqpDomain).each { |d| domain_h d if pregenerate? d }
          c.collect_all(AmqpStruct).each { |s| struct_h s if pregenerate? s }
        }
        # Now dependent domains/structs and actions
        each_class_ns { |c|
          c.collect_all(AmqpDomain).each { |d| domain_h d unless pregenerate? d}
          c.collect_all(AmqpStruct).each { |s| struct_h s unless pregenerate? s}
          c.collect_all(AmqpAction).each { |a| action_h a }
        }
      }
    }

    cpp_file("#{@dir}/specification") { 
      include "#{@dir}/specification"
      ["Command","Control","Struct"].each { |x| include "#{@dir}/Apply#{x}" }
      namespace(@ns) { 
        each_class_ns { |c|
          class_cpp c
          c.actions.each { |a| action_cpp a}
          c.structs.each { |s| struct_cpp s }
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

  def gen_visitor(base, subs)
    h_file("#{@dir}/#{base}Visitor.h") { 
      include "#{@dir}/specification"
      namespace("#{@ns}") { 
        genl
        genl "/** Visitor interface for #{base} subclasses. */"
        struct("#{base}::Visitor") {
          genl "virtual ~Visitor() {}"
          genl "typedef #{base} BaseType;"
          subs.each{ |s|
            genl "virtual void visit(const #{s.fqclassname}&) = 0;"
          }}}}

    h_file("#{@dir}/Apply#{base}.h") {
      include "#{@dir}/#{base}Visitor.h"
      include "#{@dir}/apply.h"
      namespace("#{@ns}") { 
        genl
        genl "/** apply() support for #{base} subclasses */"
        genl "template <class F>"
        struct("ApplyVisitor<#{base}::Visitor, F>",
               ["public FunctionAndResult<F>", "public #{base}::Visitor"])  {
          subs.each{ |s|
            function_defn("virtual void visit", ["const #{s.fqclassname}& x"]) {
              genl "this->invoke(x);"
            }}}}}
  end

  def gen_visitors()
  end

  def holder(base, derived)
    name=base.caps+"Holder"
    h_file("#{@dir}/#{name}") {
      include "#{@dir}/specification"
      include "qpid/framing/Blob"
      namespace(@ns){ 
        # TODO aconway 2008-02-29: 
      }
    }
  end
  def gen_holders()

  end

  def generate
    gen_specification
    gen_proxy
    gen_visitor("Command", @amqp.collect_all(AmqpCommand))
    gen_visitor("Control", @amqp.collect_all(AmqpControl))
    gen_visitor("Struct", @amqp.collect_all(AmqpStruct))
  end
end

Specification.new($outdir, $amqp).generate();

