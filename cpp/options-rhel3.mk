#
# Expects dependencies in ~/local
#

# Comment out the setting of RELEASE to build a debug release.
RELEASE := 1

# Configure Boost.
BOOST_CXXFLAGS := -I$(HOME)/local/boost-1.33.1/include/boost-1_33_1
CXXFLAGS := $(CXXFLAGS) $(BOOST_CXXFLAGS)

# Configure CppUnit.
CPPUNIT_CFLAGS := `cppunit-config --cflags`
CPPUNIT_LDFLAGS := `cppunit-config --libs`
CXXFLAGS := $(CXXFLAGS) $(CPPUNIT_CFLAGS)

#
# RedHat Enterprise 3 g++ can't handle -Wextra etc so remove them.
#

WARN := -Werror -pedantic -Wall -Wno-shadow -Wpointer-arith -Wcast-qual -Wcast-align -Wno-long-long -Wno-system-headers
