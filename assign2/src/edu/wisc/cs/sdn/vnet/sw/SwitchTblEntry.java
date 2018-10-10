package edu.wisc.cs.sdn.vnet.sw;

import edu.wisc.cs.sdn.vnet.Iface;


public class SwitchTblEntry {

    private Iface iface;
    private int timeout;

    public SwitchTblEntry(Iface iface, int timeout){
        this.iface = iface;
        this.timeout = timeout;
    }

    public Iface getIface(){
        return iface;
    }

    public int getTimeout(){
        return timeout;
    }

    public void setIface(Iface iface){
        this.iface = iface; 
    }

    public void setTimeout(int timeout){
        this.timeout = timeout;
    }
    
    @Override
    public String toString(){
        return "iface: " + iface.getName() + "\ttm: " + Integer.toString(timeout);
    }
}
