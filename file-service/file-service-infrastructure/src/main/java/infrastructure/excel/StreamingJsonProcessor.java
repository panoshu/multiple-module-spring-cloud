// package infrastructure.excel;
//
// import com.fasterxml.jackson.core.JsonFactory;
// import com.fasterxml.jackson.core.JsonParser;
// import com.fasterxml.jackson.core.JsonToken;
// import com.fasterxml.jackson.databind.ObjectMapper;
//
// import java.io.InputStream;
//
// public class StreamingJsonProcessor {
//
//  private static final ObjectMapper mapper = new ObjectMapper();
//
//  public void processOssJsonStream(InputStream ossJsonStream) throws Exception {
//    JsonFactory factory = mapper.getFactory();
//    try (JsonParser parser = factory.createParser(ossJsonStream)) {
//
//      HeaderDTO sharedHeader = null;
//
//      // 1. 遍历 JSON 的最外层 Token
//      while (parser.nextToken() != JsonToken.END_OBJECT) {
//        String fieldName = parser.getCurrentName();
//
//        // 2. 遇到 header 节点，直接将其作为一个小树读取出来
//        if ("header".equals(fieldName)) {
//          parser.nextToken(); // 进入 header 对象
//          sharedHeader = mapper.readValue(parser, HeaderDTO.class);
//        }
//
//        // 3. 遇到 details 数组节点，开始【真正的流式处理】
//        if ("details".equals(fieldName)) {
//          parser.nextToken(); // 移动到数组的开始 '['
//
//          // 只要没到数组的末尾 ']'，就持续循环
//          while (parser.nextToken() != JsonToken.END_ARRAY) {
//            // 读取单条明细（此时内存里只有这一条数据）
//            DetailDTO detail = mapper.readValue(parser, DetailDTO.class);
//
//            // ===== 这里就是 DDD 的防腐工厂转换 =====
//            ReceiptAggregate root = new ReceiptAggregate(sharedHeader, detail);
//
//            // 执行业务逻辑（校验、落库等）
//            // 注意：如果想提高吞吐，不要逐条 Insert，可以搞一个 List 攒满 500 条就批量 Insert DB 并 clear()。
//            processAggregate(root);
//          }
//        }
//      }
//    }
//  }
//}
