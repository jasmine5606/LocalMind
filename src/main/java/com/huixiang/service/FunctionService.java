package com.huixiang.service;

import cn.hutool.core.util.StrUtil;
import com.alibaba.dashscope.tools.FunctionDefinition;
import com.alibaba.dashscope.tools.ToolBase;
import com.alibaba.dashscope.tools.ToolFunction;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.google.gson.JsonParser;
import com.huixiang.entity.Shop;
import com.huixiang.entity.Voucher;
import com.huixiang.entity.VoucherOrder;
import com.huixiang.mapper.VoucherMapper;
import com.huixiang.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class FunctionService {

    @Resource
    private IShopService shopService;

    @Resource
    private IVoucherOrderService voucherOrderService;

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private VoucherMapper voucherMapper;

    @Resource
    private SmartRecommendService smartRecommendService;

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public List<ToolBase> getFunctions() {
        List<ToolBase> functions = new ArrayList<>();
        functions.add(createFunction("searchNearbyShops",
                "搜索附近商户，返回名称、评分、地址",
                "keyword", "string", "搜索关键词或品类"));
        functions.add(createFunction("queryUserOrders",
                "查询用户最近的订单",
                "status", "integer", "订单状态:1未支付/2已支付/4已取消"));
        functions.add(createFunction("recommendDeals",
                "智能推荐优惠套餐",
                "scene", "string", "场景:dining/beauty/leisure"));
        functions.add(createFunction("checkVoucherStock",
                "查询优惠券库存和秒杀时间", null, null, null));
        return functions;
    }

    private ToolBase createFunction(String name, String desc, String paramName, String paramType, String paramDesc) {
        FunctionDefinition def = FunctionDefinition.builder()
                .name(name)
                .description(desc)
                .build();
        if (paramName != null) {
            String json = String.format(
                    "{\"type\":\"object\",\"properties\":{\"%s\":{\"type\":\"%s\",\"description\":\"%s\"}}}",
                    paramName, paramType, paramDesc);
            def.setParameters(JsonParser.parseString(json).getAsJsonObject());
        } else {
            def.setParameters(JsonParser.parseString("{\"type\":\"object\",\"properties\":{}}").getAsJsonObject());
        }
        return ToolFunction.builder().function(def).build();
    }

    public String executeFunction(String functionName, String arguments) {
        try {
            JSONObject args = JSON.parseObject(arguments);
            switch (functionName) {
                case "searchNearbyShops": return searchNearbyShops(args);
                case "queryUserOrders": return queryUserOrders(args);
                case "recommendDeals": return recommendDeals(args);
                case "checkVoucherStock": return checkVoucherStock(args);
                default: return "未知功能: " + functionName;
            }
        } catch (Exception e) {
            log.error("executeFunction error: {}", functionName, e);
            return "执行功能时出错: " + e.getMessage();
        }
    }

    private String searchNearbyShops(JSONObject args) {
        String keyword = args.getString("keyword");
        if (StrUtil.isBlank(keyword)) return "请提供搜索关键词";
        List<Shop> shops = shopService.lambdaQuery()
                .like(Shop::getName, keyword).or()
                .like(Shop::getTypeId, keyword)
                .last("LIMIT 10").list();
        if (shops.isEmpty()) return "未找到与\"" + keyword + "\"相关的商户";
        StringBuilder sb = new StringBuilder("找到以下商户:\n");
        for (Shop s : shops) {
            sb.append(String.format("- %s | 评分:%.1f | 均消:%d | %s\n",
                    s.getName(),
                    s.getScore() != null ? s.getScore() : 0.0,
                    s.getAvgPrice() != null ? s.getAvgPrice() : 0,
                    s.getAddress() != null ? s.getAddress() : ""));
        }
        return sb.toString();
    }

    private String queryUserOrders(JSONObject args) {
        if (UserHolder.getUser() == null) return "请先登录后再查询订单";
        Long userId = UserHolder.getUser().getId();
        Integer status = args.getInteger("status");
        Integer limit = args.getInteger("limit");
        if (limit == null || limit <= 0) limit = 5;
        var query = voucherOrderService.lambdaQuery().eq(VoucherOrder::getUserId, userId);
        if (status != null) query.eq(VoucherOrder::getStatus, status);
        List<VoucherOrder> orders = query.orderByDesc(VoucherOrder::getCreateTime).last("LIMIT " + limit).list();
        if (orders.isEmpty()) return "暂无订单记录";
        StringBuilder sb = new StringBuilder("您的最近订单:\n");
        String[] statusNames = {"", "未支付", "已支付", "已核销", "已取消", "退款中", "已退款"};
        for (VoucherOrder o : orders) {
            String st = o.getStatus() != null && o.getStatus() < statusNames.length ? statusNames[o.getStatus()] : "未知";
            sb.append(String.format("- 订单#%d | %s | %s\n",
                    o.getId(), st, o.getCreateTime() != null ? o.getCreateTime().format(DT_FMT) : ""));
        }
        return sb.toString();
    }

    private String recommendDeals(JSONObject args) {
        if (UserHolder.getUser() != null) {
            return smartRecommendService.recommendDeals(UserHolder.getUser().getId());
        }
        return "请登录后获取个性化推荐";
    }

    private String checkVoucherStock(JSONObject args) {
        String title = args.getString("voucherTitle");
        if (StrUtil.isBlank(title)) return "请提供优惠券名称";
        List<Voucher> vouchers = voucherMapper.queryVoucherOfShop(null);
        if (vouchers == null || vouchers.isEmpty()) return "未找到相关优惠券信息";
        for (Voucher v : vouchers) {
            if (v.getTitle() != null && v.getTitle().contains(title)) {
                return String.format("优惠券: %s\n库存: %s\n开始时间: %s\n结束时间: %s",
                        v.getTitle(),
                        v.getStock() != null ? v.getStock() : "未知",
                        v.getBeginTime() != null ? v.getBeginTime().format(DT_FMT) : "未知",
                        v.getEndTime() != null ? v.getEndTime().format(DT_FMT) : "未知");
            }
        }
        return "未找到包含\"" + title + "\"的优惠券";
    }
}
