package com.example.seckilldemo.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.seckilldemo.entity.Goods;
import com.example.seckilldemo.mapper.GoodsMapper;
import com.example.seckilldemo.service.IGoodsService;
import com.example.seckilldemo.vo.GoodsVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GoodsServiceImpl extends ServiceImpl<GoodsMapper, Goods> implements IGoodsService {

    @Autowired
    private GoodsMapper goodsMapper;

    @Override
    public List<GoodsVo> findGoodsVo() {
        List<GoodsVo> res = goodsMapper.findGoodsVo();

        return res;
    }

    @Override
    public GoodsVo findGoodsVoByGoodsId(Long goodsId) {
        GoodsVo res = goodsMapper.findGoodsVoByGoodsId(goodsId);
        return res;
    }
}
