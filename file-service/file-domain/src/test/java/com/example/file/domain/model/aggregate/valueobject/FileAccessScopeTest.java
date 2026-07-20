package com.example.file.domain.model.aggregate.valueobject;

import com.example.shared.primitives.identity.CustomerNo;
import com.example.shared.primitives.identity.ProductNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("FileAccessScope 值对象")
class FileAccessScopeTest {

    @Test
    @DisplayName("合法参数创建成功")
    void should_create_with_valid_params() {
        FileAccessScope scope = new FileAccessScope(CustomerNo.of("C001"), ProductNo.of("P001"));
        assertThat(scope.customerNo()).isEqualTo(CustomerNo.of("C001"));
        assertThat(scope.productNo()).isEqualTo(ProductNo.of("P001"));
    }

    @Test
    @DisplayName("customerNo 为 null 抛异常")
    void should_throw_when_customerNo_null() {
        assertThatThrownBy(() -> new FileAccessScope(null, ProductNo.of("P001")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("productNo 为 null 抛异常")
    void should_throw_when_productNo_null() {
        assertThatThrownBy(() -> new FileAccessScope(CustomerNo.of("C001"), null))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
