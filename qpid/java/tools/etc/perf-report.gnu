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

set terminal png
set datafile separator ","

set title "Variation of avg latency between iterations"
set yrange [10:20]
set xlabel "Iterations"
set ylabel "Latency (ms)"
set output "avg_latency.png"
plot "stats-csv.log" using 9 title "avg latency" with lines, 14 title "target latency" with lines


set title "Variation of max latency between iterations"
set yrange [0:1000]
set xlabel "Iterations"
set ylabel "Latency (ms)"
set output "max_latency.png"
plot "stats-csv.log" using 11 title "max latency" with lines,14 title "target latency" with lines,100 title "100 ms" with lines


set title "Variation of standard deviation of latency between iterations"
set yrange [0:20]
set xlabel "Iterations"
set ylabel "Standard Deviation"
set output "std_dev_latency.png"
plot "stats-csv.log" using 12 title "standard deviation" with lines


set title "Variation of system throughput between iterations"
set yrange [400000:450000]
set xlabel "Iterations"
set ylabel "System Throuhgput (msg/sec)"
set output "system_rate.png"
plot "stats-csv.log" using 2 title "system throughput" with lines


set title "Variation of avg producer & consumer rates between iterations"
set yrange [6500:7500]
set xlabel "Iterations"
set ylabel "Avg Rates (msg/sec)"
set output "prod_cons_rate.png"
plot "stats-csv.log" using 6 title "producer rate" with lines,"stats-csv.log" using 3 title "consumer rate" with lines

