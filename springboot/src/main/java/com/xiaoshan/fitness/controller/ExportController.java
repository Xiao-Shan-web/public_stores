package com.xiaoshan.fitness.controller;

import com.xiaoshan.fitness.service.ExcelExportService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Excel 导出接口（管理端）
 * GET /api/v1/admin/export/members          会员明细（全量）
 * GET /api/v1/admin/export/entries?days=30  核销记录（近 N 天，含失败）
 * GET /api/v1/admin/export/orders?days=90   订单明细（近 N 天，全部状态）
 * <p>
 * Content-Disposition 同时提供 ASCII filename 与 RFC 5987 filename*（中文文件名）。
 * 响应体为二进制 xlsx；出错时响应尚未提交，由全局异常处理器返回统一 JSON。
 */
@RestController
@RequestMapping("/api/v1/admin/export")
@RequiredArgsConstructor
@Slf4j
public class ExportController {

    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ExcelExportService excelExportService;

    @GetMapping("/members")
    public void exportMembers(HttpServletResponse response) throws IOException {
        write(response, "会员明细", "members", excelExportService.exportMembers());
    }

    @GetMapping("/entries")
    public void exportEntries(HttpServletResponse response,
                              @RequestParam(defaultValue = "30") int days) throws IOException {
        write(response, "核销记录", "entries", excelExportService.exportEntries(clampDays(days)));
    }

    @GetMapping("/orders")
    public void exportOrders(HttpServletResponse response,
                             @RequestParam(defaultValue = "90") int days) throws IOException {
        write(response, "订单明细", "orders", excelExportService.exportOrders(clampDays(days)));
    }

    /**
     * 会员卡明细导出（支持筛选，全量，按办理时间倒序）
     * GET /api/v1/admin/export/memberships?status=&storeId=&cardTypeId=&phone=&cardNo=
     * phone 与 cardNo 为 OR 关系，与列表查询同口径
     */
    @GetMapping("/memberships")
    public void exportMemberships(HttpServletResponse response,
                                  @RequestParam(required = false) String status,
                                  @RequestParam(required = false) Long storeId,
                                  @RequestParam(required = false) Long cardTypeId,
                                  @RequestParam(required = false) String phone,
                                  @RequestParam(required = false) String cardNo) throws IOException {
        write(response, "会员卡明细", "memberships",
                excelExportService.exportMemberships(status, storeId, cardTypeId, phone, cardNo));
    }

    // ==================== 私有工具 ====================

    private void write(HttpServletResponse response, String cnName, String fileStem, byte[] bytes)
            throws IOException {
        // RFC 5987：中文文件名用 filename* 传输，ASCII 兜底用英文 stem
        String encoded = URLEncoder.encode(cnName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType(XLSX_CONTENT_TYPE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + fileStem + ".xlsx\"; filename*=UTF-8''" + encoded + ".xlsx");
        response.setContentLength(bytes.length);
        response.getOutputStream().write(bytes);
        response.getOutputStream().flush();
    }

    private int clampDays(int days) {
        if (days < 1) return 1;
        return Math.min(days, 3650);
    }

}
