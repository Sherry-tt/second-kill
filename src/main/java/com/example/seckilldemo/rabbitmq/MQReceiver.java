package com.example.seckilldemo.rabbitmq;

//import com.alibaba.fastjson.JSON;
//import com.example.seckilldemo.entity.SeckillMessage;
import com.example.seckilldemo.entity.SeckillMessage;
import com.example.seckilldemo.entity.SeckillOrder;
import com.example.seckilldemo.entity.User;
import com.example.seckilldemo.service.IGoodsService;
import com.example.seckilldemo.service.IOrderService;
import com.example.seckilldemo.utils.JsonUtil;
import com.example.seckilldemo.vo.GoodsVo;
import com.example.seckilldemo.vo.RespBean;
import com.example.seckilldemo.vo.RespBeanEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class MQReceiver {

//    @RabbitListener(queues="queue")
//    public void receive(Object msg) {
//        log.info("接收消息：" + msg);
//    }
//
//    @RabbitListener(queues = "queue_fanout01")
//    public void receive01(Object msg) {
//        log.info("QUEUE01接收消息：" + msg);
//    }
//
//    @RabbitListener(queues = "queue_fanout02")
//    public void receive02(Object msg) {
//        log.info("QUEUE02接收消息：" + msg);
//    }
//
//    @RabbitListener(queues = "queue_direct01")
//    public void receive03(Object msg) {
//        log.info("QUEUE01接受消息:" + msg);
//    }
//
//    @RabbitListener(queues = "queue_direct02")
//    public void receive04(Object msg) {
//        log.info("QUEUE02接受消息:" + msg);
//    }
//
//    @RabbitListener(queues = "queue_topic01")
//    private void receive05(Object msg) {
//        log.info("QUEUE01接收消息：" + msg);
//    }
//
//    @RabbitListener(queues = "queue_topic02")
//    private void receive06(Object msg) {
//        log.info("QUEUE02接收消息：" + msg);
//    }

//    @RabbitListener(queues = "queue_hearder01")
//    public void receive07(Message message) {
//        log.info("QUEUE01接收Message对象：" + message);
//        log.info("QUEUE01接收消息：", new String(message.getBody()));
//    }
//
//    @RabbitListener(queues = "queue_hearder02")
//    public void receive08(Message message) {
//        log.info("QUEUE02接收Message对象：" + message);
//        log.info("QUEUE02接收消息：", new String(message.getBody()));
//    }

    @Autowired
    private IGoodsService goodsService;
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private IOrderService orderService;

    //进行下单
    @RabbitListener(queues = "seckillQueue")
    public void receive(String message) {
        log.info("接收到订单消息:"+message);
        SeckillMessage seckillMessage = JsonUtil.jsonStr2Object(message, SeckillMessage.class);
        Long goodsId = seckillMessage.getGoodsId();
        User user = seckillMessage.getUser();

        // 判断库存
        GoodsVo goodsVo = goodsService.findGoodsVoByGoodsId(goodsId);
        if(goodsVo.getStockCount() < 1) {
            return ;
        }

        // 判断是否重复抢购
        ValueOperations valueOperations = redisTemplate.opsForValue();
        SeckillOrder seckillOrder = (SeckillOrder)valueOperations.get("order:"+user.getId()+":"+goodsId);
        if(seckillOrder != null) {
            return ;
        }

        //下单操作
        try {
            orderService.seckill(user, goodsVo);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            return ;
        }

    }
}
