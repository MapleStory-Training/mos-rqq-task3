/*
 * This file is part of MOS
 * <p>
 * Copyright (c) 2021 by cooder.org
 * <p>
 * For the full copyright and license information, please view the LICENSE
 * file that was distributed with this source code.
 */
package org.cooder.mos.fs.fat16;

import org.cooder.mos.fs.IFileSystem;
import org.cooder.mos.fs.fat16.Layout.DirectoryEntry;

import java.util.Arrays;

import static org.cooder.mos.fs.fat16.Layout.DirectoryEntry.FILE_NAME_LENGTH;
import static org.cooder.mos.fs.fat16.Layout.LfnEntry.SHORT_NAME_FLAG;

public class DirectoryTreeNode {
    public DirectoryEntry entry;
    public final DirectoryTreeNode parent;
    public DirectoryTreeNode[] children;
    public int sectorIdx = -1;
    public int sectorOffset = -1;
    public boolean fold = true;

    public DirectoryTreeNode(DirectoryTreeNode parent, DirectoryEntry entry) {
        this.parent = parent;
        this.entry = entry;
    }

    public DirectoryTreeNode[] getChildren() {
        return children;
    }

    public void setChildren(DirectoryTreeNode[] children) {
        this.children = children;
    }

    public int getSectorIdx() {
        return sectorIdx;
    }

    public void setSectorIdx(int sectorIdx) {
        this.sectorIdx = sectorIdx;
    }

    public int getSectorOffset() {
        return sectorOffset;
    }

    public void setSectorOffset(int sectorOffset) {
        this.sectorOffset = sectorOffset;
    }

    public DirectoryEntry getEntry() {
        return entry;
    }

    public boolean isDir() {
        return isRoot() || ((entry.attrs & DirectoryEntry.ATTR_MASK_DIR) != 0);
    }

    public boolean isRoot() {
        return entry == null;
    }

    public boolean isFold() {
        return fold;
    }

    public void unfold() {
        this.fold = false;
    }

    public void fold() {
        this.fold = true;
    }

    public String getName() {
        if (isRoot()) {
            return "/";
        } else {
            return byteArray2String(entry.fileName);
        }
    }

    public String getPath() {
        if (isRoot()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        if (parent != null) {
            sb.append(parent.getPath()).append(IFileSystem.separator);
        }
        sb.append(getName());
        return sb.toString();
    }

    public DirectoryTreeNode find(String name) {
        if (!isDir()) {
            throw new IllegalArgumentException();
        }

        if (isFold()) {
            throw new IllegalStateException();
        }

        if (children == null) {
            return null;
        }

        DirectoryTreeNode node;
        for (int i = 0; i < children.length; i++) {
            node = children[i];
            if (node.nameEquals(name)) {
                return node;
            }
        }

        return null;
    }

    public boolean nameEquals(String fileName) {
        if (Layout.isLFN(entry.toBytes())) {
            return false;
        }
        return Arrays.equals(entry.fileName, string2ByteArray(fileName, FILE_NAME_LENGTH));
    }

    public static byte[] string2ByteArray(String name, int length) {
        byte[] b1 = name.getBytes();
        byte[] b2 = new byte[length];
        System.arraycopy(b1, 0, b2, 0, Math.min(b1.length, length));
        return b2;
    }

    public static String byteArray2String(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < b.length; i++) {
            if (b[i] == 0) {
                break;
            }
            sb.append((char) (b[i] & 0xFF));
        }
        return sb.toString();
    }

    public DirectoryTreeNode create(String name, boolean isDir) {
        DirectoryTreeNode node = nextFreeNode();
        DirectoryEntry entry = node.entry;

        byte[] b = string2ByteArray(name, FILE_NAME_LENGTH);
        System.arraycopy(b, 0, entry.fileName, 0, b.length);
        entry.attrs |= isDir ? DirectoryEntry.ATTR_MASK_DIR : 0;
        node.setWriteTime(System.currentTimeMillis());

        return node;
    }

    public DirectoryTreeNode firstTreeNode() {
        if (!isDir()) {
            throw new IllegalArgumentException();
        }

        if (isFold()) {
            throw new IllegalStateException();
        }

        if (children == null) {
            return null;
        }

        DirectoryTreeNode node = null;
        for (int i = 0; i < children.length; i++) {
            node = children[i];
            if (!node.isFree()) {
                return node;
            }
        }
        return null;
    }

    private DirectoryTreeNode nextFreeNode() {
        if (!isDir()) {
            throw new IllegalArgumentException();
        }

        if (isFold()) {
            throw new IllegalStateException();
        }

        if (children == null) {
            return null;
        }

        DirectoryTreeNode node = null;
        for (int i = 0; i < children.length; i++) {
            node = children[i];
            if (node.isFree()) {
                return node;
            }
        }
        return null;
    }

    public boolean isFree() {
        if (Layout.isLFN(entry.toBytes())) {
            return false;
        }
        return entry.fileName[0] == 0;
    }

    public boolean valid() {
        if (entry == null) {
            return false;
        }
        if (Layout.isLFN(entry.toBytes())) {
            return false;
        }
        return !isFree();
    }

    public void reset() {
        this.fold = true;
        this.entry = new DirectoryEntry();
        this.children = null;
    }

    public void setFileSize(int fileSize) {
        this.entry.fileSize = fileSize;
    }

    public void setWriteTime(long currentTimeMillis) {
        int sec = (int) (currentTimeMillis / 1000);
        this.entry.lastWriteTime = (short) (sec & 0xFFFF);
        this.entry.lastWriteDate = (short) (sec >>> 16 & 0xFFFF);
    }

    public long getWriteTime() {
        long sec = 0x0000 | this.entry.lastWriteDate;
        sec = sec << 16;
        sec = sec | (0xFFFF & this.entry.lastWriteTime);
        return sec * 1000;
    }

    public int getFileSize() {
        return entry.fileSize;
    }

    public boolean containsUnknownInfo() {
        return Arrays.equals(new byte[]{entry.fileName[FILE_NAME_LENGTH - 2]}, string2ByteArray(SHORT_NAME_FLAG, 1));
    }
}
