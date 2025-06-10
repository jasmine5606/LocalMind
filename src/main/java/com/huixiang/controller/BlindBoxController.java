package com.huixiang.controller;

import com.huixiang.annotation.RateLimiter;
import com.huixiang.dto.Result;
import com.huixiang.entity.BlindBox;
import com.huixiang.service.IBlindBoxService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/blind-box")
public class BlindBoxController {

    @Resource
    private IBlindBoxService blindBoxService;

    @PostMapping("/create")
    public Result createBlindBox(@RequestBody Map<String, Object> body) {
        BlindBox blindBox = new BlindBox();
        blindBox.setTitle((String) body.get("title"));
        blindBox.setDescription((String) body.get("description"));
        blindBox.setPrice(Long.valueOf(body.get("price").toString()));
        blindBox.setTotalStock((Integer) body.get("totalStock"));
        blindBox.setBeginTime(java.time.LocalDateTime.parse((String) body.get("beginTime")));
        blindBox.setEndTime(java.time.LocalDateTime.parse((String) body.get("endTime")));
        blindBox.setStatus(1);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> prizes = (List<Map<String, Object>>) body.get("prizes");
        List<Long> voucherIds = new java.util.ArrayList<>();
        List<Integer> quantities = new java.util.ArrayList<>();
        List<Integer> weights = new java.util.ArrayList<>();
        for (Map<String, Object> p : prizes) {
            voucherIds.add(Long.valueOf(p.get("voucherId").toString()));
            quantities.add((Integer) p.get("quantity"));
            weights.add((Integer) p.get("weight"));
        }
        return blindBoxService.createBlindBox(blindBox, voucherIds, quantities, weights);
    }

    @PostMapping("/draw/{id}")
    @RateLimiter(dimension = "user", timeWindow = 60, maxRequests = 3, message = "不要抽太快，一秒一次就好", enableBan = true)
    public Result drawBlindBox(@PathVariable("id") Long blindBoxId,
                                @RequestParam("requestId") String requestId) {
        return blindBoxService.drawBlindBox(blindBoxId, requestId);
    }

    @GetMapping("/list")
    public Result getBlindBoxList(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blindBoxService.getBlindBoxList(current);
    }

    @GetMapping("/{id}")
    public Result getBlindBoxDetail(@PathVariable("id") Long blindBoxId) {
        return blindBoxService.getBlindBoxDetail(blindBoxId);
    }
}
