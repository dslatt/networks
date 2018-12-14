package org.openflow.protocol.statistics;

import java.nio.ByteBuffer;
import java.util.List;

import org.openflow.protocol.factory.OFMeterBandFactory;
import org.openflow.protocol.factory.OFMeterBandFactoryAware;
import org.openflow.protocol.meter.OFMeterBand;
import org.openflow.util.U16;

/**
 * Represents an ofp_meter_stats structure
 * @author Srini Seetharaman (srini.seetharaman@gmail.com)
 */
public class OFMeterConfigStatisticsReply implements OFStatistics, OFMeterBandFactoryAware {
    public static int MINIMUM_LENGTH = 40;

    protected OFMeterBandFactory meterBandFactory;
    protected short length = (short) MINIMUM_LENGTH;
    protected short flags;
    protected int meterId;
    protected List<OFMeterBand> bands;

    /**
     * @return the meterId
     */
    public int getMeterId() {
        return meterId;
    }

    /**
     * @param meterId the meterId to set
     */
    public OFMeterConfigStatisticsReply setMeterId(int meterId) {
        this.meterId = meterId;
        return this;
    }

    /**
     * @return the flags
     */
    public short getFlags() {
        return flags;
    }

    /**
     * @param flags the flags to set
     */
    public void setFlags(short flags) {
        this.flags = flags;
    }

    /**
     * @return the bands
     */
    public List<OFMeterBand> getBands() {
        return bands;
    }

    /**
     * @param bands the bands to set
     */
    public OFMeterConfigStatisticsReply setBands(List<OFMeterBand> bands) {
        this.bands = bands;
        updateLength();
        return this;
    }

    @Override
    public int getLength() {
        return U16.f(length);
    }

    @Override
    public void setMeterBandFactory(
            OFMeterBandFactory meterBandFactory) {
        this.meterBandFactory = meterBandFactory;
    }

    @Override
    public void readFrom(ByteBuffer data) {
        this.length = data.getShort();
        this.flags = data.getShort();
        this.meterId = data.getInt();
        if (this.meterBandFactory == null)
            throw new RuntimeException("OFMeterBandFactory not set");
        this.bands = meterBandFactory.parseMeterBands(data,
                U16.f(this.length) - MINIMUM_LENGTH);
    }

    @Override
    public void writeTo(ByteBuffer data) {
        data.putShort(this.length);
        data.putShort(this.flags);
        data.putInt(this.meterId);
        if (this.bands != null) {
            for (OFMeterBand meterBand : this.bands) {
                meterBand.writeTo(data);
            }
        }
    }

    @Override
    public String toString() {
        return "OFMeterConfigStatisticsReply [length=" + length + ", flags="
                + flags + ", meterId=" + meterId + ", bands=" + bands + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((bands == null) ? 0 : bands.hashCode());
        result = prime * result + flags;
        result = prime * result + length;
        result = prime * result + meterId;
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
        OFMeterConfigStatisticsReply other = (OFMeterConfigStatisticsReply) obj;
        if (bands == null) {
            if (other.bands != null)
                return false;
        } else if (!bands.equals(other.bands))
            return false;
        if (flags != other.flags)
            return false;
        if (length != other.length)
            return false;
        if (meterId != other.meterId)
            return false;
        return true;
    }
    
    public void updateLength() {
        int l = MINIMUM_LENGTH;
        if (bands != null) {
            for (OFMeterBand meterBand : this.bands) {
                l += meterBand.getLength();
            }
        }
        this.length = U16.t(l);
    }

    @Override
    public int computeLength() {
        return getLength();
    }
}
