package com.example.yazhang.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.database.sqlite.SQLiteConstraintException;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.yazhang.domain.CategoryTotal;
import com.example.yazhang.domain.EntryDraft;
import com.example.yazhang.domain.HomeSummary;
import com.example.yazhang.domain.StatsFilter;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class LedgerRepositoryTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 9);

    private AppDatabase database;
    private LedgerRepository repository;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        repository = new LedgerRepository(database);
    }

    @After
    public void tearDown() {
        database.close();
    }

    @Test
    public void homeFallsBackToYesterdayThenEmptyAndCountsOnlyTodayExpenses() {
        assertEquals(HomeSummary.Kind.EMPTY, repository.home(TODAY).kind);
        assertEquals(0L, repository.home(TODAY).totalFen);

        repository.create(EntryDraft.expense(1230L, "FOOD", TODAY.minusDays(1)));
        assertEquals(HomeSummary.Kind.YESTERDAY, repository.home(TODAY).kind);
        assertEquals(1230L, repository.home(TODAY).totalFen);

        repository.create(EntryDraft.expense(1880L, "FOOD", TODAY));
        repository.create(EntryDraft.expense(200L, "TRANSPORT", TODAY));
        assertEquals(HomeSummary.Kind.TODAY, repository.home(TODAY).kind);
        assertEquals(2080L, repository.home(TODAY).totalFen);
    }

    @Test
    public void homeIgnoresIncomeSavingAndNonPostedEntries() {
        repository.create(new EntryDraft(
                "INCOME", 500000L, "SALARY", null, null, null, TODAY));
        database.entryDao().insert(nonPostedExpense(999L, TODAY));

        assertEquals(HomeSummary.Kind.EMPTY, repository.home(TODAY).kind);
    }

    @Test
    public void statisticsUseDateTypeAndCategoryAndOnlyPostedRows() {
        repository.create(EntryDraft.expense(1000L, "FOOD", TODAY.minusDays(2)));
        repository.create(EntryDraft.expense(1880L, "FOOD", TODAY.minusDays(1)));
        repository.create(EntryDraft.expense(500L, "TRANSPORT", TODAY));
        repository.create(new EntryDraft(
                "INCOME", 300000L, "SALARY", null, null, null, TODAY));
        database.entryDao().insert(nonPostedExpense(777L, TODAY));

        StatsFilter foodFilter = StatsFilter.of(
                TODAY.minusDays(2).toString(), TODAY.toString(), "EXPENSE", "FOOD");
        List<CategoryTotal> foodTotals = repository.stats(foodFilter);
        assertEquals(1, foodTotals.size());
        assertEquals("FOOD", foodTotals.get(0).category);
        assertEquals(2880L, foodTotals.get(0).totalFen);

        StatsFilter allFilter = StatsFilter.of(
                TODAY.toString(), TODAY.toString(), "ALL", "ALL");
        List<CategoryTotal> allTotals = repository.stats(allFilter);
        assertEquals(2, allTotals.size());
        assertEquals(500L, totalFor(allTotals, "TRANSPORT"));
        assertEquals(300000L, totalFor(allTotals, "SALARY"));
    }

    @Test
    public void entriesAreNewestFirstAndLimitedToFifty() {
        for (int i = 0; i < 55; i++) {
            repository.create(EntryDraft.expense(100L + i, "FOOD", TODAY));
        }

        StatsFilter filter = StatsFilter.of(TODAY.toString(), TODAY.toString(), "EXPENSE", "ALL");
        List<EntryEntity> entries = repository.entries(filter);

        assertEquals(50, entries.size());
        assertEquals(154L, entries.get(0).amountFen);
        assertEquals(105L, entries.get(49).amountFen);
    }

    @Test
    public void requestKeyIsUniqueAtTheDatabaseBoundary() {
        EntryEntity first = postedExpense(1200L, TODAY, "same-key");
        EntryEntity duplicate = postedExpense(1300L, TODAY, "same-key");

        database.entryDao().insert(first);
        assertThrows(SQLiteConstraintException.class, () -> database.entryDao().insert(duplicate));
        assertEquals(1, repository.entries(
                StatsFilter.of(TODAY.toString(), TODAY.toString(), "EXPENSE", "ALL")).size());
    }

    @Test
    public void addSavingCreatesEntryAndUpdatesGoalInOneTransaction() {
        long goalId = database.goalDao().insert(new GoalEntity(
                0L, "旅行", 100000L, TODAY.plusMonths(2).toString(), "ACTIVE", 5000L, 1L, 1L));
        EntryDraft saving = new EntryDraft(
                "SAVING", 20000L, "SAVING", null, "攒一笔", null, TODAY);

        long entryId = repository.addSaving(goalId, saving);

        EntryEntity entry = database.entryDao().findById(entryId);
        assertNotNull(entry);
        assertEquals("SAVING", entry.type);
        assertEquals("SAVING", entry.category);
        assertEquals(20000L, entry.amountFen);
        assertEquals(goalId, entry.goalId.longValue());
        GoalEntity goal = database.goalDao().findById(goalId);
        assertEquals(25000L, goal.currentFen);
        assertEquals("ACTIVE", goal.status);

        repository.addSaving(goalId, new EntryDraft(
                "SAVING", 80000L, "SAVING", null, "补足", null, TODAY));
        assertEquals(105000L, database.goalDao().findById(goalId).currentFen);
        assertEquals("COMPLETED", database.goalDao().findById(goalId).status);
    }

    @Test
    public void failedSavingDoesNotLeaveAPartialEntry() {
        long entriesBefore = repository.countAll();

        assertThrows(IllegalArgumentException.class,
                () -> repository.addSaving(404L, new EntryDraft(
                        "SAVING", 1000L, "SAVING", null, null, null, TODAY)));
        assertThrows(IllegalArgumentException.class,
                () -> repository.addSaving(404L, EntryDraft.expense(1000L, "FOOD", TODAY)));

        assertEquals(entriesBefore, repository.countAll());
    }

    @Test
    public void createGoalStoresActiveGoalAndRejectsInvalidInput() {
        long goalId = repository.createGoal(
                "旅行基金", 100000L, TODAY.plusMonths(2).toString());

        GoalEntity goal = repository.activeGoal();
        assertNotNull(goal);
        assertEquals(goalId, goal.id);
        assertEquals("旅行基金", goal.name);
        assertEquals(100000L, goal.targetFen);
        assertEquals(0L, goal.currentFen);
        assertEquals(GoalEntity.STATUS_ACTIVE, goal.status);

        assertThrows(IllegalArgumentException.class,
                () -> repository.createGoal("  ", 100000L, TODAY.plusMonths(1).toString()));
        assertThrows(IllegalArgumentException.class,
                () -> repository.createGoal("旅行", 0L, TODAY.plusMonths(1).toString()));
        assertThrows(IllegalArgumentException.class,
                () -> repository.createGoal("旅行", 100000L, "2020-01-01"));
        assertThrows(IllegalArgumentException.class,
                () -> repository.createGoal("旅行", 100000L, "2026-02-30"));
    }

    @Test
    public void importEntriesSkipsExistingAndDuplicateKeysInOneTransaction() {
        EntryEntity existing = postedExpense(500L, TODAY, "existing");
        database.entryDao().insert(existing);

        int imported = repository.importEntries(java.util.Arrays.asList(
                postedExpense(1000L, TODAY, "new"),
                postedExpense(2000L, TODAY, "existing"),
                postedExpense(3000L, TODAY, "new")
        ));

        assertEquals(1, imported);
        assertEquals(2L, repository.countAll());
        assertEquals(2, repository.entries(
                StatsFilter.of(TODAY.toString(), TODAY.toString(), "EXPENSE", "ALL")).size());
        assertNotNull(database.entryDao().findByRequestKey("new"));
        assertEquals(0, repository.importEntries(java.util.Collections.emptyList()));
    }

    @Test
    public void backupReadsAllEntriesAndRequestKeys() {
        repository.create(EntryDraft.expense(1000L, "FOOD", TODAY));
        repository.create(EntryDraft.expense(2000L, "TRANSPORT", TODAY.minusDays(1)));
        database.entryDao().insert(nonPostedExpense(3000L, TODAY));

        assertEquals(3, repository.allEntries().size());
        assertEquals(3, repository.allRequestKeys().size());
    }

    @Test
    public void createAiStoresMetadataAndDeduplicatesTheSameImage() {
        String sha = "a1b2c3d4e5f60718293a4b5c6d7e8f90"
                + "a1b2c3d4e5f60718293a4b5c6d7e8f90";
        EntryDraft draft = new EntryDraft(
                "EXPENSE", 1230L, "FOOD", "茉酸奶", null, null, TODAY);

        long id = repository.createAi(draft, sha, 0.93);
        EntryEntity entry = database.entryDao().findById(id);

        assertNotNull(entry);
        assertEquals(EntryEntity.SOURCE_AI, entry.source);
        assertEquals(sha, entry.imageSha);
        assertEquals(0.93, entry.confidence, 0.0001);
        assertEquals("ai:" + sha, entry.requestKey);
        assertEquals(true, entry.categoryInferred);
        assertEquals(true, repository.hasImage(sha));

        assertEquals(-1L, repository.createAi(draft, sha, 0.93));
        assertEquals(1L, repository.countAll());
    }

    @Test
    public void createAiRejectsInvalidImageHashAndConfidence() {
        EntryDraft draft = EntryDraft.expense(1000L, "FOOD", TODAY);

        assertThrows(IllegalArgumentException.class,
                () -> repository.createAi(draft, "not-a-sha", 0.9));
        assertThrows(IllegalArgumentException.class,
                () -> repository.createAi(draft,
                        "a1b2c3d4e5f60718293a4b5c6d7e8f90"
                                + "a1b2c3d4e5f60718293a4b5c6d7e8f90",
                        1.1));
        assertEquals(0L, repository.countAll());
    }

    private static EntryEntity postedExpense(long amountFen, LocalDate date, String requestKey) {
        EntryEntity entry = new EntryEntity();
        entry.type = "EXPENSE";
        entry.amountFen = amountFen;
        entry.category = "FOOD";
        entry.occurredOn = date.toString();
        entry.occurredAt = 1L;
        entry.source = "MANUAL";
        entry.status = "POSTED";
        entry.requestKey = requestKey;
        return entry;
    }

    private static EntryEntity nonPostedExpense(long amountFen, LocalDate date) {
        EntryEntity entry = postedExpense(amountFen, date, UUID.randomUUID().toString());
        entry.status = "DRAFT";
        return entry;
    }

    private static long totalFor(List<CategoryTotal> totals, String category) {
        for (CategoryTotal total : totals) {
            if (category.equals(total.category)) {
                return total.totalFen;
            }
        }
        return -1L;
    }
}
