package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Iterator;

import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.ICMP;
import net.floodlightcontroller.packet.IPv4;

/**
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */

enum TypeICMP {
    TIME_EXCEDDED   (b(11), b(0)),
    NET_UNREACH     (b(3), b(0)),
    HOST_UNREACH    (b(3), b(1)),
    PORT_UNREACH    (b(3), b(3)),
    ECHO_REPLY      (b(0), b(0));

    private final byte type;
    private final byte code;

    TypeICMP (byte type, byte code){
        this.type = type;
        this.code = code;
    }

    private static byte b(int b){
        return (byte)b;
    }

    public byte type() { return type; }
    public byte code() { return code; }
}

public class Router extends Device
{	
	/** Routing table for the router */
	private RouteTable routeTable;
	
	/** ARP cache for the router */
	private ArpCache arpCache;

    private static final byte MAX_TTL = 64;

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

        if (etherPacket.getEtherType() == Ethernet.TYPE_ARP){
                    
            ARP arp = (ARP)etherPacket.getPayload();

            if (arp.getOpCode() == ARP.OP_REQUEST){
                if (ByteBuffer.wrap(arp.getTargetProtocolAddress()).getInt() == inIface.getIpAddress()){
                    sendArpReply(etherPacket, inIface);
                    return;
                }
            } else if (arp.getOpCode() == ARP.OP_REPLY){
            /*
                add entry to arp cache
                dequeue packets & populate MAC before sending
            */
            }else{
                System.out.println("dropped ARP packet due to bad opcode");
                return;
            }

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
            sendICMP(TypeICMP.TIME_EXCEDDED, etherPacket, ipacket, inIface);
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

                        sendICMP(TypeICMP.ECHO_REPLY, etherPacket, ipacket, inIface);
                        return;
                    }else{
                        System.out.println("dropped ICMP packet matching interface (not echo request)");
                        return;
                    }
                }else{
                    sendICMP(TypeICMP.PORT_UNREACH, etherPacket, ipacket, inIface);
                    return;
                }
            }
        } 

        // lookup route table entry
        RouteEntry matchEntry;
        if ((matchEntry = routeTable.lookup(ipacket.getDestinationAddress())) == null){
            System.out.println("dropped packet due to no valid match found in routeTable");
            sendICMP(TypeICMP.NET_UNREACH, etherPacket, ipacket, inIface);
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
/*
            use hashtable of queues w/ IP as key
            would need to be a synchronized hash map for the threading parts
            options
                - create a thread for each queue: it will send arp request every 1s until a replay is recieved
                   will clear the queue after 3 failed send + 1s
                   * bad: could be exploited since spamming unknown IPs will cause unlimited threads to be created
                   * good: simple, better timing since each thread only has 1 timer/queue to worry about
                - create a shared thread to keep track of the counts: each queue will have a count associated with i
                    (ie how many ARp requests rounds it has been alive for), a central timer will send ARP requests for each
                    alive queue every 1s until an ARP is recieved and its removed
                    * good: only 1 thread, not many worries about race conditions and stuff
                    * bad: complictaed to manage the timing; not as responsive per queue as they are all managed by 1 thread

            dealing w/ response:
                just remove the queue from main hashmap (so it will not be included in the maintenance loop anymore)
                from there send all elements out in queue order 
                options:
                    - do it all right away; could be slow though
                    - make a thread to do it in background & just exit; seems like a bad idea though 

            for each unique ip queue the packet
                - each queue holds packets destined for a single ip (ie mac)
            after 3 failed arp requests drop all packets in that specific queue
                - resend the arp request every 1s that no response is obtained
                - after the 3 time failure create & send host unreachable ICMP



            1. on ARP cache lookup failure
                - check if a queue exists for the IP
                    if yes add to existing queue
                    if no create new queue for its IP
                - exit this location

            2. queue algo
                - seperate thread runs on each spawned queue
                - every 1s an ARP request is created 
                    - on response send all packets in the queue
                    - on failure (3 times) clear the queue (ie drop all packets)
                        & send ICMP host unreachable

*/
            sendICMP(TypeICMP.HOST_UNREACH, etherPacket, ipacket, inIface);
            return;
        } 
                
        //set the MACs as necessary before sending
        etherPacket.setDestinationMACAddress(macMapping.getMac().toBytes()); 
        etherPacket.setSourceMACAddress(matchEntry.getInterface().getMacAddress().toBytes());

        super.sendPacket(etherPacket, matchEntry.getInterface());
	}

    private void sendArpReply(Ethernet arpRequest, Iface inIface){
        ARP newArp = new ARP();
        Ethernet newEther = new Ethernet(); 

        newEther.setEtherType(Ethernet.TYPE_ARP);
        newEther.setSourceMACAddress(inIface.getMacAddress().toBytes());
        newEther.setDestinationMACAddress(arpRequest.getSourceMACAddress());
        newEther.setPayload(newArp);

        newArp.setHardwareType(ARP.HW_TYPE_ETHERNET);
        newArp.setProtocolType(ARP.PROTO_TYPE_IP);
        newArp.setHardwareAddressLength((byte)Ethernet.DATALAYER_ADDRESS_LENGTH);
        newArp.setProtocolAddressLength((byte)4);
        newArp.setOpCode(ARP.OP_REPLY);
        newArp.setSenderHardwareAddress(inIface.getMacAddress().toBytes());
        newArp.setSenderProtocolAddress(inIface.getIpAddress());
        newArp.setTargetHardwareAddress(arpRequest.getSourceMACAddress());
        newArp.setTargetProtocolAddress(((IPv4)arpRequest.getPayload()).getSourceAddress());

        super.sendPacket(newEther, inIface);

    }

    private void sendICMP(TypeICMP type, Ethernet etherPacket, IPv4 ipacket, Iface inface){

        Ethernet ether = new Ethernet();
        IPv4 ip = new IPv4();
        ICMP icmp = new ICMP();

        switch(type){
            case NET_UNREACH:
                System.out.println("icmp network unreachable");
                break;
            case HOST_UNREACH:
                System.out.println("icmp host unreachable");
                break;
            case PORT_UNREACH:
                System.out.println("icmp port unreachable");
                break;
            case TIME_EXCEDDED:
                System.out.println("icmp time excedded");
                break;
            case ECHO_REPLY:
                System.out.println("icmp echo reply");
                break;
        }

        ether.setPayload(ip);
        ip.setPayload(icmp);

        icmp.setIcmpType(type.type());
        icmp.setIcmpCode(type.code());

        ip.setTtl(MAX_TTL);
        ip.setProtocol(IPv4.PROTOCOL_ICMP);
        ip.setDestinationAddress(ipacket.getSourceAddress());

        if (type == TypeICMP.ECHO_REPLY) {
            ip.setSourceAddress(ipacket.getDestinationAddress()); 
            icmp.setPayload(ip.getPayload());
        }else{
            ip.setSourceAddress(inface.getIpAddress());
            icmp.setPayload(new Data(buildICMPPayload(ipacket)));
        }

        ether.setEtherType(Ethernet.TYPE_IPv4);
        ether.setSourceMACAddress(inface.getMacAddress().toBytes());

        RouteEntry ipMatch;

        System.out.println("doing lookup on " + IPv4.fromIPv4Address(ip.getDestinationAddress()));
        if ((ipMatch = routeTable.lookup(ip.getDestinationAddress())) == null) {
            System.out.println("icmp route table failure\nsomething is very wrong");
            return;
        }else{
            int arpIp;
            ArpEntry arpMatch;
            
            if ((arpIp = ipMatch.getGatewayAddress()) == 0){
                System.out.println("icmp arp lookup using dest addr : " + IPv4.fromIPv4Address(ip.getDestinationAddress()));
                arpIp = ip.getDestinationAddress(); 
            }

            if ((arpMatch = arpCache.lookup(arpIp)) == null){
                System.out.println("icmp arp lookup failed\nsomething is very wrong again");
                return;
            }else{
                ether.setDestinationMACAddress(arpMatch.getMac().toBytes());
            }
        }

        super.sendPacket(ether, inface);

    }

    private byte[] buildICMPPayload(IPv4 ipacket){

/*
        im worried that serialize could cause an issue here
        as long as checksum/length values arent set to 0 I think things should be ok though
        since it says it only changes values if the above is true
*/

        byte[] padding = new byte[4];
        byte[] ipHeader = ipacket.serialize();
        byte[] end = Arrays.copyOfRange(ipacket.getPayload().serialize(), 0, 8);

        byte[] payload = new byte[ipHeader.length + padding.length + end.length];

        System.arraycopy(padding, 0, payload, 0, padding.length);
        System.arraycopy(ipHeader, 0, payload, padding.length, ipHeader.length);
        System.arraycopy(end, 0, payload, padding.length + ipHeader.length, end.length);

        return payload;

    }

    public static void printPacket(Ethernet packet){
        System.out.printf("dest MAC: %s%nsrc MAC: %s%n", packet.getDestinationMAC().toString(), packet.getSourceMAC().toString());
        IPv4 ipkt = (IPv4)packet.getPayload();
        System.out.printf("dest IP: %s%nsrc IP %s%n", IPv4.fromIPv4Address(ipkt.getDestinationAddress()), IPv4.fromIPv4Address(ipkt.getSourceAddress()));

        System.out.println("*** -> Send packet: " + packet.toString().replace("\n", "\n\t"));
    }
}
