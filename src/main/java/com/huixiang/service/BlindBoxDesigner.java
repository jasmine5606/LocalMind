package com.huixiang.service;

import cn.hutool.core.util.StrUtil;
import com.huixiang.entity.Voucher;
import com.huixiang.mapper.VoucherMapper;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class BlindBoxDesigner {

    @Resource
    private VoucherMapper voucherMapper;

    @Resource
    private IBlindBoxService blindBoxService;

    @Resource
    private BusinessIntelligenceTools biTools;

    private final Map<String, BlindBoxPlan> pendingPlans = new HashMap<>();

    static class BlindBoxPlan {
        String planId;
        String title;
        String description;
        Long price;
        List<Long> voucherIds = new ArrayList<>();
        List<Integer> quantities = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        String rationale;
    }

    @Tool("根据自然语言需求设计盲盒活动方案。分析历史数据，自动推荐优惠券组合和概率分配，返回方案供人工确认")
    public String designBlindBox(String requirement) {
        String planId = UUID.randomUUID().toString().substring(0, 8);

        List<Voucher> allVouchers = voucherMapper.queryVoucherOfShop(null);
        if (allVouchers == null || allVouchers.isEmpty()) {
            return "当前没有可用的优惠券，请先创建优惠券";
        }

        List<Voucher> available = allVouchers.stream()
                .filter(v -> v.getStock() != null && v.getStock() > 0)
                .filter(v -> v.getPayValue() != null && v.getActualValue() != null)
                .collect(Collectors.toList());
        if (available.isEmpty()) return "当前没有库存充足的优惠券";

        String keyword = extractKeyword(requirement);

        List<Voucher> filtered = available.stream()
                .filter(v -> keyword.isEmpty() ||
                        (v.getTitle() != null && v.getTitle().contains(keyword)))
                .collect(Collectors.toList());
        if (filtered.isEmpty()) filtered = available;

        filtered.sort((a, b) -> Long.compare(b.getActualValue(), a.getActualValue()));

        int count = Math.min(filtered.size(), 4);
        filtered = filtered.subList(0, count);

        BlindBoxPlan plan = new BlindBoxPlan();
        plan.planId = planId;
        plan.title = generateTitle(requirement);
        plan.description = requirement;
        plan.rationale = buildRationale(filtered, requirement);

        long maxActual = filtered.stream().mapToLong(Voucher::getActualValue).max().orElse(100);
        long minActual = filtered.stream().mapToLong(Voucher::getActualValue).min().orElse(30);

        if (plan.price == null) {
            plan.price = Math.max(minActual / 2, 30);
            plan.price = Math.min(plan.price, maxActual - 10);
        }

        int totalStock = Math.min(
                filtered.stream().mapToInt(v -> v.getStock() != null ? v.getStock() : 20).sum(),
                100
        );

        for (int i = 0; i < filtered.size(); i++) {
            Voucher v = filtered.get(i);
            long value = v.getActualValue();
            long maxV = maxActual;

            int weight;
            int qty;
            if (value >= maxV * 0.9) {
                weight = 10;
                qty = Math.min(v.getStock() != null ? v.getStock() : 5, (int)(totalStock * 0.10));
            } else if (value >= maxV * 0.6) {
                weight = 30;
                qty = Math.min(v.getStock() != null ? v.getStock() : 15, (int)(totalStock * 0.30));
            } else if (value >= maxV * 0.3) {
                weight = 40;
                qty = Math.min(v.getStock() != null ? v.getStock() : 25, (int)(totalStock * 0.40));
            } else {
                weight = 20;
                qty = Math.min(v.getStock() != null ? v.getStock() : 30, (int)(totalStock * 0.20));
            }
            if (qty <= 0) qty = 5;
            plan.voucherIds.add(v.getId());
            plan.quantities.add(qty);
            plan.weights.add(weight);
        }

        pendingPlans.put(planId, plan);

        return buildPlanReport(plan);
    }

    @Tool("确认并执行盲盒创建方案。传入 planId 即可执行之前 designBlindBox 返回的方案")
    public String executeBlindBoxPlan(String planId) {
        BlindBoxPlan plan = pendingPlans.remove(planId);
        if (plan == null) return "方案不存在或已过期，请重新设计";

        try {
            com.huixiang.entity.BlindBox blindBox = new com.huixiang.entity.BlindBox();
            blindBox.setTitle(plan.title);
            blindBox.setDescription(plan.description);
            blindBox.setPrice(plan.price);
            blindBox.setTotalStock(plan.quantities.stream().mapToInt(Integer::intValue).sum());
            blindBox.setBeginTime(java.time.LocalDateTime.now().plusHours(1));
            blindBox.setEndTime(java.time.LocalDateTime.now().plusDays(7));
            blindBox.setStatus(1);

            var result = blindBoxService.createBlindBox(
                    blindBox, plan.voucherIds, plan.quantities, plan.weights);
            if (result.getSuccess()) {
                return String.format("✓ 盲盒活动创建成功！\n" +
                    "活动ID: %s\n标题: %s\n价格: ¥%.2f\n" +
                    "开始时间: %s\n结束时间: %s\n" +
                    "总库存: %d 份\n奖品数: %d 种",
                    result.getData(), plan.title, plan.price / 100.0,
                    blindBox.getBeginTime().toLocalDate(),
                    blindBox.getEndTime().toLocalDate(),
                    blindBox.getTotalStock(), plan.voucherIds.size());
            }
            return "创建失败: " + result.getErrorMsg();
        } catch (Exception e) {
            log.error("execute plan error", e);
            return "创建失败: " + e.getMessage();
        }
    }

    private String extractKeyword(String requirement) {
        for (String kw : Arrays.asList("火锅","烧烤","日料","咖啡","茶饮","美发","按摩","KTV","自助","烤肉")) {
            if (requirement.contains(kw)) return kw;
        }
        return "";
    }

    private String generateTitle(String requirement) {
        String keyword = extractKeyword(requirement);
        if (!keyword.isEmpty()) return "惊喜盲盒 — " + keyword + "专场";
        return "惊喜盲盒 — 超值随机抽";
    }

    private String buildRationale(List<Voucher> vouchers, String requirement) {
        StringBuilder sb = new StringBuilder("方案设计依据:\n");
        long totalActual = vouchers.stream().mapToLong(v -> v.getActualValue() != null ? v.getActualValue() : 0).sum();
        long avgActual = vouchers.isEmpty() ? 0 : totalActual / vouchers.size();

        int highCount = (int) vouchers.stream()
                .filter(v -> v.getActualValue() != null && v.getActualValue() >= avgActual * 1.5)
                .count();
        sb.append(String.format("- 筛选了 %d 种优惠券，面额范围 ¥%.0f ~ ¥%.0f\n",
                vouchers.size(),
                vouchers.stream().mapToLong(Voucher::getActualValue).min().orElse(0) / 100.0,
                vouchers.stream().mapToLong(Voucher::getActualValue).max().orElse(0) / 100.0));
        sb.append(String.format("- 高端券(¥%.0f+)占 10%% 概率，保证稀缺性和吸引力\n", avgActual * 1.5 / 100.0));
        sb.append(String.format("- 中端券占比最大(30-40%%)，保证多数用户抽到超值回报\n"));
        sb.append("- 建议盲盒定价为最低面额的50-70%，确保大部分用户'赚到'\n");
        return sb.toString();
    }

    private String buildPlanReport(BlindBoxPlan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 盲盒设计方案 ===\n\n");
        sb.append(String.format("标题: %s\n", plan.title));
        sb.append(String.format("价格: ¥%.2f\n\n", plan.price / 100.0));

        sb.append("奖品池:\n");
        int totalWeight = plan.weights.stream().mapToInt(Integer::intValue).sum();
        for (int i = 0; i < plan.voucherIds.size(); i++) {
            double prob = totalWeight > 0
                    ? plan.weights.get(i) * 100.0 / totalWeight
                    : 0;
            sb.append(String.format("  [券#%d] 数量: %d | 概率: %.1f%% | 权重: %d\n",
                    plan.voucherIds.get(i), plan.quantities.get(i),
                    prob, plan.weights.get(i)));
        }

        sb.append("\n").append(plan.rationale);
        sb.append(String.format("\n方案ID: %s\n", plan.planId));
        sb.append("确认创建请说'执行方案 " + plan.planId + "'");
        return sb.toString();
    }
}
