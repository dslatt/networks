package org.openflow.protocol.statistics;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;

import org.openflow.protocol.meter.OFMeterBandType;
import org.openflow.util.U16;

/**
 * Represents an ofp_meter_stats structure
 * @author Srini Seetharaman (srini.seetharaman@gmail.com)
 */
public class OFMeterFeatures implements OFStatistics {
    public static int MINIMUM_LENGTH = 16;

    protected short length = (short)MINIMUM_LENGTH;
    protected int maxMeter;
    protected int bandTypes;
    protected int capabilities;
    protected byte maxBands;
    protected byte maxColor;

    public int getMaxMeter() {
        return maxMeter;
    }
    public void setMaxMeter(int maxMeter) {
        this.maxMeter = maxMeter;
    }
    public Set<OFMeterBandType> getBandTypes() {
        Set<OFMeterBandType> bandTypeSet = new HashSet<OFMeterBandType>();
        for (Short i: OFMeterBandType.getValues()) {
            if ((this.bandTypes & (1<<i.intValue())) != 0)
                bandTypeSet.add(OFMeterBandType.valueOf(i));
        }
        return bandTypeSet;
    }
    public void setBandTypes(int bandTypes) {
        this.bandTypes = bandTypes;
    }
    public void setBandTypes(Set<OFMeterBandType> bandTypes) {
        this.bandTypes = 0;
        for (OFMeterBandType bandType: bandTypes)
            this.bandTypes |= (1 << bandType.getTypeValue());
    }
    public int getCapabilities() {
        return capabilities;
    }
    public void setCapabilities(int capabilities) {
        this.capabilities = capabilities;
    }
    public byte getMaxBands() {
        return maxBands;
    }
    public void setMaxBands(byte maxBands) {
        this.maxBands = maxBands;
    }
    public byte getMaxColor() {
        return maxColor;
    }
    public void setMaxColor(byte maxColor) {
        this.maxColor = maxColor;
    }

    @Override
    public int getLength() {
        return U16.f(length);
    }

    @Override
    public void readFrom(ByteBuffer data) {
        this.maxMeter = data.getInt();
        this.bandTypes = data.getInt();
        this.capabilities = data.getInt();
        this.maxBands = data.get();
        this.maxColor = data.get();
        data.getShort(); //pad
    }

    @Override
    public void writeTo(ByteBuffer data) {
        data.putInt(this.maxMeter);
        data.putInt(this.bandTypes);
        data.putInt(this.capabilities);
        data.put(this.maxBands);
        data.put(this.maxColor);
        data.putShort((short)0); //pad
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + bandTypes;
        result = prime * result + capabilities;
        result = prime * result + length;
        result = prime * result + maxBands;
        result = prime * result + maxColor;
        result = prime * result + maxMeter;
        return result;
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        OFMeterFeatures other = (OFMeterFeatures) obj;
        if (bandTypes != other.bandTypes)
            return false;
        if (capabilities != other.capabilities)
            return false;
        if (length != other.length)
            return false;
        if (maxBands != other.maxBands)
            return false;
        if (maxColor != other.maxColor)
            return false;
        if (maxMeter != other.maxMeter)
            return false;
        return true;
    }
    @Override
    public String toString() {
        return "OFMeterFeatures [length=" + length + ", maxMeter=" + maxMeter
                + ", bandTypes=" + bandTypes + ", capabilities=" + capabilities
                + ", maxBands=" + maxBands + ", maxColor=" + maxColor + "]";
    }

    @Override
    public int computeLength() {
        return getLength();
    }
}

