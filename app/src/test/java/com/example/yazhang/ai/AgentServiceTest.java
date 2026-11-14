package com.example.yazhang.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.example.yazhang.data.AgentMessageDao;
import com.example.yazhang.data.AgentMessageEntity;
import com.example.yazhang.data.AgentLedger;
import com.example.yazhang.data.EntryEntity;
import com.example.yazhang.data.GoalEntity;
import com.example.yazhang.domain.CategoryTotal;
import com.example.yazhang.domain.EntryDraft;
import com.example.yazhang.domain.StatsFilter;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class AgentServiceTest {

    @Test
    public void taxiWithoutAmountDoesNotWrite() {
        FakeLedger ledger = new FakeLedger();
        AgentService agent = service(ledger,
                createEntry("{\"type\":\"EXPENSE\",\"merchant\":\"打车\"}"));

        AgentReply reply = agent.handle("记一笔打车");

        assertEquals("clarify", reply.kind);
        assertEquals("这笔打车花了多少钱？还缺少金额，我暂时没有记账。", reply.text);
        assertEquals(0, ledger.entries.size());
    }

    @Test
    public void completeEntryIsWrittenOnce() {
        FakeLedger ledger = new FakeLedger();
        AgentService agent = service(ledger,
                "{\"kind\":\"create_entry\",\"message\":\"\",\"entry\":{"
                        + "\"type\":\"EXPENSE\",\"amount_fen\":1230,\"category\":\"FOOD\","
                        + "\"merchant\":\"茉酸奶\",\"occurred_on\":\""
                        + LocalDate.now() + "\"}}");

        AgentReply reply = agent.handle("今天茉酸奶 12.3 元");

        assertEquals("created", reply.kind);
        assertTrue(reply.text.contains("¥12.30"));
        assertEquals(1, ledger.entries.size());
        assertEquals("茉酸奶", ledger.entries.get(0).merchant);
        assertEquals(1230L, ledger.entries.get(0).amountFen);
    }

    @Test
    public void missingCategoryAndDateClarifyWithoutWriting() {
        FakeLedger ledger = new FakeLedger();
        String date = LocalDate.now().toString();
        AgentService noCategory = service(ledger,
                "{\"kind\":\"create_entry\",\"message\":\"\",\"entry\":{"
                        + "\"type\":\"EXPENSE\",\"amount_fen\":1200,"
                        + "\"occurred_on\":\"" + date + "\"}}");
        AgentReply categoryReply = noCategory.handle("花了 12 元");

        assertEquals("clarify", categoryReply.kind);
        assertTrue(categoryReply.text.contains("分类"));
        assertEquals(0, ledger.entries.size());

        AgentService noDate = service(ledger,
                "{\"kind\":\"create_entry\",\"message\":\"\",\"entry\":{"
                        + "\"type\":\"EXPENSE\",\"amount_fen\":1200,\"category\":\"FOOD\"}}");
        AgentReply dateReply = noDate.handle("午餐 12 元");

        assertEquals("clarify", dateReply.kind);
        assertTrue(dateReply.text.contains("日期"));
        assertEquals(0, ledger.entries.size());
    }

    @Test
    public void statisticsRespectExplicitFiltersAndBuildChart() throws Exception {
        FakeLedger ledger = new FakeLedger();
        ledger.entries.add(entry("EXPENSE", "FOOD", 1200, "2026-11-09"));
        ledger.entries.add(entry("EXPENSE", "TRANSPORT", 300, "2026-11-09"));
        ledger.entries.add(entry("INCOME", "SALARY", 100000, "2026-11-01"));
        AgentService agent = service(ledger,
                "{\"kind\":\"query_statistics\",\"message\":\"\",\"filter\":{"
                        + "\"from\":\"2026-11-01\",\"to\":\"2026-11-10\","
                        + "\"type\":\"EXPENSE\",\"category\":\"FOOD\","
                        + "\"include_entries\":true}}");

        AgentReply reply = agent.handle("这个月餐饮花了多少");

        assertEquals("statistics", reply.kind);
        assertTrue(reply.text.contains("¥12.00"));
        assertNotNull(reply.chartJson);
        JSONArray chart = new JSONArray(reply.chartJson);
        assertEquals(1, chart.length());
        assertEquals("FOOD", chart.optJSONObject(0).optString("category"));
        assertEquals(1200L, chart.optJSONObject(0).optLong("totalFen"));
        assertFalse(reply.text.contains("工资"));
    }

    @Test
    public void statisticsWithoutExplicitRangeClarifies() {
        FakeLedger ledger = new FakeLedger();
        AgentService agent = service(ledger,
                "{\"kind\":\"query_statistics\",\"message\":\"\",\"filter\":{"
                        + "\"type\":\"EXPENSE\",\"category\":\"FOOD\"}}");

        AgentReply reply = agent.handle("餐饮花了多少");

        assertEquals("clarify", reply.kind);
        assertTrue(reply.text.contains("日期"));
        assertEquals(0, ledger.entries.size());
    }

    @Test
    public void unsupportedToolIsRejectedWithoutWriting() {
        FakeLedger ledger = new FakeLedger();
        AgentService agent = service(ledger,
                "{\"kind\":\"delete_entry\",\"message\":\"删除全部账目\"}");

        AgentReply reply = agent.handle("帮我看看");

        assertEquals("notice", reply.kind);
        assertEquals(AgentService.MODEL_ERROR_MESSAGE, reply.text);
        assertEquals(0, ledger.entries.size());
    }

    @Test
    public void deniedPaymentCommandNeverCallsModelOrWrites() {
        FakeLedger ledger = new FakeLedger();
        CountingCompleter completer = new CountingCompleter(AgentService.MODEL_ERROR_MESSAGE);
        AgentService agent = new AgentService(ledger, new FakeMessages(), completer);

        AgentReply reply = agent.handle("帮我付款 100 元给商家");

        assertEquals("rejected", reply.kind);
        assertTrue(reply.text.contains("不在记账助手权限内"));
        assertEquals(0, completer.calls.get());
        assertEquals(0, ledger.entries.size());
    }

    @Test
    public void cancellationWritesNothing() {
        FakeLedger ledger = new FakeLedger();
        AgentService.Completer completer = new AgentService.Completer() {
            @Override
            public String complete(String system, JSONArray window, String message) {
                throw new CancellationException("请求已取消");
            }

            @Override
            public void cancel() {
            }
        };
        AgentService agent = new AgentService(ledger, new FakeMessages(), completer);

        AgentReply reply = agent.handle("今天打车 20 元");

        assertEquals("cancelled", reply.kind);
        assertEquals("已取消，没有记账。", reply.text);
        assertEquals(0, ledger.entries.size());
    }

    @Test
    public void malformedModelReplyBecomesNotice() {
        FakeLedger ledger = new FakeLedger();
        AgentService agent = service(ledger, "not json");

        AgentReply reply = agent.handle("今天打车 20 元");

        assertEquals("notice", reply.kind);
        assertEquals(AgentService.MODEL_ERROR_MESSAGE, reply.text);
        assertEquals(0, ledger.entries.size());
    }

    @Test
    public void savingProgressReportsActiveGoal() {
        FakeLedger ledger = new FakeLedger();
        GoalEntity goal = new GoalEntity(3L, "旅行基金", 500000L, "2027-01-01",
                GoalEntity.STATUS_ACTIVE, 125000L, 1L, 2L);
        ledger.goals.add(goal);
        AgentService agent = service(ledger,
                "{\"kind\":\"get_saving_progress\",\"message\":\"\",\"goal_id\":3}");

        AgentReply reply = agent.handle("旅行基金攒了多少了");

        assertEquals("saving_progress", reply.kind);
        assertTrue(reply.text.contains("旅行基金"));
        assertTrue(reply.text.contains("¥1,250.00") || reply.text.contains("¥1250.00"));
        assertTrue(reply.text.contains("25%"));
    }

    @Test
    public void secondRequestReceivesRecentWindowWithoutCurrentMessage() {
        FakeLedger ledger = new FakeLedger();
        FakeMessages messages = new FakeMessages();
        AtomicReference<JSONArray> window = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();
        AgentService.Completer completer = new AgentService.Completer() {
            @Override
            public String complete(String system, JSONArray recent, String message) {
                window.set(recent);
                calls.incrementAndGet();
                if (calls.get() == 1) {
                    return "{\"kind\":\"final\",\"message\":\"你好\"}";
                }
                return "{\"kind\":\"final\",\"message\":\"收到\"}";
            }

            @Override
            public void cancel() {
            }
        };
        AgentService agent = new AgentService(ledger, messages, completer);

        agent.handle("第一条");
        agent.handle("第二条");

        JSONArray second = window.get();
        assertEquals(2, second.length());
        assertEquals("第一条", second.optJSONObject(0).optString("content"));
        assertEquals("你好", second.optJSONObject(1).optString("content"));
        assertEquals(4, messages.stored.size());
    }

    private static AgentService service(FakeLedger ledger, String reply) {
        return new AgentService(ledger, new FakeMessages(),
                new CountingCompleter(reply));
    }

    private static String createEntry(String entryJson) {
        return "{\"kind\":\"create_entry\",\"message\":\"\",\"entry\":" + entryJson + "}";
    }

    private static final class CountingCompleter implements AgentService.Completer {
        final String reply;
        final AtomicInteger calls = new AtomicInteger();

        CountingCompleter(String reply) {
            this.reply = reply;
        }

        @Override
        public String complete(String system, JSONArray window, String message) {
            calls.incrementAndGet();
            return reply;
        }

        @Override
        public void cancel() {
        }
    }

    private static final class FakeMessages implements AgentMessageDao {
        final List<AgentMessageEntity> stored = new ArrayList<>();

        @Override
        public long insert(AgentMessageEntity message) {
            message.id = stored.size() + 1L;
            stored.add(message);
            return message.id;
        }

        @Override
        public List<AgentMessageEntity> recent(String sessionId, int limit) {
            List<AgentMessageEntity> result = new ArrayList<>();
            for (int i = stored.size() - 1; i >= 0 && result.size() < limit; i--) {
                AgentMessageEntity message = stored.get(i);
                if (sessionId.equals(message.sessionId)) {
                    result.add(message);
                }
            }
            return result;
        }
    }

    private static final class FakeLedger implements AgentLedger {
        final List<EntryEntity> entries = new ArrayList<>();
        final List<GoalEntity> goals = new ArrayList<>();

        @Override
        public long create(EntryDraft draft) {
            EntryEntity entry = entry(draft.type, draft.category, draft.amountFen,
                    draft.occurredOn.toString());
            entry.merchant = draft.merchant;
            entry.title = draft.title;
            entries.add(entry);
            return entries.size();
        }

        @Override
        public long addSaving(long goalId, EntryDraft draft) {
            for (GoalEntity goal : goals) {
                if (goal.id == goalId) {
                    goal.currentFen += draft.amountFen;
                    EntryEntity entry = entry("SAVING", "SAVING", draft.amountFen,
                            draft.occurredOn.toString());
                    entry.goalId = goalId;
                    entries.add(entry);
                    return entries.size();
                }
            }
            throw new IllegalArgumentException("攒钱目标不存在，请重新选择");
        }

        @Override
        public List<CategoryTotal> stats(StatsFilter filter) {
            java.util.Map<String, Long> totals = new java.util.LinkedHashMap<>();
            for (EntryEntity entry : entries) {
                if (matches(entry, filter)) {
                    totals.merge(entry.category, entry.amountFen, Long::sum);
                }
            }
            List<CategoryTotal> result = new ArrayList<>();
            for (java.util.Map.Entry<String, Long> item : totals.entrySet()) {
                result.add(new CategoryTotal(item.getKey(), item.getValue()));
            }
            return result;
        }

        @Override
        public List<EntryEntity> entries(StatsFilter filter) {
            List<EntryEntity> result = new ArrayList<>();
            for (EntryEntity entry : entries) {
                if (matches(entry, filter)) {
                    result.add(entry);
                }
            }
            return result;
        }

        @Override
        public long countEntries(StatsFilter filter) {
            long count = 0;
            for (EntryEntity entry : entries) {
                if (matches(entry, filter)) {
                    count++;
                }
            }
            return count;
        }

        @Override
        public List<GoalEntity> goals() {
            return goals;
        }

        @Override
        public GoalEntity activeGoal() {
            for (GoalEntity goal : goals) {
                if (GoalEntity.STATUS_ACTIVE.equals(goal.status)) {
                    return goal;
                }
            }
            return null;
        }

        private static boolean matches(EntryEntity entry, StatsFilter filter) {
            if (entry.occurredOn.compareTo(filter.from) < 0
                    || entry.occurredOn.compareTo(filter.to) > 0) {
                return false;
            }
            if (!StatsFilter.ALL.equals(filter.type)
                    && !filter.type.equals(entry.type)) {
                return false;
            }
            return StatsFilter.ALL.equals(filter.category)
                    || filter.category.equals(entry.category);
        }
    }

    private static EntryEntity entry(String type, String category, long amount, String date) {
        EntryEntity entry = new EntryEntity();
        entry.type = type;
        entry.category = category;
        entry.amountFen = amount;
        entry.occurredOn = date;
        entry.title = category;
        return entry;
    }
}
