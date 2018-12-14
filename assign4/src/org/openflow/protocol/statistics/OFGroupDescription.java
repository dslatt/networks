package org.openflow.protocol.statistics;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.openflow.protocol.OFBucket;
import org.openflow.protocol.factory.OFActionFactory;
import org.openflow.protocol.factory.OFActionFactoryAware;
import org.openflow.util.U16;

/**
 * Represents an ofp_meter_stats structure
 * @author Srini Seetharaman (srini.seetharaman@gmail.com)
 */
public class OFGroupDescription implements OFStatistics, OFActionFactoryAware {
    public static int MINIMUM_LENGTH = 8;

    protected OFActionFactory actionFactory;
    protected short length = (short) MINIMUM_LENGTH;
    protected byte type;
    protected int groupId;
    protected List<OFBucket> buckets;

    /**
     * @return the groupId
     */
    public int getGroupId() {
        return groupId;
    }

    /**
     * @param groupId the groupId to set
     */
    public OFGroupDescription setGroupId(int groupId) {
        this.groupId = groupId;
        return this;
    }

    /**
     * @return the type
     */
    public byte getType() {
        return type;
    }

    /**
     * @param type the type to set
     */
    public void setType(byte type) {
        this.type = type;
    }

    /**
     * @return the buckets
     */
    public List<OFBucket> getBuckets() {
        return buckets;
    }

    /**
     * @param buckets the buckets to set
     */
    public OFGroupDescription setBuckets(List<OFBucket> buckets) {
        this.buckets = buckets;
        updateLength();
        return this;
    }

    @Override
    public int getLength() {
        return U16.f(length);
    }

    @Override
    public void setActionFactory(OFActionFactory actionFactory) {
        this.actionFactory = actionFactory;
    }

    @Override
    public void readFrom(ByteBuffer data) {
        this.length = data.getShort();
        this.type = data.get();
        data.get(); //pad
        this.groupId = data.getInt();
        
        int remaining = this.getLength() - MINIMUM_LENGTH;
        if (data.remaining() < remaining)
            remaining = data.remaining();
        this.buckets = new ArrayList<OFBucket>();
        while (remaining >= OFBucket.MINIMUM_LENGTH) {
            OFBucket bucket = new OFBucket();
            bucket.setActionFactory(actionFactory);
            bucket.readFrom(data);
            this.buckets.add(bucket);
            remaining -= U16.f(bucket.getLength());
        }
    }

    @Override
    public void writeTo(ByteBuffer data) {
        data.putShort(this.length);
        data.put(this.type);
        data.put((byte)0); //pad
        data.putInt(this.groupId);
        if (this.buckets != null) {
            for (OFBucket bucket : this.buckets) {
                bucket.writeTo(data);
            }
        }
    }

    @Override
    public String toString() {
        return "OFMeterConfigStatisticsReply [length=" + length + ", type="
                + type + ", groupId=" + groupId + ", buckets=" + buckets + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((buckets == null) ? 0 : buckets.hashCode());
        result = prime * result + type;
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
        OFGroupDescription other = (OFGroupDescription) obj;
        if (buckets == null) {
            if (other.buckets != null)
                return false;
        } else if (!buckets.equals(other.buckets))
            return false;
        if (type != other.type)
            return false;
        if (length != other.length)
            return false;
        if (groupId != other.groupId)
            return false;
        return true;
    }
    
    public void updateLength() {
        int l = MINIMUM_LENGTH;
        if (buckets != null) {
            for (OFBucket bucket : this.buckets) {
                l += bucket.getLength();
            }
        }
        this.length = U16.t(l);
    }

    @Override
    public int computeLength() {
        return getLength();
    }
}
