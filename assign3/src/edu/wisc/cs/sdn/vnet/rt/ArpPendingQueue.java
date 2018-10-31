package edu.wisc.cs.sdn.vnet.rt;

import java.util.LinkedList;

import edu.wisc.cs.sdn.vnet.Iface;
import net.floodlightcontroller.packet.Ethernet;

public class ArpPendingQueue {

    private int arpCycles = 0;
    private LinkedList<Ethernet> packetQueue;
    private Ethernet arpPacket;

    /* The interface from the RouteEntry on the packet
       Is unique per IP so I think it should work */
    private Iface iface;

    public ArpPendingQueue(Iface iface){
        this.iface = iface;
        this.packetQueue = new LinkedList<Ethernet>();
    }

    public ArpPendingQueue(Ethernet packet, Iface iface){
        this(iface);
        this.enqueue(packet);
    }

    public void enqueue(Ethernet packet){
        this.packetQueue.add(packet);
    }

    public Ethernet dequeue(){
        if (this.packetQueue.size() != 0){
            return this.packetQueue.pop();
        }else{
            return null;
        }
    }

    public void incrementCycleCount(){
        this.arpCycles++;
    }

    public int getCycleCount(){
        return this.arpCycles;
    }

    public Ethernet getArpPacket(){
        return this.arpPacket;
    }

    public void setArpPacket(Ethernet arpPacket){
        this.arpPacket = arpPacket;
    }

    public Iface getIface(){
        return this.iface;
    }

}