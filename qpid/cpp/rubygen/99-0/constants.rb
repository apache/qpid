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
            l << "#{c.name.shout}_CLASS_ID=#{c.index}"
            l.concat c.methods_.map { |m|
              "#{c.name.shout}_#{m.name.shout}_METHOD_ID=#{m.index}" }
          }
          genl l.join(",\n")
        }}}
  end

  def exbase(c)
    case c.class_
    when "soft-error" then "ChannelException"
    when "hard-error" then "ConnectionException"
    end
  end

  def reply_exceptions_h()
    h_file("#{@dir}/reply_exceptions") {
      include "qpid/Exception"
      namespace(@namespace) {
        @amqp.constants.each { |c|
          base = exbase c
          if base
            genl
            struct(c.name.caps+"Exception", base) {
              genl "#{c.name.caps}Exception(const std::string& msg=std::string()) : #{base}(#{c.value}, \"#{c.name}: \"+msg) {}"
            }
          end
        }
        genl
        genl "void throwReplyException(int code, const std::string& text);"
      }
    }
  end

  def reply_exceptions_cpp()
    cpp_file("#{@dir}/reply_exceptions") {
      include "#{@dir}/reply_exceptions"
      include "<sstream>"
      namespace("qpid::framing") {
        scope("void throwReplyException(int code, const std::string& text) {"){
          scope("switch (code) {") {
            genl "case 200: break; // No exception"
            @amqp.constants.each {  |c|
              if exbase c 
                genl "case #{c.value}: throw #{c.name.caps}Exception(text);"
              end
            }
            scope("default:","") {
              genl "std::ostringstream msg;"
              genl 'msg << "Invalid reply code " << code << ": " << text;'
              genl 'throw InvalidArgumentException(msg.str());'
            }}}}}
  end

  def generate()
    constants_h
    reply_exceptions_h
    reply_exceptions_cpp
  end
end

ConstantsGen.new($outdir, $amqp).generate();

