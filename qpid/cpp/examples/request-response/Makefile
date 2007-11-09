CXX=g++
CXXFLAGS= 

PROGRAMS=client server
all: $(PROGRAMS)

client: client.cpp
	$(CXX) $(CXXFLAGS) -lqpidclient -o $@ $^

server: server.cpp
	$(CXX) $(CXXFLAGS) -lqpidclient -o $@ $^


clean:
	rm -f $(PROGRAMS) 
