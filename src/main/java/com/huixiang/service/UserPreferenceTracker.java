package com.huixiang.service;

import com.huixiang.dto.Result;
import com.huixiang.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Aspect
@Component
public class UserPreferenceTracker {

    @Resource
    private SmartRecommendService smartRecommendService;

    @AfterReturning(pointcut = "execution(* com.huixiang.controller.ShopController.queryShopById(..)) && args(id)", returning = "result")
    public void trackShopView(Long id, Result result) {
        try {
            if (UserHolder.getUser() != null && result != null && result.getSuccess()) {
                smartRecommendService.recordInteraction(UserHolder.getUser().getId(), id);
            }
        } catch (Exception ignored) {
        }
    }

    @AfterReturning(pointcut = "execution(* com.huixiang.controller.VoucherOrderController.seckillVoucher(..)) && args(voucherId,requestId)", returning = "result")
    public void trackPurchase(Long voucherId, String requestId, Result result) {
        try {
            if (UserHolder.getUser() != null) {
                log.debug("user {} purchased voucher {}", UserHolder.getUser().getId(), voucherId);
            }
        } catch (Exception ignored) {
        }
    }
}
