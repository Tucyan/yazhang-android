package com.example.yazhang.data;

import com.example.yazhang.domain.CategoryTotal;
import com.example.yazhang.domain.EntryDraft;
import com.example.yazhang.domain.EntryValidator;
import com.example.yazhang.domain.HomeSummary;
import com.example.yazhang.domain.Money;
import com.example.yazhang.domain.StatsFilter;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

public final class LedgerRepository implements AiEntryStore, AgentLedger {

    private final AppDatabase database;

    public LedgerRepository(AppDatabase database) {
        if (database == null) {
            throw new IllegalArgumentException("数据库不能为空");
        }
        this.database = database;
    }

    public boolean hasImage(String imageSha) {
        if (imageSha == null || imageSha.isEmpty()) {
            return false;
        }
        return database.entryDao().findByImageSha(imageSha) != null;
    }

    /** Returns the new entry id, or -1 when the same image was already recorded. */
    public long createAi(EntryDraft draft, String imageSha, double confidence) {
        requireValid(draft);
        if (imageSha == null || !imageSha.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("图片校验值无效，请重新选择图片");
        }
        if (!(confidence >= 0.0 && confidence <= 1.0)) {
            throw new IllegalArgumentException("识别置信度无效，请重试");
        }
        String requestKey = "ai:" + imageSha;
        if (database.entryDao().findByRequestKey(requestKey) != null) {
            return -1L;
        }
        EntryEntity entry = entryFrom(draft, EntryEntity.SOURCE_AI, null);
        entry.requestKey = requestKey;
        entry.imageSha = imageSha;
        entry.confidence = confidence;
        entry.categoryInferred = true;
        try {
            return database.entryDao().insert(entry);
        } catch (android.database.sqlite.SQLiteConstraintException duplicate) {
            return -1L;
        }
    }

    public HomeSummary home(LocalDate today) {
        if (today == null) {
            throw new IllegalArgumentException("首页日期不能为空");
        }
        long todayTotal = database.entryDao().expenseTotalOn(today.toString());
        if (todayTotal > 0) {
            return new HomeSummary(HomeSummary.Kind.TODAY, todayTotal);
        }
        long yesterdayTotal = database.entryDao().expenseTotalOn(today.minusDays(1).toString());
        if (yesterdayTotal > 0) {
            return new HomeSummary(HomeSummary.Kind.YESTERDAY, yesterdayTotal);
        }
        return new HomeSummary(HomeSummary.Kind.EMPTY, 0L);
    }

    public long create(EntryDraft draft) {
        requireValid(draft);
        EntryEntity entry = entryFrom(draft, EntryEntity.SOURCE_MANUAL, null);
        return database.entryDao().insert(entry);
    }

    public List<CategoryTotal> stats(StatsFilter filter) {
        if (filter == null) {
            throw new IllegalArgumentException("统计筛选条件不能为空");
        }
        return database.entryDao().categoryTotals(
                filter.from, filter.to, filter.type, filter.category);
    }

    public List<EntryEntity> entries(StatsFilter filter) {
        if (filter == null) {
            throw new IllegalArgumentException("统计筛选条件不能为空");
        }
        return database.entryDao().entries(
                filter.from, filter.to, filter.type, filter.category);
    }

    public long countEntries(StatsFilter filter) {
        if (filter == null) {
            throw new IllegalArgumentException("统计筛选条件不能为空");
        }
        return database.entryDao().countEntries(
                filter.from, filter.to, filter.type, filter.category);
    }

    public long addSaving(long goalId, EntryDraft draft) {
        requireValid(draft);
        if (!"SAVING".equals(draft.type) || !"SAVING".equals(draft.category)) {
            throw new IllegalArgumentException("攒钱账目必须使用攒钱类型和攒钱分类");
        }
        return database.runInTransaction(() -> {
            GoalEntity goal = database.goalDao().findById(goalId);
            if (goal == null) {
                throw new IllegalArgumentException("攒钱目标不存在，请重新选择");
            }
            if (!GoalEntity.STATUS_ACTIVE.equals(goal.status)) {
                throw new IllegalArgumentException("该攒钱目标已完成，不能继续存入");
            }
            long updatedProgress;
            try {
                updatedProgress = Math.addExact(goal.currentFen, draft.amountFen);
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException("攒钱进度超出可支持范围", e);
            }

            EntryEntity entry = entryFrom(draft, EntryEntity.SOURCE_MANUAL, goalId);
            long entryId = database.entryDao().insert(entry);
            goal.currentFen = updatedProgress;
            if (updatedProgress >= goal.targetFen) {
                goal.status = GoalEntity.STATUS_COMPLETED;
            }
            goal.updatedAt = entry.updatedAt;
            database.goalDao().update(goal);
            return entryId;
        });
    }

    public long countAll() {
        return database.entryDao().countAll();
    }

    public List<EntryEntity> allEntries() {
        return database.entryDao().allEntries();
    }

    public List<String> allRequestKeys() {
        return database.entryDao().allRequestKeys();
    }

    public List<GoalEntity> goals() {
        return database.goalDao().findAll();
    }

    public GoalEntity activeGoal() {
        return database.goalDao().activeGoal();
    }

    public long createGoal(String name, long targetFen, String targetDate) {
        String cleanName = name == null ? "" : name.trim();
        if (cleanName.isEmpty()) {
            throw new IllegalArgumentException("目标名称不能为空，请填写后再保存");
        }
        if (cleanName.length() > 30) {
            throw new IllegalArgumentException("目标名称最多 30 字，请精简后再保存");
        }
        if (targetFen <= 0 || targetFen > Money.MAX_FEN) {
            throw new IllegalArgumentException("目标金额须大于 0 且不超过 1,000,000.00 元");
        }
        LocalDate date;
        try {
            date = LocalDate.parse(targetDate == null ? "" : targetDate.trim());
        } catch (DateTimeParseException error) {
            throw new IllegalArgumentException("目标日期格式无效，请使用 YYYY-MM-DD", error);
        }
        if (date.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("目标日期不能早于今天，请重新选择");
        }

        long now = System.currentTimeMillis();
        GoalEntity goal = new GoalEntity();
        goal.name = cleanName;
        goal.targetFen = targetFen;
        goal.targetDate = date.toString();
        goal.status = GoalEntity.STATUS_ACTIVE;
        goal.currentFen = 0L;
        goal.createdAt = now;
        goal.updatedAt = now;
        return database.goalDao().insert(goal);
    }

    public int importEntries(List<EntryEntity> entries) {
        if (entries == null || entries.isEmpty()) {
            return 0;
        }
        return database.runInTransaction(() -> {
            int inserted = 0;
            for (EntryEntity entry : entries) {
                if (entry == null || entry.requestKey == null
                        || entry.requestKey.trim().isEmpty()) {
                    continue;
                }
                if (database.entryDao().findByRequestKey(entry.requestKey) != null) {
                    continue;
                }
                entry.id = 0L;
                entry.status = EntryEntity.STATUS_POSTED;
                database.entryDao().insert(entry);
                inserted++;
            }
            return inserted;
        });
    }

    private static void requireValid(EntryDraft draft) {
        String error = EntryValidator.validate(draft);
        if (error != null) {
            throw new IllegalArgumentException(error);
        }
    }

    private static EntryEntity entryFrom(EntryDraft draft, String source, Long goalId) {
        long now = System.currentTimeMillis();
        EntryEntity entry = new EntryEntity();
        entry.type = draft.type;
        entry.amountFen = draft.amountFen;
        entry.category = draft.category;
        entry.merchant = draft.merchant;
        entry.title = draft.title;
        entry.detail = draft.detail;
        entry.occurredOn = draft.occurredOn.toString();
        entry.occurredAt = now;
        entry.source = source;
        entry.status = EntryEntity.STATUS_POSTED;
        entry.categoryInferred = false;
        entry.confidence = 0.0;
        entry.requestKey = UUID.randomUUID().toString();
        entry.goalId = goalId;
        entry.createdAt = now;
        entry.updatedAt = now;
        return entry;
    }
}
