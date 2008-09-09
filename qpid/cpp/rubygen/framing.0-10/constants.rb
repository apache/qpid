#!/usr/bin/env ruby
#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#
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
        # Constants for class/method names.
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
      }
    }
  end

  def enum_h()
    h_file("#{@dir}/enum") {
      # Constants for enum domains.
      namespace(@namespace) {
        @amqp.domains.each { |d| define_enum(d.enum) if d.enum }
        @amqp.classes.each { |c|
          enums=c.collect_all(AmqpEnum)
          if !enums.empty? then
            namespace(c.nsname) { enums.each { |e| define_enum(e) } }
          end
        }
      }
    }
  end

  def define_enum(enum)
    # Generated like this: enum containing_class::Foo { FOO_X, FOO_Y; }
    name="#{enum.parent.name.caps}"
    prefix=enum.parent.name.shout+"_" 
    scope("enum #{name} {","};") {
      genl enum.choices.collect { |c| "#{prefix}#{c.name.shout}=#{c.value}" }.join(",\n")
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
      include "qpid/sys/ExceptionHolder"
      namespace(@namespace) {
        define_exceptions_for("execution", "error-code", "SessionException")
        define_exceptions_for("connection", "close-code", "ConnectionException")
        define_exceptions_for("session", "detach-code", "ChannelException")
        genl
        genl "void throwExecutionException(int code, const std::string& text);"
        genl "void setExecutionException(sys::ExceptionHolder& holder, int code, const std::string& text);"
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
          genl "sys::ExceptionHolder h;"
          genl "setExecutionException(h, code, text);"
          genl "h.raise();"
        }
        scope("void setExecutionException(sys::ExceptionHolder& holder, int code, const std::string& text) {"){        
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
    enum_h
    reply_exceptions_h
    reply_exceptions_cpp
  end
end

ConstantsGen.new($outdir, $amqp).generate();

