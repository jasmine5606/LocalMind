package com.huixiang.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.huixiang.dto.Result;
import com.huixiang.entity.BlindBox;

public interface IBlindBoxService extends IService<BlindBox> {

    Result createBlindBox(BlindBox blindBox, java.util.List<Long> voucherIds, java.util.List<Integer> quantities, java.util.List<Integer> weights);

    Result drawBlindBox(Long blindBoxId, String requestId);

    Result getBlindBoxList(Integer current);

    Result getBlindBoxDetail(Long blindBoxId);
}
