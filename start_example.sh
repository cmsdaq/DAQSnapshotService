#Script to start a DAQAggregator standalone Java process, catch its pid and redirect its output to logfile

#arg 1 : daq aggregator executable
#arg 2 : daq aggregator config file
#arg 3 : setup name (used for pid index)
#arg 4 : daq aggregator output log file

DELIM=" = "

java -jar $1 $2 >> $4 2>&1 &
echo $3$DELIM$! >> /mydir/server_files/pid-index.file
