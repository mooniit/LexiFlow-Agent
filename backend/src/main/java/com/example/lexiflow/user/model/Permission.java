package com.example.lexiflow.user.model;

import com.baomidou.mybatisplus.annotation.TableName;
import com.example.lexiflow.common.model.AuditFields;

@TableName("permission")
public class Permission extends AuditFields {

    private Long id;
    private String code;
    private String name;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
