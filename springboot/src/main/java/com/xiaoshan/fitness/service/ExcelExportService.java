package com.xiaoshan.fitness.service;

import com.xiaoshan.fitness.mapper.StatMapper;
import com.xiaoshan.fitness.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Excel 导出服务（Apache POI SXSSF 流式写出，临时行窗口 500）
 * 导出内容：
 * - 会员明细：users LEFT JOIN 最新会员卡 / 人脸录入标记
 * - 核销记录：entry_records 关联会员手机号 / 卡号 / 卡名称（近 N 天，含失败）
 * - 订单明细：card_orders 关联会员手机号 / 卡名称（近 N 天，含全部状态）
 * 生成完整字节数组后再写响应：失败时响应未被污染，可由全局异常处理器返回统一 JSON。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExcelExportService {

    private final UserMapper userMapper;
    private final StatMapper statMapper;
    private final com.xiaoshan.fitness.mapper.MembershipMapper membershipMapper;

    /**
     * 会员明细导出（全量，按注册时间倒序）
     */
    public byte[] exportMembers() {
        List<Map<String, Object>> list = userMapper.findAll("", 0, 1_000_000);
        List<String> headers = List.of("手机号", "最新会员卡", "剩余次数", "到期时间", "卡状态", "账号状态", "注册时间", "人脸录入");
        List<List<Object>> rows = new ArrayList<>(list.size());
        for (Map<String, Object> r : list) {
            rows.add(List.of(
                    str(r.get("phone")),
                    str(r.get("cardName")),
                    r.get("remainTimes") == null ? "—" : r.get("remainTimes"),
                    str(r.get("expireTime")),
                    cardStatusText(str(r.get("cardStatus"))),
                    Boolean.TRUE.equals(r.get("userStatus")) ? "正常" : "禁用",
                    str(r.get("createTime")),
                    Boolean.TRUE.equals(r.get("faceRegistered")) ? "是" : "否"
            ));
        }
        log.info("[导出] 会员明细 {} 行", rows.size());
        return buildWorkbook("会员明细", headers, rows);
    }

    /**
     * 核销记录导出（近 N 天，含成功与失败）
     */
    public byte[] exportEntries(int days) {
        List<Map<String, Object>> list = statMapper.entriesForExport(days);
        List<String> headers = List.of("会员手机号", "卡号", "卡名称", "核销时间", "核销结果", "失败原因");
        List<List<Object>> rows = new ArrayList<>(list.size());
        for (Map<String, Object> r : list) {
            rows.add(List.of(
                    str(r.get("phone")),
                    str(r.get("cardNo")),
                    str(r.get("cardName")),
                    str(r.get("useTime")),
                    "SUCCESS".equals(r.get("result")) ? "成功" : "失败",
                    str(r.get("failReason"))
            ));
        }
        log.info("[导出] 核销记录 近{}天 {} 行", days, rows.size());
        return buildWorkbook("核销记录", headers, rows);
    }

    /**
     * 订单明细导出（近 N 天，全部状态）
     */
    public byte[] exportOrders(int days) {
        List<Map<String, Object>> list = statMapper.ordersForExport(days);
        List<String> headers = List.of("订单号", "会员手机号", "卡类型", "金额(元)", "订单状态", "创建时间", "支付时间");
        List<List<Object>> rows = new ArrayList<>(list.size());
        for (Map<String, Object> r : list) {
            rows.add(List.of(
                    str(r.get("orderNo")),
                    str(r.get("phone")),
                    str(r.get("cardName")),
                    r.get("amount") == null ? "" : r.get("amount"),
                    orderStatusText(str(r.get("status"))),
                    str(r.get("createTime")),
                    str(r.get("payTime"))
            ));
        }
        log.info("[导出] 订单明细 近{}天 {} 行", days, rows.size());
        return buildWorkbook("订单明细", headers, rows);
    }

    /**
     * 会员卡明细导出（支持筛选，全量，按办理时间倒序）
     * phone 与 cardNo 为 OR 关系，与列表查询同口径
     */
    public byte[] exportMemberships(String status, Long storeId, Long cardTypeId, String phone, String cardNo) {
        List<Map<String, Object>> list = membershipMapper.findAll(status, storeId, cardTypeId, phone, cardNo, 0, 1_000_000);
        List<String> headers = List.of("卡号", "手机号", "卡类型", "分类", "适用范围", "门店",
                "总次数", "剩余次数", "状态", "生效时间", "到期时间", "办理时间");
        List<List<Object>> rows = new ArrayList<>(list.size());
        for (Map<String, Object> r : list) {
            rows.add(List.of(
                    str(r.get("cardNo")),
                    str(r.get("phone")),
                    str(r.get("cardTypeName")),
                    "PT".equals(str(r.get("category"))) ? "私教课卡" : "普通卡",
                    "ALL_STORE".equals(str(r.get("scope"))) ? "全店通用" : str(r.get("storeName")),
                    str(r.get("storeName")),
                    r.get("totalTimes") == null ? "—" : r.get("totalTimes"),
                    r.get("remainingTimes") == null ? "—" : r.get("remainingTimes"),
                    membershipStatusText(str(r.get("status"))),
                    str(r.get("startTime")),
                    str(r.get("endTime")),
                    str(r.get("createdAt"))
            ));
        }
        log.info("[导出] 会员卡明细 {} 行", rows.size());
        return buildWorkbook("会员卡明细", headers, rows);
    }

    // ==================== 工作簿构建 ====================

    private byte[] buildWorkbook(String sheetName, List<String> headers, List<List<Object>> rows) {
        SXSSFWorkbook wb = new SXSSFWorkbook(500);
        try {
            Sheet sheet = wb.createSheet(sheetName);

            // 表头样式：加粗 + 灰底
            CellStyle headStyle = wb.createCellStyle();
            Font headFont = wb.createFont();
            headFont.setBold(true);
            headStyle.setFont(headFont);
            headStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row head = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell c = head.createCell(i);
                c.setCellValue(headers.get(i));
                c.setCellStyle(headStyle);
                sheet.setColumnWidth(i, 20 * 256);
            }

            int r = 1;
            for (List<Object> row : rows) {
                Row dataRow = sheet.createRow(r++);
                for (int i = 0; i < row.size(); i++) {
                    Cell c = dataRow.createCell(i);
                    Object v = row.get(i);
                    if (v == null) {
                        c.setCellValue("");
                    } else if (v instanceof Number n) {
                        c.setCellValue(n.doubleValue());
                    } else {
                        c.setCellValue(String.valueOf(v));
                    }
                }
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wb.write(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            log.error("[导出] Excel 生成失败：{}", sheetName, e);
            throw new IllegalStateException("Excel 导出失败，请稍后重试", e);
        } finally {
            wb.dispose();
            try {
                wb.close();
            } catch (IOException ignore) {
                // 关闭资源失败不影响已生成结果
            }
        }
    }

    private String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private String cardStatusText(String status) {
        if (status == null) return "无卡";
        return switch (status) {
            case "UNACTIVATED" -> "未激活";
            case "ACTIVE" -> "有效";
            case "EXPIRED" -> "已过期";
            case "DISABLED" -> "已停用";
            default -> status;
        };
    }

    private String orderStatusText(String status) {
        if (status == null) return "";
        return switch (status) {
            case "PENDING" -> "待支付";
            case "PAID" -> "已支付";
            case "CANCELLED" -> "已取消";
            default -> status;
        };
    }

    private String membershipStatusText(String status) {
        if (status == null) return "";
        return switch (status) {
            case "UNACTIVATED" -> "未激活";
            case "ACTIVE" -> "生效中";
            case "EXPIRED" -> "已过期";
            case "DISABLED" -> "已停用";
            default -> status;
        };
    }

}
