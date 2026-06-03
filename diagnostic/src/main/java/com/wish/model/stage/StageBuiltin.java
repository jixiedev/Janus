package com.wish.model.stage;

import com.wish.tools.kubernetes.KubernetesTool;
import com.wish.tools.s3.S3Tool;
import com.wish.tools.warehouse.QueryWarehouseTool;

/** Built-in (non-MCP) tools declared per {@link Stage}. */
public enum StageBuiltin {
    QUERY_WAREHOUSE,
    S3,
    KUBERNETES;

    public Object createInstance() {
        return switch (this) {
            case QUERY_WAREHOUSE -> new QueryWarehouseTool();
            case S3 -> new S3Tool();
            case KUBERNETES -> new KubernetesTool();
        };
    }
}
