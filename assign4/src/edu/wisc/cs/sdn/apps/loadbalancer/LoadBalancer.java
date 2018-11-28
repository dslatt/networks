package edu.wisc.cs.sdn.apps.loadbalancer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFOXMFieldType;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.protocol.action.OFActionSetField;
import org.openflow.protocol.instruction.OFInstructionApplyActions;
import org.openflow.protocol.instruction.OFInstructionGotoTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.wisc.cs.sdn.apps.l3routing.L3Routing;
import edu.wisc.cs.sdn.apps.util.ArpServer;
import edu.wisc.cs.sdn.apps.util.SwitchCommands;
import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch.PortChangeType;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.ImmutablePort;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.devicemanager.internal.DeviceManagerImpl;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.TCP;
import net.floodlightcontroller.util.MACAddress;

public class LoadBalancer implements IFloodlightModule, IOFSwitchListener,
		IOFMessageListener
{
	public static final String MODULE_NAME = LoadBalancer.class.getSimpleName();
	
	private static final byte TCP_FLAG_SYN = 0x02;
	
	private static final short IDLE_TIMEOUT = 20;
	
	// Interface to the logging system
    private static Logger log = LoggerFactory.getLogger(MODULE_NAME);
    
    // Interface to Floodlight core for interacting with connected switches
    private IFloodlightProviderService floodlightProv;
    
    // Interface to device manager service
    private IDeviceService deviceProv;
    
    // Switch table in which rules should be installed
    private byte table;
    
    // Set of virtual IPs and the load balancer instances they correspond with
    private Map<Integer,LoadBalancerInstance> instances;

    /**
     * Loads dependencies and initializes data structures.
     */
	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException 
	{
		log.info(String.format("Initializing %s...", MODULE_NAME));
		
		// Obtain table number from config
		Map<String,String> config = context.getConfigParams(this);
        this.table = Byte.parseByte(config.get("table"));
        
        // Create instances from config
        this.instances = new HashMap<Integer,LoadBalancerInstance>();
        String[] instanceConfigs = config.get("instances").split(";");
        for (String instanceConfig : instanceConfigs)
        {
        	String[] configItems = instanceConfig.split(" ");
        	if (configItems.length != 3)
        	{ 
        		log.error("Ignoring bad instance config: " + instanceConfig);
        		continue;
        	}
        	LoadBalancerInstance instance = new LoadBalancerInstance(
        			configItems[0], configItems[1], configItems[2].split(","));
            this.instances.put(instance.getVirtualIP(), instance);
            log.info("Added load balancer instance: " + instance);
        }
        
		this.floodlightProv = context.getServiceImpl(
				IFloodlightProviderService.class);
        this.deviceProv = context.getServiceImpl(IDeviceService.class);
        
        /*********************************************************************/
        /* TODO: Initialize other class variables, if necessary              */
        
        /*********************************************************************/
	}

	/**
     * Subscribes to events and performs other startup tasks.
     */
	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException 
	{
		log.info(String.format("Starting %s...", MODULE_NAME));
		this.floodlightProv.addOFSwitchListener(this);
		this.floodlightProv.addOFMessageListener(OFType.PACKET_IN, this);
		
		/*********************************************************************/
		/* TODO: Perform other tasks, if necessary                           */
		
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
		
		/*********************************************************************/
		/* TODO: Install rules to send:                                      */
		/*       (1) packets from new connections to each virtual load       */
		/*       balancer IP to the controller                               */
		/*       (2) ARP packets to the controller, and                      */
		/*       (3) all other packets to the next rule table in the switch  */
		
		/*********************************************************************/

		// need to creates rules for every vIP in instance list
		for (Integer vIP : instances.keySet()){

			// create rule for vIP matching
			OFMatch tcpMatch = new OFMatch();
			tcpMatch.setDataLayerType(OFMatch.ETH_TYPE_IPV4);
			tcpMatch.setNetworkProtocol(OFMatch.IP_PROTO_TCP);
			tcpMatch.setNetworkDestination(vIP);

			// create rule for ARP matching
			OFMatch arpMatch = new OFMatch();
			arpMatch.setDataLayerType(OFMatch.ETH_TYPE_ARP);
			arpMatch.setField(OFOXMFieldType.ARP_TPA, vIP);

			// install rules to send correct packets to SDN controller (ie receive)
			SwitchCommands.installRule(sw, table, SwitchCommands.DEFAULT_PRIORITY, 
				tcpMatch, 
				Arrays.asList(
					new OFInstructionApplyActions(
						Arrays.asList(
							new OFActionOutput(OFPort.OFPP_CONTROLLER)
						)
					)
				));
			SwitchCommands.installRule(sw, table, SwitchCommands.DEFAULT_PRIORITY, 
				arpMatch,
				Arrays.asList(
					new OFInstructionApplyActions(
						Arrays.asList(
							new OFActionOutput(OFPort.OFPP_CONTROLLER)
						)
					)
				));
		}

		// install rule to send all others to next table w/ low priority
		SwitchCommands.installRule(sw, table, SwitchCommands.MIN_PRIORITY, 
			new OFMatch(), 
			Arrays.asList(
				new OFInstructionGotoTable(L3Routing.table)
			));

	}
	
	/**
	 * Handle incoming packets sent from switches.
	 * @param sw switch on which the packet was received
	 * @param msg message from the switch
	 * @param cntx the Floodlight context in which the message should be handled
	 * @return indication whether another module should also process the packet
	 */
	@Override
	public net.floodlightcontroller.core.IListener.Command receive(
			IOFSwitch sw, OFMessage msg, FloodlightContext cntx) 
	{
		// We're only interested in packet-in messages
		if (msg.getType() != OFType.PACKET_IN)
		{ return Command.CONTINUE; }
		OFPacketIn pktIn = (OFPacketIn)msg;
		
		// Handle the packet
		Ethernet ethPkt = new Ethernet();
		ethPkt.deserialize(pktIn.getPacketData(), 0,
				pktIn.getPacketData().length);
		
		/*********************************************************************/
		/* TODO: Send an ARP reply for ARP requests for virtual IPs; for TCP */
		/*       SYNs sent to a virtual IP, select a host and install        */
		/*       connection-specific rules to rewrite IP and MAC addresses;  */
		/*       ignore all other packets                                    */
		
		/*********************************************************************/

		IPv4 ipPkt;
		TCP tcpPkt;
		ARP arpPkt;
		int vIP;

		// check for TCP SYN packet w/ virtual IP 
		if(ethPkt.getEtherType() == Ethernet.TYPE_IPv4 && 
			(ipPkt = (IPv4)ethPkt.getPayload()).getProtocol() == IPv4.PROTOCOL_TCP &&
			(tcpPkt = (TCP)ipPkt.getPayload()).getFlags() == TCP_FLAG_SYN) &&
			(instances.contains(vIP = ipPkt.getDestinationAddress())) {

			// next real IP from the LoadBalancerInstance associated w/ vIP
			int nextInstanceIP = instances.get(vIP).getNextHostIP();

			OFMatch clientMatch = new OFMatch();
			clientMatch.setDataLayerType(OFMatch.ETH_TYPE_IPV4);
			clientMatch.setNetworkProtocol(OFMatch.IP_PROTO_TCP);
			clientMatch.setNetworkDestination(ipPkt.getDestinationAddress());
			clientMatch.setNetworkSource(ipPkt.getSourceAddress());
			clientMatch.setTransportDestination(tcpPkt.getDestinationPort());
			clientMatch.setTransportSource(tcpPkt.getSourcePort());

			OFMatch serverMatch = new OFMatch();
			serverMatch.setDataLayerType(OFMatch.ETH_TYPE_IPV4);
			serverMatch.setNetworkProtocol(OFMatch.IP_PROTO_TCP);
			serverMatch.setNetworkDestination(ipPkt.getSourceAddress());
			serverMatch.setNetworkSource(nextInstanceIP);
			serverMatch.setTransportDestination(tcpPkt.getSourcePort());
			serverMatch.setTransportSource(tcpPkt.getDestinationPort());

			SwitchCommands.installRule(sw, table, SwitchCommands.MAX_PRIORITY, 
				clientMatch, 
				Arrays.asList(
					new OFInstructionApplyActions(
						Arrays.asList(
							new OFActionSetField(OFOXMFieldType.ETH_DST,
								getHostMACAddress(nextInstanceIP)),
							new OFActionSetField(OFOXMFieldType.IPV4_DST, nextInstanceIP)
						)
					),
					new OFInstructionGotoTable(L3Routing.table)
				),
				SwitchCommands.NO_TIMEOUT,
				IDLE_TIMEOUT);

			SwitchCommands.installRule(sw, table, SwitchCommands.MAX_PRIORITY,
				serverMatch,
				Arrays.asList(
					new OFInstructionApplyActions(
						Arrays.asList(
							new OFActionSetField(OFOXMFieldType.ETH_SRC,
								instances.get(vIP).getVirtualMAC()),
							new OFActionSetField(OFOXMFieldType.IPV4_SRC, vIP)
						)
					),
					new OFInstructionGotoTable(L3Routing.table)
				),
				SwitchCommands.NO_TIMEOUT,
				IDLE_TIMEOUT);
		// check for ARP packet w/ virtual IP dest
		} else if (ethPkt.getEtherType() == Ethernet.TYPE_ARP &&
			instances.containsKey(vIP = (IPv4.toIPv4Address((arpPkt = (ARP)ethPkt.getPayload()).getTargetProtocolAddress())))){

			ARP arpReply = new ARP();
			Ethernet ethReply = new Ethernet();

			byte[] vMAC = instances.get(vIP).getVirtualMAC();

			ethReply.setEtherType(Ethernet.TYPE_ARP);
			ethReply.setSourceMACAddress(ethPkt.getSourceMACAddress());
			ethReply.setDestinationMACAddress(vMAC);
			ethReply.setPayload(arpReply);

			arpReply.setHardwareType(Ethernet.TYPE_ARP);
			arpReply.setOpCode(ARP.OP_REPLY);
			arpReply.setProtocolType(ARP.PROTO_TYPE_IP);
			arpReply.setHardwareAddressLength(arpPkt.getHardwareAddressLength());
			arpReply.setProtocolAddressLength(arpPkt.getProtocolAddressLength());
			arpReply.setSenderHardwareAddress(vMAC);
			arpReply.setSenderProtocolAddress(vIP);
			arpReply.setTargetHardwareAddress(arpPkt.getSenderHardwareAddress());
			arpReply.setTargetProtocolAddress(arpPkt.getSenderProtocolAddress());

			SwitchCommands.sendPacket(sw, (short)pktIn.getInPort(), ethReply);
		}

		
		// We don't care about other packets
		return Command.CONTINUE;
	}
	
	/**
	 * Returns the MAC address for a host, given the host's IP address.
	 * @param hostIPAddress the host's IP address
	 * @return the hosts's MAC address, null if unknown
	 */
	private byte[] getHostMACAddress(int hostIPAddress)
	{
		Iterator<? extends IDevice> iterator = this.deviceProv.queryDevices(
				null, null, hostIPAddress, null, null);
		if (!iterator.hasNext())
		{ return null; }
		IDevice device = iterator.next();
		return MACAddress.valueOf(device.getMACAddress()).toBytes();
	}

	/**
	 * Event handler called when a switch leaves the network.
	 * @param DPID for the switch
	 */
	@Override
	public void switchRemoved(long switchId) 
	{ /* Nothing we need to do, since the switch is no longer active */ }

	/**
	 * Event handler called when the controller becomes the master for a switch.
	 * @param DPID for the switch
	 */
	@Override
	public void switchActivated(long switchId)
	{ /* Nothing we need to do, since we're not switching controller roles */ }

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
	{ /* Nothing we need to do, since load balancer rules are port-agnostic */}

	/**
	 * Event handler called when some attribute of a switch changes.
	 * @param DPID for the switch
	 */
	@Override
	public void switchChanged(long switchId) 
	{ /* Nothing we need to do */ }
	
    /**
     * Tell the module system which services we provide.
     */
	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() 
	{ return null; }

	/**
     * Tell the module system which services we implement.
     */
	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> 
			getServiceImpls() 
	{ return null; }

	/**
     * Tell the module system which modules we depend on.
     */
	@Override
	public Collection<Class<? extends IFloodlightService>> 
			getModuleDependencies() 
	{
		Collection<Class<? extends IFloodlightService >> floodlightService =
	            new ArrayList<Class<? extends IFloodlightService>>();
        floodlightService.add(IFloodlightProviderService.class);
        floodlightService.add(IDeviceService.class);
        return floodlightService;
	}

	/**
	 * Gets a name for this module.
	 * @return name for this module
	 */
	@Override
	public String getName() 
	{ return MODULE_NAME; }

	/**
	 * Check if events must be passed to another module before this module is
	 * notified of the event.
	 */
	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) 
	{
		return (OFType.PACKET_IN == type 
				&& (name.equals(ArpServer.MODULE_NAME) 
					|| name.equals(DeviceManagerImpl.MODULE_NAME))); 
	}

	/**
	 * Check if events must be passed to another module after this module has
	 * been notified of the event.
	 */
	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) 
	{ return false; }
}
