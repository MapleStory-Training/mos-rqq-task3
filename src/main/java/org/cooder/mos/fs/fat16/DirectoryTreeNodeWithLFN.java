package org.cooder.mos.fs.fat16;

import org.apache.commons.lang3.StringUtils;
import org.cooder.mos.fs.fat16.Layout.DirectoryEntry;
import org.cooder.mos.fs.fat16.Layout.LfnEntry;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.cooder.mos.fs.fat16.Layout.LfnEntry.*;
import static org.cooder.mos.fs.fat16.Layout.isLFN;

/**
 * @author renqianqian
 * @date 2021/6/22
 */
public class DirectoryTreeNodeWithLFN extends DirectoryTreeNode {

    private LfnEntry[] lfnEntries;
    private String lfn;

    public DirectoryTreeNodeWithLFN(DirectoryTreeNode parent, DirectoryEntry entry) {
        super(parent, entry);
    }

    public LfnEntry[] getLfnEntries() {
        return lfnEntries;
    }

    public void setLfnEntries(LfnEntry[] lfnEntries) {
        this.lfnEntries = lfnEntries;
        if (lfnEntries != null) {
            this.lfn = convertLfn(lfnEntries);
        }
    }

    @Override
    public String getName() {
        if (StringUtils.isBlank(lfn)) {
            return super.getName();
        }
        return lfn;
    }

    @Override
    public boolean nameEquals(String fileName) {
        if (entry instanceof Layout.LfnEntry) {
            return false;
        }

        if (StringUtils.isBlank(lfn)) {
            return super.nameEquals(fileName);
        } else {
            return fileName.equals(lfn);
        }
    }

    @Override
    public DirectoryTreeNode create(String name, boolean isDir) {
        if (Boolean.FALSE.equals(needLfn(name))) {
            return super.create(name, isDir);
        }

        // 长文件名生成目录项
        int length = lfnCount(name) + 1;
        DirectoryTreeNode[] nodes = nextFreeNode(length);

        // 填充lfn
        LfnEntry[] lfnEntries = new LfnEntry[length - 1];
        int lfnSize = lfnEntries.length * Layout.PER_DIRECTOR_ENTRY_SIZE;
        ByteBuffer buf = ByteBuffer.allocateDirect(lfnSize);
        buf.put(string2ByteArray(name, lfnSize));
        buf.rewind();
        for (int i = nodes.length - 2, num = 0; i >= 0; i--, num++) {
            LfnEntry entry = LfnEntry.from(nodes[i].entry.toBytes());

            entry.ordinal = (byte) (ORDINAL_MASK_NUM & num + 1);
            entry.ordinal |= i == 0 ? 1 << 6 : 0;
            entry.attrs = ATTR_MASK_LFN;
            buf.get(entry.part1, 0, 10);
            buf.get(entry.part2, 0, 20);

            nodes[i].entry = entry;
            lfnEntries[num] = entry;
        }

        // 填充目录项
        DirectoryTreeNodeWithLFN node = (DirectoryTreeNodeWithLFN) nodes[length - 1];
        byte[] b = getShortName(name, DirectoryEntry.FILE_NAME_LENGTH);
        System.arraycopy(b, 0, node.entry.fileName, 0, b.length);
        node.entry.attrs |= isDir ? DirectoryEntry.ATTR_MASK_DIR : 0;
        node.setWriteTime(System.currentTimeMillis());
        node.setLfnEntries(lfnEntries);

        return node;
    }

    /**
     * 获取短文件名
     * todo 最多支持9个同名前缀文件
     *
     * @param lfn
     * @param length
     * @return
     */
    private byte[] getShortName(String lfn, int length) {
        int[] sameNamePrefix = new int[9];
        String fnPrefix = lfn.substring(0, length - 2);
        for (DirectoryTreeNode child : this.children) {
            DirectoryTreeNodeWithLFN childWithLFN = (DirectoryTreeNodeWithLFN) child;
            if (StringUtils.isNotBlank(childWithLFN.lfn) && childWithLFN.lfn.startsWith(fnPrefix)) {
                int sameNameNum = Integer.parseInt(byteArray2String(childWithLFN.entry.fileName).substring(length - 1));
                sameNamePrefix[sameNameNum - 1] = sameNameNum;
            }
        }

        int desNum = 0;
        for (int i = 0; i < sameNamePrefix.length; i++) {
            if (sameNamePrefix[i] == 0) {
                desNum = i + 1;
                break;
            }
        }
        if (desNum == 0) {
            throw new IllegalStateException("only can create up to 9 files with the same prefix :" + fnPrefix);
        }
        return string2ByteArray(fnPrefix + SHORT_NAME_FLAG + desNum, length);
    }

    private Boolean needLfn(String name) {
        return name.getBytes().length > DirectoryEntry.FILE_NAME_LENGTH;
    }

    private int lfnCount(String name) {
        if (Boolean.FALSE.equals(needLfn(name))) {
            return 0;
        }
        return name.getBytes().length % LfnEntry.PART_FILE_NAME_LENGTH == 0
                ? name.getBytes().length / LfnEntry.PART_FILE_NAME_LENGTH
                : name.getBytes().length / LfnEntry.PART_FILE_NAME_LENGTH + 1;
    }

    private DirectoryTreeNode[] nextFreeNode(int count) {
        if (!isDir()) {
            throw new IllegalArgumentException();
        }

        if (isFold()) {
            throw new IllegalStateException();
        }

        if (children == null) {
            return null;
        }

        DirectoryTreeNode[] nodes = new DirectoryTreeNodeWithLFN[count];
        int[] idxes = new int[count];
        int p = 0;
        for (int i = 0; i < children.length; i++) {
            if (p == count) {
                break;
            }

            DirectoryTreeNode node = children[i];
            if (node.isFree()) {
                if (p - 1 < 0 || idxes[p - 1] == i - 1) {
                    nodes[p] = node;
                    idxes[p] = i;
                    p++;
                } else {
                    p = 0;
                }

            } else {
                p = 0;
            }
        }
        return p == count ? nodes : null;
    }

    @Override
    public boolean isFree() {
        if (entry instanceof Layout.LfnEntry) {
            return false;
        }
        return super.isFree();
    }

    @Override
    public boolean valid() {
        return entry != null && !isFree() && !isLFN(entry.toBytes());
    }

    @Override
    public void reset() {
        super.reset();
        this.lfnEntries = null;
        this.lfn = null;
    }

    public DirectoryTreeNode[] getLfnNodes() {
        if (StringUtils.isBlank(lfn)) {
            return null;
        }

        int idx = -1;
        for (int i = 0; i < this.parent.children.length; i++) {
            DirectoryEntry entry = this.parent.children[i].entry;
            if (!(entry instanceof Layout.LfnEntry)
                    && Arrays.equals(this.entry.fileName, entry.fileName)) {
                idx = i;
                break;
            }
        }

        int startIdx = idx - this.lfnEntries.length;
        DirectoryTreeNode[] lfnNodes = new DirectoryTreeNode[this.lfnEntries.length];
        for (int i = 0; i < lfnNodes.length; i++) {
            lfnNodes[i] = this.parent.children[startIdx + i];
        }
        return lfnNodes;
    }

    private String convertLfn(LfnEntry[] lfnEntries) {
        StringBuilder sb = new StringBuilder();
        for (LfnEntry lfnEntry : lfnEntries) {
            sb.append(byteArray2String(lfnEntry.part1)).append(byteArray2String(lfnEntry.part2));
        }
        return sb.toString();
    }

    @Override
    public boolean containsUnknownInfo() {
        return false;
    }
}
