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
    elsif (d.type_ == "array") 
      genl "typedef Array<#{ArrayTypes[d.name].amqp2cpp}> #{typename};"
    else
      genl "typedef #{d.type_.amqp2cpp} #{typename};"
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
      x.fields.each { |f| genl "#{f.type_.amqp2cpp} #{f.cppname};" }
      genl
      genl "static const char* NAME;"
      consts.each { |c| genl "static const uint8_t #{c.upcase}=#{x.send c or 0};"}
      genl "static const uint8_t CLASS_CODE=#{x.containing_class.nsname}::CODE;"
      genl
      genl "#{x.classname}();"           
      scope("#{x.classname}(",");") { genl x.parameters } unless x.fields.empty?
      genl
      genl "void accept(Visitor&) const;"
      genl
      yield if block
    }
  end

  def action_struct_cpp(x)
    genl
    genl "const char* #{x.classname}::NAME=\"#{x.fqname}\";"
    genl
    genl "#{x.classname}::#{x.classname}() {}"; 
    genl
    if not x.fields.empty?
      scope("#{x.classname}::#{x.classname}(",") :") { genl x.parameters }
      indent() { genl x.initializers }
      genl "{}"
      genl
    end
    scope("void #{x.classname}::accept(Visitor&) const {","}") {
      genl "// FIXME aconway 2008-02-27: todo"
    }
  end

  # structs

  def struct_h(s) action_struct_h(s, "Struct", ["size","pack","code"]); end
  def struct_cpp(s) action_struct_cpp(s) end

  # command and control
  
  def action_h(a)
    action_struct_h(a, a.base, ["code"]) {
      scope("template <class T> void invoke(T& target) {","}") {
        scope("target.#{a.funcname}(", ");") { genl a.values }
      }
      genl
      scope("template <class S> void serialize(S& s) {","}") {
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
          c.domains.each { |d| domain_h d if pregenerate? d }
          c.structs.each { |s| struct_h s if pregenerate? s }
        }
        # Now dependent domains/structs and actions
        each_class_ns { |c|
          c.domains.each { |d| domain_h d if not pregenerate? d }
          c.structs.each { |s| struct_h s if not pregenerate? s }
          c.actions.each { |a| action_h a }
        }
      }
    }

    cpp_file("#{@dir}/specification") { 
      include "#{@dir}/specification"
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
    h_file("#{@dir}/Proxy.h") { 
      include "#{@dir}/specification"
      namespace(@ns) { 
        genl "template <class F, class R=F::result_type>"
        cpp_class("ProxyTemplate") {
          public
          genl "ProxyTemplate(F f) : functor(f) {}"
          @amqp.classes.each { |c|
            c.actions.each { |a|
              scope("R #{a.funcname}(", ")") {  genl a.parameters }
              scope() { 
                var=a.name.funcname
                scope("#{a.classname} #{var}(",");") { genl a.arguments }
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
  
  def generate
    gen_specification
    gen_proxy
  end
end

Specification.new($outdir, $amqp).generate();

