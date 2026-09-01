package com.match.transfer;

import lombok.Data;

@Data
public class ModelWorkspaceRequest {
    private String modelPath;
    private String configPath;
    private Boolean overwrite;
}
