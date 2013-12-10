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

use Digest::MD5;

sub random_string
{
    my $len=$_[0];
    my @chars=('a'..'z','A'..'Z','0'..'9','_');
    my $result;

    foreach (1..$len) {
        $result .= $chars[rand @chars];
    }
    return $result;
}

sub generate_uuid
{
    return Digest::MD5::md5_base64( rand );
}

1;
