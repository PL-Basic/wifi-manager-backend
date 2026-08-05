package com.plagod.dto.user;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import java.util.List;

@Data
public class UserRoleBatchRequest {

    @NotEmpty(message = "用户ID列表不能为空")
    @Size(max = 100, message = "单次最多查询100个用户")
    private List<Long> userIds;
}
