package com.match.util.page;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.ArrayList;
import java.util.List;

public class PageUtil {
    /**
     * 处理List集合数据进行分页
     *
     * @param currentPage 当前页
     * @param pageSize    每页数据个数
     * @param list        进行分页的数据
     * @param <T>
     * @return
     */
    public static <T> Page<T> page(int currentPage, int pageSize, List<T> list) {
        Page<T> page = new Page<>(currentPage, pageSize);
        int count = list.size();
        List<T> pageList = new ArrayList<>();
        int currId = currentPage > 1 ? (currentPage - 1) * pageSize : 0;
        for (int i = 0; i < pageSize && i < count - currId; i++) {
            pageList.add(list.get(currId + i));
        }
        page.setSize(pageSize);
        page.setCurrent(currentPage);
        page.setTotal(count);
        page.setPages(count % 10 == 0 ? count / 10 : count / 10 + 1);
        page.setRecords(pageList);
        return page;
    }
}
