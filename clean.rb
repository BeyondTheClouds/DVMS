#!/usr/bin/ruby

require 'optparse'
require 'rubygems'
require 'fileutils'
require 'socket'      # Sockets are in standard library

puts "Cleaning running processes"

print "[AkkaChord.jar] killing all processes... "
java_processes_pids = `ps x | grep java | grep "akka-arc.jar" | awk '{print $1}'`
for java_process_pid in java_processes_pids
	`kill -9 #{java_process_pid.strip} 2> /dev/null`
end
puts "Done"

print "[cluster.rb] killing all processes... "
ruby_cluster_processes_pids = `ps x | grep ruby | grep "cluster.rb" | awk '{print $1}'`
for ruby_cluster_process_pid in ruby_cluster_processes_pids
	`kill -9 #{ruby_cluster_process_pid.strip} 2> /dev/null`
end
puts "Done"

print "[failure_checker.rb] killing all processes... "
ruby_cluster_processes_pids = `ps x | grep ruby | grep "failure_checker.rb" | awk '{print $1}'`
for ruby_cluster_process_pid in ruby_cluster_processes_pids
	`kill -9 #{ruby_cluster_process_pid.strip} 2> /dev/null`
end
puts "Done"