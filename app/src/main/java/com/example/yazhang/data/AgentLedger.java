package com.example.yazhang.data;

import com.example.yazhang.domain.CategoryTotal;
import com.example.yazhang.domain.EntryDraft;
import com.example.yazhang.domain.StatsFilter;

import java.util.List;

/** The only ledger operations the local Agent may use. */
public interface AgentLedger {

    long create(EntryDraft draft);

    long addSaving(long goalId, EntryDraft draft);

    List<CategoryTotal> stats(StatsFilter filter);

    List<EntryEntity> entries(StatsFilter filter);

    long countEntries(StatsFilter filter);

    List<GoalEntity> goals();

    GoalEntity activeGoal();
}
