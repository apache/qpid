#!/usr/bin/env ruby
$: << ".."                      # Include .. in load path
require 'cppgen'

class Amqp_0_10 < CppGen
  ArrayTypes={
    "str16-array" => "Str16",
    "amqp-host-array" => "connection::AmqpHostUrl",
    "command-fragments" => "session::CommandFragment",
    "in-doubt" => "dtx::Xid"
  }
  
  def initialize(outdir, amqp)
    super(outdir, amqp)
    @ns="qpid::amqp_0_10"
    @dir="qpid/amqp_0_10"
  end

  # domains

  def domain_h(d)
    typename=d.name.typename
    if d.enum
      scope("enum #{typename} {", "};") { 
        genl d.enum.choices.map { |c| "#{c.name.constname} = #{c.value}" }.join(",\n")
      }
    elsif d.type_ == "array"
      @array_domains << "typedef Array<#{ArrayTypes[d.name]}> #{typename};\n"
    else
      genl "typedef #{d.type_.amqp2cpp} #{typename};"
    end
  end

  # structs
  
  # field members and MemberInfo
  def member_h(m)
    genl "static const MemberInfo INFO;"    
    m.fields.each { |f| genl "#{f.type_.amqp2cpp} #{f.cppname};" }
  end

  # MemberInfo constant definition.
  def member_cpp(m)
    infotype=m.is_a?(AmqpStruct) ? "Struct1Info" : "MemberInfo"
    scope("{infotype} #{m.classname}::INFO = {","};") {
      inits=[]
      inits << (m.parent.is_a?(AmqpClass) ? "&CLASS_INFO" : "0")
      inits << m.name << (m.code or "0");
      if m.is_a?(AmqpStruct)
        inits << (m.size or 0) << (m.pack or 0)
      end
      genl inits.join(", ")
    }
  end

  def struct_h(s) struct(s.classname, "public Struct") { member_h s }; end
  def struct_cpp(s) member_cpp s; end
  def gen_structs()
    file="#{@dir}/structs"
    h_file(file) {
      include "#{@dir}/built_in_types.h"
      include "#{@dir}/helpers.h"
      include "<boost/call_traits.hpp>"
      genl "using boost::call_traits;"
      namespace(@ns) { 
        @amqp.structs.each { |s| struct_h s }
        each_class_ns { |c| c.structs.each { |s| struct_h s }}
      }
    }
    cpp_file(file) {
      include file
      namespace(@ns) {
        @amqp.structs.each { |s| struct_h s }
        each_class_ns { |c| c.structs.each { |s| struct_cpp s }}
      }
    }
  end

  # command and control
  
  def action_h(a) 
    name=a.name.typename
    struct(name, "public #{a.base}") {
      genl "#{name}() {}"           
      scope("#{name}(",");") { genl a.parameters } unless a.fields.empty?
      scope("template <class T> void invoke(T& target) {","}") {
        scope("target.#{a.funcname}(", ");") { genl a.values }
      }
      scope("template <class S> void serialize(S& s) {","}") {
        gen "s"
        a.fields.each { |f| gen "(#{f.cppname})"}
        genl ";"
      } unless a.fields.empty?
      member_h a
    }
  end
  
  def action_cpp(a)   # command or control
    # ctor
    scope("#{a.classname}::#{a.classname}(",") :") { genl a.parameters }
    indent() { genl a.initializers }
    genl "{}"
    # member constants
    member_cpp a
  end

  def class_h(c)
    genl "extern const ClassInfo CLASS_INFO;"
    @array_domains=""
    c.domains.each { |d| domain_h d }
    c.structs.each { |s| struct_h s }
    gen @array_domains
  end

  def class_cpp(c)
    genl "const ClassInfo CLASS_INFO = { #{c.code}, \"#{c.name}\" };"
    c.structs.each { |s| struct_cpp s }
  end

  def gen_specification()
    h_file("#{@dir}/specification") {
      include "#{@dir}/built_in_types"
      include "#{@dir}/helpers"
      include "<boost/call_traits.hpp>"
      genl "using boost::call_traits;"
      namespace(@ns) {
        # We don't generate top-level domains, as they have clashing values.
        each_class_ns { |c| class_h c }
        each_class_ns { |c| c.actions.each { |a| action_h a}        
        }
      }
    }
    cpp_file("#{@dir}/specification") { 
      include "#{@dir}/specification"
      namespace(@ns) { 
        each_class_ns { |c| class_cpp c }
        each_class_ns { |c| c.actions.each { |a| action_cpp a}
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

Amqp_0_10.new($outdir, $amqp).generate();

