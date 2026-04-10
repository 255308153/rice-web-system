package com.rice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rice.dto.HotProductDTO;
import com.rice.entity.OrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {

    @Select("""
            SELECT
                oi.product_id AS productId,
                p.name AS productName,
                COALESCE(SUM(oi.quantity), 0) AS salesCount,
                COALESCE(SUM(oi.quantity * oi.price), 0) AS salesAmount
            FROM order_item oi
            INNER JOIN `order` o ON o.id = oi.order_id
            INNER JOIN product p ON p.id = oi.product_id
            WHERE o.status IN (1, 2, 3)
            GROUP BY oi.product_id, p.name
            ORDER BY salesCount DESC, salesAmount DESC
            LIMIT #{limit}
            """)
    List<HotProductDTO> selectHotProducts(@Param("limit") int limit);

    @Select("""
            SELECT
                oi.product_id AS productId,
                p.name AS productName,
                COALESCE(SUM(oi.quantity), 0) AS salesCount,
                COALESCE(SUM(oi.quantity * oi.price), 0) AS salesAmount
            FROM order_item oi
            INNER JOIN `order` o ON o.id = oi.order_id
            INNER JOIN product p ON p.id = oi.product_id
            WHERE o.shop_id = #{shopId}
              AND o.status IN (1, 2, 3, 4)
            GROUP BY oi.product_id, p.name
            ORDER BY salesCount DESC, salesAmount DESC
            LIMIT #{limit}
            """)
    List<HotProductDTO> selectHotProductsByShop(@Param("shopId") Long shopId, @Param("limit") int limit);
}
