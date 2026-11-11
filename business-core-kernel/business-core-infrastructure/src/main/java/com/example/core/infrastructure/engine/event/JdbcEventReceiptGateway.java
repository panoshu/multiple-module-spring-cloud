package com.example.core.infrastructure.engine.event;

import com.example.core.domain.engine.gateway.EventReceiptGateway;
import com.example.core.infrastructure.business.entity.EventReceiptDO;
import com.example.core.infrastructure.business.mapper.EventReceiptMapper;
import com.example.shared.identifier.contract.IdService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

import static com.example.core.infrastructure.business.entity.table.EventReceiptDOTableDef.EVENT_RECEIPT_DO;

/**
 * 基于唯一索引的事件消费回执实现（spec §7.4）。
 * <p>
 * 先查后插，并以 {@code (event_id, consumer)} 唯一索引兜住并发插入：撞键时按重复事件短路。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JdbcEventReceiptGateway implements EventReceiptGateway {

  private static final String STATUS_SUCCESS = "SUCCESS";

  private final EventReceiptMapper mapper;
  private final IdService idService;

  @Override
  public boolean tryConsume(String eventId, String consumer) {
    if (!hasText(eventId) || !hasText(consumer)) {
      // 缺少判重标识时保守放行，避免阻断无法识别的事件
      return true;
    }
    EventReceiptDO existing = mapper.selectOneByQuery(QueryWrapper.create()
      .where(EVENT_RECEIPT_DO.EVENT_ID.eq(eventId))
      .and(EVENT_RECEIPT_DO.CONSUMER.eq(consumer)));
    if (existing != null) {
      return false;
    }
    try {
      mapper.insert(newReceipt(eventId, consumer));
      return true;
    } catch (DuplicateKeyException e) {
      log.debug("事件消费回执并发撞键，按重复事件短路: eventId={}, consumer={}", eventId, consumer);
      return false;
    }
  }

  private EventReceiptDO newReceipt(String eventId, String consumer) {
    LocalDateTime now = LocalDateTime.now();
    EventReceiptDO aDo = new EventReceiptDO();
    aDo.setId(idService.nextLongId("EVENT_RECEIPT"));
    aDo.setEventId(eventId);
    aDo.setConsumer(consumer);
    aDo.setStatus(STATUS_SUCCESS);
    aDo.setCreatedBy("SYSTEM");
    aDo.setCreateTime(now);
    aDo.setUpdateTime(now);
    aDo.setDeleted(false);
    aDo.setVersion(0);
    return aDo;
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
