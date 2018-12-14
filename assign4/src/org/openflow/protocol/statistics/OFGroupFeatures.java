package org.openflow.protocol.statistics;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import org.openflow.protocol.action.OFActionType;
import org.openflow.util.U16;

/**
 * Represents an ofp_meter_stats structure
 * @author Srini Seetharaman (srini.seetharaman@gmail.com)
 */
public class OFGroupFeatures implements OFStatistics {
    public static int MINIMUM_LENGTH = 40;

    protected short length = (short)MINIMUM_LENGTH;
    protected int types;
    private int capabilities;
    protected int[] maxGroups = new int[4];
    protected int[] actions = new int[4];

    public enum OFGroupCapabilities {
        OFPGFC_SELECT_WEIGHT    (1 << 0),
        OFPGFC_SELECT_LIVENESS  (1 << 1),
        OFPGFC_CHAINING         (1 << 2),
        OFPGFC_CHAINING_CHECKS  (1 << 3);

        protected int value;

        private OFGroupCapabilities(int value) {
            this.value = value;
        }

        /**
         * Given a group capabilities short value, return the set of OFGroupCapabilities enums
         * associated with it
         *
         * @param i capabilities bitmap
         * @return EnumSet<OFGroupCapabilities>
         */
        public static EnumSet<OFGroupCapabilities> valueOf(int i) {
            EnumSet<OFGroupCapabilities> capabilities = EnumSet.noneOf(OFGroupCapabilities.class);
            for (OFGroupCapabilities value: OFGroupCapabilities.values()) {
                if ((i & value.getValue()) != 0)
                    capabilities.add(value);
            }
            return capabilities;
        }

        /**
         * Given a set of OFGroupCapabilities enums, convert to bitmap value
         *
         * @param capabilities Set<OFGroupCapabilities>
         * @return bitmap value
         */
        public static int toBitmap(Set<OFGroupCapabilities> capabilities) {
            short bitmap = 0;
            for (OFGroupCapabilities flag: capabilities)
                bitmap |= flag.getValue();
            return bitmap;
        }

        /**
         * @return the value
         */
        public int getValue() {
            return value;
        }
    }

    /**
     * @return the types
     */
    public int getTypes() {
        return types;
    }
    /**
     * @param types the types to set
     */
    public void setTypes(int types) {
        this.types = types;
    }
    /**
     * @return the capabilities
     */
    public int getCapabilities() {
        return capabilities;
    }
    /**
     * @param capabilities the capabilities to set
     */
    public void setCapabilities(int capabilities) {
        this.capabilities = capabilities;
    }
    /**
     * @param type the type to retrieve the supported actions
     * @return actions the set of action types supported
     */
    public Set<OFActionType> getActions(int type) {
        Set<OFActionType> actionTypeSet = new HashSet<OFActionType>();
        for (Short i: OFActionType.getValues()) {
            if ((actions[type] & (1<<i.intValue())) != 0)
                actionTypeSet.add(OFActionType.valueOf(i));
        }
        return actionTypeSet;
    }

    /**
     * @param type the type to retrieve the supported actions
     * @param actions the set of action types supported
     */
    public void setActions(int type, Set<OFActionType> actions) {
        this.actions[type] = 0;
        for (OFActionType actionType: actions)
            this.actions[type] |= (1 << actionType.getTypeValue());
    }

    /**
     * @return the four maxGroup values
     */
    public int[] getMaxGroups() {
        return maxGroups;
    }
    /**
     * @param type the type corresponding to this maxGroup
     * @return the maxGroup value for this type
     */
    public int getMaxGroups(int type) {
        return maxGroups[type];
    }
    /**
     * @param maxGroups the four maxGroups values to set
     */
    public void setMaxGroups(int[] maxGroups) {
        this.maxGroups = maxGroups;
    }
    /**
     * @param type the type corresponding to this maxGroup
     * @param maxGroup the maxGroup values to set for this type
     */
    public void setMaxGroups(int type, int maxGroup) {
        this.maxGroups[type] = maxGroup;
    }
    /**
     * @return the four action bitmaps
     */
    public int[] getActions() {
        return actions;
    }
    /**
     * @param actions the four action bitmaps to set
     */
    public void setActions(int[] actions) {
        this.actions = actions;
    }

    @Override
    public int getLength() {
        return U16.f(length);
    }

    @Override
    public void readFrom(ByteBuffer data) {
        this.setTypes(data.getInt());
        this.setCapabilities(data.getInt());
        for (int i=0;i<4;i++)
            this.maxGroups[i] = data.getInt();
        for (int i=0;i<4;i++)
            this.actions[i] = data.getInt();
    }

    @Override
    public void writeTo(ByteBuffer data) {
        data.putInt(this.getTypes());
        data.putInt(this.getCapabilities());
        for (int i=0;i<4;i++)
            data.putInt(this.maxGroups[i]);
        for (int i=0;i<4;i++)
            data.putInt(this.actions[i]);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode(actions);
        result = prime * result + capabilities;
        result = prime * result + length;
        result = prime * result + Arrays.hashCode(maxGroups);
        result = prime * result + types;
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
        OFGroupFeatures other = (OFGroupFeatures) obj;
        if (!Arrays.equals(actions, other.actions))
            return false;
        if (capabilities != other.capabilities)
            return false;
        if (length != other.length)
            return false;
        if (!Arrays.equals(maxGroups, other.maxGroups))
            return false;
        if (types != other.types)
            return false;
        return true;
    }
    @Override
    public String toString() {
        return "OFGroupFeatures [length=" + length + ", types=" + types
                + ", capabilities=" + capabilities + ", maxGroups="
                + Arrays.toString(maxGroups) + ", actions="
                + Arrays.toString(actions) + "]";
    }

    @Override
    public int computeLength() {
        return getLength();
    }
}
