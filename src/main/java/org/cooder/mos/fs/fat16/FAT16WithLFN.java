package org.cooder.mos.fs.fat16;

import org.cooder.mos.device.IDisk;
import org.cooder.mos.fs.fat16.Layout.DirectoryEntry;
import org.cooder.mos.fs.fat16.Layout.LfnEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author renqianqian
 * @date 2021/6/21
 */
public class FAT16WithLFN extends FAT16 {

    public FAT16WithLFN(IDisk disk) {
        super(disk);
        this.root = new DirectoryTreeNodeWithLFN(null, null);
        reload();
    }

    @Override
    public synchronized void reload() {
        loadFAT();
        loadSubEntries(root);
    }

    @Override
    public void loadEntries(DirectoryTreeNode parent) {
        if (parent.isDir() && parent.isFold()) {
            loadSubEntries(parent);
        }
    }

    private void loadSubEntries(DirectoryTreeNode parent) {
        int sectorIdx, limit;
        List<DirectoryTreeNode> children = new ArrayList<>();
        // 读取根目录项
        if (parent == root) {
            sectorIdx = Layout.ROOT_DIRECTORY_REGION_START;
            limit = Layout.ROOT_DIRECTORY_REGION_SIZE;

            children = loadEntries(parent, sectorIdx, limit);
        } else {
            // 读取簇中目录项
            int[] clusters = clusterFrom(parent.getEntry().startingCluster);
            for (int cluster : clusters) {
                sectorIdx = Layout.getClusterDataStartSector(cluster);
                limit = Layout.SECTORS_PER_CLUSTER;

                children.addAll(loadEntries(parent, sectorIdx, limit));
            }
        }

        parent.setChildren(children.toArray(new DirectoryTreeNode[children.size()]));
        parent.unfold();
    }

    private List<DirectoryTreeNode> loadEntries(DirectoryTreeNode parent, int sectorIdx, int limitSectorCount) {
        List<DirectoryTreeNode> nodes = new ArrayList<>(limitSectorCount);
        byte[] buffer = new byte[Layout.PER_DIRECTOR_ENTRY_SIZE];
        for (int i = 0; i < limitSectorCount; i++) {
            byte[] sectorData = disk.readSector(sectorIdx + i);

            List<LfnEntry> lfnEntries = new ArrayList<>();
            for (int j = 0; j < sectorData.length; j += Layout.PER_DIRECTOR_ENTRY_SIZE) {
                System.arraycopy(sectorData, j, buffer, 0, Layout.PER_DIRECTOR_ENTRY_SIZE);

                if (Layout.isLFN(buffer)) {
                    LfnEntry entry = LfnEntry.from(buffer);
                    DirectoryTreeNode node = new DirectoryTreeNodeWithLFN(parent, entry);
                    node.setSectorIdx(sectorIdx + i);
                    node.setSectorOffset(j);
                    nodes.add(node);
                    lfnEntries.add(entry);
                } else {
                    DirectoryEntry entry = DirectoryEntry.from(buffer);
                    DirectoryTreeNodeWithLFN node = new DirectoryTreeNodeWithLFN(parent, entry);
                    node.setSectorIdx(sectorIdx + i);
                    node.setSectorOffset(j);
                    node.setLfnEntries(transform(lfnEntries));
                    nodes.add(node);
                    lfnEntries = new ArrayList<>();
                }
            }
        }
        return nodes;
    }

    private LfnEntry[] transform(List<LfnEntry> origin) {
        if (origin.isEmpty()) {
            return null;
        }
        Collections.reverse(origin);
        int i = 1;
        for (LfnEntry entry : origin) {
            if (i != entry.lfnNum()) {
                throw new IllegalStateException("lfn num is not right");
            }
            i++;
        }
        if (!origin.get(i - 2).last()) {
            throw new IllegalStateException("not find last lfn");
        }

        LfnEntry[] lfnEntries = new LfnEntry[i - 1];
        for (int j = 0; j < i - 1; j++) {
            lfnEntries[j] = origin.get(j);
        }
        return lfnEntries;
    }

    //
    // Directory Tree Method.
    //

    @Override
    public void writeDirectoryTreeNode(DirectoryTreeNode node) {
        // 持久化目录项
        byte[] entryData = node.getEntry().toBytes();
        byte[] sectorData = disk.readSector(node.getSectorIdx());
        System.arraycopy(entryData, 0, sectorData, node.getSectorOffset(), entryData.length);

        disk.writeSector(node.getSectorIdx(), sectorData);

        // 持久化长文件名项
        DirectoryTreeNodeWithLFN nodeWithLFN = (DirectoryTreeNodeWithLFN) node;
        DirectoryTreeNode[] lfnNodes = nodeWithLFN.getLfnNodes();
        if (lfnNodes != null) {
            for (DirectoryTreeNode lfnNode : lfnNodes) {
                entryData = lfnNode.getEntry().toBytes();
                sectorData = disk.readSector(lfnNode.getSectorIdx());
                System.arraycopy(entryData, 0, sectorData, lfnNode.getSectorOffset(), entryData.length);

                disk.writeSector(lfnNode.getSectorIdx(), sectorData);
            }
        }
    }

    @Override
    public void removeTreeNode(DirectoryTreeNode node) {
        if (node == null || node == root) {
            return;
        }

        DirectoryTreeNodeWithLFN nodeWithLFN = (DirectoryTreeNodeWithLFN) node;
        DirectoryTreeNode[] lfnNodes = nodeWithLFN.getLfnNodes();
        if (lfnNodes != null) {
            for (DirectoryTreeNode lfnNode : lfnNodes) {
                lfnNode.reset();
            }
            writeDirectoryTreeNode(node);
        }
        node.reset();
        writeDirectoryTreeNode(node);
    }

    @Override
    public DirectoryTreeNode findSubTreeNode(DirectoryTreeNode parent, String name) {
        if (parent == null) {
            parent = root;
        }

        if (!parent.isDir()) {
            throw new IllegalArgumentException(name + ": not directory");
        }

        if (parent.isFold()) {
            loadSubEntries(parent);
        }

        return parent.find(name);
    }

    @Override
    public boolean isEmpty(DirectoryTreeNode parent) {
        if (parent == null) {
            parent = root;
        }

        if (!parent.isDir()) {
            throw new IllegalArgumentException(parent.getName() + ": not directory");
        }

        if (parent.isFold()) {
            loadSubEntries(parent);
        }

        return parent.firstTreeNode() == null;
    }

    @Override
    public DirectoryTreeNode createTreeNode(DirectoryTreeNode parent, String name, boolean isDir) {
        if (parent == null) {
            parent = root;
        }

        if (parent.isFold()) {
            loadSubEntries(parent);
        }

        DirectoryTreeNode node = parent.find(name);
        if (node != null) {
            throw new IllegalStateException("file exist.");
        }

        node = parent.create(name, isDir);

        // update
        DirectoryEntry entry = node.getEntry();
        entry.startingCluster = (short) (nextFreeCluster(-1) & 0xFFFF);
        writeDirectoryTreeNode(node);

        return node;
    }

}
