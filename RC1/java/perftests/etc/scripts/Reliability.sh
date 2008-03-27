 find . -regex '.*R-Qpid-0[1-2].*\.sh' -exec  {} -o results-Reliability/ --csv \; && for i in `seq 1 6` ; do find . -regex '.*R-Qpid-0[3-7].*\.sh' -exec  {} -o results-Reliability/ --csv  \; ; done
