#!/usr/bin/ruby

require 'optparse'

require 'date'

def seconds_since_now date
    hours,minutes,seconds,frac = Date.send(:day_fraction_to_time, DateTime.now - date)
    puts "#{hours}:#{minutes}:#{seconds}"
    return hours*3600+ minutes*60+ seconds
end

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

interface = hash_options[:interface]

def log msg
    puts msg
    `echo "[#{Time.now.strftime("%D:%M:%Y %I:%M:%S %p")}] #{msg}" >> failure.log`
end

while(true)
    sleep(30) # giving time to the node to join a ring

   log "#{Time.now} [fail-checker] Checking if the node failed to join a ring"

    # # checking if the current node is alone
    # max_size = `cat run.log | grep "size" | awk '{print $2}' | tail -n 1`.strip

    # if(max_size == "1" || max_size == "")
    #     puts "[fail-checker] The chord node is linked with no other nodes, I restart the process of joining a ring."
    #     `./clean.rb; ./autobind.rb --interface #{interface}`
    #     exit
    # end

    # last_message_date_as_string = `tail run.log | grep "[INFO]" | awk '{print $2 " " $3}' | sed "s/\\(\\[\\|\\]\\|\\..*\\)//g" | grep -e "[0-9]" | tail -n 1`

    # # checking if there was an error message in the tail that is very old
    # if(last_message_date_as_string.strip != "")
    #     last_message_date = DateTime.strptime(last_message_date_as_string.strip,"%m/%d/%Y %H:%M:%S")
    #     seconds_since_last_message = seconds_since_now(last_message_date)

    #     puts "toto: #{seconds_since_last_message}"
    #     #puts "difference: #{Time.now.to_i - date}"

    #     if(seconds_since_last_message > 20)
    #         puts "[fail-checker] The chord node is waiting since a long time, I restart the process of joining a ring."
    #         `./clean.rb; ./autobind.rb --interface #{interface}`
    #         exit
    #     end
    # end

    # checking if the last modification of the log file is very old
    log_file_path = "run.log"
    last_modification_time = File.mtime(File.new(log_file_path))

    log "difference : #{Time.now - last_modification_time}"

    if((Time.now - last_modification_time) > 30)
        log "#{Time.now} [fail-checker] The log file's last modification is very old, I restart the process of joining a ring."
        
        `./clean.rb; ./autobind.rb --interface #{interface}`
        exit
    end

    log "#{Time.now} [fail-checker] Everything seems ok"

    succesfully_launched = true

    #exit
end