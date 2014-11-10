/**
 * This file is part of Waarp Project.
 *
 * Copyright 2009, Frederic Bregier, and individual contributors by the @author
 * tags. See the COPYRIGHT.txt in the distribution for a full listing of
 * individual contributors.
 *
 * All Waarp Project is free software: you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Waarp is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Waarp . If not, see <http://www.gnu.org/licenses/>.
 */
package fr.gouv.vitam.utils;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import net.iharder.Base64;
import fr.gouv.vitam.utils.exception.InvalidUuidOperationException;
import fr.gouv.vitam.utils.logging.VitamLogger;
import fr.gouv.vitam.utils.logging.VitamLoggerFactory;

/**
 * UUID Generator (also Global UUID Generator) <br>
 * <br>
 * Inspired from com.groupon locality-uuid which used combination of internal counter value - process id -
 * fragment of MAC address and Timestamp. see https://github.com/groupon/locality-uuid.java <br>
 * <br>
 * But force sequence and take care of errors and improves some performance issues
 *
 * @author "Frederic Bregier"
 *
 */
public final class UUID {
    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(UUID.class);

    private static final int KEYSIZE = 18;
    private static final int KEYB64SIZE = 24;
    private static final int KEYB16SIZE = KEYSIZE * 2;
    private static final int UTILUUIDKEYSIZE    = 16;
    /**
     * Random Generator
     */
    private static final ThreadLocalRandom RANDOM = ThreadLocalRandom.current();
    /**
     * So MAX value on 2 bytes
     */
    private static final int MAX_PID = 65536;
    /**
     * Version to store (to check correctness if future algorithm)
     */
    private static final char VERSION = 'd';
    /**
     * HEX_CHARS
     */
    private static final char[] HEX_CHARS = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f', };
    /**
     * VERSION_DEC
     */
    private static final int VERSION_DEC = asByte(VERSION, '0');

    private static final Pattern MACHINE_ID_PATTERN = Pattern.compile("^(?:[0-9a-fA-F][:-]?){6,8}$");
    private static final int MACHINE_ID_LEN = 6;

    /**
     * 2 bytes value maximum
     */
    private static final int JVMPID = jvmProcessId();
    /**
     * Try to get Mac Address but could be also changed dynamically
     */
    private static final byte[] MAC = macAddress();
    /**
     * Counter part
     */
    private static final AtomicInteger COUNTER = new AtomicInteger(RANDOM.nextInt());

    /**
     * real UUID
     */
    private final byte[] uuid;

    /**
     * Constructor that generates a new UUID using the current process id, MAC address, and timestamp
     */
    public UUID() {
        final long time = System.currentTimeMillis();
        uuid = new byte[KEYSIZE];

        // atomically
        final int count = COUNTER.incrementAndGet();

        // switch the order of the count in 3 bit segments and place into uuid
        uuid[0] = (byte) (((count & 0x0F) << 4) | ((count & 0xF0) >> 4));
        uuid[1] = (byte) (((count & 0xF00) >> 4) | ((count & 0xF000) >> 12));
        uuid[2] = (byte) (((count & 0xF0000) >> 12) | ((count & 0xF00000) >> 20));

        // copy pid to uuid
        uuid[3] = (byte) (JVMPID >> 8);
        uuid[4] = (byte) (JVMPID);

        // place UUID version (hex 'c') in first four bits and piece of MAC in
        // the second four bits
        uuid[5] = (byte) (VERSION_DEC | (0x0F & MAC[0]));
        // copy rest of mac address into uuid
        uuid[6] = MAC[1];
        uuid[7] = MAC[2];
        uuid[8] = MAC[3];
        uuid[9] = MAC[4];
        uuid[10] = MAC[5];

        // copy timestamp into uuid (up to 48 bits so up to 2 200 000 years after Time 0)
        uuid[11] = (byte) (time >> 48);
        uuid[12] = (byte) (time >> 40);
        uuid[13] = (byte) (time >> 32);
        uuid[14] = (byte) (time >> 24);
        uuid[15] = (byte) (time >> 16);
        uuid[16] = (byte) (time >> 8);
        uuid[17] = (byte) (time);
    }
    /**
     * Create a UUID immediately compatible with a standard UUID implementation
     * @param on128bits
     */
    public UUID(boolean on128bits) {
        this();
        if (on128bits) {
            uuid[5] = (byte) VERSION_DEC;
            uuid[11] = 0;
        }
    }
    /**
     * Create a UUID immediately compatible with a standard UUID implementation
     * @param mostSigBits
     * @param leastSigBits
     */
    public UUID(long mostSigBits, long leastSigBits) {
        uuid = new byte[KEYSIZE];
        uuid[0] = (byte) (mostSigBits >> 56);
        uuid[1] = (byte) (mostSigBits >> 48);
        uuid[2] = (byte) (mostSigBits >> 40);
        uuid[3] = (byte) (mostSigBits >> 32);
        uuid[4] = (byte) (mostSigBits >> 24);
        uuid[5] = (byte) VERSION_DEC;
        uuid[6] = (byte) (mostSigBits >> 16);
        uuid[7] = (byte) (mostSigBits >> 8);
        uuid[8] = (byte) (mostSigBits);

        uuid[9] = (byte) (leastSigBits >> 56);
        uuid[10] = (byte) (leastSigBits >> 48);
        uuid[11] = 0;
        uuid[12] = (byte) (leastSigBits >> 40);
        uuid[13] = (byte) (leastSigBits >> 32);
        uuid[14] = (byte) (leastSigBits >> 24);
        uuid[15] = (byte) (leastSigBits >> 16);
        uuid[16] = (byte) (leastSigBits >> 8);
        uuid[17] = (byte) (leastSigBits);
    }
    /**
     * Create a UUID immediately compatible with a standard UUID implementation
     * @param uuid
     */
    public UUID(java.util.UUID uuid) {
        this(uuid.getMostSignificantBits(), uuid.getLeastSignificantBits());
    }
    /**
     * Constructor that takes a byte array as this UUID's content
     *
     * @param bytes
     *            UUID content
     * @throws InvalidUuidOperationException
     */
    public UUID(final byte[] bytes) throws InvalidUuidOperationException {
        if (bytes.length != KEYSIZE && bytes.length != UTILUUIDKEYSIZE) {
            throw new InvalidUuidOperationException("Attempted to parse malformed UUID: (" + bytes.length + ") " + Arrays.toString(bytes));
        }
        uuid = Arrays.copyOf(bytes, KEYSIZE);
        if (bytes.length == UTILUUIDKEYSIZE) {
            uuid[5] = (byte) VERSION_DEC;
            System.arraycopy(bytes, 5, uuid, 6, 5);
            uuid[11] = 0;
            System.arraycopy(bytes, 10, uuid, 12, 6);
        }
    }

    /**
     * Build from String key
     *
     * @param idsource
     * @throws InvalidUuidOperationException
     */
    public UUID(final String idsource) throws InvalidUuidOperationException {
        final String id = idsource.trim();

        final int len = id.length();
        if (len == KEYB16SIZE) {
            // HEXA
            uuid = new byte[KEYSIZE];
            final char[] chars = id.toCharArray();
            for (int i = 0, j = 0; i < KEYSIZE;) {
                uuid[i++] = asByte(chars[j++], chars[j++]);
            }
        } else if (len == KEYB64SIZE || len == KEYB64SIZE + 1) {
            // BASE64
            try {
                uuid = Base64.decode(id, Base64.URL_SAFE | Base64.DONT_GUNZIP);
            } catch (final IOException e) {
                throw new InvalidUuidOperationException("Attempted to parse malformed UUID: " + id, e);
            }
        } else {
            throw new InvalidUuidOperationException("Attempted to parse malformed UUID: (" + len + ") " + id);
        }
    }

    /**
     *
     * @param uuids
     * @return the assembly UUID of all given UUIDs
     */
    public static String assembleUuids(final UUID... uuids) {
        final StringBuilder builder = new StringBuilder();
        for (final UUID uuid : uuids) {
            builder.append(uuid.toString());
        }
        return builder.toString();
    }

    /**
     *
     * @param idsource
     * @return the array of UUID according to the source (concatenation of UUIDs)
     * @throws InvalidUuidOperationException
     */
    public static UUID[] getUuids(final String idsource) throws InvalidUuidOperationException {
        final String id = idsource.trim();
        final int nb = id.length() / KEYB64SIZE;
        final UUID[] uuids = new UUID[nb];
        int beginIndex = 0;
        int endIndex = KEYB64SIZE;
        for (int i = 0; i < nb; i++) {
            uuids[i] = new UUID(id.substring(beginIndex, endIndex));
            beginIndex = endIndex;
            endIndex += KEYB64SIZE;
        }
        return uuids;
    }

    /**
     *
     * @param idsource
     * @return the number of UUID in this idsource
     */
    public static int getUuidNb(final String idsource) {
        return idsource.trim().length() / KEYB64SIZE;
    }

    /**
     *
     * @param idsource
     * @return true if this idsource represents more than one UUID (path of UUIDs)
     */
    public static boolean isMultipleUUID(final String idsource) {
        return idsource.trim().length() > KEYB64SIZE;
    }

    /**
     *
     * @param idsource
     * @return the last UUID from this idsource
     * @throws InvalidUuidOperationException
     */
    public static UUID getLast(final String idsource) throws InvalidUuidOperationException {
        final String id = idsource.trim();
        final int nb = id.length() / KEYB64SIZE - 1;
        final int pos = KEYB64SIZE * nb;
        return new UUID(id.substring(pos, pos + KEYB64SIZE));
    }

    /**
     *
     * @param idsource
     * @return the first UUID from this idsource
     * @throws InvalidUuidOperationException
     */
    public static UUID getFirst(final String idsource) throws InvalidUuidOperationException {
        final String id = idsource.trim().substring(0, KEYB64SIZE);
        return new UUID(id);
    }

    /**
     *
     * @param idsource
     * @return the last UUID from this idsource
     */
    public static String getLastAsString(final String idsource) {
        final String id = idsource.trim();
        final int nb = id.length() / KEYB64SIZE - 1;
        final int pos = KEYB64SIZE * nb;
        return id.substring(pos, pos + KEYB64SIZE);
    }

    /**
     *
     * @param idsource
     * @return the first UUID from this idsource
     */
    public static String getFirstAsString(final String idsource) {
        return idsource.trim().substring(0, KEYB64SIZE);
    }

    /**
     * 
     * @param idsource
     * @param idIn
     * @return True if idIn is in idsource
     */
    public static boolean isInPath(final String idsource, String idIn) {
        final String id = idsource.trim();
        final int nb = id.length() / KEYB64SIZE;
        int beginIndex = 0;
        int endIndex = KEYB64SIZE;
        for (int i = 0; i < nb; i++) {
            if (idIn.equals(id.substring(beginIndex, endIndex))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 
     * @param idsource
     * @param idsIn
     * @return True if any of id in idsIn is in idsource
     */
    public static boolean isInPath(final String idsource, Set<String> idsIn) {
        final String id = idsource.trim();
        final int nb = id.length() / KEYB64SIZE;
        final int beginIndex = 0;
        final int endIndex = KEYB64SIZE;
        final String searched = id.substring(beginIndex, endIndex);
        for (int i = 0; i < nb; i++) {
            if (idsIn.contains(searched)) {
                return true;
            }
        }
        return false;
    }

    /**
     *
     * @param idsource
     * @return the array of UUID according to the source (concatenation of UUIDs separated by '#')
     * @throws InvalidUuidOperationException
     */
    public static UUID[] getUuidsSharp(final String idsource) throws InvalidUuidOperationException {
        final String id = idsource.trim();
        final int nb = id.length() / (KEYB64SIZE + 1) + 1;
        final UUID[] uuids = new UUID[nb];
        int beginIndex = 0;
        int endIndex = KEYB64SIZE;
        for (int i = 0; i < nb; i++) {
            uuids[i] = new UUID(id.substring(beginIndex, endIndex));
            beginIndex = endIndex + 1;
            endIndex += KEYB64SIZE + 1;
        }
        return uuids;
    }

    private static final byte asByte(final char a, final char b) {
        char a2 = a;
        if (a >= HEX_CHARS[10]) {
            a2 -= HEX_CHARS[10] - 10;
        } else {
            a2 -= HEX_CHARS[0];
        }
        char b2 = b;
        if (b >= HEX_CHARS[10]) {
            b2 -= HEX_CHARS[10] - 10;
        } else {
            b2 -= HEX_CHARS[0];
        }
        return (byte) ((a2 << 4) + b2);
    }

    /**
     * @return the Base64 representation (default of toString)
     */
    public final String toBase64() {
        try {
            return Base64.encodeBytes(uuid, Base64.URL_SAFE);
        } catch (final IOException e) {
            return Base64.encodeBytes(uuid);
        }
    }
    /**
     * 
     * @param bytes
     * @return the hex string
     */
    public static final String toHex(byte []bytes) {
        final int keysize = bytes.length;
        final int keyb16 = keysize*2;
        final char[] id = new char[keyb16];

        // split each byte into 4 bit numbers and map to hex characters
        for (int i = 0, j = 0; i < keysize; i++) {
            id[j++] = HEX_CHARS[(bytes[i] & 0xF0) >> 4];
            id[j++] = HEX_CHARS[(bytes[i] & 0x0F)];
        }
        return new String(id);
    }
    /**
     * 
     * @param hex
     * @return the bytes from hex
     */
    public static byte[] fromHex(String hex) {
        final int keysize = hex.length()/2;
        final byte [] bytes = new byte[keysize];
        final char[] chars = hex.toCharArray();
        for (int i = 0, j = 0; i < KEYSIZE;) {
            bytes[i++] = asByte(chars[j++], chars[j++]);
        }
        return bytes;
    }
    /**
     * @return the Hexadecimal representation
     */
    public final String toHex() {
        final char[] id = new char[KEYB16SIZE];

        // split each byte into 4 bit numbers and map to hex characters
        for (int i = 0, j = 0; i < KEYSIZE; i++) {
            id[j++] = HEX_CHARS[(uuid[i] & 0xF0) >> 4];
            id[j++] = HEX_CHARS[(uuid[i] & 0x0F)];
        }
        return new String(id);
    }

    @Override
    public String toString() {
        return toBase64();
    }

    /**
     * copy the uuid of this UUID, so that it can't be changed, and return it
     *
     * @return raw byte array of UUID
     */
    public byte[] getBytes() {
        return Arrays.copyOf(uuid, KEYSIZE);
    }

    /**
     * extract version field as a hex char from raw UUID bytes
     *
     * @return version char
     */
    public char getVersion() {
        return HEX_CHARS[(uuid[5] & 0xF0) >> 4];
    }

    /**
     * extract process id from raw UUID bytes and return as int
     *
     * @return id of process that generated the UUID, or -1 for unrecognized format
     */
    public int getProcessId() {
        if (getVersion() != VERSION) {
            return -1;
        }

        return ((uuid[3] & 0xFF) << 8) | (uuid[4] & 0xFF);
    }

    /**
     * @return the associated counter value
     */
    public int getCounter() {
        int count = uuid[2] & 0xF0 >> 4 << 16;
        count |= uuid[2] & 0x0F << 4 << 16;
        count |= uuid[1] & 0xF0 >> 4 << 8;
        count |= uuid[1] & 0x0F << 4 << 8;
        count |= uuid[0] & 0xF0 >> 4;
        count |= uuid[0] & 0x0F << 4;
        return count;
    }

    /**
     * extract timestamp from raw UUID bytes and return as int
     *
     * @return millisecond UTC timestamp from generation of the UUID, or -1 for unrecognized format
     */
    public long getTimestamp() {
        if (getVersion() != VERSION) {
            return -1;
        }

        long time;
        time = ((long) uuid[11] & 0xFF) << 48;
        time |= ((long) uuid[12] & 0xFF) << 40;
        time |= ((long) uuid[13] & 0xFF) << 32;
        time |= ((long) uuid[14] & 0xFF) << 24;
        time |= ((long) uuid[15] & 0xFF) << 16;
        time |= ((long) uuid[16] & 0xFF) << 8;
        time |= ((long) uuid[17] & 0xFF);
        return time;
    }

    /**
     * extract MAC address fragment from raw UUID bytes, setting missing values to 0,
     * thus the first half byte will be 0, followed by 7 and half bytes
     * of the active MAC address when the UUID was generated
     *
     * @return byte array of UUID fragment, or null for unrecognized format
     */
    public byte[] getMacFragment() {
        if (getVersion() != VERSION) {
            return null;
        }

        final byte[] x = new byte[6];

        x[0] = (byte) (uuid[5] & 0x0F);
        x[1] = uuid[6];
        x[2] = uuid[7];
        x[3] = uuid[8];
        x[4] = uuid[9];
        x[5] = uuid[10];

        return x;
    }
    /**
     * 
     * @return the least significant bits (as in standard UUID implementation)
     */
    public long getLeastSignificantBits() {
        long least;
        least = ((long) uuid[9] & 0xFF) << 56;
        least |= ((long) uuid[10] & 0xFF) << 48;
        least |= ((long) uuid[12] & 0xFF) << 40;
        least |= ((long) uuid[13] & 0xFF) << 32;
        least |= ((long) uuid[14] & 0xFF) << 24;
        least |= ((long) uuid[15] & 0xFF) << 16;
        least |= ((long) uuid[16] & 0xFF) << 8;
        least |= ((long) uuid[17] & 0xFF);
        return least;
    }
    /**
     * 
     * @return the most significant bits (as in standard UUID implementation)
     */
    public long getMostSignificantBits() {
        long most;
        most = ((long) uuid[0] & 0xFF) << 56;
        most |= ((long) uuid[1] & 0xFF) << 48;
        most |= ((long) uuid[2] & 0xFF) << 40;
        most |= ((long) uuid[3] & 0xFF) << 32;
        most |= ((long) uuid[4] & 0xFF) << 24;
        most |= ((long) uuid[6] & 0xFF) << 16;
        most |= ((long) uuid[7] & 0xFF) << 8;
        most |= ((long) uuid[8] & 0xFF);
        return most;
    }
    /**
     * 
     * @return a UUID compatible with Java.Util package implementation
     */
    public java.util.UUID getJavaUuid() {
        return new java.util.UUID(getMostSignificantBits(), getLeastSignificantBits());
    }
    @Override
    public boolean equals(final Object o) {
        if (o == null || !(o instanceof UUID)) {
            return false;
        }
        return (this == o) || Arrays.equals(uuid, ((UUID) o).uuid);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(uuid);
    }

    /**
     *
     * @param length
     * @return a byte array with random values
     */
    public static final byte[] getRandom(final int length) {
        final byte[] result = new byte[length];
        RANDOM.nextBytes(result);
        return result;
    }

    /**
     *
     * @return the mac address if possible, else random values
     */
    public static final byte[] macAddress() {
        try {
            byte[] machineId = null;
            final String customMachineId = SystemPropertyUtil.get("fr.gouv.vitam.machineId");
            if (customMachineId != null) {
                if (MACHINE_ID_PATTERN.matcher(customMachineId).matches()) {
                    machineId = parseMachineId(customMachineId);
                }
            }

            if (machineId == null) {
                machineId = defaultMachineId();
            }
            return machineId;
        } catch (final Exception e) {
            LOGGER.error("Could not get MAC address", e);
            return getRandom(MACHINE_ID_LEN);
        }
    }

    private static final byte[] parseMachineId(final String valueSource) {
        // Strip separators.
        final String value = valueSource.replaceAll("[:-]", "");

        final byte[] machineId = new byte[MACHINE_ID_LEN];
        for (int i = 0; i < value.length() && i < MACHINE_ID_LEN; i += 2) {
            machineId[i] = (byte) Integer.parseInt(value.substring(i, i + 2), 16);
        }

        return machineId;
    }

    private static final byte[] NOT_FOUND = { -1 };
    private static final byte[] defaultMachineId() {
        // Find the best MAC address available.
        byte[] bestMacAddr = NOT_FOUND;
        InetAddress bestInetAddr = null;
        try {
            bestInetAddr = InetAddress.getByAddress(new byte[] { 127, 0, 0, 1 });
        } catch (final UnknownHostException e) {
            // Never happens.
            throw new IllegalArgumentException(e);
        }

        // Retrieve the list of available network interfaces.
        final Map<NetworkInterface, InetAddress> ifaces = new LinkedHashMap<NetworkInterface, InetAddress>();
        try {
            for (final Enumeration<NetworkInterface> i = NetworkInterface.getNetworkInterfaces(); i.hasMoreElements();) {
                final NetworkInterface iface = i.nextElement();
                // Use the interface with proper INET addresses only.
                final Enumeration<InetAddress> addrs = iface.getInetAddresses();
                if (addrs.hasMoreElements()) {
                    final InetAddress a = addrs.nextElement();
                    if (!a.isLoopbackAddress()) {
                        ifaces.put(iface, a);
                    }
                }
            }
        } catch (final SocketException e) {
        }

        for (final Entry<NetworkInterface, InetAddress> entry : ifaces.entrySet()) {
            final NetworkInterface iface = entry.getKey();
            final InetAddress inetAddr = entry.getValue();
            if (iface.isVirtual()) {
                continue;
            }

            byte[] macAddr;
            try {
                macAddr = iface.getHardwareAddress();
            } catch (final SocketException e) {
                continue;
            }

            boolean replace = false;
            int res = compareAddresses(bestMacAddr, macAddr);
            if (res < 0) {
                // Found a better MAC address.
                replace = true;
            } else if (res == 0) {
                // Two MAC addresses are of pretty much same quality.
                res = compareAddresses(bestInetAddr, inetAddr);
                if (res < 0) {
                    // Found a MAC address with better INET address.
                    replace = true;
                } else if (res == 0) {
                    // Cannot tell the difference. Choose the longer one.
                    if (bestMacAddr.length < macAddr.length) {
                        replace = true;
                    }
                }
            }

            if (replace) {
                bestMacAddr = macAddr;
                bestInetAddr = inetAddr;
            }
        }

        if (bestMacAddr == NOT_FOUND) {
            bestMacAddr = getRandom(MACHINE_ID_LEN);
        }
        return bestMacAddr;
    }

    /**
     * @return positive - current is better, 0 - cannot tell from MAC addr, negative - candidate is better.
     */
    private static final int compareAddresses(final byte[] current, final byte[] candidate) {
        if (candidate == null) {
            return 1;
        }
        // Must be EUI-48 or longer.
        if (candidate.length < 6) {
            return 1;
        }
        // Must not be filled with only 0 and 1.
        boolean onlyZeroAndOne = true;
        for (final byte b : candidate) {
            if (b != 0 && b != 1) {
                onlyZeroAndOne = false;
                break;
            }
        }
        if (onlyZeroAndOne) {
            return 1;
        }
        // Must not be a multicast address
        if ((candidate[0] & 1) != 0) {
            return 1;
        }
        // Prefer globally unique address.
        if ((current[0] & 2) == 0) {
            if ((candidate[0] & 2) == 0) {
                // Both current and candidate are globally unique addresses.
                return 0;
            } else {
                // Only current is globally unique.
                return 1;
            }
        } else {
            if ((candidate[0] & 2) == 0) {
                // Only candidate is globally unique.
                return -1;
            } else {
                // Both current and candidate are non-unique.
                return 0;
            }
        }
    }

    /**
     * @return positive - current is better, 0 - cannot tell, negative - candidate is better
     */
    private static final int compareAddresses(final InetAddress current, final InetAddress candidate) {
        return scoreAddress(current) - scoreAddress(candidate);
    }

    private static final int scoreAddress(final InetAddress addr) {
        if (addr.isAnyLocalAddress()) {
            return 0;
        }
        if (addr.isMulticastAddress()) {
            return 1;
        }
        if (addr.isLinkLocalAddress()) {
            return 2;
        }
        if (addr.isSiteLocalAddress()) {
            return 3;
        }

        return 4;
    }

    // pulled from http://stackoverflow.com/questions/35842/how-can-a-java-program-get-its-own-process-id
    /**
     * @return the JVM Process ID
     */
    public static final int jvmProcessId() {
        // Note: may fail in some JVM implementations
        // something like '<pid>@<hostname>', at least in SUN / Oracle JVMs
        try {
            final String jvmName = ManagementFactory.getRuntimeMXBean().getName();
            final int index = jvmName.indexOf('@');

            if (index < 1) {
                LOGGER.error("Could not get JVMPID");
                return RANDOM.nextInt(MAX_PID);
            }
            try {
                return Integer.parseInt(jvmName.substring(0, index)) % MAX_PID;
            } catch (final NumberFormatException e) {
                LOGGER.error("Could not get JVMPID", e);
                return RANDOM.nextInt(MAX_PID);
            }
        } catch (final Exception e) {
            LOGGER.error("Error while getting JVMPID", e);
            return RANDOM.nextInt(MAX_PID);
        }
    }

}
