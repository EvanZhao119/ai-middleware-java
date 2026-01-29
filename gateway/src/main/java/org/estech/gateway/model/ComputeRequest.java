package org.estech.gateway.model;

import lombok.Data;

@Data
public class ComputeRequest {
    private String impl;   // service: flux/api/python
    private String path;   // path:/moderation/check
    private String method; // GET, POST, PUT, DELETE
    private Object input;  // request body
}
