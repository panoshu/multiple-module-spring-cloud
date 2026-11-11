package com.example.core.infrastructure.engine.event;

import com.example.core.infrastructure.business.entity.EventReceiptDO;
import com.example.core.infrastructure.business.mapper.EventReceiptMapper;
import com.example.shared.identifier.contract.IdService;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JdbcEventReceiptGateway 事件消费回执")
class JdbcEventReceiptGatewayTest {

  @Mock
  private EventReceiptMapper mapper;
  @Mock
  private IdService idService;

  private JdbcEventReceiptGateway gateway;

  @BeforeEach
  void setUp() {
    gateway = new JdbcEventReceiptGateway(mapper, idService);
  }

  @Test
  @DisplayName("首次消费登记回执并返回 true")
  void tryConsume_firstTimeRegistersReceipt() {
    when(mapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
    when(idService.nextLongId(anyString())).thenReturn(1001L);

    assertThat(gateway.tryConsume("evt-1", "FileParsedEventListener")).isTrue();

    ArgumentCaptor<EventReceiptDO> captor = ArgumentCaptor.forClass(EventReceiptDO.class);
    verify(mapper).insert(captor.capture());
    EventReceiptDO saved = captor.getValue();
    assertThat(saved.getId()).isEqualTo(1001L);
    assertThat(saved.getEventId()).isEqualTo("evt-1");
    assertThat(saved.getConsumer()).isEqualTo("FileParsedEventListener");
    assertThat(saved.getStatus()).isEqualTo("SUCCESS");
    assertThat(saved.getCreateTime()).isNotNull();
    assertThat(saved.getDeleted()).isFalse();
  }

  @Test
  @DisplayName("已存在回执时短路且不再插入")
  void tryConsume_duplicateShortCircuits() {
    when(mapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(new EventReceiptDO());

    assertThat(gateway.tryConsume("evt-1", "C")).isFalse();

    verify(mapper, never()).insert(any());
  }

  @Test
  @DisplayName("并发插入撞唯一键时短路")
  void tryConsume_uniqueKeyRaceShortCircuits() {
    when(mapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
    when(idService.nextLongId(anyString())).thenReturn(1002L);
    doThrow(new DuplicateKeyException("dup")).when(mapper).insert(any(EventReceiptDO.class));

    assertThat(gateway.tryConsume("evt-1", "C")).isFalse();
  }

  @Test
  @DisplayName("缺失 eventId 或 consumer 时保守放行且不查库")
  void tryConsume_blankIdentifiersPassThrough() {
    assertThat(gateway.tryConsume(null, "C")).isTrue();
    assertThat(gateway.tryConsume("evt-1", "  ")).isTrue();

    verifyNoInteractions(mapper);
  }
}
