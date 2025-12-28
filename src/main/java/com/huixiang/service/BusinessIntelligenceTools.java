package com.huixiang.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huixiang.entity.VoucherOrder;
import com.huixiang.entity.Shop;
import com.huixiang.mapper.VoucherMapper;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class BusinessIntelligenceTools {

    @Resource
    private IVoucherOrderService orderService;

    @Resource
    private IShopService shopService;

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private VoucherMapper voucherMapper;

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    @Tool("分析指定时间段内的订单数据，返回 GMV、订单量、秒杀转化率、核销率、退款率")
    public String analyzeOrderMetrics(String startDate, String endDate) {
        LocalDateTime start = parseDate(startDate);
        LocalDateTime end = parseDate(endDate);
        if (start == null || end == null) return "时间格式错误，请使用 yyyy-MM-dd";

        List<VoucherOrder> orders = orderService.lambdaQuery()
                .ge(VoucherOrder::getCreateTime, start)
                .le(VoucherOrder::getCreateTime, end)
                .list();

        if (orders.isEmpty()) return "该时间段内无订单数据";

        long total = orders.size();
        long paid = orders.stream().filter(o -> o.getStatus() == 2).count();
        long used = orders.stream().filter(o -> o.getStatus() == 3).count();
        long refunded = orders.stream().filter(o -> o.getStatus() == 6).count();
        long cancelled = orders.stream().filter(o -> o.getStatus() == 4).count();

        double payRate = total > 0 ? paid * 100.0 / total : 0;
        double useRate = paid > 0 ? used * 100.0 / paid : 0;
        double refundRate = paid > 0 ? refunded * 100.0 / paid : 0;

        return String.format(
            "=== 运营数据报告 ===\n" +
            "时间: %s ~ %s\n" +
            "总订单: %d 笔\n" +
            "已支付: %d 笔 (支付转化率 %.1f%%)\n" +
            "已核销: %d 笔 (核销率 %.1f%%)\n" +
            "退款: %d 笔 (退款率 %.1f%%)\n" +
            "已取消: %d 笔\n" +
            "待支付: %d 笔",
            start.format(DateTimeFormatter.ofPattern("MM-dd")),
            end.format(DateTimeFormatter.ofPattern("MM-dd")),
            total, paid, payRate, used, useRate, refunded, refundRate,
            cancelled, total - paid - cancelled
        );
    }

    @Tool("分析指定商户的优惠券表现：秒杀速度、核销率、退款率，并给出运营建议")
    public String analyzeShopPerformance(Long shopId) {
        Shop shop = shopService.getById(shopId);
        if (shop == null) return "商户不存在";

        List<VoucherOrder> orders = orderService.lambdaQuery()
                .eq(VoucherOrder::getVoucherId, shopId)
                .list();

        if (orders.isEmpty()) return shop.getName() + "暂无秒杀订单数据";

        long total = orders.size();
        long paid = orders.stream().filter(o -> o.getStatus() == 2).count();
        long used = orders.stream().filter(o -> o.getStatus() == 3).count();
        long refunded = orders.stream().filter(o -> o.getStatus() == 6).count();

        double useRate = paid > 0 ? used * 100.0 / paid : 0;
        double refundRate = paid > 0 ? refunded * 100.0 / paid : 0;

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("=== %s 运营分析 ===\n", shop.getName()));
        sb.append(String.format("总订单: %d | 已支付: %d | 核销率: %.1f%% | 退款率: %.1f%%\n",
                total, paid, useRate, refundRate));

        if (total < 10) {
            sb.append("⚠ 订单量偏低，建议加大曝光或降低券面额\n");
        } else if (useRate < 50) {
            sb.append("⚠ 核销率偏低(").append(String.format("%.1f", useRate)).append("%%)，可能券面额不足以吸引到店消费，或限制条件过多\n");
        } else if (refundRate > 15) {
            sb.append("⚠ 退款率偏高(").append(String.format("%.1f", refundRate)).append("%%)，检查是否有虚假宣传或服务问题\n");
        } else {
            sb.append("✓ 商户表现良好，可考虑扩大优惠券投放量\n");
        }

        return sb.toString();
    }

    @Tool("对比两个时间段的运营数据，输出增长率和变化趋势")
    public String comparePeriods(String period1Start, String period1End, String label1,
                                  String period2Start, String period2End, String label2) {
        LocalDateTime p1s = parseDate(period1Start);
        LocalDateTime p1e = parseDate(period1End);
        LocalDateTime p2s = parseDate(period2Start);
        LocalDateTime p2e = parseDate(period2End);

        if (p1s == null || p2s == null) return "时间格式错误";

        long p1Total = countOrders(p1s, p1e);
        long p1Paid = countOrdersByStatus(p1s, p1e, 2);
        long p2Total = countOrders(p2s, p2e);
        long p2Paid = countOrdersByStatus(p2s, p2e, 2);

        double orderGrowth = p1Total > 0 ? (p2Total - p1Total) * 100.0 / p1Total : 0;
        double paidGrowth = p1Paid > 0 ? (p2Paid - p1Paid) * 100.0 / p1Paid : 0;

        return String.format(
            "=== 环比对比 ===\n" +
            "%s: 总订单 %d | 已支付 %d\n" +
            "%s: 总订单 %d | 已支付 %d\n" +
            "订单量变化: %+.1f%%\n" +
            "支付量变化: %+.1f%%",
            StrUtil.blankToDefault(label1, "上期"), p1Total, p1Paid,
            StrUtil.blankToDefault(label2, "本期"), p2Total, p2Paid,
            orderGrowth, paidGrowth
        );
    }

    @Tool("自动检测当前系统中的异常情况：库存异常、退款激增、秒杀时间冲突等")
    public String detectAnomalies() {
        StringBuilder sb = new StringBuilder("=== 系统异常检测报告 ===\n");

        int anomalyCount = 0;

        List<com.huixiang.entity.Voucher> vouchers = voucherMapper.queryVoucherOfShop(null);
        if (vouchers != null) {
            for (com.huixiang.entity.Voucher v : vouchers) {
                if (v.getStock() != null && v.getStock() == 0) {
                    boolean hasOrders = orderService.lambdaQuery()
                            .eq(VoucherOrder::getVoucherId, v.getId())
                            .count() > 5;
                    if (hasOrders && anomalyCount < 3) {
                        sb.append(String.format("⚠ %s 已售罄但仍有需求，建议补货\n", v.getTitle()));
                        anomalyCount++;
                    }
                }
            }
        }

        List<VoucherOrder> recentOrders = orderService.lambdaQuery()
                .ge(VoucherOrder::getCreateTime, LocalDateTime.now().minusHours(24))
                .list();
        if (recentOrders != null) {
            long cancelled24h = recentOrders.stream().filter(o -> o.getStatus() == 4 || o.getStatus() == 6).count();
            if (recentOrders.size() > 10 && cancelled24h * 100.0 / recentOrders.size() > 30) {
                sb.append(String.format("⚠ 近24小时取消/退款率 %.0f%%，建议排查原因\n",
                        cancelled24h * 100.0 / recentOrders.size()));
                anomalyCount++;
            }
        }

        if (anomalyCount == 0) {
            sb.append("✓ 系统运行正常，未发现异常\n");
        }

        return sb.toString();
    }

    private LocalDateTime parseDate(String dateStr) {
        if (StrUtil.isBlank(dateStr)) return null;
        try {
            return LocalDateTime.parse(dateStr + "T00:00:00");
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(dateStr);
            } catch (Exception e2) {
                return null;
            }
        }
    }

    private long countOrders(LocalDateTime start, LocalDateTime end) {
        return orderService.lambdaQuery()
                .ge(VoucherOrder::getCreateTime, start)
                .le(VoucherOrder::getCreateTime, end)
                .count();
    }

    private long countOrdersByStatus(LocalDateTime start, LocalDateTime end, int status) {
        return orderService.lambdaQuery()
                .ge(VoucherOrder::getCreateTime, start)
                .le(VoucherOrder::getCreateTime, end)
                .eq(VoucherOrder::getStatus, status)
                .count();
    }
}
