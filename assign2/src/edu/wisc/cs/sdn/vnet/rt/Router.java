package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import java.nio.ByteBuffer;
import java.util.Iterator;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;

/**
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class Router extends Device
{	
	/** Routing table for the router */
	private RouteTable routeTable;
	
	/** ARP cache for the router */
	private ArpCache arpCache;
	
	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	public Router(String host, DumpFile logfile)
	{
		super(host,logfile);
		this.routeTable = new RouteTable();
		this.arpCache = new ArpCache();
	}
	
	/**
	 * @return routing table for the router
	 */
	public RouteTable getRouteTable()
	{ return this.routeTable; }
	
	/**
	 * Load a new routing table from a file.
	 * @param routeTableFile the name of the file containing the routing table
	 */
	public void loadRouteTable(String routeTableFile)
	{
		if (!routeTable.load(routeTableFile, this))
		{
			System.err.println("Error setting up routing table from file "
					+ routeTableFile);
			System.exit(1);
		}
		
		System.out.println("Loaded static route table");
		System.out.println("-------------------------------------------------");
		System.out.print(this.routeTable.toString());
		System.out.println("-------------------------------------------------");
	}
	
	/**
	 * Load a new ARP cache from a file.
	 * @param arpCacheFile the name of the file containing the ARP cache
	 */
	public void loadArpCache(String arpCacheFile)
	{
		if (!arpCache.load(arpCacheFile))
		{
			System.err.println("Error setting up ARP cache from file "
					+ arpCacheFile);
			System.exit(1);
		}
		
		System.out.println("Loaded static ARP cache");
		System.out.println("----------------------------------");
		System.out.print(this.arpCache.toString());
		System.out.println("----------------------------------");
	}

	/**
	 * Handle an Ethernet packet received on a specific interface.
	 * @param etherPacket the Ethernet packet that was received
	 * @param inIface the interface on which the packet was received
	 */
	public void handlePacket(Ethernet etherPacket, Iface inIface)
	{
		System.out.println("*** -> Received packet: " +
                etherPacket.toString().replace("\n", "\n\t"));
		
		/********************************************************************/
		/* TODO: Handle packets                                             */
/*
                1 - if packet is not IPv4: drop it
                2 - verify packet checksum
                     - only compute over the IP header (read length of 'length' field)
                     - must zero out the checksum field in header before computation
                     - utilize serialize() from IPv4 class to compute
                     - if incorrect drop the packet
                3 - verify packet TTL (how many hops left?)
                    - decrement existing TTL by 1
                    - if resultsing TTL is 0 or less than drop packet
                4 - check if packet if for one of router interfaces
                    - use the interfaces list from superclass Device
                    - if packets dest IP matches an interface, just drop the packet
                5 - next step is to forward the packet according to table entries
                    - use lookup from Router to get correct RouteEntry for dest IP
                    - if no match just drop packet
                6 - determine next hop IP and MAC
                    - call lookup from ArpCache to get the next MAC (new dest MAC)
                    - MAC of outgoing interface should be set as source MAC
                7 - call sendPacket to send the packet 

                to drop packet just return w/o doing any send
*/   

                if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4){
                    System.out.println("dropped packet due to type mismatch");
                    return;
                }

                IPv4 ipacket = (IPv4)etherPacket.getPayload();
                short checksum = ipacket.getChecksum();

                ipacket.resetChecksum();

                // sets the checksum, headerLength, and totalLength fields
                byte[] sipacket = ipacket.serialize();

                // use the fresh calculated checksum from serialize                
                if (ByteBuffer.wrap(sipacket).getShort(10) != checksum){
                    System.out.println("dropped packet due to bad checksum");
                    return;
                }

                ipacket.setTtl((byte)(ipacket.getTtl() - 1));
                if (ipacket.getTtl() <= 0){
                    System.out.println("dropped packet due to zero ttl");
                    return;
                }

                // need to compute new checksum after updating ttl
                ipacket.resetChecksum();
                ipacket.serialize();

                // check router interfaces
                Iterator<Iface> ifacesItr = super.getInterfaces() .values().iterator();
                System.out.println("name\tip\tmac\n");
                while(ifacesItr.hasNext()){
                    Iface iff = ifacesItr.next();
                    System.out.printf("%s\t%s\t%s\n", iff.getName(), IPv4.fromIPv4Address(iff.getIpAddress()), iff.getMacAddress().toString());
                    if (iff.getIpAddress() == ipacket.getDestinationAddress()){
                        return;
                    }
                } 

                // lookup route table entry
                RouteEntry matchEntry;
                if ((matchEntry = routeTable.lookup(ipacket.getDestinationAddress())) == null){
                    System.out.println("dropped packet due to no valid match found in routeTable");
                    return;
                }

                int useAddr;

                // if gateway = 0 use destination, else use gateway
                if ((useAddr = matchEntry.getGatewayAddress()) == 0){
                    System.out.println("using dest ip (gateway = 0)");
                    useAddr = ipacket.getDestinationAddress();
                }

                // ARP lookup
                ArpEntry macMapping;
                if ((macMapping = arpCache.lookup(useAddr)) == null){
                    System.out.println("error: no arp mapping found");
                    return;
                } 

                //set the MACs as necessary before sending
                etherPacket.setDestinationMACAddress(macMapping.getMac().toBytes()); 
                etherPacket.setSourceMACAddress(matchEntry.getInterface().getMacAddress().toBytes());

                super.sendPacket(etherPacket, matchEntry.getInterface());
                
                //printPacket(etherPacket);
		
		/********************************************************************/
	}

        public static void printPacket(Ethernet packet){
            System.out.printf("dest MAC: %s%nsrc MAC: %s%n", packet.getDestinationMAC().toString(), packet.getSourceMAC().toString());
            IPv4 ipkt = (IPv4)packet.getPayload();
            System.out.printf("dest IP: %s%nsrc IP %s%n", IPv4.fromIPv4Address(ipkt.getDestinationAddress()), IPv4.fromIPv4Address(ipkt.getSourceAddress()));

	    System.out.println("*** -> Send packet: " + packet.toString().replace("\n", "\n\t"));
        }
}
