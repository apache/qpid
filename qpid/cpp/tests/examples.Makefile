#
# XXX: Edit these locations to suit.
#
BOOST_LOCATION := $(HOME)/local/boost-1.33.1
APR_LOCATION := $(HOME)/local/apr-1.2.7

CXXFLAGS := -DNDEBUG -DUSE_APR -MMD -fpic

#
# Configure Boost.
#
BOOST_CFLAGS := -I$(BOOST_LOCATION)/include/boost-1_33_1
CXXFLAGS := $(CXXFLAGS) $(BOOST_CFLAGS)

#
# Configure APR.
#
APR_CFLAGS := -I$(APR_LOCATION)/include/apr-1
APR_LDFLAGS := $(shell $(APR_LOCATION)/bin/apr-1-config --libs) -L$(APR_LOCATION)/lib -lapr-1
CXXFLAGS := $(CXXFLAGS) $(APR_CFLAGS)
LDFLAGS := $(LDFLAGS) $(APR_LDFLAGS)

#
# Configure Qpid cpp client.
#
QPID_CLIENT_LDFLAGS := ../lib/libcommon.so ../lib/libclient.so
includeDir := ../include
QPID_CLIENT_CFLAGS := \
  -I$(includeDir)/gen               \
  -I$(includeDir)/client            \
  -I$(includeDir)/broker            \
  -I$(includeDir)/common            \
  -I$(includeDir)/common/sys        \
  -I$(includeDir)/common/framing

CXXFLAGS := $(CXXFLAGS) $(QPID_CLIENT_CFLAGS)
LDFLAGS := $(LDFLAGS) $(QPID_CLIENT_LDFLAGS)

CXX := g++

#
# Add rule to build examples.
#
.SUFFIX: .cpp
%: %.cpp
	$(CXX) $(CXXFLAGS) $(LDFLAGS) $< -o $@

#
# Define targets.
#

EXAMPLES := client_test topic_listener topic_publisher echo_service

cppFiles := $(wildcard *.cpp)
programs = $(foreach cppFile, $(cppFiles), $(subst .cpp, ,$(cppFile)))

.PHONY:
all: $(programs)

debug:
	@echo cppFiles = $(cppFiles)
	@echo programs = $(programs)

.PHONY:
clean:
	-rm $(EXAMPLES)
