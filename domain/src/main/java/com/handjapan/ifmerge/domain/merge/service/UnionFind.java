package com.handjapan.ifmerge.domain.merge.service;

import java.util.*;

/**
 * Union-Find（路径压缩 + 按秩合并）。
 * 原 Python：IFmerge/ebs_merger/merge_grouper.py:10-72。
 */
public class UnionFind {

    private final Map<String, String> parent;
    private final Map<String, Integer> rank;

    public UnionFind(Collection<String> elements) {
        this.parent = new HashMap<>(elements.size() * 2);
        this.rank = new HashMap<>(elements.size() * 2);
        for (String e : elements) {
            parent.put(e, e);
            rank.put(e, 0);
        }
    }

    public String find(String x) {
        String p = parent.get(x);
        if (p == null) throw new IllegalArgumentException("Unknown element: " + x);
        if (!p.equals(x)) {
            String root = find(p);
            parent.put(x, root);
            return root;
        }
        return p;
    }

    public void union(String x, String y) {
        String rootX = find(x);
        String rootY = find(y);
        if (rootX.equals(rootY)) return;

        int rx = rank.get(rootX);
        int ry = rank.get(rootY);
        if (rx < ry) {
            parent.put(rootX, rootY);
        } else if (rx > ry) {
            parent.put(rootY, rootX);
        } else {
            parent.put(rootY, rootX);
            rank.put(rootX, rx + 1);
        }
    }

    /**
     * {代表元素 → メンバーリスト} を返す。
     */
    public Map<String, List<String>> getGroups() {
        Map<String, List<String>> groups = new LinkedHashMap<>();
        for (String x : parent.keySet()) {
            String root = find(x);
            groups.computeIfAbsent(root, k -> new ArrayList<>()).add(x);
        }
        return groups;
    }
}
