#!/usr/bin/ruby

require 'optparse'
require 'rubygems'
require 'fileutils'
require 'socket'      # Sockets are in standard library
require './RubySimpleActor.rb'

current_path = Dir.pwd

hash_options = {}
OptionParser.new do |opts|
    opts.banner = "Usage: ruby cluster.rb --ip <ip> --port <port> [options]"
    opts.on('--ip [ARG]', "the ip that will be used by the launched chord node") do |v|
        hash_options[:ip] = v
    end
    opts.on('--port [ARG]', "the ip that will be used by the launched chord node") do |v|
        hash_options[:port] = v
    end
    opts.on('-h', '--help', 'Display this help') do
        log opts
        exit
    end
end.parse!

raise "I need an <ip> and <port> argument" if hash_options[:ip].nil?



# UDP_SOCKET_CLIENT = UDPSocket.new
PORT = 33333

MAX_CONCURRENT_JOINING_REQUEST = 10
$ok_to_launch_other_local_nodes = false


ip = hash_options[:ip]
port = hash_options[:port]

finished = false


def log msg
	puts msg
	`echo "[#{Time.now.strftime("%D:%M:%Y %I:%M:%S %p")}] #{msg}" >> cluster.log`
end

Thread.abort_on_exception = true

class NetworkActor < RubySimpleActor

	def log msg
    	puts msg
    	`echo "[#{Time.now.strftime("%D:%M:%Y %I:%M:%S %p")}] (NetworkActor): #{msg}" >> cluster.log`
	end

	def initialize

		super()

		@nodes = []



		@UDP_SOCKET_CLIENT = UDPSocket.new
		@UDP_SOCKET_CLIENT.setsockopt(Socket::SOL_SOCKET, Socket::SO_BROADCAST, true)

		@t2 = Thread.new(:log) do |log|
			log "NetworkActor is waiting for a connection"
			
			BasicSocket.do_not_reverse_lookup = true

			@UDPSockServer = UDPSocket.new
			@UDPSockServer.bind("0.0.0.0", PORT)

			while(true)

				msg, addr = @UDPSockServer.recvfrom(1024) 

				@nodes.each do |node|
					node < msg.strip
				end
			end
		end

	end

	def register_node node
		@nodes = @nodes + [node]
	end

	def send msg, to, port
		@UDP_SOCKET_CLIENT.send(msg, 0, to, PORT)
	end

	def on_receive msg
	end
end

class NodeActor < RubySimpleActor

	def as_string
		return "#{@ip}@#{@port}"
	end

	def log msg
    	puts msg
    	`echo "[#{Time.now.strftime("%D:%M:%Y %I:%M:%S %p")}] (#{@ip}@#{@port}): #{msg}" >> cluster.log`
	end


	def launch_connected_chord_ring(ip, port, remote_ip, remote_port)
		log "launching a chord node #{ip}@#{port} that will connect to #{remote_ip}:#{remote_port}"

		chord_launch_cmd = %Q{#!/bin/bash
	java -jar target/akka-arc.jar ip=#{ip} port=#{port} remote_ip=#{remote_ip} remote_port=#{remote_port} debug=true
		}
		`echo "#{chord_launch_cmd}" > run_#{port}.sh`
		`chmod +x run_#{port}.sh`

		system("bash run_#{port}.sh >> run_#{port}.log &")
		system("rm run_#{port}.sh")
	end

	def launch_standalone_chord_ring(ip, port)
		log "launching a standalone chord node #{ip}@#{port}"

		chord_launch_cmd = %Q{#!/bin/bash
	java -jar target/akka-arc.jar ip=#{ip} port=#{port} debug=true
		}
		`echo "#{chord_launch_cmd}" > run_#{port}.sh`
		`chmod +x run_#{port}.sh`

		system("bash run_#{port}.sh >> run_#{port}.log &")
		system("rm run_#{port}.sh")
	end

	def initialize(ip, port)

		super()

		@ip = ip
		@port = port

		@discovering = true
		@serving = false
		@scanning = true
		@joining_request_count = 0

		@processed_requests = []

		@t1 = Thread.new(:log) do |log|

			cpt = 0

			while(@discovering)
				# log "trying to discover another peer on the network"
				if(cpt==10)
					@starting_a_chord_node = true
					random_wait_time = 1+rand(0.99)*10 
					log "reach a critical case , will wait: #{random_wait_time}"
					sleep(random_wait_time)
			
					log "Informing the network that I would like to create a ring"
					NETWORK_ACTOR.send("May I create a ring: #{@ip}:#{@port}", '<broadcast>', PORT)
					
					Thread.new do
						sleep 10
						if(@starting_a_chord_node)
							log "I have decided to create my own ring"
							NETWORK_ACTOR.send("Starting a chord node: #{@ip}:#{@port}", '<broadcast>', PORT)
							
							@starting_a_chord_node = false
							@discovering = false

							Thread.new do
								sleep 5
								log "Now I can serve"
								@serving = true

								$ok_to_launch_other_local_nodes = true
							end

							launch_standalone_chord_ring "#{@ip}", "#{@port}"
						else
							log "Someone else is serving request"
						end

					end
					

				else
					# log "broadcasting the looking up message"
					NETWORK_ACTOR.send("Looking for a chord node: #{@ip}:#{@port}", '<broadcast>', PORT)
				end

				cpt = cpt+1
				sleep(1)
			end
		end
	end

	

	def on_receive msg

		if( (msg =~ /Looking for a chord node: /) == 0)
			msg["Looking for a chord node: "] = ""
			remote_ip = msg.split(":")[0]
			remote_port = msg.split(":")[1]

			if( !(remote_ip == @ip && remote_port == @port) && @serving &&
				!@processed_requests.include?("#{remote_ip}@#{remote_port}") && 
				@joining_request_count<5)
				
				@processed_requests += ["#{remote_ip}@#{remote_port}"]
				@joining_request_count += 1
				log "Received a lookup request from addr: #{msg}, currently handling #{@joining_request_count} request(s)"	
				NETWORK_ACTOR.send("Connect to me: #{@ip}:#{@port}", "#{remote_ip}", "#{remote_port}")

				Thread.new do
					sleep(5)
					@processed_requests -= ["#{remote_ip}@#{remote_port}"]
					@joining_request_count -= 1
				end
			end
		end

		if( (msg =~ /May I create a ring: /) == 0)
			msg["May I create a ring: "] = ""
			remote_ip = msg.split(":")[0]
			remote_port = msg.split(":")[1]

			if(!(remote_ip == @ip && remote_port == @port))
				if(@serving)
					log "Another node is wrongly trying to create a ring: #{remote_ip}:#{remote_port}"
					NETWORK_ACTOR.send("Stop the ring creation: #{@ip}:#{@port}", '<broadcast>', PORT)

				end
			end
		end

		if( (msg =~ /Stop the ring creation: /) == 0)

			log "alert rouge"

			@starting_a_chord_node = false

			msg["Stop the ring creation: "] = ""
			remote_ip = msg.split(":")[0]
			remote_port = msg.split(":")[1]
			
			if(!(remote_ip == @ip && remote_port == @port))

				log "#{remote_ip}:#{remote_port} told me to stop my ring creation"
				@starting_a_chord_node = false
			end
		end

		if( (msg =~ /Connect to me: /) == 0)
			msg["Connect to me: "] = ""
			remote_ip = msg.split(":")[0]
			remote_port = msg.split(":")[1]
			
			log("#{remote_ip}:#{remote_port} told me to connect to him")

			if(!(remote_ip == @ip && remote_port == @port))
				if(@discovering)
					log "Now I should connect to #{remote_ip}:#{remote_port}"
					@discovering = false

					if(@starting_a_chord_node)
						@starting_a_chord_node = false
					end

					Thread.new do
						sleep 5
						log "Now I can serve"
						@serving = true
					end

					log "Launching the chord ring (it will connect to #{remote_ip}:#{remote_port})"
					launch_connected_chord_ring "#{@ip}", "#{@port}", "#{remote_ip}", "#{remote_port}"
				end

				if(@starting_a_chord_node)
					@starting_a_chord_node = false
				end
			end
		end

		if((msg =~ /Starting a chord node: /) == 0)

			begin
				msg["Starting a chord node: "] = ""
			rescue
				log("Failed with (#{msg})")
			end
			remote_ip = msg.split(":")[0]
			remote_port = msg.split(":")[1]

			if(!(remote_ip == @ip && remote_port == @port))
				log "another node is starting_a_chord_node: #{remote_ip}:#{remote_port}"
				if(@discovering && @starting_a_chord_node)
					log "I renounce to create a standalone chord node"
				end
				@starting_a_chord_node = false
			end
		end
	end
end

networkActor = NetworkActor.new
NETWORK_ACTOR = networkActor


log "Starting a NodeActor: #{ip}@#{port.to_i}"
networkActor.register_node(NodeActor.new(ip, port.to_i))


while(!$ok_to_launch_other_local_nodes)
	sleep(1)
end

for i in 1..10 do
	log "Starting a NodeActor: #{ip}@#{port.to_i+i}"
	networkActor.register_node(NodeActor.new(ip, port.to_i+i))
end

while(true)
	sleep(1)
end

log "End of the program"
