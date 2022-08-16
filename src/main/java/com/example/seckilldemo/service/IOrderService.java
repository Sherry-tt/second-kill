package com.example.seckilldemo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.seckilldemo.entity.Order;
import com.example.seckilldemo.entity.User;
import com.example.seckilldemo.vo.GoodsVo;
import com.example.seckilldemo.vo.OrderDetailVo;

public interface IOrderService extends IService<Order> {
    Order seckill(User user, GoodsVo goodsVo);

    OrderDetailVo detail(Long orderId);

    String createPath(User user, Long goodsId);
//
    Boolean checkPath(User user, String path, Long goodsId);
//
    Boolean checkCptcha(User user, Long goodsId, String captcha);
}
