package com.huixiang.service;

import com.huixiang.entity.Shop;
import com.huixiang.entity.Voucher;
import com.huixiang.mapper.VoucherMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SmartRecommendService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IShopService shopService;

    @Resource
    private VoucherMapper voucherMapper;

    private static final String USER_LIKES_PREFIX = "user:likes:";
    private static final String SHOP_LIKEDBY_PREFIX = "shop:likedby:";
    private static final int SIMILAR_USER_COUNT = 10;
    private static final int RECOMMEND_COUNT = 6;

    public void recordInteraction(Long userId, Long shopId) {
        String now = String.valueOf(System.currentTimeMillis());
        String userKey = USER_LIKES_PREFIX + userId;
        String shopKey = SHOP_LIKEDBY_PREFIX + shopId;

        stringRedisTemplate.opsForZSet().add(userKey, shopId.toString(), Double.parseDouble(now));
        stringRedisTemplate.opsForZSet().add(shopKey, userId.toString(), Double.parseDouble(now));

        stringRedisTemplate.expire(userKey, 30, TimeUnit.DAYS);
        stringRedisTemplate.expire(shopKey, 30, TimeUnit.DAYS);
    }

    public List<Shop> recommendShops(Long userId) {
        Set<String> userShopIds = stringRedisTemplate.opsForZSet()
                .reverseRange(USER_LIKES_PREFIX + userId, 0, -1);
        if (userShopIds == null) userShopIds = Collections.emptySet();

        Map<Long, Double> candidateScores = new HashMap<>();

        for (String shopId : userShopIds) {
            Set<String> similarUsers = stringRedisTemplate.opsForZSet()
                    .reverseRange(SHOP_LIKEDBY_PREFIX + shopId, 0, SIMILAR_USER_COUNT);
            if (similarUsers == null) continue;

            for (String suId : similarUsers) {
                long similarUserId = Long.parseLong(suId);
                if (similarUserId == userId) continue;

                Set<String> theirShops = stringRedisTemplate.opsForZSet()
                        .reverseRange(USER_LIKES_PREFIX + similarUserId, 0, 20);
                if (theirShops == null) continue;

                for (String ts : theirShops) {
                    if (userShopIds.contains(ts)) continue;
                    long shopIdL = Long.parseLong(ts);
                    candidateScores.merge(shopIdL, 1.0, Double::sum);
                }
            }
        }

        List<Long> topShopIds = candidateScores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(RECOMMEND_COUNT)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (topShopIds.isEmpty()) {
            List<Shop> all = shopService.lambdaQuery()
                    .orderByDesc(Shop::getScore)
                    .last("LIMIT " + RECOMMEND_COUNT)
                    .list();
            return all != null ? all : Collections.emptyList();
        }

        List<Shop> shops = shopService.lambdaQuery()
                .in(Shop::getId, topShopIds)
                .list();
        if (shops == null) return Collections.emptyList();

        Map<Long, Shop> shopMap = shops.stream()
                .collect(Collectors.toMap(Shop::getId, s -> s));
        List<Shop> result = new ArrayList<>();
        for (Long id : topShopIds) {
            Shop s = shopMap.get(id);
            if (s != null) result.add(s);
        }
        return result;
    }

    public String recommendDeals(Long userId) {
        List<Shop> shops = recommendShops(userId);
        if (shops.isEmpty()) {
            return "暂时没有推荐结果，多浏览一些商户后推荐会更精准";
        }
        StringBuilder sb = new StringBuilder("基于您的浏览偏好为您推荐:\n");
        int i = 1;
        for (Shop s : shops) {
            sb.append(String.format("%d. %s | 评分:%.1f | 均消:%d元\n",
                    i++, s.getName(),
                    s.getScore() != null ? s.getScore() : 0.0,
                    s.getAvgPrice() != null ? s.getAvgPrice() : 0));
            List<Voucher> vouchers = voucherMapper.queryVoucherOfShop(s.getId());
            if (vouchers != null && !vouchers.isEmpty()) {
                for (Voucher v : vouchers) {
                    if (v.getStock() != null && v.getStock() > 0) {
                        sb.append(String.format("   └ 优惠: %s ¥%s→¥%s (库存:%d)\n",
                                v.getTitle(), v.getPayValue(), v.getActualValue(), v.getStock()));
                    }
                }
            }
        }
        return sb.toString();
    }
}
