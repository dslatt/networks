package edu.wisc.cs.sdn.vnet.sw;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.MACAddress;
import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import java.util.Timer;
import java.util.TimerTask;

/**
 * @author Aaron Gember-Jacobson
 */
public class Switch extends Device
{	

        private static final int MAC_TBL_INIT_SIZE = 16;
        private static final int MAC_TBL_TIMEOUT = 15;
        private static final int TMOUT_FREQ = 1;

        private ConcurrentHashMap macTable;

        private Timer timer;

        private void printTable(){
                Map.Entry<MACAddress, SwitchTblEntry> map;
                Iterator<Map.Entry> itr = macTable.entrySet().iterator();
                System.out.println("Current MAC Table (MAC | Iface name | timeout)");
                while(itr.hasNext()){
                    map = itr.next();
                    System.out.println(map.getKey().toString() + "\t| " + map.getValue().toString());
                }
        }

        class TimeoutTask extends TimerTask {
            public void run(){
                Map.Entry<MACAddress, SwitchTblEntry> map;
                Iterator<Map.Entry> itr = macTable.entrySet().iterator();
                while(itr.hasNext()){
                    map = itr.next();
                    if (map.getValue().getTimeout() == 0){
                        macTable.remove(map.getKey());
                    }else{
                        map.getValue().setTimeout(map.getValue().getTimeout() - 1);
                    }
                }
            }
        }

	/**
	 * Creates a router for a specific host.
	 * @param host hostname for the router
	 */
	public Switch(String host, DumpFile logfile)
	{
		super(host,logfile);
                    /*
                initilalize the ConcurrnetHashMap

                start new timer (every 1s)
                - for each mapping table entry
                    if timeout != 0 then decrement by 1
                    else purge mapping from table
                    */

                macTable = new ConcurrentHashMap<MACAddress, SwitchTblEntry>(MAC_TBL_INIT_SIZE);

                timer = new Timer();
                timer.schedule(new TimeoutTask(), 0, TMOUT_FREQ * 1000); 

	}

	/**
	 * Handle an Ethernet packet received on a specific interface.
	 * @param etherPacket the Ethernet packet that was received
	 * @param inIface the interface on which the packet was received
	 */
	public void handlePacket(Ethernet etherPacket, Iface inIface)
	{

                SwitchTblEntry map;

		System.out.println("*** -> Received packet: " +
                etherPacket.toString().replace("\n", "\n\t"));
		
		/********************************************************************/


		/* TODO: Handle packets                                             */
                    /*
                update mapping table
                - if src iface/mac is present then reset timer
                  else just add new entry (iface, mac) to table

                send packet
                 - if mapping is present to correct iface
                   else broadcast pkt
                    */		

                // update table
                if ((map = (SwitchTblEntry)macTable.get(etherPacket.getSourceMAC())) != null){
                    map.setTimeout(MAC_TBL_TIMEOUT);
                }else{
                    macTable.put(etherPacket.getSourceMAC(), new SwitchTblEntry(inIface, MAC_TBL_TIMEOUT));
                }

                // send packet
                if ((map = (SwitchTblEntry)macTable.get(etherPacket.getDestinationMAC())) != null){
                    super.sendPacket(etherPacket, map.getIface());
                }else{
                    Iterator<Iface> itr = super.getInterfaces().values().iterator();
                    while(itr.hasNext()){
                        Iface pktIface = itr.next();
                        if(!inIface.getName().equals(pktIface.getName()))
                            super.sendPacket(etherPacket, pktIface);
                    }
                }

		/********************************************************************/
	}
}
