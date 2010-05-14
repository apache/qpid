CXX=g++
CXXFLAGS=-g -O2
LDFLAGS=-lqpidmessaging

all: drain spout client server map_sender map_receiver hello_world

drain: drain.o OptionParser.o

spout: spout.o OptionParser.o

clean:
	rm -f drain spout client server map_sender map_receiver hello_world *.o
