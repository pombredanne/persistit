/**
 * Copyright (C) 2011 Akiban Technologies Inc.
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses.
 */

package com.persistit;

import static com.persistit.TransactionIndex.vh2ts;
import static com.persistit.TransactionStatus.ABORTED;
import static com.persistit.TransactionStatus.UNCOMMITTED;

import com.persistit.exception.CorruptValueException;
import com.persistit.exception.PersistitException;
import com.persistit.exception.PersistitInterruptedException;
import com.persistit.exception.TimeoutException;
import com.persistit.util.Util;

public class MVV {

    final static int TYPE_MVV = 0xFE;
    final static int TYPE_ANTIVALUE = Value.CLASS_ANTIVALUE;

    final static int VERSION_NOT_FOUND = -1;

    final static int STORE_EXISTED_MASK = 0x80000000;
    final static int STORE_LENGTH_MASK = 0x7FFFFFFF;

    static final byte TYPE_MVV_BYTE = (byte) TYPE_MVV;
    final static int PRIMORDIAL_VALUE_VERSION = 0;
    final static int UNDEFINED_VALUE_LENGTH = 0;

    private final static int LENGTH_TYPE_MVV = 1; // byte
    private final static int LENGTH_VERSION = 8; // long
    private final static int LENGTH_VALUE_LENGTH = 2; // short

    final static int LENGTH_PER_VERSION = LENGTH_VERSION + LENGTH_VALUE_LENGTH;

    static long getVersion(final byte[] bytes, final int offset) {
        return Util.getLong(bytes, offset);
    }

    static void putVersion(final byte[] bytes, final int offset, final long version) {
        Util.putLong(bytes, offset, version);
    }

    static int getLength(final byte[] bytes, final int offset) {
        return Util.getChar(bytes, offset + LENGTH_VERSION) & 0x7FFF;
    }

    static void putLength(final byte[] bytes, final int offset, final int length) {
        Util.putChar(bytes, offset + LENGTH_VERSION, length);
    }

    static boolean isMarked(final byte[] bytes, final int offset) {
        return (Util.getChar(bytes, offset + LENGTH_VERSION) & 0x8000) != 0;
    }

    static void mark(final byte[] bytes, final int offset) {
        Util.putChar(bytes, offset + LENGTH_VERSION, Util.getChar(bytes, offset + LENGTH_VERSION) | 0x8000);
    }

    static void unmark(final byte[] bytes, final int offset) {
        Util.putChar(bytes, offset + LENGTH_VERSION, Util.getChar(bytes, offset + LENGTH_VERSION) & 0x7FFF);
    }

    /**
     * Get the full length of all overhead for the desired number of versions.
     * Note in practice a single version, i.e. primordial, doesn't actually have
     * any overhead.
     * 
     * @param numVersions
     *            Number of versions to compute overhead for.
     * @return length of all overhead
     */
    static int overheadLength(int numVersions) {
        return LENGTH_TYPE_MVV + LENGTH_PER_VERSION * numVersions;
    }

    /**
     * Compute and estimate of the required length to append a new version to
     * the given MVV array. This method is a loose estimate and may be greater,
     * but never less, than the length ultimately needed.
     * 
     * @param source
     *            MVV array of any size/state
     * @param sourceLength
     *            Length of {@code source} currently in use
     * @param newVersionLength
     *            Length of the new version that will be put into {@code source}
     * @return Required length estimate
     */
    static int estimateRequiredLength(byte[] source, int sourceLength, int newVersionLength) {
        if (sourceLength < 0) {
            return overheadLength(1) + newVersionLength;
        } else if (sourceLength == 0 || source[0] != TYPE_MVV_BYTE) {
            return overheadLength(2) + sourceLength + newVersionLength;
        } else {
            return sourceLength + LENGTH_PER_VERSION + newVersionLength;
        }
    }

    /**
     * Compute the final length needed to add, or update, a specific version
     * into the given MVV array. More costly than
     * {@link #estimateRequiredLength(byte[], int, int)} as all existing
     * versions must be inspected to handle update cases.
     * 
     * @param target
     *            MVV array of any size/state
     * @param targetLength
     *            Length of {@code source} currently in use
     * @param newVersion
     *            Version to be inserted
     * @param newVersionLength
     *            Length of version being inserted
     * @return Exact required length
     */
    static int exactRequiredLength(byte[] target, int targetOffset, int targetLength, long newVersion,
            int newVersionLength) {
        if (targetLength < 0) {
            return overheadLength(1) + newVersionLength;
        } else if (targetLength == 0 || target[targetOffset] != TYPE_MVV_BYTE) {
            return overheadLength(2) + targetLength + newVersionLength;
        } else {
            int offset = targetOffset + 1;
            while (offset < targetLength) {
                final long version = Util.getLong(target, offset);
                final int valueLength = Util.getShort(target, offset + LENGTH_VERSION);
                offset += LENGTH_PER_VERSION + valueLength;
                if (version == newVersion) {
                    return targetLength - valueLength + newVersionLength;
                }
            }
            return targetLength + LENGTH_PER_VERSION + newVersionLength;
        }
    }

    /**
     * Simple helper method to test if a given byte array is an MVV.
     * 
     * @param source
     *            Potential MVV to test
     * @param sourceLength
     *            Length of the given array currently in use.
     * 
     * @return <code>true</code> if the array is an MVV
     */
    static boolean isArrayMVV(byte[] source, int offset, int length) {
        return (length > 0) && (source[offset] == TYPE_MVV_BYTE);
    }

    /**
     * <p>
     * Write a value, with the specified version and length, into the given MVV
     * byte array. The resulting contents of the target array will always be MVV
     * no matter the starting state (i.e., empty, primordial, or MVV). That is,
     * an empty or primordial target will be promoted to an MVV and an MVV will
     * get appended to. If the exact version already exists in the target array
     * it will be updated as required.
     * </p>
     * <p>
     * The target MVV array must have enough available space to hold the updated
     * value. The caller can invoke the
     * {@link #exactRequiredLength(byte[], int, long, int)} method to determine
     * how much space is required.
     * </p>
     * 
     * @param target
     *            Destination byte array to append to or update
     * @param targetOffset
     *            starting offset within the destination array
     * @param targetLength
     *            Length of destination currently in use. The state of the
     *            destination array, which corresponds to the higher level
     *            {@link com.persistit.Value} state, is indicated by lengths
     *            <ul>
     *            <li>&lt; 0: target currently unoccupied and unused (no key or
     *            value)</li>
     *            <li>=0: target currently unoccupied but used (key but no
     *            value, i.e. undefined)</li>
     *            <li>>0: target currently occupied and used (key and value)</li>
     *            </ul>
     * @param targetLimit
     *            maximum index within target array to which bytes can be
     *            written. If this limit is exceeded an IllegalArgumentException
     *            is thrown.
     * @param versionHandle
     *            Version associated with source
     * @param source
     *            Value to store
     * @param sourceOffset
     *            starting offset within the source array
     * @param sourceLength
     *            Length of source currently in use
     * @return Compound value consisting of a flag indicating if the version
     *         already existed and the new consumed length of target. Use the
     *         mask values {@link MVV2#STORE_EXISTED_MASK} and
     *         {@link MVV2#STORE_LENGTH_MASK} for decoding the two pieces.
     * @throws IllegalArgumentException
     *             If target is too small to hold final MVV contents
     */
    public static int storeVersion(byte[] target, int targetOffset, int targetLength, int targetLimit,
            long versionHandle, byte[] source, int sourceOffset, int sourceLength) {
        int existedMask = 0;
        int to = targetOffset;
        if (targetLimit > target.length) {
            throw new IllegalArgumentException("Target limit exceed target array length: " + targetLimit + " > "
                    + target.length);
        }
        if (source == target) {
            throw new IllegalArgumentException("Source and target arrays must be different");
        }
        /*
         * Value did not previously exist. Result will be an MVV with one
         * version.
         */
        if (targetLength < 0) {
            assertCapacity(targetLimit, targetOffset + sourceLength + overheadLength(1));
            // Promote to MVV, no original state to preserve
            target[to++] = TYPE_MVV_BYTE;
        }

        /*
         * Value previously existed as a primordial undefined value (length =
         * 0). Result will be an MVV with two versions.
         */
        else if (targetLength == 0) {
            assertCapacity(targetLimit, targetOffset + sourceLength + overheadLength(2));
            // Promote to MVV, original state is undefined
            target[to++] = TYPE_MVV_BYTE;
            to += writeVersionHandle(target, to, PRIMORDIAL_VALUE_VERSION);
            to += writeValueLength(target, to, UNDEFINED_VALUE_LENGTH);
        }

        /*
         * Value previously existed as a primordial value. Result will be an MVV
         * with two versions.
         */
        else if (target[0] != TYPE_MVV_BYTE) {
            assertCapacity(targetLimit, targetOffset + sourceLength + targetLength + overheadLength(2));
            // Promote to MVV, shift existing down for header
            System.arraycopy(target, to, target, to + LENGTH_TYPE_MVV + LENGTH_PER_VERSION, targetLength);
            target[to++] = TYPE_MVV_BYTE;
            to += writeVersionHandle(target, to, PRIMORDIAL_VALUE_VERSION);
            to += writeValueLength(target, to, targetLength);
            to += targetLength;
        }

        /*
         * Value previously existed as an MVV. Result will be an MVV with an
         * extra version in most cases. The number result has the same number of
         * versions when the supplied versionHandle matches one of the existing
         * versions, in which case the value associated with that version is
         * simply replaced.
         */
        else {
            /*
             * Search for the matching version.
             */
            to++;
            int next = to;
            int end = targetOffset + targetLength;

            while (next < end) {
                final long curVersion = getVersion(target, to);
                final int vlength = getLength(target, to);
                next += LENGTH_PER_VERSION + vlength;
                if (curVersion == versionHandle) {
                    existedMask = STORE_EXISTED_MASK;
                    if (vlength == sourceLength) {
                        /*
                         * Replace the version having the same version handle;
                         * same length - can simply be copied in place.
                         */
                        System.arraycopy(source, sourceOffset, target, next - vlength, vlength);
                        return targetLength | existedMask;
                    } else {
                        assertCapacity(targetLimit, targetOffset + targetLength + overheadLength(1) + to - next
                                + sourceLength);
                        /*
                         * Remove the version having the same version handle -
                         * the new version will be added below.
                         */
                        System.arraycopy(target, next, target, to, targetOffset + targetLength - next);
                        end -= (next - to);
                        next = to;
                    }
                }
                to = next;
            }
        }
        assertCapacity(targetLimit, to + LENGTH_PER_VERSION + sourceLength);
        // Append new value
        to += writeVersionHandle(target, to, versionHandle);
        to += writeValueLength(target, to, sourceLength);
        System.arraycopy(source, sourceOffset, target, to, sourceLength);
        to += sourceLength;

        return (to - targetOffset) | existedMask;
    }

    /**
     * <p>
     * Remove obsolete or aborted values from an MVV. The MVV is defined by the
     * supplied byte array, starting offset and length. The result is that a
     * pruned version of the same MVV is written to the same byte array and
     * offset, and the length of the modified version is returned. Pruning never
     * lengths an MVV and therefore the returned length is guaranteed to be less
     * than or equal the the initial length.
     * </p>
     * <p>
     * This method leaves the byte array unchanged if any of its checked
     * Exceptions is thrown.
     * </p>
     * <p>
     * This method removes any version previously added by an aborted
     * transaction. It also decrements the MVV count of the affected
     * transaction, indicating that it is not necessary to perform proactive
     * cleanup to discard that the associated TransactionStatus. Therefore it is
     * mandatory for the caller to write the pruned MVV back into the B-Tree.
     * 
     * @param bytes
     *            the byte array
     * @param offset
     *            the index of the first byte of the MVV within the byte array
     * @param length
     *            the count of bytes in the MVV
     * @param ti
     *            The TransactionIndex
     * @param convertToPrimordial
     *            indicates whether the MVV should be converted to a primordial
     *            (non-MVV) value if possible. A reason for not doing so is if
     *            the caller will immediately store a new value.
     * @throws PersistitInterruptedException
     *             if the TransactionIndex throws an InterruptedException
     * @throws TimeoutException
     *             if the TransactionIndex times out while attempting to acquire
     *             a lock
     * @throws CorruptValueException
     *             if the MVV value is corrupt
     */
    static int prune(final byte[] bytes, final int offset, final int length, final TransactionIndex ti,
            boolean convertToPrimordial) throws PersistitInterruptedException, TimeoutException, CorruptValueException {
        if (!isArrayMVV(bytes, offset, length)) {
            /*
             * Not an MVV
             */
            return length;
        }
        boolean primordial = convertToPrimordial;
        int marked = 0;
        try {
            int from = offset + 1;
            int to = from;
            /*
             * Used to keep track of the latest version discovered in the
             * traversal. These variables identify a version that should be kept
             * only because it is the highest version in the MVV.
             */
            int lastVersionIndex = -1;
            long lastVersionHandle = Long.MIN_VALUE;
            long uncommittedTransactionTs = 0;
            /*
             * First pass - mark all the versions to keep. Keep every
             * UNCOMMITTED version (there may be more than one created by the
             * same transaction), every version needed to support a concurrent
             * transaction, and the most recent committed version.
             */
            while (from < offset + length) {
                final int vlength = getLength(bytes, from);
                final long versionHandle = getVersion(bytes, from);
                final long tc = ti.commitStatus(versionHandle, UNCOMMITTED, 0);
                if (tc >= 0) {
                    if (tc == UNCOMMITTED) {
                        long ts = vh2ts(versionHandle);
                        if (uncommittedTransactionTs != 0 && uncommittedTransactionTs != ts) {
                            throw new CorruptValueException("Multiple uncommitted version");
                        }
                        uncommittedTransactionTs = ts;
                        mark(bytes, from);
                        marked++;
                        /*
                         * Mark the last committed version too because the
                         * transaction for the current version may abort
                         */
                        if (lastVersionIndex != -1) {
                            mark(bytes, lastVersionIndex);
                            marked++;
                            lastVersionIndex = -1;
                        }
                        primordial = false;
                    } else {
                        if (lastVersionIndex != -1
                                && ti.hasConcurrentTransaction(vh2ts(lastVersionHandle), vh2ts(versionHandle))) {
                            mark(bytes, lastVersionIndex);
                            marked++;
                            primordial = false;
                        }
                        lastVersionIndex = from;
                        lastVersionHandle = versionHandle;
                    }
                } else if (tc == ABORTED) {
                    ti.decrementMvvCount(versionHandle);
                }
                from += vlength + LENGTH_PER_VERSION;
                if (from > offset + length) {
                    throw new CorruptValueException("MVV Value is corrupt at index: " + from);
                }
            }
            if (lastVersionIndex != -1) {
                mark(bytes, lastVersionIndex);
                marked++;
                if (ti.hasConcurrentTransaction(0, vh2ts(lastVersionHandle))) {
                    primordial = false;
                }
            }
            /*
             * Second pass - remove any unmarked versions and unmark any marked
             * versions.
             */
            if (primordial && marked <= 1) {
                /*
                 * Special handling for conversion to primordial value. Find the
                 * marked version and promote it to primordial.
                 */
                if (marked > 0) {
                    from = offset + 1;
                    while (from < offset + length) {
                        int vlength = getLength(bytes, from);
                        if (isMarked(bytes, from)) {
                            System.arraycopy(bytes, from + LENGTH_PER_VERSION, bytes, offset, vlength);
                            marked--;
                            return vlength;
                        }
                        from += vlength + LENGTH_PER_VERSION;
                    }
                }
                /*
                 * No marked versions - indicate by leaving primordial
                 * AntiValue.
                 */
                bytes[offset] = TYPE_ANTIVALUE;
                return 1;
            } else {
                /*
                 * Multiple versions will remain in the MVV. Remove the unmarked
                 * ones.
                 */
                from = offset + 1;
                to = from;
                int newLength = length;
                while (from < offset + newLength) {
                    final int vlength = getLength(bytes, from);
                    if (isMarked(bytes, from)) {
                        unmark(bytes, from);
                        marked--;
                        if (from > to) {
                            System.arraycopy(bytes, from, bytes, to, offset + length - from);
                        }
                        newLength -= (from - to);
                        to += vlength + LENGTH_PER_VERSION;
                        from = to;
                    } else {
                        from += vlength + LENGTH_PER_VERSION;
                    }
                }
                return to - offset;
            }
        } catch (InterruptedException ie) {
            throw new PersistitInterruptedException(ie);
        } finally {
            /*
             * Make sure all marks are removed even if this method exits via an
             * Exception.
             */
            if (marked > 0) {
                int index = offset + 1;
                while (index < length) {
                    int vlength = getLength(bytes, index);
                    unmark(bytes, index);
                    index += vlength + LENGTH_PER_VERSION;
                    if (vlength <= 0) {
                        break;
                    }
                }
            }
        }
    }

    /**
     * Search for a known version within a MVV array. If the version is found
     * within the array, copy the contents out to the target and return the
     * consumed length.
     * 
     * @param source
     *            MVV array to search
     * @param sourceLength
     *            Valid length of source
     * @param version
     *            Version to search for
     * @param target
     *            Array to copy desired value to
     * @return Length of new contents in target or {@link MVV#VERSION_NOT_FOUND}
     *         if no value was copied.
     * @throws IllegalArgumentException
     *             If the target array is too small to hold the value
     */
    public static int fetchVersion(byte[] source, int sourceLength, long version, byte[] target) {
        int offset = 0;
        int length = VERSION_NOT_FOUND;

        if (version == 0 && (sourceLength == 0 || source[0] != TYPE_MVV_BYTE)) {
            length = sourceLength;
        } else if (sourceLength > 0 && source[0] == TYPE_MVV_BYTE) {
            offset = 1;
            while (offset < sourceLength) {
                long curVersion = Util.getLong(source, offset);
                int curLength = Util.getShort(source, offset + LENGTH_VERSION);
                offset += LENGTH_PER_VERSION;
                if (curVersion == version) {
                    length = curLength;
                    break;
                }
                offset += curLength;
            }
        }

        if (length > 0) {
            assertCapacity(target.length, length);
            System.arraycopy(source, offset, target, 0, length);
        }

        return length;
    }

    public static interface VersionVisitor {
        /**
         * Called before iterating over MVV array. Allows for instance re-use.
         * 
         * @throws PersistitException
         *             For errors from concrete implementations.
         */
        void init() throws PersistitException;

        /**
         * Called once, and only once, for each version in the MVV array.
         * 
         * @param version
         *            Version of the stored value
         * @param valueOffset
         *            Offset in MVV array to start of stored value
         * @param valueLength
         *            Length of stored value
         * @throws PersistitException
         *             For errors from concrete implementations.
         */
        void sawVersion(long version, int valueOffset, int valueLength) throws PersistitException;
    }

    /**
     * Enumerate all versions of the values contained within the given MVV
     * array.
     * 
     * @param visitor
     *            FetchVisitor for consuming version info
     * @param source
     *            MVV array to search
     * @param sourceLength
     *            Consumed length of source
     * @throws PersistitException
     *             For any error coming from <code>visitor</code>
     */
    public static void visitAllVersions(VersionVisitor visitor, byte[] source, int sourceOffset, int sourceLength)
            throws PersistitException {
        visitor.init();
        if (sourceLength < 0) {
            // No versions
        } else if (sourceLength == 0) {
            visitor.sawVersion(PRIMORDIAL_VALUE_VERSION, sourceOffset, UNDEFINED_VALUE_LENGTH);
        } else if (source[sourceOffset] != TYPE_MVV_BYTE) {
            visitor.sawVersion(PRIMORDIAL_VALUE_VERSION, sourceOffset, sourceLength);
        } else {
            int offset = sourceOffset + 1;
            while (offset < sourceOffset + sourceLength) {
                final long version = Util.getLong(source, offset);
                final int valueLength = Util.getShort(source, offset + LENGTH_VERSION);
                offset += LENGTH_PER_VERSION;
                visitor.sawVersion(version, offset, valueLength);
                offset += valueLength;
            }
            if (offset != sourceOffset + sourceLength) {
                throw new CorruptValueException("invalid length in MVV at offset/length=" + sourceOffset + "/"
                        + sourceLength);
            }
        }
    }

    /**
     * Fetch a version of a value from a MVV array given a known offset. The
     * offset should be the starting position of of the actual value and not the
     * MVV header bytes. Intended to be used in connection with the
     * {@link #visitAllVersions(VersionVisitor, byte[], int)} method which gives
     * this offset.
     * 
     * @param source
     *            MVV array to search
     * @param sourceLength
     *            Consumed length of source
     * @param offset
     *            Offset inside {@code source} to start of actual value
     * @param target
     *            Array to copy desired value to
     * @return Length of new contents in target or {@link MVV#VERSION_NOT_FOUND}
     *         if no value was copied.
     * @throws IllegalArgumentException
     *             If the target array is too small to hold the value
     */
    public static int fetchVersionByOffset(byte[] source, int sourceLength, int offset, byte[] target) {
        if (offset < 0 || (offset > 0 && offset > sourceLength)) {
            throw new IllegalArgumentException("Offset out of range: " + offset);
        }
        final int length = (offset == 0) ? sourceLength : Util.getShort(source, offset - LENGTH_VALUE_LENGTH);
        if (length > 0) {
            assertCapacity(target.length, length);
            System.arraycopy(source, offset, target, 0, length);
        }
        return length;
    }

    /**
     * Internal helper. Write a version handle into the given byte array at the
     * specified offset.
     * 
     * @param target
     *            Destination array
     * @param offset
     *            Position in target to write
     * @param versionHandle
     *            Version to write
     * @return Byte count consumed in target
     */
    private static int writeVersionHandle(byte[] target, int offset, long versionHandle) {
        Util.putLong(target, offset, versionHandle);
        return LENGTH_VERSION;
    }

    /**
     * Internal helper. Write a value length given byte array at the specified
     * offset.
     * 
     * @param target
     *            Destination array
     * @param offset
     *            Position in target to write
     * @param length
     *            Length to write
     * @return Byte count consumed in target
     */
    private static int writeValueLength(byte[] target, int offset, int length) {
        Util.putShort(target, offset, length);
        return LENGTH_VALUE_LENGTH;
    }

    /**
     * Internal helper. Used to assert a given byte array is large enough before
     * writing.
     * 
     * @param target
     *            Array to check
     * @param length
     *            Length needed
     * @throws IllegalArgumentException
     *             If the array is too small
     */
    private static void assertCapacity(int limit, int length) throws IllegalArgumentException {
        if (limit < length) {
            throw new IllegalArgumentException("Destination array not big enough: " + limit + " < " + length);
        }
    }
}