package com.huixiang.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.huixiang.dto.Result;
import com.huixiang.entity.*;
import com.huixiang.mapper.BlindBoxMapper;
import com.huixiang.mapper.BlindBoxOrderMapper;
import com.huixiang.mapper.BlindBoxPrizeMapper;
import com.huixiang.service.IBlindBoxService;
import com.huixiang.utils.RedisIdWorker;
import com.huixiang.utils.SystemConstants;
import com.huixiang.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class BlindBoxServiceImpl extends ServiceImpl<BlindBoxMapper, BlindBox>
        implements IBlindBoxService {

    @Resource
    private BlindBoxPrizeMapper prizeMapper;

    @Resource
    private BlindBoxOrderMapper orderMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<List> BLINDBOX_SCRIPT;

    static {
        BLINDBOX_SCRIPT = new DefaultRedisScript<>();
        BLINDBOX_SCRIPT.setLocation(new ClassPathResource("blindbox.lua"));
        BLINDBOX_SCRIPT.setResultType(List.class);
    }

    private static final String BLINDBOX_STOCK_KEY = "blindbox:stock:";
    private static final String BLINDBOX_PRIZES_KEY = "blindbox:prizes:";

    @Override
    @Transactional
    public Result createBlindBox(BlindBox blindBox, List<Long> voucherIds,
                                  List<Integer> quantities, List<Integer> weights) {
        if (voucherIds.size() != quantities.size() || voucherIds.size() != weights.size()) {
            return Result.fail("奖品参数数量不一致");
        }
        if (voucherIds.size() < 2) {
            return Result.fail("奖品至少需要2种");
        }

        blindBox.setRemainStock(blindBox.getTotalStock());
        blindBox.setCreateTime(LocalDateTime.now());
        save(blindBox);

        for (int i = 0; i < voucherIds.size(); i++) {
            BlindBoxPrize prize = new BlindBoxPrize();
            prize.setBlindBoxId(blindBox.getId());
            prize.setVoucherId(voucherIds.get(i));
            prize.setQuantity(quantities.get(i));
            prize.setRemainQuantity(quantities.get(i));
            prize.setWeight(weights.get(i));
            prize.setCreateTime(LocalDateTime.now());
            prizeMapper.insert(prize);
        }

        cachePrizePool(blindBox.getId());
        stringRedisTemplate.opsForValue().set(
                BLINDBOX_STOCK_KEY + blindBox.getId(),
                String.valueOf(blindBox.getTotalStock()));

        return Result.ok(blindBox.getId());
    }

    private void cachePrizePool(Long blindBoxId) {
        List<BlindBoxPrize> prizes = prizeMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BlindBoxPrize>()
                        .eq(BlindBoxPrize::getBlindBoxId, blindBoxId));
        String key = BLINDBOX_PRIZES_KEY + blindBoxId;
        stringRedisTemplate.delete(key);
        for (BlindBoxPrize p : prizes) {
            stringRedisTemplate.opsForList().rightPushAll(key,
                    String.valueOf(p.getVoucherId()),
                    String.valueOf(p.getRemainQuantity()),
                    String.valueOf(p.getWeight()));
        }
    }

    @Override
    public Result drawBlindBox(Long blindBoxId, String requestId) {
        Long userId = UserHolder.getUser().getId();

        int count = orderMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BlindBoxOrder>()
                        .eq(BlindBoxOrder::getRequestId, requestId));
        if (count > 0) {
            BlindBoxOrder exist = orderMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BlindBoxOrder>()
                            .eq(BlindBoxOrder::getRequestId, requestId));
            return Result.ok(Map.of("orderId", exist.getId(), "prizeVoucherId", exist.getPrizeVoucherId(), "duplicate", true));
        }

        BlindBox blindBox = getById(blindBoxId);
        if (blindBox == null) return Result.fail("盲盒不存在");
        if (blindBox.getStatus() != null && blindBox.getStatus() != 1) return Result.fail("盲盒已结束");
        if (blindBox.getBeginTime().isAfter(LocalDateTime.now())) return Result.fail("盲盒尚未开始");
        if (blindBox.getEndTime().isBefore(LocalDateTime.now())) return Result.fail("盲盒已结束");

        long orderId = redisIdWorker.nextId("blindbox");
        List<Long> result = stringRedisTemplate.execute(BLINDBOX_SCRIPT,
                Collections.emptyList(),
                blindBoxId.toString(), userId.toString(), String.valueOf(orderId));

        if (result == null || result.isEmpty()) return Result.fail("系统繁忙");
        int code = result.get(0).intValue();
        if (code == -1) return Result.fail("盲盒已售罄");
        if (code == -2) return Result.fail("您已参与过该盲盒");
        if (code == -3 || code == -4) return Result.fail("奖品池异常");

        Long prizeVoucherId = Long.valueOf(result.get(1).toString());

        BlindBoxOrder order = new BlindBoxOrder();
        order.setId(orderId);
        order.setUserId(userId);
        order.setBlindBoxId(blindBoxId);
        order.setPrizeVoucherId(prizeVoucherId);
        order.setRequestId(requestId);
        order.setStatus(1);
        order.setCreateTime(LocalDateTime.now());
        orderMapper.insert(order);

        Map<String, Object> data = new HashMap<>();
        data.put("orderId", orderId);
        data.put("prizeVoucherId", prizeVoucherId);
        data.put("duplicate", false);
        return Result.ok(data);
    }

    @Override
    public Result getBlindBoxList(Integer current) {
        Page<BlindBox> page = query().ge("end_time", LocalDateTime.now())
                .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
        return Result.ok(page.getRecords());
    }

    @Override
    public Result getBlindBoxDetail(Long blindBoxId) {
        BlindBox blindBox = getById(blindBoxId);
        if (blindBox == null) return Result.fail("盲盒不存在");

        List<BlindBoxPrize> prizes = prizeMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BlindBoxPrize>()
                        .eq(BlindBoxPrize::getBlindBoxId, blindBoxId));
        int totalWeight = prizes.stream().mapToInt(BlindBoxPrize::getWeight).sum();

        List<Map<String, Object>> prizeList = new ArrayList<>();
        for (BlindBoxPrize p : prizes) {
            Map<String, Object> info = new HashMap<>();
            info.put("voucherId", p.getVoucherId());
            info.put("probability", totalWeight > 0
                    ? String.format("%.1f%%", p.getWeight() * 100.0 / totalWeight) : "0%");
            info.put("remain", p.getRemainQuantity());
            prizeList.add(info);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("blindBox", blindBox);
        data.put("prizes", prizeList);
        return Result.ok(data);
    }
}
