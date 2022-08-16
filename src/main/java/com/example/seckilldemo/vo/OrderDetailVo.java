package com.example.seckilldemo.vo;

import com.example.seckilldemo.entity.Order;
import com.example.seckilldemo.entity.Order;
import lombok.Data;

@Data
public class OrderDetailVo {

    private Order order;
    private GoodsVo goodsVo;
}
