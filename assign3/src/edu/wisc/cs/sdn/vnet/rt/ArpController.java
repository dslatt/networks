package edu.wisc.cs.sdn.vnet.rt;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import edu.wisc.cs.sdn.vnet.Iface;
import edu.wisc.cs.sdn.vnet.rt.ICMPController.ICMPType;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.MACAddress;

public class ArpController {

    enum ArpPacketType {
        ARP_REPLY,
        ARP_REQUEST
    }

    // HashMap containg all living ArpQueues
    // key = ip, value = queued packets waiting on ARP(ip) request
    private ConcurrentHashMap<Integer, ArpPendingQueue> arpQueues;

    private ArpCache arpCache;

    private Timer requestTimer;

    // reference to the host router used to send packets
    private Router host;

    class ArpRequestTimer extends TimerTask {
        public void run(){
        /* 
            go through arpQueues:
                if queue has been alive >3 request rounds: kill it & send ICMP stuff
                if queue has been alive 3 request rounds: just increment count
                if queue has been alive <3 request rounds: send ArpRequest & increment count 

                otherwise just inc the queue cycle count & generate/send
                an ARP request for the specific ip
        */

        synchronized(arpQueues){

        for (int queueIp : arpQueues.keySet()) {
            ArpPendingQueue arpQueue = arpQueues.get(queueIp);

            if (arpQueue.getCycleCount() > 3){
                // this might cause a runtime exception here; will probably have to fix
                arpQueue = arpQueues.remove(queueIp);

                Ethernet packet;
                while ((packet = arpQueue.dequeue()) != null){
                    // either save the interface the packet came in on here, or do a lookup in the route table
                    // currently doing a lookup based on the packets IP 
                    // in order to save the incoming interface, wrap each packet in something to hold(Ethernet + Iface)
                    int ip = ((IPv4)packet.getPayload()).getSourceAddress();
                    host.icmpController.sendPacket(ICMPType.HOST_UNREACH,
                                                    packet,
                                                    host.performRouteLookup(ip, packet, null, false).getInterface());
                }  
            }else if (arpQueue.getCycleCount() < 3){
                // avoid recreating the same ARP packet by just saving it the 1st time
                Ethernet arpPacket;
                if ((arpPacket = arpQueue.getArpPacket()) == null){
                    arpPacket = createArpPacket(null, arpQueue.getIface(), ArpPacketType.ARP_REQUEST, queueIp);
                    arpQueue.setArpPacket(arpPacket);
                }
                host.sendPacket(arpPacket, arpQueue.getIface());
            }
            arpQueue.incrementCycleCount();
        }

        }

        }
    }

    public ArpController(Router host){

        /*
            save host router reference
            init the hashmap
            create & start the 1s timer task
        */

        this.host = host;
        this.arpCache = new ArpCache();
        this.arpQueues = new ConcurrentHashMap<Integer, ArpPendingQueue>();        
        this.requestTimer = new Timer();
        requestTimer.scheduleAtFixedRate(new ArpRequestTimer(), 0, 1000);
    } 

    public void requestAddress(int ipin, Ethernet packet, Iface iface){

        /*
            if arpQueues contains a key = 'ip' then:
                add packet to queue
            else
                create new arpQueue & add packet into queue
                add new queue to arpQueues
        */

        synchronized(this.arpQueues){

            IPv4 ip = (IPv4)packet.getPayload();
            ArpPendingQueue arpQueue = arpQueues.get(ipin); 

            if (arpQueue != null){
                arpQueue.enqueue(packet);
            }else{
                arpQueues.put(ipin, new ArpPendingQueue(packet, iface));
            }

        }
    } 

    public boolean handleArpPacket(Ethernet etherPacket, Iface inIface){

        ARP arp = (ARP)etherPacket.getPayload();

        System.out.println("inFace IP: " + IPv4.fromIPv4Address(inIface.getIpAddress()));
        System.out.println("ARP IP: " + IPv4.fromIPv4Address(IPv4.toIPv4Address(arp.getTargetProtocolAddress())));


        if (arp.getOpCode() == ARP.OP_REQUEST &&
        IPv4.toIPv4Address(arp.getTargetProtocolAddress()) == inIface.getIpAddress()){
            System.out.println("handling ARP request (ie send a reply)");
            handleRequest(etherPacket, inIface, arp);
        } else if (arp.getOpCode() == ARP.OP_REPLY){
            System.out.println("handling ARP reply (from a request?)");
            handleReply(IPv4.toIPv4Address(arp.getSenderProtocolAddress()),
                        arp.getSenderHardwareAddress(),
                        inIface);

        }else{
            System.out.println("return false");
            return false;
        }
        System.out.println("return true");
        return true;
    }

    public void insertIntoCache(MACAddress mac, int ip){
        synchronized(this.arpCache) {
            arpCache.insert(mac, ip);
        }
    }

    public ArpEntry lookupFromCache(int ip){
        synchronized(this.arpCache) {
            return arpCache.lookup(ip);
        }
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
		System.out.print(arpCache.toString());
		System.out.println("----------------------------------");
    }

    private void handleReply(int arpIp, byte[] arpMac, Iface replyIface){
        /*
            find the correct queue in arpQueues & remove from the HashMap
            for each packet in queue:
                update w/ address from arpReply & send
        */

        /*
            ISSUE:
            if this reply comes while the ArpRequestTimer is accessing the
            queue related to it, it will send another ArpRequest before this
            code can remove the queue
            I dont think that this breaks anything but it generates a wasteful extra
            ArpRequest/ArpReplay sequence since any ArpReply w/o a mathcing queue
            will be ignored

            Im not sure of the best solution to this, but Im not going to spend
            time fixing it as I dont believe it will matter much
            (since our code isnt really used, dont want to waste time on it)
        */

        insertIntoCache(MACAddress.valueOf(arpMac), arpIp);

        synchronized(this.arpQueues){
            ArpPendingQueue arpQueue = arpQueues.remove(arpIp);

            if (arpQueue != null) {
                Ethernet packet;
                while ((packet = arpQueue.dequeue()) != null){
                    packet.setDestinationMACAddress(arpMac);
                    host.sendPacket(packet, replyIface); 
                }
            }
        }
    }

    private void handleRequest(Ethernet etherPacket, Iface iface, ARP arp){
        host.sendPacket(createArpPacket(etherPacket, iface, ArpPacketType.ARP_REPLY, 0), iface);
    }

    /* there is a better way to define this function, Ill figure it out later */
    private Ethernet createArpPacket(Ethernet arpRequest, Iface iface, ArpPacketType type, int ip){
        ARP newArp = new ARP();
        Ethernet newEther = new Ethernet(); 

        if (type == ArpPacketType.ARP_REPLY){
            newEther.setDestinationMACAddress(arpRequest.getSourceMACAddress());
            newArp.setOpCode(ARP.OP_REPLY);
            newArp.setTargetHardwareAddress(arpRequest.getSourceMACAddress());
            newArp.setTargetProtocolAddress(((ARP)arpRequest.getPayload()).getSenderProtocolAddress());
        }else if (type == ArpPacketType.ARP_REQUEST){
            newEther.setDestinationMACAddress(MACAddress.valueOf("FF:FF:FF:FF:FF:FF").toBytes());
            newArp.setOpCode(ARP.OP_REQUEST);
            newArp.setTargetHardwareAddress(MACAddress.valueOf(0).toBytes());  // NOTE: default byte value is 0, still not 100% sure this does what I want though
            newArp.setTargetProtocolAddress(ip);
        }

        newEther.setEtherType(Ethernet.TYPE_ARP);
        newEther.setSourceMACAddress(iface.getMacAddress().toBytes());
        newEther.setPayload(newArp);

        newArp.setHardwareType(ARP.HW_TYPE_ETHERNET);
        newArp.setProtocolType(ARP.PROTO_TYPE_IP);
        newArp.setHardwareAddressLength((byte)Ethernet.DATALAYER_ADDRESS_LENGTH);
        newArp.setProtocolAddressLength((byte)4);
        newArp.setSenderHardwareAddress(iface.getMacAddress().toBytes());
        newArp.setSenderProtocolAddress(iface.getIpAddress());

        return newEther;
    }
}
