package org.openflow.protocol.statistics;

import java.nio.ByteBuffer;
import org.openflow.util.U16;

/**
 * Represents an ofp_group_stats_request structure
 * @author Srini Seetharaman (srini.seetharaman@gmail.com)
 */
public class OFGroupStatisticsRequest implements OFStatistics {
    public static int MINIMUM_LENGTH = 8;
    protected short length = (short)MINIMUM_LENGTH;
    protected int groupId;

    public int getGroupId() {
        return groupId;
    }
    public void setGroupId(int groupId) {
        this.groupId = groupId;
    }

    @Override
    public int getLength() {
        return U16.f(length);
    }

    @Override
    public void readFrom(ByteBuffer data) {
        this.groupId = data.getInt();
        data.getInt(); //pad
    }

    @Override
    public void writeTo(ByteBuffer data) {
        data.putInt(this.groupId);
        data.putInt(0); //pad
    }

    @Override
    public String toString() {
        return "OFGroupStatisticsRequest [length=" + length + ", groupId="
                + groupId + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + length;
        result = prime * result + groupId;
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
        OFGroupStatisticsRequest other = (OFGroupStatisticsRequest) obj;
        if (length != other.length)
            return false;
        if (groupId != other.groupId)
            return false;
        return true;
    }

    @Override
    public int computeLength() {
        return getLength();
    }
}
