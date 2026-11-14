package com.example.yazhang.ui;

import android.os.Bundle;

import com.example.yazhang.LedgerActivity;
import com.example.yazhang.data.AppDatabase;
import com.example.yazhang.data.LedgerRepository;

public final class StatsActivity extends LedgerActivity {

    private StatsView page;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LedgerRepository repository = new LedgerRepository(AppDatabase.get(this));
        page = new StatsView(this, repository);
        showLayout(1, page.getView());
    }

    @Override
    protected void onResume() {
        super.onResume();
        page.refresh();
    }
}
