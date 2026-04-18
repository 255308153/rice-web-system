package com.rice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rice.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    /**
     * 原子扣库存并累加销量：
     * 仅当库存足够时更新成功（返回 1），否则返回 0。
     */
    @Update("""
            UPDATE product
            SET stock = stock - #{quantity},
                sales = COALESCE(sales, 0) + #{quantity}
            WHERE id = #{productId}
              AND status = 1
              AND stock >= #{quantity}
            """)
    int decreaseStockAndIncreaseSales(@Param("productId") Long productId, @Param("quantity") Integer quantity);
}
