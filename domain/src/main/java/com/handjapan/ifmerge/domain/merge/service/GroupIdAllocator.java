package com.handjapan.ifmerge.domain.merge.service;

import java.util.HashMap;
import java.util.Map;

/**
 * モジュール毎の連番でグルーピング ID を採番する。
 * 例：FI001、FI002、SD001、...
 * 原 Python：IFmerge/ebs_merger/cli.py:223-229。
 */
public class GroupIdAllocator {

    private final Map<String, Integer> counters = new HashMap<>();

    public String next(String module) {
        String safe = sanitize(module);
        int n = counters.merge(safe, 1, Integer::sum);
        return String.format("%s%03d", safe, n);
    }

    public String allocateForDefaultModule() {
        return next("G");
    }

    private static String sanitize(String module) {
        if (module == null || module.isBlank()) return "G";
        return module.replaceAll("[\\\\/:*?\\[\\]]", "_");
    }
}
