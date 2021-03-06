/*
 * This file is part of MOS
 * <p>
 * Copyright (c) 2021 by cooder.org
 * <p>
 * For the full copyright and license information, please view the LICENSE
 * file that was distributed with this source code.
 */
package org.cooder.mos.fs.fat16;

import java.nio.ByteBuffer;

import static org.cooder.mos.fs.fat16.Layout.LfnEntry.ATTR_MASK_LFN;

public class Layout {
    public static final int RESERVED_SECTORS = 1;
    public static final int NUM_OF_FAT_COPY = 2;
    public static final int SECTORS_PER_FAT = 256;
    public static final int PER_SECTOR_SIZE = 512;
    public static final int PER_DIRECTOR_ENTRY_SIZE = 32;
    public static final int SECTORS_PER_CLUSTER = 64;
    public static final int PER_CLUSTER_SIZE = PER_SECTOR_SIZE * SECTORS_PER_CLUSTER;
    public static final int ROOT_ENTRIES_COUNT = (PER_CLUSTER_SIZE - PER_SECTOR_SIZE) / 32;

    public static final int VOLUME_START = 0;
    public static final int RESERVED_REGION_SIZE = RESERVED_SECTORS;

    public static final int FAT_REGION_START = VOLUME_START + RESERVED_REGION_SIZE;
    public static final int FAT_REGION_SIZE = NUM_OF_FAT_COPY * SECTORS_PER_FAT;

    public static final int ROOT_DIRECTORY_REGION_START = FAT_REGION_START + FAT_REGION_SIZE;
    public static final int ROOT_DIRECTORY_REGION_SIZE = (ROOT_ENTRIES_COUNT * PER_DIRECTOR_ENTRY_SIZE)
            / PER_SECTOR_SIZE;

    public static final int DATA_REGION_START = ROOT_DIRECTORY_REGION_START + ROOT_DIRECTORY_REGION_SIZE;
    public static final int HEAD_CLUSTER_COUNT = DATA_REGION_START / SECTORS_PER_CLUSTER
            + ((DATA_REGION_START % SECTORS_PER_CLUSTER) == 0 ? 0 : 1);

    /**
     * 引导扇区Layout，涉及到整形数的都是大端字节序
     */
    public static class BootSector {
        // 跳转指令，3 bytes
        final byte[] jmpCode = new byte[]{(byte) 0xEB, 0x3C, (byte) 0x90};

        // Oem Name 8 bytes
        final byte[] oemName = new byte[]{'m', 'o', 's', '-', 'r', 'q', 'q', 0};

        // 每扇区字节数，2 bytes
        final short sectorSize = PER_SECTOR_SIZE;

        // 每簇扇区数，1 byte
        final byte clusterWidth = SECTORS_PER_CLUSTER;

        // 保留扇区数，2bytes
        final short reservedSectors = RESERVED_SECTORS;

        // FAT数量
        final byte numOfFATCopy = NUM_OF_FAT_COPY;

        // 根目录项数
        final short rootEntriesCount = ROOT_ENTRIES_COUNT;

        final short smallNumberOfSectors = (short) 0xFFFF;

        final byte mediaDescriptor = (byte) 0xFA;

        final short sectorsPerFAT = SECTORS_PER_FAT;

        final short sectorsPerTrack = 63;

        final short numberOfHeads = 0;

        final int hiddenSectors = 0;

        final int largeNumberOfSectors = (short) 0xFFFF;

        final byte driveNumber = 0;

        final byte reserved = 0;

        final byte extendedBootSignature = 0;

        final int volumeSerialNumber = 0;

        final byte[] volumeLabel = new byte[11];

        final byte[] fileSystemType = new byte[]{'F', 'A', 'T', '1', '6', 0, 0, 0};

        final byte[] bootstrapCode = new byte[448];

        final short bootSectorSignature = 0x55AA;

        public byte[] toBytes() {
            ByteBuffer buf = ByteBuffer.allocateDirect(512);
            buf.put(jmpCode);
            buf.put(oemName);
            buf.putShort(sectorSize);
            buf.put(clusterWidth);
            buf.putShort(reservedSectors);
            buf.put(numOfFATCopy);
            buf.putShort(rootEntriesCount);
            buf.putShort(smallNumberOfSectors);
            buf.put(mediaDescriptor);
            buf.putShort(sectorsPerFAT);
            buf.putShort(sectorsPerTrack);
            buf.putShort(numberOfHeads);
            buf.putInt(hiddenSectors);
            buf.putInt(largeNumberOfSectors);
            buf.put(driveNumber);
            buf.put(reserved);
            buf.put(extendedBootSignature);
            buf.putInt(volumeSerialNumber);
            buf.put(volumeLabel);
            buf.put(fileSystemType);
            buf.put(bootstrapCode);
            buf.putShort(bootSectorSignature);

            buf.rewind();

            byte[] data = new byte[512];
            buf.get(data);
            return data;
        }
    }

    public static class DirectoryEntry {

        public static final int FILE_NAME_LENGTH = 8;

        public static final byte ATTR_MASK_READONLY = 0x01;
        public static final byte ATTR_MASK_HIDDEN = 0x02;
        public static final byte ATTR_MASK_SYSTEM = 0x04;
        public static final byte ATTR_MASK_VOLUME = 0x08;
        public static final byte ATTR_MASK_DIR = 0x10;
        public static final byte ATTR_MASK_ACHIEVE = 0x20;

        // 8 bytes
        public byte[] fileName = new byte[8];
        public byte[] extension = new byte[3];
        public byte attrs;
        public byte reserved;
        public byte creation;
        public short createTime;
        public short createDate;
        public short lastAccessDate;
        public short unused;
        public short lastWriteTime;
        public short lastWriteDate;
        public short startingCluster;
        public int fileSize;

        public byte[] toBytes() {
            ByteBuffer buf = ByteBuffer.allocateDirect(32);
            buf.put(fileName);
            buf.put(extension);
            buf.put(attrs);
            buf.put(reserved);
            buf.put(creation);
            buf.putShort(createTime);
            buf.putShort(createDate);
            buf.putShort(lastAccessDate);
            buf.putShort(lastWriteTime);
            buf.putShort(lastWriteDate);
            buf.putShort(startingCluster);
            buf.putInt(fileSize);

            buf.rewind();

            byte[] data = new byte[32];
            buf.get(data);
            return data;
        }

        public static DirectoryEntry from(byte[] data) {
            ByteBuffer buf = ByteBuffer.allocateDirect(32);
            buf.put(data);
            buf.rewind();

            DirectoryEntry e = new DirectoryEntry();
            buf.get(e.fileName, 0, 8);
            buf.get(e.extension, 0, 3);
            e.attrs = buf.get();
            e.reserved = buf.get();
            e.creation = buf.get();
            e.createTime = buf.getShort();
            e.createDate = buf.getShort();
            e.lastAccessDate = buf.getShort();
            e.lastWriteTime = buf.getShort();
            e.lastWriteDate = buf.getShort();
            e.startingCluster = buf.getShort();
            e.fileSize = buf.getInt();

            return e;
        }

        @Override
        public String toString() {
            return String.format("filename: %s, startCluster: %d", new String(fileName), startingCluster);
        }
    }

    public static class LfnEntry extends DirectoryEntry {

        public static final int PART_FILE_NAME_LENGTH = 30;

        public static final String SHORT_NAME_FLAG = "~";

        public static final byte ORDINAL_MASK_DEL = (byte) 0x80;
        public static final byte ORDINAL_MASK_LAST = 0x40;
        public static final byte ORDINAL_MASK_NUM = 0x3F;

        public static final byte ATTR_MASK_LFN = 0x0F;

        public byte ordinal;
        public byte[] part1 = new byte[10];
        public byte attrs;
        public byte[] part2 = new byte[20];

        @Override
        public byte[] toBytes() {
            ByteBuffer buf = ByteBuffer.allocateDirect(32);
            buf.put(ordinal);
            buf.put(part1);
            buf.put(attrs);
            buf.put(part2);

            buf.rewind();

            byte[] data = new byte[32];
            buf.get(data);
            return data;
        }

        public boolean last() {
            return (ordinal & ORDINAL_MASK_LAST) != 0;
        }

        public int lfnNum() {
            return ordinal & ORDINAL_MASK_NUM;
        }

        public static LfnEntry from(byte[] data) {
            ByteBuffer buf = ByteBuffer.allocateDirect(32);
            buf.put(data);
            buf.rewind();

            LfnEntry lfn = new LfnEntry();
            lfn.ordinal = buf.get();
            buf.get(lfn.part1, 0, 10);
            lfn.attrs = buf.get();
            buf.get(lfn.part2, 0, 20);

            return lfn;
        }

        @Override
        public String toString() {
            return String.format("filename part %d : %s", ordinal & ORDINAL_MASK_NUM, new String(part1) + new String(part2));
        }
    }

    public static boolean isLFN(byte[] data) {
        ByteBuffer buf = ByteBuffer.allocateDirect(32);
        buf.put(data);
        buf.rewind();
        buf.position(11);
        return (buf.get() & ATTR_MASK_LFN) == ATTR_MASK_LFN;
    }

    public static int getClusterDataStartSector(int clusterIdx) {
        return clusterIdx * SECTORS_PER_CLUSTER;
    }

    public static int getSectorDataStartPos(int sectorIdx) {
        return sectorIdx * PER_SECTOR_SIZE;
    }

    public static int getClusterDataLastSector(int clusterIdx) {
        return getClusterDataStartSector(clusterIdx) + SECTORS_PER_CLUSTER - 1;
    }
}
