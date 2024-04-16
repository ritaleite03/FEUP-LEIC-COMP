package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.Type;

public class InferType {
    public Type type;
    public boolean needsResult;

    InferType(Type type) {
        this.type = type;
        this.needsResult = true;
    }

    InferType(Type type, boolean needsResult) {
        this.type = type;
        this.needsResult = needsResult;
    }
}