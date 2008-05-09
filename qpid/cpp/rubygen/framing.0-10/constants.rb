#!/usr/bin/env ruby
$: << ".."                      # Include .. in load path
require 'cppgen'

class ConstantsGen < CppGen
  
  def initialize(outdir, amqp)
    super(outdir, amqp)
    @namespace="qpid::framing"
    @dir="qpid/framing"
  end

  def constants_h()
    h_file("#{@dir}/constants") {
      namespace(@namespace) { 
        scope("enum AmqpConstant {","};") {
          l=[]
          l.concat @amqp.constants.map { |c| "#{c.name.shout}=#{c.value}" }
          @amqp.classes.each { |c|
            l << "#{c.name.shout}_CLASS_ID=#{c.code}"
            l.concat c.methods_.map { |m|
              "#{c.name.shout}_#{m.name.shout}_METHOD_ID=#{m.code}" }
          }
          genl l.join(",\n")
        }
        namespace("execution") {
          define_constants_for(@amqp.class_("execution").domain("error-code").enum)
        }
        namespace("connection") {
          define_constants_for(@amqp.class_("connection").domain("close-code").enum)
        }
        namespace("session") {
          define_constants_for(@amqp.class_("session").domain("detach-code").enum)
        }
        define_constants_for(@amqp.class_("dtx").domain("xa-status").enum)        
      }
    }
  end

  def define_constants_for(enum)
    scope("enum #{enum.parent.name.caps} {","};") {
      genl enum.choices.collect { |c| "#{c.name.shout}=#{c.value}" }.join(",\n")
    }
  end

  def define_exception(c, base, package)
      name=c.name.caps+"Exception"
      genl
      doxygen_comment { genl c.doc }
      struct(c.name.caps+"Exception", base) {
      genl "std::string getPrefix() const { return \"#{c.name}\"; }"
      genl "#{c.name.caps}Exception(const std::string& msg=std::string()) : #{base}(#{c.value}, \"\"+msg) {}"
      }
  end

  def define_exceptions_for(class_name, domain_name, base)
    enum = @amqp.class_(class_name).domain(domain_name).enum
    enum.choices.each { |c| define_exception(c, base, class_name) unless c.name == "normal" }
  end

  def reply_exceptions_h()
    h_file("#{@dir}/reply_exceptions") {
      include "qpid/Exception"
      include "qpid/ExceptionHolder"
      namespace(@namespace) {
        define_exceptions_for("execution", "error-code", "SessionException")
        define_exceptions_for("connection", "close-code", "ConnectionException")
        define_exceptions_for("session", "detach-code", "ChannelException")
        genl
        genl "void throwExecutionException(int code, const std::string& text);"
        genl "void setExecutionException(ExceptionHolder& holder, int code, const std::string& text);"
      }
    }
  end

  def reply_exceptions_cpp()
    cpp_file("#{@dir}/reply_exceptions") {
      include "#{@dir}/reply_exceptions"
      include "<sstream>"
      include "<assert.h>"
      namespace("qpid::framing") {
        scope("void throwExecutionException(int code, const std::string& text) {"){
          genl "ExceptionHolder h;"
          genl "setExecutionException(h, code, text);"
          genl "h.raise();"
        }
        scope("void setExecutionException(ExceptionHolder& holder, int code, const std::string& text) {"){        
          scope("switch (code) {") {
            enum = @amqp.class_("execution").domain("error-code").enum
            enum.choices.each { |c| 
              genl "case #{c.value}: holder = new #{c.name.caps}Exception(text); break;"
            }
            genl 'default: assert(0);'
            genl '    holder = new InvalidArgumentException(QPID_MSG("Bad exception code: " << code << ": " << text));'
          }
        }
      }
    }
  end

  def generate()
    constants_h
    reply_exceptions_h
    reply_exceptions_cpp
  end
end

ConstantsGen.new($outdir, $amqp).generate();

