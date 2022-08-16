package com.example.seckilldemo.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.seckilldemo.config.AccessLimit;
import com.example.seckilldemo.entity.Order;
import com.example.seckilldemo.entity.SeckillMessage;
import com.example.seckilldemo.entity.SeckillOrder;
import com.example.seckilldemo.entity.User;
import com.example.seckilldemo.exception.GlobalException;
import com.example.seckilldemo.rabbitmq.MQSender;
import com.example.seckilldemo.service.IGoodsService;
import com.example.seckilldemo.service.IOrderService;
import com.example.seckilldemo.service.ISeckillOrderService;
import com.example.seckilldemo.utils.JsonUtil;
import com.example.seckilldemo.vo.GoodsVo;
import com.example.seckilldemo.vo.RespBean;
import com.example.seckilldemo.vo.RespBeanEnum;
//import com.sun.org.apache.xpath.internal.operations.Mod;

import com.wf.captcha.ArithmeticCaptcha;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Controller
@RequestMapping("/seckill")
public class SecKillController implements InitializingBean {

    @Autowired
    private IGoodsService goodsService;

    @Autowired
    private ISeckillOrderService seckillOrderService;

    @Autowired
    private IOrderService orderService;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private MQSender mqSender;

    @Autowired
    private RedisScript<Long> script;

    private Map<Long,Boolean> EmptyStockMap = new HashMap<>();

//    @RequestMapping("/doSeckill2")
//    public String doSeckill2(Model model, User user, Long goodsId) {
//        if(user == null) {
//            return "login";
//        }
//
//        model.addAttribute("user", user);
//        GoodsVo goodsVo = goodsService.findGoodsVoByGoodsId(goodsId);
//        model.addAttribute("goods", goodsVo);
//        //判断库存
//        if(goodsVo.getStockCount() <= 0) {
//            model.addAttribute("errmsg", RespBeanEnum.EMPTY_STOCK.getMessage());
//            return "secKillFail";
//        }
//
//        //判断当前用户是否已经秒杀过
//        QueryWrapper<SeckillOrder> wrapper = new QueryWrapper<>();
//        wrapper.eq("user_id", user.getId());
//        wrapper.eq("goods_id", goodsId);
//        SeckillOrder seckillOrder = seckillOrderService.getOne(wrapper);
//        if(seckillOrder != null) {
//            model.addAttribute("errmsg", RespBeanEnum.HAS_SECKILL.getMessage());
//            return "secKillFail";
//        }

//        //下订单
//        Order order = orderService.seckill(user,goodsVo);
//        model.addAttribute("order", order);
//        return "orderDetail";
//    }


//    @RequestMapping(value = "/doSeckill", method = RequestMethod.POST)
//    @ResponseBody
//    public RespBean doSeckill(Model model, User user, Long goodsId) {
//        if(user == null) {
//            return RespBean.error(RespBeanEnum.USER_TIME_OUT);
//        }
//
//        GoodsVo goodsVo = goodsService.findGoodsVoByGoodsId(goodsId);
//        if(goodsVo.getStockCount() <= 0) {
//            model.addAttribute("errmsg", RespBeanEnum.EMPTY_STOCK.getMessage());
//            return RespBean.error(RespBeanEnum.EMPTY_STOCK);
//        }
//
//        //判断当前用户是否已经秒杀过(数据库查询）
////        QueryWrapper<SeckillOrder> wrapper = new QueryWrapper<>();
////        wrapper.eq("user_id", user.getId());
////        wrapper.eq("goods_id", goodsId);
////        SeckillOrder seckillOrder = seckillOrderService.getOne(wrapper);
//        //判断当前用户是否已经秒杀过(Redis查询）
//        SeckillOrder seckillOrder = (SeckillOrder)redisTemplate.opsForValue().get("order:"+user.getId()+":"+goodsId);
//        if(seckillOrder != null) {
//            model.addAttribute("errmsg", RespBeanEnum.HAS_SECKILL.getMessage());
//            return RespBean.error(RespBeanEnum.HAS_SECKILL);
//        }
//
//////        判断当前用户是否已经秒杀过
////        SeckillOrder seckillOrder = (SeckillOrder)valueOperations.get("order:"+user.getId()+":"+goodsId);
////        if(seckillOrder != null) {
////            return RespBean.error(RespBeanEnum.HAS_SECKILL);
////        }
//
//        //下订单
//        Order order = orderService.seckill(user,goodsVo);
//        return RespBean.success(order);
//    }

    @RequestMapping(value = "/{path}/doSeckill", method = RequestMethod.POST)
//    @RequestMapping(value = "/doSeckill", method = RequestMethod.POST)
    @ResponseBody
//    public RespBean doSeckill(Model model, User user, Long goodsId) {
    public RespBean doSeckill(@PathVariable String path, User user, Long goodsId) {
        if(user == null) {
            return RespBean.error(RespBeanEnum.SESSION_ERROR);
        }

        ValueOperations valueOperations = redisTemplate.opsForValue();
        Boolean check = orderService.checkPath(user, path, goodsId);
        if(!check) {
            return RespBean.error(RespBeanEnum.REQUEST_ILLEGAL);
        }
        //判断当前用户是否已经秒杀过
        SeckillOrder seckillOrder = (SeckillOrder)valueOperations.get("order:"+user.getId()+":"+goodsId);
        if(seckillOrder != null) {
            return RespBean.error(RespBeanEnum.SESSION_ERROR);
        }

        // 通过内存标记减少redis的访问
        if(EmptyStockMap.get(goodsId)) {
            return RespBean.error(RespBeanEnum.EMPTY_STOCK);
        }

        //redis预减库存
//        Long stock = valueOperations.decrement("seckillGoods:" + goodsId);
        //使用lua脚本
        Long stock = (Long) redisTemplate.execute(script, Collections.singletonList("seckillGoods:" + goodsId), Collections.EMPTY_LIST);
        if(stock < 0) {
            EmptyStockMap.put(goodsId, true);
            valueOperations.increment("seckillGoods:" + goodsId);
            return RespBean.error(RespBeanEnum.EMPTY_STOCK);
        }

        //下订单
        SeckillMessage seckillMessage = new SeckillMessage(user, goodsId);
        mqSender.sendSeckillMessage(JsonUtil.object2JsonStr(seckillMessage));
        return RespBean.success(0);
    }

    //加载完毕后执行，将商品库存数量加载到redis
    @Override
    public void afterPropertiesSet() throws Exception {
        List<GoodsVo> goodsVos = goodsService.findGoodsVo();
        if(goodsVos.size() == 0) {
            return ;
        }
        ValueOperations valueOperations = redisTemplate.opsForValue();
        for(GoodsVo goodsVo : goodsVos) {
            valueOperations.set("seckillGoods:" + goodsVo.getId(), goodsVo.getStockCount());
            EmptyStockMap.put(goodsVo.getId(), false);
        }
    }
//

    /**
     * 查询用户秒杀的结果
     * @param user
     * @param goodsId
     * @return orderId: 成功； -1：失败； 0：排队中
     */
    @RequestMapping("/result")
    @ResponseBody
    public RespBean getResult(User user, String goodsId) {
        if(user == null) {
//            return RespBean.error(RespBeanEnum.USER_TIME_OUT);
            return RespBean.error(RespBeanEnum.SESSION_ERROR);
        }
        String key = "order:" + user.getId() + ":" + goodsId;
        ValueOperations valueOperations = redisTemplate.opsForValue();
        Long orderId = -1l;
        if(valueOperations.get(key) != null) {
            SeckillOrder seckillOrder = (SeckillOrder) valueOperations.get(key);
            orderId = seckillOrder.getOrderId();
        } else if((int)valueOperations.get("seckillGoods:" + goodsId) <= 0) {
            orderId = -1l;
        } else {
            orderId = 0l;
        }
        return RespBean.success(orderId);
    }

    //获取秒杀的接口
    @RequestMapping(value = "/path", method = RequestMethod.GET)
    @ResponseBody
    @AccessLimit(second=5,maxCount=5,needLogin=true)
//    public RespBean getPath(User user, Long goodsId, String captcha) {
    public RespBean getPath(User user, Long goodsId, String captcha, HttpServletRequest request) {
        if(user == null) {
            return RespBean.error(RespBeanEnum.SESSION_ERROR);
        }
//        ValueOperations valueOperations = redisTemplate.opsForValue();
        //限制访问次数，5秒内访问5次
//        String uri = request.getRequestURI();
//        captcha = "0";
//        Integer count = (Integer) valueOperations.get(uri + ":" + user.getId());
//        if (count == null) {
//            valueOperations.set(uri + ":" + user.getId(), 1, 5, TimeUnit.SECONDS);
//        } else if (count < 5) {
//            valueOperations.increment(uri + ":" + user.getId());
//        } else {
//            return RespBean.error(RespBeanEnum.ACCESS_LIMIT_REACHED);
//        }

        //判断验证码是否正确
//        Boolean check = orderService.checkCptcha(user, goodsId, captcha);
//        if(!check) {
//            return RespBean.error(RespBeanEnum.ERROR_CAPTCHA);
//        }

        //生成秒杀的接口
        String str = orderService.createPath(user, goodsId);
        return RespBean.success(str);
    }

    //生成验证码
    @RequestMapping(value = "/captcha", method = RequestMethod.GET)
    public void vertifyCode(User user, Long goodsId, HttpServletResponse response) {
        if(user == null || goodsId < 0) {
            throw new GlobalException(RespBeanEnum.REQUEST_ILLEGAL);
        }

        //设置请求头为输出图片数据
        response.setContentType("image/jpg");
        response.setHeader("Pargam","No-cache");
        response.setHeader("Cache-Control", "no-cache");
        response.setDateHeader("Expires", 0);
        //生成验证码，将结果放入redis中

        ArithmeticCaptcha arithmeticCaptcha = new ArithmeticCaptcha(130, 32, 3);

        String text = arithmeticCaptcha.text();
        System.out.println("验证码:"+text);
        redisTemplate.opsForValue().set("captcha:" + user.getId() + ":" + goodsId, arithmeticCaptcha.text(), 300, TimeUnit.SECONDS);

        try {
            arithmeticCaptcha.out(response.getOutputStream());
        } catch (IOException e) {
//            e.printStackTrace();
            log.error("验证码生成失败", e.getMessage());
        }
    }
}

