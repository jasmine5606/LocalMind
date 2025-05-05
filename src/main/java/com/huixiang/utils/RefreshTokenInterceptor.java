package com.huixiang.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.huixiang.dto.UserDTO;
import com.huixiang.entity.UserInfo;
import com.huixiang.service.IUserInfoService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.huixiang.utils.RedisConstants.LOGIN_USER_KEY;
import static com.huixiang.utils.RedisConstants.LOGIN_USER_TTL;

public class RefreshTokenInterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;
    private IUserInfoService userInfoService;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate, IUserInfoService userInfoService) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.userInfoService = userInfoService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            return true;
        }
        String key = LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
        if (userMap.isEmpty()) {
            return true;
        }
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        loadMemberLevel(userDTO);
        UserHolder.saveUser(userDTO);
        stringRedisTemplate.expire(key, LOGIN_USER_TTL, TimeUnit.MINUTES);
        return true;
    }

    private void loadMemberLevel(UserDTO userDTO) {
        try {
            UserInfo userInfo = userInfoService.getById(userDTO.getId());
            if (userInfo != null && userInfo.getLevel() != null) {
                userDTO.setMemberLevel(userInfo.getLevel() ? 1 : 0);
            }
        } catch (Exception e) {
            userDTO.setMemberLevel(0);
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserHolder.removeUser();
    }
}
