package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;
import edu.wisc.cs.sdn.vnet.rt.ICMPController.ICMPType;

import java.nio.ByteBuffer;
import java.util.Iterator;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.ICMP;
import net.floodlightcontroller.packet.IPv4;

/**
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */

public class Router extends Device
{	
	/** Routing table for the router */
    private	RouteTable routeTable;
    
    /** Controls ARP cache & processes all ARP actions */
    private ArpController arpController;

    /** Controls all ICMP generated */
    ICMPController icmpController;

	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	public Router(String host, DumpFile logfile)
	{
		super(host,logfile);
        this.routeTable = new RouteTable();
        this.arpController = new ArpController(this);
        this.icmpController = new ICMPController(this);
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
    public void loadArpCache(String arpCacheFile) {
        arpController.loadArpCache(arpCacheFile);
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

        if (etherPacket.getEtherType() == Ethernet.TYPE_ARP){
            if (!arpController.handleArpPacket(etherPacket, inIface)){
                System.out.println("dropped ARP packet due to bad opcode");
            }
            return;
        } else if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4){
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
            icmpController.sendPacket(ICMPType.TIME_EXCEDDED, etherPacket, inIface);
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
                if (ipacket.getProtocol() == IPv4.PROTOCOL_ICMP){
                    if (((ICMP)ipacket.getPayload()).getIcmpType() == (byte)8){
                        icmpController.sendPacket(ICMPType.ECHO_REPLY, etherPacket, inIface);
                        return;
                    }else{
                        System.out.println("dropped ICMP packet matching interface (not echo request)");
                        return;
                    }
                }else{
                    icmpController.sendPacket(ICMPType.PORT_UNREACH, etherPacket, inIface);
                    return;
                }
            }
        } 

        RouteEntry matchEntry;
        ArpEntry macMapping;
        if ((matchEntry = performRouteLookup(ipacket.getDestinationAddress(), etherPacket, inIface, true)) == null){
            return;
        }

        if ((macMapping = performArpLookup(etherPacket, matchEntry, true)) == null) {
            return;
        }
                
        //set the MACs as necessary before sending
        etherPacket.setDestinationMACAddress(macMapping.getMac().toBytes()); 
        etherPacket.setSourceMACAddress(matchEntry.getInterface().getMacAddress().toBytes());

        super.sendPacket(etherPacket, matchEntry.getInterface());
    }

    /**
     * Perform a route table lookup on a given ip
     * @param ip
     * @param etherPacket
     * @param iface
     * @param processErrors
     * @return RouteEntry on success, null on failure
     */
    public RouteEntry performRouteLookup(int ip, Ethernet etherPacket, Iface iface, boolean processErrors){

        // lookup route table entry
        RouteEntry matchEntry;
        if ((matchEntry = routeTable.lookup(ip)) == null &&
             processErrors){
            System.out.println("dropped packet due to no valid match found in routeTable");
            icmpController.sendPacket(ICMPType.NET_UNREACH, etherPacket, iface);
            return null;
        }

        return matchEntry;

    }

    /**
     * Perform an ARP cache lookup for a given packet
     * @param etherPacket
     * @param matchEntry
     * @param processErrors
     * @return ArpEntry on success, null on failure
     */
    public ArpEntry performArpLookup(Ethernet etherPacket, RouteEntry matchEntry, boolean processErrors){

        IPv4 ipacket = (IPv4)etherPacket.getPayload();
        int useAddr;


        System.out.println("gateway: " + IPv4.fromIPv4Address(matchEntry.getGatewayAddress()));
        System.out.println("dest: " + IPv4.fromIPv4Address(ipacket.getDestinationAddress()));
        // if gateway = 0 use destination, else use gateway
        if ((useAddr = matchEntry.getGatewayAddress()) == 0){
            System.out.println("using dest ip (gateway = 0)");
            useAddr = ipacket.getDestinationAddress();
        }

        // ARP lookup
        ArpEntry macMapping;
        if ((macMapping = arpController.lookupFromCache(useAddr)) == null &&
             processErrors){
            System.out.println("error: no arp mapping found");
            arpController.requestAddress(useAddr, etherPacket, matchEntry.getInterface());
            //icmpController.sendPacket(ICMPType.HOST_UNREACH, etherPacket, inface); 
            return null;
        }

        return macMapping;
    }

    public static void printPacket(Ethernet packet){
        System.out.printf("dest MAC: %s%nsrc MAC: %s%n", packet.getDestinationMAC().toString(), packet.getSourceMAC().toString());
        IPv4 ipkt = (IPv4)packet.getPayload();
        System.out.printf("dest IP: %s%nsrc IP %s%n", IPv4.fromIPv4Address(ipkt.getDestinationAddress()), IPv4.fromIPv4Address(ipkt.getSourceAddress()));

        System.out.println("*** -> Send packet: " + packet.toString().replace("\n", "\n\t"));
    }
}
