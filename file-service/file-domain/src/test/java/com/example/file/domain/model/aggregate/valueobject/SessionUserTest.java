package com.example.file.domain.model.aggregate.valueobject;

import com.example.shared.primitives.identity.CustomerNo;
import com.example.shared.primitives.identity.ProductNo;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SessionUser 值对象")
class SessionUserTest {

    @Test
    @DisplayName("合法参数创建成功")
    void should_create_with_valid_params() {
        SessionUser user = new SessionUser(UserNo.of("u1"), CustomerNo.of("C001"), ProductNo.of("P001"));
        assertThat(user.userNo()).isEqualTo(UserNo.of("u1"));
        assertThat(user.customerNo()).isEqualTo(CustomerNo.of("C001"));
        assertThat(user.productNo()).isEqualTo(ProductNo.of("P001"));
    }

    @Test
    @DisplayName("userNo 为 null 抛异常")
    void should_throw_when_userNo_null() {
        assertThatThrownBy(() -> new SessionUser(null, CustomerNo.of("C001"), ProductNo.of("P001")))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
