package infrastructure.excel;

/**
 * Excel 坐标与下标转换工具
 */
public final class CoordinateUtils {

  private CoordinateUtils() {
  }

  /**
   * 将列字母转换为 0-based 索引 (如 "A" -> 0, "B" -> 1, "AA" -> 26)
   */
  public static int colNameToIndex(String colName) {
    String cleanCol = colName.replaceAll("[0-9]", "").toUpperCase();
    int index = 0;
    for (int i = 0; i < cleanCol.length(); i++) {
      index = index * 26 + (cleanCol.charAt(i) - 'A' + 1);
    }
    return index - 1;
  }

  /**
   * 从坐标中提取 0-based 行索引 (如 "B3" -> 2)
   */
  public static int getRowIndex(String cellCoordinate) {
    String rowStr = cellCoordinate.replaceAll("[a-zA-Z]", "");
    return Integer.parseInt(rowStr) - 1; // 业务配置是 1-based，转为 0-based
  }

  /**
   * 从坐标中提取 0-based 列索引 (如 "B3" -> 1)
   */
  public static int getColIndex(String cellCoordinate) {
    return colNameToIndex(cellCoordinate);
  }
}
