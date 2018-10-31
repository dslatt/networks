package edu.wisc.cs.sdn.vnet.rt;

import java.util.Arrays;

import edu.wisc.cs.sdn.vnet.Iface;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.ICMP;
import net.floodlightcontroller.packet.IPv4;

public class ICMPController {

    private static final byte MAX_TTL = 64;

    private Router host;

    public enum ICMPType {
        TIME_EXCEDDED   (b(11), b(0)),
        NET_UNREACH     (b(3), b(0)),
        HOST_UNREACH    (b(3), b(1)),
        PORT_UNREACH    (b(3), b(3)),
        ECHO_REPLY      (b(0), b(0));

        private final byte type;
        private final byte code;

        ICMPType (byte type, byte code){
            this.type = type;
            this.code = code;
        }

        private static byte b(int b){
            return (byte)b;
        }

        public byte type() { return type; }
        public byte code() { return code; }
    }

    public ICMPController(Router host){
        this.host = host;
    }

    public void sendPacket(ICMPType type, Ethernet etherPacket, Iface inface){

        Ethernet ether = new Ethernet();
        IPv4 ip = new IPv4();
        ICMP icmp = new ICMP();

        IPv4 ipacket = (IPv4)etherPacket.getPayload();

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

        if (type == ICMPType.ECHO_REPLY) {
            ip.setSourceAddress(ipacket.getDestinationAddress()); 
            icmp.setPayload(((ICMP)ipacket.getPayload()).getPayload());
        }else{
            ip.setSourceAddress(inface.getIpAddress());
            icmp.setPayload(new Data(buildICMPPayload(ipacket)));
        }

        ether.setEtherType(Ethernet.TYPE_IPv4);
        ether.setSourceMACAddress(inface.getMacAddress().toBytes());

        RouteEntry ipMatch;
        ArpEntry arpMatch;

        /* 
           Perform lookup based on IP for the packet
           but don't process any error conditions (ie send more ICMP packets)
        */

        System.out.println("doing lookup on " + IPv4.fromIPv4Address(ip.getDestinationAddress()));
        if ((ipMatch = host.performRouteLookup(ip.getDestinationAddress(), ether, null, false)) == null) {
            System.out.println("icmp route table failure\nsomething is very wrong");
            return;
        }else if ((arpMatch = host.performArpLookup(ether, ipMatch, false)) == null){
            System.out.println("icmp arp lookup failed\nsomething is very wrong again");
            return;
        }

        System.out.println("completed lookup giving: " + arpMatch.getMac().toString());

        ether.setDestinationMACAddress(arpMatch.getMac().toBytes());

        host.sendPacket(ether, inface);
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
}
