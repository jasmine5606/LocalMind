package com.huixiang.dto;

import lombok.Data;

@Data
public class UserDTO {
    private Long id;
    private String nickName;
    private String icon;
    private Integer memberLevel;

    public boolean isVip() {
        return memberLevel != null && memberLevel > 0;
    }
}
