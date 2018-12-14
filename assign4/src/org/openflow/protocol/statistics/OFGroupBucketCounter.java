package org.openflow.protocol.statistics;

import java.nio.ByteBuffer;

/**
 * Represents an ofp_bucket_counter structure
 * @author Srini Seetharaman (srini.seetharaman@gmail.com)
 */
public class OFGroupBucketCounter {
    private long packetCount;
    private long byteCount;
    public final static int MINIMUM_LENGTH = 16;

    public OFGroupBucketCounter() {
        //Noop
    }

    public OFGroupBucketCounter(long packetCount, long byteCount) {
        this.packetCount = packetCount;
        this.byteCount = byteCount;
    }

    public long getPacketCount() {
        return packetCount;
    }
    public void setPacketCount(long packetCount) {
        this.packetCount = packetCount;
    }

    public long getByteCount() {
        return byteCount;
    }

    public void setByteCount(long byteCount) {
        this.byteCount = byteCount;
    }

    public int getLength() {
        return MINIMUM_LENGTH;
    }

    @Override
    public String toString() {
        return "OFGroupBucketCounter [packetCount=" + packetCount
                + ", byteCount=" + byteCount + "]";
    }

    public void readFrom(ByteBuffer data) {
        this.packetCount = data.getLong();
        this.byteCount = data.getLong();
    }

    public void writeTo(ByteBuffer data) {
        data.putLong(this.packetCount);
        data.putLong(this.byteCount);
    }
}
