package edu.wisc.cs.sdn.vnet.rt;

import java.util.LinkedList;

import edu.wisc.cs.sdn.vnet.Iface;
import net.floodlightcontroller.packet.Ethernet;

public class ArpPendingQueue {

    /** How many ARP request cycles the entry has been alive for */
    private int arpCycles = 0;

    /** Queue for pending packets */
    private LinkedList<Ethernet> packetQueue;

    /** ARP request packet for this queue */
    private Ethernet arpPacket;

    /* The interface from the RouteEntry on the packet
       Is unique per IP so I think it should work */
    private Iface iface;

    /**
     * Create a queue for packet packets pending on ARP
     * @param iface
     */
    public ArpPendingQueue(Iface iface){
        this.iface = iface;
        this.packetQueue = new LinkedList<Ethernet>();
    }

    /**
     * Create a queue for packets penidng on ARP and add packet to it
     * @param packet
     * @param iface
     */
    public ArpPendingQueue(Ethernet packet, Iface iface){
        this(iface);
        this.enqueue(packet);
    }

    /**
     * Add a packet to the queue
     * @param packet
     */
    public void enqueue(Ethernet packet){
        this.packetQueue.add(packet);
    }

    /**
     * Remove a packet from the queue
     * @return Packet from front of queue
     */
    public Ethernet dequeue(){
        if (this.packetQueue.size() != 0){
            return this.packetQueue.pop();
        }else{
            return null;
        }
    }

    /** 
     * Increment the numebr fo ARP cyclces by 1
    */
    public void incrementCycleCount(){
        this.arpCycles++;
    }

    /**
     * Return number of ARP cycles
     * @return number of ARP cycles
     */
    public int getCycleCount(){
        return this.arpCycles;
    }

    /**
     * Return saved ARP request packet
     * @return saved ARP request packet
     */
    public Ethernet getArpPacket(){
        return this.arpPacket;
    }

    /**
     * Set the ARP request packet for the queue
     * @param arpPacket
     */
    public void setArpPacket(Ethernet arpPacket){
        this.arpPacket = arpPacket;
    }

    /**
     * Return the interface corresponding to ARP ip
     * @return Interface for the ARP ip
     */
    public Iface getIface(){
        return this.iface;
    }

}