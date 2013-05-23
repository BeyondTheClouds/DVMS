#!/usr/bin/ruby

require 'optparse'
require 'rubygems'
require 'fileutils'
require 'socket'      # Sockets are in standard library
require 'open3'

current_path = Dir.pwd

hash_options = {}
OptionParser.new do |opts|
    opts.banner = "Usage: ruby cluster.rb --ip <ip> --port <port> [options]"
    opts.on('--interface [ARG]', "the interface that will be used for networking") do |v|
        hash_options[:interface] = v
    end
    opts.on('-h', '--help', 'Display this help') do
        puts opts
        exit
    end
end.parse!

raise "I need an <interface> argument" if hash_options[:interface].nil?

def log msg
    puts msg
    `echo "[#{Time.now.strftime("%D:%M:%Y %I:%M:%S %p")}] #{msg}" >> autobind.log`
end

interface = hash_options[:interface]

ip = ""
system = `uname`.strip

if(system == "Darwin")
    ip = `ifconfig #{interface} | grep 'inet ' | cut -d' ' -f2`
end

if(system == "Linux")
    ip = `/sbin/ifconfig #{interface} | awk -F ' *|:' '/inet addr/{print $4}'`
end

ip = ip.strip

if(ip != "")

    log "launching the cluster.rb --ip #{ip} --port 3000"
    `./cluster.rb --ip #{ip} --port 3000 > /dev/null &`

    # log "launching the failure_checker.rb --interface #{interface}"
    # `./failure_checker.rb --interface #{interface} > /dev/null &`
else
    log "Cannot Launch chord: Interface <#{interface}> does not exist or $\"uname\" is not in [Darwin, Linux]!"    
end

