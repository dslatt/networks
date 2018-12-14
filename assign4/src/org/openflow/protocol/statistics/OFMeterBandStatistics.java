package org.openflow.protocol.statistics;

import java.nio.ByteBuffer;

/**
 * Represents an ofp_meter_band_stats structure
 * @author Srini Seetharaman (srini.seetharaman@gmail.com)
 */
public class OFMeterBandStatistics {
    protected long packetBandCount;
    protected long byteBandCount;
    public final static int MINIMUM_LENGTH = 16;

    public OFMeterBandStatistics() {
        //Noop
    }

    public OFMeterBandStatistics(long packetBandCount, long byteBandCount) {
        this.packetBandCount = packetBandCount;
        this.byteBandCount = byteBandCount;
    }

    public long getPacketBandCount() {
        return packetBandCount;
    }
    public void setPacketBandCount(long packetBandCount) {
        this.packetBandCount = packetBandCount;
    }

    public long getByteBandCount() {
        return byteBandCount;
    }

    public void setByteBandCount(long byteBandCount) {
        this.byteBandCount = byteBandCount;
    }

    public int getLength() {
        return MINIMUM_LENGTH;
    }

    @Override
    public String toString() {
        return "OFMeterBandStatistics [packetBandCount=" + packetBandCount
                + ", byteBandCount=" + byteBandCount + "]";
    }

    public void readFrom(ByteBuffer data) {
        this.packetBandCount = data.getLong();
        this.byteBandCount = data.getLong();
    }

    public void writeTo(ByteBuffer data) {
        data.putLong(this.packetBandCount);
        data.putLong(this.byteBandCount);
    }
}
