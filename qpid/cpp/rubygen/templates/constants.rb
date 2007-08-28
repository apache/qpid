#!/usr/bin/env ruby
$: << ".."                      # Include .. in load path
require 'cppgen'

class ConstantsGen < CppGen
  
  def initialize(outdir, amqp)
    super(outdir, amqp)
    @namespace="qpid::framing"
    @dir="qpid/framing"
  end

  def generate()
    h_file("#{@dir}/constants") {
      namespace(@namespace) { 
        @amqp.constants.each { |c|
          genl "inline const int #{c.name.shout} = #{c.value};"
        }
      }
    }
    
    h_file("#{@dir}/reply_exceptions") {
      include "constants"
      include "qpid/Exception"
      namespace(@namespace) {
        @amqp.constants.each { |c|
          if c.class_
            exname=c.name.caps+"Exception"
            base = c.class_=="soft-error" ? "ChannelException" : "ConnectionException"
            text=(c.doc or c.name).tr_s!(" \t\n"," ")
            struct(exname, base) {
              genl "#{exname}(const std::string& msg=\"#{text})\") : #{base}(#{c.value}, msg) {}"
            }
          end
        }
      }
    }
  end
end

ConstantsGen.new(Outdir, Amqp).generate();

