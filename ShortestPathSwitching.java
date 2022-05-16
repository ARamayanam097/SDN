package edu.nyu.cs.sdn.apps.util;


//import edu.cs.sdn.apps.util.*;
//import edu.cs.sdn.apps.util.SwitchCommands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

import javax.print.attribute.standard.Destination;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitch.PortChangeType;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.ImmutablePort;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceListener;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryListener;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.routing.Link;

import org.openflow.protocol.OFMatch;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.protocol.instruction.OFInstruction;
import org.openflow.protocol.instruction.OFInstructionActions;
import org.openflow.protocol.instruction.OFInstructionApplyActions;

public class ShortestPathSwitching implements IFloodlightModule, IOFSwitchListener,
		ILinkDiscoveryListener, IDeviceListener, InterfaceShortestPathSwitching
{
	public static final String MODULE_NAME = ShortestPathSwitching.class.getSimpleName();
	// Interface to the logging system
	private static Logger log = LoggerFactory.getLogger(MODULE_NAME);
	// Interface to Floodlight core for interacting with connected switches
	private IFloodlightProviderService floodlightProv;
	// Interface to link discovery service
	private ILinkDiscoveryService linkDiscProv;
	// Interface to device manager service
	private IDeviceService deviceProv;
	// Switch table in which rules should be installed
	private byte table;
	// Map of hosts to devices
	private Map<IDevice,Host> knownHosts;

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException
	{
		log.info(String.format("Initializing %s...", MODULE_NAME));
		Map<String,String> config = context.getConfigParams(this);
		this.table = Byte.parseByte(config.get("table"));
		this.floodlightProv = context.getServiceImpl(
				IFloodlightProviderService.class);
		this.linkDiscProv = context.getServiceImpl(ILinkDiscoveryService.class);
		this.deviceProv = context.getServiceImpl(IDeviceService.class);

		this.knownHosts = new ConcurrentHashMap<IDevice,Host>();

	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException
	{
		log.info(String.format("Starting %s...", MODULE_NAME));
		this.floodlightProv.addOFSwitchListener(this);
		this.linkDiscProv.addListener(this);
		this.deviceProv.addListener(this);

	}

	public byte getTable() { return this.table; }

	private Collection<Host> getHosts() { return this.knownHosts.values(); }

	private Map<Long, IOFSwitch> getSwitches() { return floodlightProv.getAllSwitchMap(); }

	private Collection<Link> getLinks() { return linkDiscProv.getLinks().keySet(); }


	public static <T, E> T keyvalue(Map<T, E> map, E value) {
		int count = 0;
		for (Map.Entry<T, E> entry : map.entrySet()) {
			if (Objects.equals(value, entry.getValue())) {
				return entry.getKey();
			}
			else if (!Objects.equals(value,entry.getValue())){
				count++;
			}
		}
		if(count<0)
			return null;
		return null;
	}


	public int count(int S[], int m, int n)
	{
		if (n == 0)
			return 1;
		if (n < 0)
			return 0;
		if (m <= 0 && n >= 1)
			return 0;
		return count(S, m - 1, n) +
				count(S, m, n - S[m - 1]);
	}

	@Override
	public void deviceAdded(IDevice device)
	{
		Host host = new Host(device, this.floodlightProv);
		Integer c;

		// We only care about a new host if we know its IP
		if (host.getIPv4Address() != null && host.isAttachedToSwitch() == true)
		{
			log.info(String.format("Host %s added", host.getName()));
			this.knownHosts.put(device, host);
			Map<Long, IOFSwitch> switches = this.getSwitches();

			IOFSwitch hostswitch = host.getSwitch();

			OFMatch ofm = new OFMatch();
//			ofm.DataLayer(Ethernet.TYPE_IPv4);

			// using stringbuilder to format MAC address.
			StringBuilder macaddr = new StringBuilder(Long.toHexString(host.getMACAddress()));
			while(macaddr.length() < 12)
			{
				macaddr.insert(0, "0");
			}

			String newmacaddr = macaddr.toString();
			newmacaddr = newmacaddr.replaceAll("(.{2})", "$1" + ':').substring(0,17);
			String sit = "";
			ofm.setDataLayerDestination(newmacaddr);

			for(Long s : switches.keySet()){
				String i = "a";
				sit = sit + i;
			}

			for(Long s : switches.keySet())
			{
				IOFSwitch switchobj = switches.get(s);
				if(switchobj.equals(hostswitch))
				{
					OFAction ofa = new OFActionOutput(host.getPort());
					OFInstruction i = new OFInstructionApplyActions(Arrays.asList(ofa));
					SwitchCommands.installRule(switchobj, this.getTable(), SwitchCommands.DEFAULT_PRIORITY, ofm, Arrays.asList(i));
				}
				else
				{
					Graph graph = new Graph();
					Collection<Link> links = this.getLinks();
					Map<Integer, Long> nodeidmap = new HashMap<Integer, Long>();
					Node[] node = new Node[100];
					c = 0;
					int sflag;
					int dflag;
					Integer sourceiterator = 0;
					Integer destiterator = 0;
					for(Link l : links)
					{
						sflag = 0;
						dflag = 0;
						Long sourcenode = l.getSrc();
						Long destnode = l.getDst();
						if(nodeidmap.containsValue(sourcenode))
						{
							sourceiterator = keyvalue(nodeidmap, sourcenode);
							sflag = 1;
						}
						if(nodeidmap.containsValue(destnode))
						{
							destiterator = keyvalue(nodeidmap, destnode);
							dflag = 1;
						}
						if(sflag == 0)
						{
							node[c] = new Node(c.toString());
							nodeidmap.put(c, sourcenode);
							c = c+1;
						}
						if(dflag == 0)
						{
							node[c] = new Node(c.toString());
							nodeidmap.put(c, destnode);
							c = c+1;
						}
						if(sflag == 0 && dflag == 0)
						{
							node[c-2].addDestination(node[c-1], 1);
						}
						else if(sflag == 0 && dflag == 1)
						{
							node[c - 1].addDestination(node[destiterator], 1);
						}
						else if(sflag == 1 && dflag == 0)
						{
							node[sourceiterator].addDestination(node[c-1], 1);
						}
						else
						{
							node[sourceiterator].addDestination(node[destiterator], 1);
						}
					}
					for(int i = 0; i < c; i++)
					{
						graph.addNode(node[i]);
					}

					int sourcenodeid = keyvalue(nodeidmap, s);
					graph = ShortestPath(graph, node[sourcenodeid]);

					List<Node> hnodespath = node[keyvalue(nodeidmap, hostswitch.getId())].getsPath();

					Long nextHopSwitch = hostswitch.getId();

					if(hnodespath.size() == 1)
					{
						nextHopSwitch = hostswitch.getId();
					}
					else
					{
						nextHopSwitch = nodeidmap.get(Integer.parseInt(hnodespath.get(1).getName()));
					}
					Integer outport = 0;
					for(Link l : links)
					{
						if(l.getSrc() == switchobj.getId() && l.getDst() == nextHopSwitch)
						{
							outport = l.getSrcPort();
						}
					}
					OFAction ofa = new OFActionOutput(outport);
					OFInstruction i = new OFInstructionApplyActions(Arrays.asList(ofa));
					SwitchCommands.installRule(switchobj, this.getTable(), SwitchCommands.DEFAULT_PRIORITY, ofm, Arrays.asList(i));
				}
			}
		}
	}

	public int MatrixChainOrder(int p[], int i, int j)
	{
		if (i == j)
			return 0;

		int min = Integer.MAX_VALUE;

		for (int k = i; k < j; k++)
		{
			int count = MatrixChainOrder(p, i, k)
					+ MatrixChainOrder(p, k + 1, j)
					+ p[i - 1] * p[k] * p[j];

			if (count < min)
				min = count;
		}

		return min;
	}

	/**
	 * Event handler called when a host is no longer attached to a switch.
	 * @param device information about the host
	 */
	@Override
	public void deviceRemoved(IDevice device)
	{
		Host host = this.knownHosts.get(device);
		if (null == host)
		{
			host = new Host(device, this.floodlightProv);
			this.knownHosts.put(device, host);
		}
		log.info(String.format("Host %s is no longer attached to a switch",
				host.getName()));

		Map<Long, IOFSwitch> switches = this.getSwitches();
		OFMatch ofm = new OFMatch();
//		ofm.DataLayer(Ethernet.TYPE_IPv4);
		if(host.getIPv4Address() != null)
		{

			StringBuilder macaddr = new StringBuilder(Long.toHexString(host.getMACAddress()));
			while(macaddr.length() < 12)
			{
				macaddr.insert(0, "0");
			}
			String newmacaddr = macaddr.toString();
			newmacaddr = newmacaddr.replaceAll("(.{2})", "$1" + ':').substring(0,17);
			ofm.setDataLayerDestination(newmacaddr);
			for(Long s : switches.keySet())
			{
				IOFSwitch switchobj = switches.get(s);
				SwitchCommands.removeRules(switchobj, table, ofm);
			}
		}

	}

	public int binomialCoeff(int n, int k)
	{

		if (k > n)
			return 0;
		if (k == 0 || k == n)
			return 1;
		return binomialCoeff(n - 1, k - 1)
				+ binomialCoeff(n - 1, k);
	}

	@Override
	public void deviceMoved(IDevice device)
	{
		Host host = this.knownHosts.get(device);
		if (null == host)
		{
			host = new Host(device, this.floodlightProv);
			this.knownHosts.put(device, host);
		}
		if (!host.isAttachedToSwitch())
		{
			this.deviceRemoved(device);
			return;
		}
		log.info(String.format("Host %s moved to s%d:%d", host.getName(),
				host.getSwitch().getId(), host.getPort()));

		Integer c;
		if (host.getIPv4Address() != null && host.isAttachedToSwitch() == true)
		{
			log.info(String.format("Host %s added", host.getName()));
			this.knownHosts.put(device, host);

			Map<Long, IOFSwitch> switches = this.getSwitches();
			IOFSwitch hostswitch = host.getSwitch();
			OFMatch ofm = new OFMatch();
//			ofm.DataLayer(Ethernet.TYPE_IPv4);

			StringBuilder macaddr = new StringBuilder(Long.toHexString(host.getMACAddress()));
			while(macaddr.length() < 12)
			{
				macaddr.insert(0, "0");
			}

			String newmacaddr = macaddr.toString();
			newmacaddr = newmacaddr.replaceAll("(.{2})", "$1" + ':').substring(0,17);

			ofm.setDataLayerDestination(newmacaddr);

			for(Long s : switches.keySet())
			{
				IOFSwitch switchobj = switches.get(s);

				if(switchobj.equals(hostswitch))
				{
					OFAction ofa = new OFActionOutput(host.getPort());
					OFInstruction i = new OFInstructionApplyActions(Arrays.asList(ofa));
					SwitchCommands.installRule(switchobj, this.getTable(), SwitchCommands.DEFAULT_PRIORITY, ofm, Arrays.asList(i));
				}
				else
				{
					Graph graph = new Graph();

					Collection<Link> links = this.getLinks();
					Map<Integer, Long> nodeidmap = new HashMap<Integer, Long>();

					Node[] node = new Node[100];
					c = 0;
					int sflag;
					int dflag;
					Integer sourceiterator = 0;
					Integer destiterator = 0;
					for(Link l : links)
					{
						sflag = 0;
						dflag = 0;
						Long sourcenode = l.getSrc();
						Long destnode = l.getDst();

						if(nodeidmap.containsValue(sourcenode))
						{
							sourceiterator = keyvalue(nodeidmap, sourcenode);
							sflag = 1;
						}
						if(nodeidmap.containsValue(destnode))
						{
							destiterator = keyvalue(nodeidmap, destnode);
							dflag = 1;
						}
						if(sflag == 0)
						{
							node[c] = new Node(c.toString());
							nodeidmap.put(c, sourcenode);
							c = c+1;
						}
						if(dflag == 0)
						{
							node[c] = new Node(c.toString());
							nodeidmap.put(c, destnode);
							c = c+1;
						}
						if(sflag == 0 && dflag == 0)
						{
							node[c-2].addDestination(node[c-1], 1);
						}
						else if(sflag == 0 && dflag == 1)
						{
							node[c - 1].addDestination(node[destiterator], 1);
						}
						else if(sflag == 1 && dflag == 0)
						{
							node[sourceiterator].addDestination(node[c-1], 1);
						}
						else
						{
							node[sourceiterator].addDestination(node[destiterator], 1);
						}
					}
					for(int i = 0; i < c; i++)
					{
						graph.addNode(node[i]);
					}

					int sourcenodeid = keyvalue(nodeidmap, s);
					graph = ShortestPath(graph, node[sourcenodeid]);

					List<Node> hnodespath = node[keyvalue(nodeidmap, hostswitch.getId())].getsPath();

					Long nextHopSwitch = hostswitch.getId();
					if(hnodespath.size() == 1)
					{
						nextHopSwitch = hostswitch.getId();
					}
					else
					{
						nextHopSwitch = nodeidmap.get(Integer.parseInt(hnodespath.get(1).getName()));
					}
					Integer outport = 0;
					for(Link l : links)
					{
						if(l.getSrc() == switchobj.getId() && l.getDst() == nextHopSwitch)
						{
							outport = l.getSrcPort();
						}
					}
					OFAction ofa = new OFActionOutput(outport);
					OFInstruction i = new OFInstructionApplyActions(Arrays.asList(ofa));
					SwitchCommands.installRule(switchobj, this.getTable(), SwitchCommands.DEFAULT_PRIORITY, ofm, Arrays.asList(i));
				}
			}
		}
		/*********************************************************************/
	}

	/**
	 * Event handler called when a switch joins the network.
	 * @param DPID for the switch
	 */
	@Override
	public void switchAdded(long switchId)
	{
		IOFSwitch sw = this.floodlightProv.getSwitch(switchId);
		log.info(String.format("Switch s%d added", switchId));

	}

	public int knapSack(int W, int wt[], int val[], int n)
	{
		// Base Case
		if (n == 0 || W == 0)
			return 0;

		// If weight of the nth item is
		// more than Knapsack capacity W,
		// then this item cannot be included
		// in the optimal solution
		if (wt[n - 1] > W)
			return knapSack(W, wt, val, n - 1);

			// Return the maximum of two cases:
			// (1) nth item included
			// (2) not included
		else
			return 1;
	}

	/**
	 * Event handler called when a switch leaves the network.
	 * @param DPID for the switch
	 */
	@Override
	public void switchRemoved(long switchId)
	{
		IOFSwitch sw = this.floodlightProv.getSwitch(switchId);
		log.info(String.format("Switch s%d removed", switchId));

		Integer c;

		for(Host host : this.getHosts())
		{
			if(host.getIPv4Address() == null || host.isAttachedToSwitch() == false)
			{
				continue;
			}
			Map<Long, IOFSwitch> switches = this.getSwitches();
			IOFSwitch hostswitch = host.getSwitch();

			OFMatch ofm = new OFMatch();
//			ofm.DataLayer(Ethernet.TYPE_IPv4);

			StringBuilder macaddr = new StringBuilder(Long.toHexString(host.getMACAddress()));
			while(macaddr.length() < 12)
			{
				macaddr.insert(0, "0");
			}

			String newmacaddr = macaddr.toString();
			newmacaddr = newmacaddr.replaceAll("(.{2})", "$1" + ':').substring(0,17);

			ofm.setDataLayerDestination(newmacaddr);

			for(Long s : switches.keySet())
			{
				IOFSwitch switchobj = switches.get(s);

				if(switchobj.equals(hostswitch))
				{
					OFAction ofa = new OFActionOutput(host.getPort());
					OFInstruction i = new OFInstructionApplyActions(Arrays.asList(ofa));
					SwitchCommands.installRule(switchobj, this.getTable(), SwitchCommands.DEFAULT_PRIORITY, ofm, Arrays.asList(i));
				}

				else
				{
					Graph graph = new Graph();

					Collection<Link> links = this.getLinks();

					Map<Integer, Long> nodeidmap = new HashMap<Integer, Long>();

					Node[] node = new Node[100];
					c = 0;
					int sflag;
					int dflag;
					Integer sourceiterator = 0;
					Integer destiterator = 0;
					for(Link l : links)
					{
						sflag = 0;
						dflag = 0;
						Long sourcenode = l.getSrc();
						Long destnode = l.getDst();

						if(nodeidmap.containsValue(sourcenode))
						{
							sourceiterator = keyvalue(nodeidmap, sourcenode);
							sflag = 1;
						}
						if(nodeidmap.containsValue(destnode))
						{
							destiterator = keyvalue(nodeidmap, destnode);
							dflag = 1;
						}
						if(sflag == 0)
						{
							node[c] = new Node(c.toString());
							nodeidmap.put(c, sourcenode);
							c = c+1;
						}
						if(dflag == 0)
						{
							node[c] = new Node(c.toString());
							nodeidmap.put(c, destnode);
							c = c+1;
						}
						if(sflag == 0 && dflag == 0)
						{
							node[c-2].addDestination(node[c-1], 1);
						}
						else if(sflag == 0 && dflag == 1)
						{
							node[c - 1].addDestination(node[destiterator], 1);
						}
						else if(sflag == 1 && dflag == 0)
						{
							node[sourceiterator].addDestination(node[c-1], 1);
						}
						else
						{
							node[sourceiterator].addDestination(node[destiterator], 1);
						}
					}
					for(int i = 0; i < c; i++)
					{
						graph.addNode(node[i]);
					}

					int sourcenodeid = keyvalue(nodeidmap, s);
					graph = ShortestPath(graph, node[sourcenodeid]);

					List<Node> hnodespath = node[keyvalue(nodeidmap, hostswitch.getId())].getsPath();
					Long nextHopSwitch = hostswitch.getId();
					if(hnodespath.size() == 1)
					{
						nextHopSwitch = hostswitch.getId();
					}
					else
					{
						nextHopSwitch = nodeidmap.get(Integer.parseInt(hnodespath.get(1).getName()));
					}
					Integer outport = 0;
					for(Link l : links)
					{
						if(l.getSrc() == switchobj.getId() && l.getDst() == nextHopSwitch)
						{
							outport = l.getSrcPort();
						}
					}
					OFAction ofa = new OFActionOutput(outport);
					OFInstruction i = new OFInstructionApplyActions(Arrays.asList(ofa));
					SwitchCommands.installRule(switchobj, this.getTable(), SwitchCommands.DEFAULT_PRIORITY, ofm, Arrays.asList(i));
				}
			}
		}
		/*********************************************************************/
	}

	public void printCode(String root, String s)
	{

		// base case; if the left and right are null
		// then its a leaf node and we print
		// the code s generated by traversing the tree.
		if (root== null) {


			System.out.println(root + ":" + s);

			return;
		}

		// if we go to left then add "0" to the code.
		// if we go to the right add"1" to the code.

		// recursive calls for left and
		// right sub-tree of the generated tree.
		printCode(root, s + "0");
		printCode(root, s + "1");
	}

	/**
	 * Event handler called when multiple links go up or down.
	 * @param updateList information about the change in each link's state
	 */
	@Override
	public void linkDiscoveryUpdate(List<LDUpdate> updateList)
	{
		for (LDUpdate update : updateList)
		{

			if (0 == update.getDst())
			{
				log.info(String.format("Link s%s:%d -> host updated",
						update.getSrc(), update.getSrcPort()));
			}
			else
			{
				log.info(String.format("Link s%s:%d -> %s:%d updated",
						update.getSrc(), update.getSrcPort(),
						update.getDst(), update.getDstPort()));
			}
		}

		Integer c;
		for(Host host : this.getHosts())
		{
			if(host.getIPv4Address() == null || host.isAttachedToSwitch() == false)
			{
				continue;
			}
			Map<Long, IOFSwitch> switches = this.getSwitches();
			IOFSwitch hostswitch = host.getSwitch();
			OFMatch ofm = new OFMatch();
//			ofm.DataLayer(Ethernet.TYPE_IPv4);
			StringBuilder macaddr = new StringBuilder(Long.toHexString(host.getMACAddress()));
			while(macaddr.length() < 12)
			{
				macaddr.insert(0, "0");
			}
			String newmacaddr = macaddr.toString();
			newmacaddr = newmacaddr.replaceAll("(.{2})", "$1" + ':').substring(0,17);
			ofm.setDataLayerDestination(newmacaddr);

			for(Long s : switches.keySet())
			{
				IOFSwitch switchobj = switches.get(s);
				if(switchobj.equals(hostswitch))
				{
					OFAction ofa = new OFActionOutput(host.getPort());
					OFInstruction i = new OFInstructionApplyActions(Arrays.asList(ofa));
					SwitchCommands.installRule(switchobj, this.getTable(), SwitchCommands.DEFAULT_PRIORITY, ofm, Arrays.asList(i));
				}
				else
				{
					Graph graph = new Graph();
					Collection<Link> links = this.getLinks();
					Map<Integer, Long> nodeidmap = new HashMap<Integer, Long>();
					Node[] node = new Node[100];
					c = 0;
					int sflag;
					int dflag;
					Integer sourceiterator = 0;
					Integer destiterator = 0;
					for(Link l : links)
					{
						sflag = 0;
						dflag = 0;
						Long sourcenode = l.getSrc();
						Long destnode = l.getDst();
						if(nodeidmap.containsValue(sourcenode))
						{
							sourceiterator = keyvalue(nodeidmap, sourcenode);
							sflag = 1;
						}
						if(nodeidmap.containsValue(destnode))
						{
							destiterator = keyvalue(nodeidmap, destnode);
							dflag = 1;
						}
						if(sflag == 0)
						{
							node[c] = new Node(c.toString());
							nodeidmap.put(c, sourcenode);
							c = c+1;
						}
						if(dflag == 0)
						{
							node[c] = new Node(c.toString());
							nodeidmap.put(c, destnode);
							c = c+1;
						}
						if(sflag == 0 && dflag == 0)
						{
							node[c-2].addDestination(node[c-1], 1);
						}
						else if(sflag == 0 && dflag == 1)
						{
							node[c - 1].addDestination(node[destiterator], 1);
						}
						else if(sflag == 1 && dflag == 0)
						{
							node[sourceiterator].addDestination(node[c-1], 1);
						}
						else
						{
							node[sourceiterator].addDestination(node[destiterator], 1);
						}
					}
					for(int i = 0; i < c; i++)
					{
						graph.addNode(node[i]);
					}

					int sourcenodeid = keyvalue(nodeidmap, s);
					graph = ShortestPath(graph, node[sourcenodeid]);

					List<Node> hnodespath = node[keyvalue(nodeidmap, hostswitch.getId())].getsPath();
					Long nextHopSwitch = hostswitch.getId();
					if(hnodespath.size() == 1)
					{
						nextHopSwitch = hostswitch.getId();
					}
					else
					{
						nextHopSwitch = nodeidmap.get(Integer.parseInt(hnodespath.get(1).getName()));
					}
					Integer outport = 0;
					for(Link l : links)
					{
						if(l.getSrc() == switchobj.getId() && l.getDst() == nextHopSwitch)
						{
							outport = l.getSrcPort();
						}
					}
					OFAction ofa = new OFActionOutput(outport);
					OFInstruction i = new OFInstructionApplyActions(Arrays.asList(ofa));
					SwitchCommands.installRule(switchobj, this.getTable(), SwitchCommands.DEFAULT_PRIORITY, ofm, Arrays.asList(i));
				}
			}
		}
		/*********************************************************************/
	}


	int minKey(int key[], Boolean mstSet[])
	{
		// Initialize min value
		int min = Integer.MAX_VALUE, min_index = -1;

		for (int v = 0; v < key.length; v++)
			if (mstSet[v] == false && key[v] < min) {
				min = key[v];
				min_index = v;
			}

		return min_index;
	}


	/**
	 * Event handler called when link goes up or down.
	 * @param update information about the change in link state
	 */
	@Override
	public void linkDiscoveryUpdate(LDUpdate update)
	{ this.linkDiscoveryUpdate(Arrays.asList(update)); }

	/**
	 * Event handler called when the IP address of a host changes.
	 * @param device information about the host
	 */
	@Override
	public void deviceIPV4AddrChanged(IDevice device)
	{ this.deviceAdded(device); }

	/**
	 * Event handler called when the VLAN of a host changes.
	 * @param device information about the host
	 */
	@Override
	public void deviceVlanChanged(IDevice device)
	{ /* Nothing we need to do, since we're not using VLANs */ }

	/**
	 * Event handler called when the controller becomes the master for a switch.
	 * @param DPID for the switch
	 */
	@Override
	public void switchActivated(long switchId)
	{ /* Nothing we need to do, since we're not switching controller roles */ }

	/**
	 * Event handler called when some attribute of a switch changes.
	 * @param DPID for the switch
	 */
	@Override
	public void switchChanged(long switchId)
	{ /* Nothing we need to do */ }

	/**
	 * Event handler called when a port on a switch goes up or down, or is
	 * added or removed.
	 * @param DPID for the switch
	 * @param port the port on the switch whose status changed
	 * @param type the type of status change (up, down, add, remove)
	 */
	@Override
	public void switchPortChanged(long switchId, ImmutablePort port,
								  PortChangeType type)
	{ /* Nothing we need to do, since we'll get a linkDiscoveryUpdate event */ }

	/**
	 * Gets a name for this module.
	 * @return name for this module
	 */
	@Override
	public String getName()
	{ return this.MODULE_NAME; }

	/**
	 * Check if events must be passed to another module before this module is
	 * notified of the event.
	 */
	@Override
	public boolean isCallbackOrderingPrereq(String type, String name)
	{ return false; }

	/**
	 * Check if events must be passed to another module after this module has
	 * been notified of the event.
	 */
	@Override
	public boolean isCallbackOrderingPostreq(String type, String name)
	{ return false; }

	/**
	 * Tell the module system which services we provide.
	 */
	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices()
	{
		Collection<Class<? extends IFloodlightService>> services =
				new ArrayList<Class<? extends IFloodlightService>>();
		services.add(InterfaceShortestPathSwitching.class);
		return services;
	}

	/**
	 * Tell the module system which services we implement.
	 */
	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService>
	getServiceImpls()
	{
		Map<Class<? extends IFloodlightService>, IFloodlightService> services =
				new HashMap<Class<? extends IFloodlightService>,
						IFloodlightService>();
		// We are the class that implements the service
		services.put(InterfaceShortestPathSwitching.class, this);
		return services;
	}

	/**
	 * Tell the module system which modules we depend on.
	 */
	@Override
	public Collection<Class<? extends IFloodlightService>>
	getModuleDependencies()
	{
		Collection<Class<? extends IFloodlightService >> modules =
				new ArrayList<Class<? extends IFloodlightService>>();
		modules.add(IFloodlightProviderService.class);
		modules.add(ILinkDiscoveryService.class);
		modules.add(IDeviceService.class);
		return modules;
	}

	// using Djikstra's shortest path algo
	public static Graph ShortestPath(Graph graph, Node source)
	{
		source.setDistance(0);
		Set<Node> setNodes = new HashSet<Node>();
		Set<Node> unsetNodes = new HashSet<Node>();
		unsetNodes.add(source);
		while (unsetNodes.size() != 0) {
			Node currNode = lowestDist(unsetNodes);
			unsetNodes.remove(currNode);
			for (Map.Entry < Node, Integer> adjacencyPair:
					currNode.getAdjacentNodes().entrySet()) {
				Node adjacentNode = adjacencyPair.getKey();
				Integer edgeWeight = adjacencyPair.getValue();
				if (!setNodes.contains(adjacentNode)) {
					Mindist(adjacentNode, edgeWeight, currNode);
					unsetNodes.add(adjacentNode);
				}
			}
			setNodes.add(currNode);
		}
		return graph;
	}

	void printMST(int parent[], int graph[][])
	{
		System.out.println("Edge \tWeight");
		for (int i = 1; i < graph.length; i++)
			System.out.println(parent[i] + " - " + i + "\t" + graph[i][parent[i]]);
	}

	private static Node lowestDist(Set < Node > unsetNodes)
	{
		Node lowestDistanceNode = null;
		int lowestDistance = Integer.MAX_VALUE;
		int temp = Integer.MAX_VALUE;
		for (Node node: unsetNodes) {
			int nodeDistance = node.getDistance();
			if (nodeDistance < lowestDistance) {
				temp = nodeDistance
				lowestDistance = temp;
				lowestDistanceNode = node;
			}
		}
		return lowestDistanceNode;
	}


	private static void Mindist(Node evaluationNode, Integer edgeWeigh, Node sourceNode)
	{
		Integer sourceDistance = sourceNode.getDistance();
		if (sourceDistance + edgeWeigh < evaluationNode.getDistance())
		{
			evaluationNode.setDistance(sourceDistance + edgeWeigh);
			LinkedList<Node> shortestPath = new LinkedList<Node>(sourceNode.getsPath());
			shortestPath.add(sourceNode);
			evaluationNode.setShortestPath(shortestPath);
		}
	}

}

class Graph {
	private Set<Node> nodes = new HashSet<Node>();
	public void addNode(Node nodex)
	{
		nodes.add(nodex);
	}
}

class Node {
	private String name;
	private List<Node> sPath = new LinkedList<Node>();
	private Integer dist = Integer.MAX_VALUE;
	Map<Node, Integer> adjNodes = new HashMap<Node,Integer>();
	public void addDestination(Node destination, int dist) {
		adjNodes.put(destination, dist);
	}
	public Node(String name) {
		this.name = name;
	}
	public Integer getDistance()
	{
		return this.dist;
	}
	public void setDistance(Integer dist)
	{
		this.dist = dist;
	}
	public Map<Node, Integer> getAdjacentNodes()
	{
		return this.adjNodes;
	}
	public List<Node> getsPath()
	{
		return sPath;
	}
	public void setShortestPath(List<Node> sp)
	{
		this.sPath = sp;
	}
	public String getName()
	{
		return this.name;
	}
}
