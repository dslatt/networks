package org.openflow.protocol.statistics;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.openflow.util.U16;

/**
 * Represents an ofp_meter_stats structure
 * @author Srini Seetharaman (srini.seetharaman@gmail.com)
 */
public class OFMeterStatisticsReply implements OFStatistics {
    public static int MINIMUM_LENGTH = 40;

    protected short length;
    protected int meterId;
    protected int flowCount;
    protected long packetInCount;
    protected long byteInCount;
    protected List<OFMeterBandStatistics> bandStatistics;

    public OFMeterStatisticsReply() {
        super();
        this.length = (short) MINIMUM_LENGTH;
        bandStatistics = new ArrayList<OFMeterBandStatistics>();
    }

    /**
     * @return the meterId
     */
    public int getMeterId() {
        return meterId;
    }

    /**
     * @param meterId the meterId to set
     */
    public OFMeterStatisticsReply setMeterId(int meterId) {
        this.meterId = meterId;
        return this;
    }

    /**
     * @return the packetInCount
     */
    public long getPacketInCount() {
        return packetInCount;
    }

    /**
     * @param packetInCount the packetInCount to set
     */
    public OFMeterStatisticsReply setPacketInCount(long packetInCount) {
        this.packetInCount = packetInCount;
        return this;
    }

    /**
     * @return the flowCount
     */
    public int getFlowCount() {
        return flowCount;
    }

    /**
     * @param flowCount the flowCount to set
     */
    public void setFlowCount(int flowCount) {
        this.flowCount = flowCount;
    }

    /**
     * @return the byteInCount
     */
    public long getByteInCount() {
        return byteInCount;
    }

    /**
     * @param byteInCount the byteInCount to set
     */
    public OFMeterStatisticsReply setByteInCount(long byteInCount) {
        this.byteInCount = byteInCount;
        return this;
    }

    /**
     * @param length the length to set
     */
    public void setLength(short length) {
        this.length = length;
    }

    @Override
    public int getLength() {
        return U16.f(length);
    }

    /**
     * @return the bandStatistics
     */
    public List<OFMeterBandStatistics> getBandStatistics() {
        return bandStatistics;
    }

    /**
     * @param bandStatistics the bandStatistics to set
     */
    public OFMeterStatisticsReply setBandStatistics(List<OFMeterBandStatistics> bandStatistics) {
        this.bandStatistics = bandStatistics;
        updateLength();
        return this;
    }

    @Override
    public void readFrom(ByteBuffer data) {
        this.meterId = data.getInt();
        this.length = data.getShort();
        for (int i=0;i<6;i++)
            data.get(); // pad
        this.setFlowCount(data.getInt());
        this.packetInCount = data.getLong();
        this.byteInCount = data.getLong();
        for (int i=0;i<this.length-MINIMUM_LENGTH;i+=OFMeterBandStatistics.MINIMUM_LENGTH) {
            OFMeterBandStatistics bandStat = new OFMeterBandStatistics();
            bandStat.readFrom(data);
            this.bandStatistics.add(bandStat);
        }
    }

    @Override
    public void writeTo(ByteBuffer data) {
        data.putInt(this.meterId);
        data.putShort(this.length);
        for (int i=0;i<6;i++)
            data.put((byte) 0); //pad
        data.putInt(this.getFlowCount());
        data.putLong(this.packetInCount);
        data.putLong(this.byteInCount);
        if (bandStatistics != null) {
            for (OFMeterBandStatistics bandStat : bandStatistics) {
                bandStat.writeTo(data);
            }
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                + ((bandStatistics == null) ? 0 : bandStatistics.hashCode());
        result = prime * result + (int) (byteInCount ^ (byteInCount >>> 32));
        result = prime * result + getFlowCount();
        result = prime * result + length;
        result = prime * result + meterId;
        result = prime * result
                + (int) (packetInCount ^ (packetInCount >>> 32));
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
        OFMeterStatisticsReply other = (OFMeterStatisticsReply) obj;
        if (bandStatistics == null) {
            if (other.bandStatistics != null)
                return false;
        } else if (!bandStatistics.equals(other.bandStatistics))
            return false;
        if (byteInCount != other.byteInCount)
            return false;
        if (getFlowCount() != other.getFlowCount())
            return false;
        if (length != other.length)
            return false;
        if (meterId != other.meterId)
            return false;
        if (packetInCount != other.packetInCount)
            return false;
        return true;
    }

    public void updateLength() {
        int l = MINIMUM_LENGTH;
        if (bandStatistics != null) {
            for (OFMeterBandStatistics bandStat : bandStatistics) {
                l += bandStat.getLength();
            }
        }
        this.length = U16.t(l);
    }

    @Override
    public String toString() {
        return "OFMeterStatisticsReply [length=" + length + ", meterId="
                + meterId + ", flowCount=" + getFlowCount() + ", packetInCount="
                + packetInCount + ", byteInCount=" + byteInCount
                + ", bandStatistics=" + bandStatistics + "]";
    }

    @Override
    public int computeLength() {
        return getLength();
    }
}
