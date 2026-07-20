package com.example.file.domain.model.aggregate.valueobject;

import com.example.shared.domain.aggregate.valueobject.ValueObject;
import com.example.shared.primitives.identity.CustomerNo;
import com.example.shared.primitives.identity.ProductNo;
import com.example.shared.primitives.identity.UserNo;

/**
 * 会话用户值对象（从 HTTP Header 提取）
 */
public record SessionUser(UserNo userNo, CustomerNo customerNo, ProductNo productNo) implements ValueObject {
    public SessionUser {
        if (userNo == null) throw new IllegalArgumentException("userNo 不能为空");
        if (customerNo == null) throw new IllegalArgumentException("customerNo 不能为空");
        if (productNo == null) throw new IllegalArgumentException("productNo 不能为空");
    }
}
