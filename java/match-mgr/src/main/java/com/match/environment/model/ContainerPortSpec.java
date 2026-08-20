package com.match.environment.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContainerPortSpec {
    private Integer containerPort;
    private String protocol;
}
