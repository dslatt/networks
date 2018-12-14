package org.openflow.protocol.statistics;

import java.nio.ByteBuffer;
import org.openflow.util.U16;

/**
 * Represents an ofp_meter_stats_request structure
 * @author Srini Seetharaman (srini.seetharaman@gmail.com)
 */
public class OFMeterStatisticsRequest implements OFStatistics {
    public static int MINIMUM_LENGTH = 8;
    protected short length = (short)MINIMUM_LENGTH;
    protected int meterId;

    public int getMeterId() {
        return meterId;
    }
    public void setMeterId(int meterId) {
        this.meterId = meterId;
    }

    @Override
    public int getLength() {
        return U16.f(length);
    }

    @Override
    public void readFrom(ByteBuffer data) {
        this.meterId = data.getInt();
        data.getInt(); //pad
    }

    @Override
    public void writeTo(ByteBuffer data) {
        data.putInt(this.meterId);
        data.putInt(0); //pad
    }

    @Override
    public String toString() {
        return "OFMeterStatisticsRequest [length=" + length + ", meterId="
                + meterId + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
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
        OFMeterStatisticsRequest other = (OFMeterStatisticsRequest) obj;
        if (length != other.length)
            return false;
        if (meterId != other.meterId)
            return false;
        return true;
    }

    @Override
    public int computeLength() {
        return getLength();
    }
}
