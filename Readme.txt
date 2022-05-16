README

1. Overview

SDN controller is built using Floodlight and ensures the implementation of the shortest path routing algorithm using Dijkstra. The simulation is tested by using Mininet which can build various topologies such as assign1 or someloops. This implementation works to find the shortest path whenever a link if brought up or down.

2. Installations

1. Java 7
2. Python3
3. Mininet
4. Floodlight
5. OpenFlow 1.0
6. VirtualBox

3. Steps/Details 

	1. Get all the installations done as prescribed above.

	2. Download the Virtualbox .ova file.

	3. Set up the Virtual machine in Virtualbox and start it. Password is 'mininet'

	4. Open up a terminal. Compile using:

	    cd ~/openflow
	
    		ant

	5. Start Floodlight:

    	java -jar FloodlightWithApps.jar -cf shortestPathSwitching.prop

	6. Start Mininet:

  	  sudo -E python run_mininet.py single,3

    	The above command creates a topology with three hosts and one switch. Please refer report for 						all possible topologies.

	 Various commands can now be run to test the routing algorithm. For example 'ping'. The initial ping will get dropped, as the rules would not have been configured yet, but after that, the rules will be in place, and the ping will successfully follow the shortest-path route.


1. The LoadBalancer.java file in the edu.wisc.cs.sdn.apps.loadbalancer package contains the code for the load balancer application. 
2. A single distributed load balancer is represented by the LoadBalancerInstance class. 
3. Each load balancer instance has a virtual IP address, MAC address, and a list of hosts to which TCP connections should be distributed. 
4. The LoadBalancer class's instances class variable associates a virtual IP address with a specific load balancer instance.
Note: Refer to the outputs folder for the results snapshots.

4. References 
https://www.baeldung.com/java-dijkstra
https://github.com/vnatesh/SDN-Controller
