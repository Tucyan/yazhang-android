package com.example.yazhang.domain;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class StatsCategoryOptionsTest {

    @Test
    public void allTypeOffersOnlyAllCategory() {
        assertArrayEquals(new String[]{StatsFilter.ALL},
                StatsCategoryOptions.codesFor(StatsFilter.ALL));
        assertArrayEquals(new String[]{"全部分类"},
                StatsCategoryOptions.labelsFor(StatsFilter.ALL));
    }

    @Test
    public void expenseUsesAllCodeAsFirstOption() {
        String[] codes = StatsCategoryOptions.codesFor("EXPENSE");
        String[] labels = StatsCategoryOptions.labelsFor("EXPENSE");

        assertEquals(7, codes.length);
        assertEquals(StatsFilter.ALL, codes[0]);
        assertTrue(Arrays.asList(codes).contains("FOOD"));
        assertEquals("全部分类", labels[0]);
        assertEquals("餐饮", labels[1]);
    }

    @Test
    public void everyFirstOptionProducesValidFilter() {
        for (String type : new String[]{"EXPENSE", "INCOME", "SAVING"}) {
            String category = StatsCategoryOptions.codesFor(type)[0];
            StatsFilter filter = StatsFilter.of("2026-11-01", "2026-11-10", type, category);
            assertEquals(StatsFilter.ALL, filter.category);
        }
    }

    @Test
    public void categoryCodesMatchDeclaredType() {
        for (String code : StatsCategoryOptions.codesFor("EXPENSE")) {
            if (!StatsFilter.ALL.equals(code)) {
                assertTrue("EXPENSE 分类代码应匹配支出类型: " + code,
                        EntryValidator.categoryMatchesType("EXPENSE", code));
            }
        }
        assertEquals("SALARY", StatsCategoryOptions.codesFor("INCOME")[1]);
        assertEquals("SAVING", StatsCategoryOptions.codesFor("SAVING")[1]);
    }
}
