package com.huixiang.service;

import cn.hutool.core.util.StrUtil;
import com.huixiang.entity.Shop;
import com.huixiang.entity.VoucherOrder;
import com.huixiang.mapper.VoucherMapper;
import com.huixiang.utils.UserHolder;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Component
public class AgentTools {

    @Resource
    private IShopService shopService;

    @Resource
    private IVoucherOrderService voucherOrderService;

    @Resource
    private VoucherMapper voucherMapper;

    @Resource
    private SmartRecommendService smartRecommendService;

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Tool("搜索附近商户，返回名称、评分、地址、优惠信息")
    public String searchNearbyShops(String keyword) {
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

    @Tool("查询当前用户最近的订单，可指定状态")
    public String queryUserOrders(Integer status) {
        if (UserHolder.getUser() == null) return "请先登录后再查询订单";
        Long userId = UserHolder.getUser().getId();
        int limit = 5;
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

    @Tool("根据用户偏好智能推荐商户和优惠套餐")
    public String recommendDeals(String scene) {
        if (UserHolder.getUser() != null) {
            return smartRecommendService.recommendDeals(UserHolder.getUser().getId());
        }
        return "请登录后获取个性化推荐";
    }

    @Tool("查询指定名称的优惠券库存和秒杀时间")
    public String checkVoucherStock(String title) {
        if (StrUtil.isBlank(title)) return "请提供优惠券名称";
        List<com.huixiang.entity.Voucher> vouchers = voucherMapper.queryVoucherOfShop(null);
        if (vouchers == null || vouchers.isEmpty()) return "未找到相关优惠券信息";
        for (com.huixiang.entity.Voucher v : vouchers) {
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
